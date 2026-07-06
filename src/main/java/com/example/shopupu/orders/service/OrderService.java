package com.example.shopupu.orders.service;

import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.cart.entity.CartItem;
import com.example.shopupu.cart.repository.CartItemRepository;
import com.example.shopupu.cart.repository.CartRepository;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductVariant;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.config.CheckoutProperties;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.inventory.service.InventoryService;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderItem;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.entity.OrderStatusHistory;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.orders.repository.OrderStatusHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /** States where inventory is held only as a reservation (not yet sold). */
    private static final EnumSet<OrderStatus> RESERVED_STATES =
            EnumSet.of(OrderStatus.CREATED, OrderStatus.PENDING_PAYMENT);

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;
    private final AccessControlService accessControlService;
    private final InventoryService inventoryService;
    private final OrderNumberGenerator orderNumberGenerator;
    private final CheckoutProperties checkoutProperties;
    private final com.example.shopupu.promo.service.PromoService promoService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.example.shopupu.common.audit.AuditService auditService;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private final com.example.shopupu.orders.mapper.OrderMapper orderMapper;

    @Transactional
    public Order createOrderFromCart(User user, String idempotencyKey) {
        return createOrderFromCart(user, idempotencyKey, null);
    }

    /**
     * Checkout (ORD-02/ORD-03/INV-02): reserves stock atomically, snapshots
     * variant data into order items and is idempotent per Idempotency-Key.
     */
    @Transactional
    public Order createOrderFromCart(User user, String idempotencyKey, String promoCode) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Order> existing = orderRepository.findByUserAndIdempotencyKey(user, idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        List<CartItem> cartItems = cartItemRepository.findByCart(cart);
        if (cartItems.isEmpty()) {
            throw new BusinessRuleException("Cart is empty - nothing to order");
        }

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.CREATED);
        order.setOrderNumber(orderNumberGenerator.next());
        order.setIdempotencyKey(idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey);

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            ProductVariant variant = cartItem.getVariant();
            Product product = variant.getProduct();
            validateSellable(variant);

            // atomic reservation - throws OutOfStockException when short (INV-03)
            inventoryService.reserve(variant.getId(), cartItem.getQuantity(),
                    "order:" + order.getOrderNumber());

            // current price is the source of truth, never the cart (CART-03)
            BigDecimal price = variant.getPrice();
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            items.add(OrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .variantId(variant.getId())
                    .title(product.getTitle())
                    .sku(variant.getSku())
                    .size(variant.getSize())
                    .color(variant.getColor())
                    .brand(product.getBrand() != null ? product.getBrand().getName() : null)
                    .price(price)
                    .quantity(cartItem.getQuantity())
                    .lineTotal(lineTotal)
                    .build());
        }

        order.setItems(items);
        order.setSubtotalAmount(subtotal);
        order.setShippingAmount(BigDecimal.ZERO);

        // promo is re-validated at checkout, never trusted from an earlier apply (PROMO-02)
        com.example.shopupu.promo.entity.PromoCode promo = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (promoCode != null && !promoCode.isBlank()) {
            promo = promoService.validate(promoCode, user, subtotal);
            discount = promoService.discountFor(promo, subtotal);
            order.setPromoCode(promo.getCode());
        }
        order.setDiscountAmount(discount);
        order.setPaymentAmount(subtotal.subtract(discount).max(BigDecimal.ZERO));

        Order savedOrder;
        try {
            savedOrder = orderRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            // concurrent request with the same idempotency key won the race
            if (idempotencyKey != null) {
                throw new BusinessRuleException("Order for this Idempotency-Key is already being created");
            }
            throw e;
        }
        recordHistory(savedOrder, null, OrderStatus.CREATED, user.getEmail());
        if (promo != null) {
            promoService.redeem(promo, user, savedOrder);
        }
        cartItemRepository.deleteAll(cartItems);
        meterRegistry.counter("shopupu.orders", "event", "created").increment();
        return savedOrder;
    }

    // paged lists are mapped inside the transaction: items are lazy and OSIV is off
    @Transactional(readOnly = true)
    public Page<com.example.shopupu.orders.dto.OrderDto> getOrdersForUser(User user, OrderStatus status, Pageable pageable) {
        Page<Order> page = status == null
                ? orderRepository.findByUser(user, pageable)
                : orderRepository.findByUserAndStatus(user, status, pageable);
        return page.map(orderMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<com.example.shopupu.orders.dto.OrderDto> getAllOrders(OrderStatus status, Pageable pageable) {
        accessControlService.requireAdmin();
        Page<Order> page = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);
        return page.map(orderMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistory> getStatusHistory(Long orderId) {
        return statusHistoryRepository.findByOrder_IdOrderByCreatedAtAsc(orderId);
    }

    public Order getOrder(Long id) {
        return orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found - " + id));
    }

    public Order getOrderForCurrentUser(Long id) {
        Order order = getOrder(id);
        accessControlService.requireOrderOwnerOrAdmin(order);
        return order;
    }

    @Transactional
    public Order updateStatus(Long id, OrderStatus newStatus) {
        accessControlService.requireAdmin();
        Order order = getOrder(id);
        Order updated = applyStatus(order, newStatus, accessControlService.currentEmail());
        auditService.record(accessControlService.currentEmail(), "ORDER_STATUS_CHANGED",
                "order", updated.getOrderNumber(), "-> " + newStatus);
        return updated;
    }

    /** Customer/admin cancellation; releases the inventory reservation (ORD-06). */
    @Transactional
    public Order cancelOrder(Long id) {
        Order order = getOrder(id);
        accessControlService.requireOrderOwnerOrAdmin(order);
        if (!RESERVED_STATES.contains(order.getStatus())) {
            throw new BusinessRuleException("Order can no longer be cancelled; request a refund instead");
        }
        return applyStatus(order, OrderStatus.CANCELLED, accessControlService.currentEmail());
    }

    /** Payment webhook confirmed: reservation becomes a sale. */
    @Transactional
    public Order markPaidFromPayment(Long id) {
        Order order = getOrder(id);
        return applyStatus(order, OrderStatus.PAID, "payment-callback");
    }

    /** Payment initiated: hold the order for the payment flow. */
    @Transactional
    public Order markPendingPayment(Long id) {
        Order order = getOrder(id);
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            return order;
        }
        return applyStatus(order, OrderStatus.PENDING_PAYMENT, "payment");
    }

    /** Payment failed/expired: return the order to CREATED so the user can retry. */
    @Transactional
    public Order onPaymentFailed(Long id) {
        Order order = getOrder(id);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return order;
        }
        return applyStatus(order, OrderStatus.CREATED, "payment-callback");
    }

    /** Refund: money returned, goods go back to stock (ORD-07). */
    @Transactional
    public Order markRefunded(Long id, String actor) {
        Order order = getOrder(id);
        return applyStatus(order, OrderStatus.REFUNDED, actor);
    }

    @Transactional
    public Order updateShippingAmount(Long id, BigDecimal shippingAmount) {
        Order order = getOrder(id);
        if (!RESERVED_STATES.contains(order.getStatus())) {
            throw new BusinessRuleException("Shipping can only be changed before payment");
        }
        BigDecimal normalizedShipping = shippingAmount == null ? BigDecimal.ZERO : shippingAmount;
        order.setShippingAmount(normalizedShipping);

        // FREE_SHIPPING promos discount the shipping cost, so recalc here (ORD-04)
        BigDecimal discount = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();
        if (order.getPromoCode() != null && promoService.isFreeShipping(order.getPromoCode())) {
            discount = normalizedShipping;
            order.setDiscountAmount(discount);
        }
        order.setPaymentAmount(order.getSubtotalAmount().add(normalizedShipping).subtract(discount)
                .max(BigDecimal.ZERO));
        return orderRepository.save(order);
    }

    /** Auto-cancel unpaid orders whose reservation TTL ran out (INV-02, CART-04 cousin). */
    @Transactional
    public int expireStaleOrders() {
        Instant cutoff = Instant.now().minus(checkoutProperties.getPendingPaymentTtlMin(), ChronoUnit.MINUTES);
        List<Order> stale = orderRepository.findTop100ByStatusInAndCreatedAtBefore(RESERVED_STATES, cutoff);
        for (Order order : stale) {
            applyStatus(order, OrderStatus.CANCELLED, "system:expiration");
        }
        return stale.size();
    }

    private Order applyStatus(Order order, OrderStatus newStatus, String actor) {
        OrderStatus current = order.getStatus();

        if (current == newStatus) {
            return order;
        }
        if (!current.canTransitionTo(newStatus)) {
            throw new BusinessRuleException("Order status " + newStatus + " is not allowed from " + current);
        }

        applyInventoryEffects(order, current, newStatus);

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        recordHistory(saved, current, newStatus, actor);
        meterRegistry.counter("shopupu.orders", "event", newStatus.name().toLowerCase()).increment();
        eventPublisher.publishEvent(new com.example.shopupu.notifications.OrderStatusChangedEvent(
                saved.getId(), saved.getOrderNumber(),
                saved.getUser() != null ? saved.getUser().getEmail() : null,
                current, newStatus));
        return saved;
    }

    private void applyInventoryEffects(Order order, OrderStatus from, OrderStatus to) {
        String reference = "order:" + order.getOrderNumber();
        if (to == OrderStatus.PAID && RESERVED_STATES.contains(from)) {
            forEachItem(order, (variantId, qty) -> inventoryService.commitSale(variantId, qty, reference));
        } else if (to == OrderStatus.CANCELLED && RESERVED_STATES.contains(from)) {
            forEachItem(order, (variantId, qty) -> inventoryService.release(variantId, qty, reference));
        } else if (to == OrderStatus.REFUNDED) {
            forEachItem(order, (variantId, qty) -> inventoryService.restock(variantId, qty, reference));
        }
    }

    private void forEachItem(Order order, ItemEffect effect) {
        for (OrderItem item : order.getItems()) {
            if (item.getVariantId() != null) {
                effect.apply(item.getVariantId(), item.getQuantity());
            }
        }
    }

    private interface ItemEffect {
        void apply(Long variantId, int quantity);
    }

    private void recordHistory(Order order, OrderStatus from, OrderStatus to, String actor) {
        statusHistoryRepository.save(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(from)
                .toStatus(to)
                .changedBy(actor)
                .build());
    }

    private void validateSellable(ProductVariant variant) {
        Product product = variant.getProduct();
        if (!Boolean.TRUE.equals(product.getEnabled()) || product.isDeleted()
                || !Boolean.TRUE.equals(variant.getEnabled())) {
            throw new BusinessRuleException("Product is not available: " + product.getId());
        }
    }
}
