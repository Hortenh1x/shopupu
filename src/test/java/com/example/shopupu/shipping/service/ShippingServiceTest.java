package com.example.shopupu.shipping.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopupu.common.exception.BadRequestException;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.config.ShippingProperties;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.orders.service.OrderService;
import com.example.shopupu.shipping.dto.SetShippingAddressRequest;
import com.example.shopupu.shipping.dto.SetShippingMethodRequest;
import com.example.shopupu.shipping.entity.Shipment;
import com.example.shopupu.shipping.entity.ShippingAddress;
import com.example.shopupu.shipping.entity.ShippingMethod;
import com.example.shopupu.shipping.entity.ShippingStatus;
import com.example.shopupu.shipping.mapper.ShippingMapperImpl;
import com.example.shopupu.shipping.repository.ShipmentRepository;
import com.example.shopupu.shipping.repository.ShippingAddressRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * describes the ShippingServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class ShippingServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShippingAddressRepository addressRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private OrderService orderService;

    private ShippingProperties shippingProperties;
    private ShippingService shippingService;
    private Order order;

    // handles setUp.
    @BeforeEach
    void setUp() {
        shippingProperties = new ShippingProperties();
        shippingProperties.setFreeShippingThreshold(new BigDecimal("100.00"));
        shippingService = new ShippingService(
                orderRepository,
                shipmentRepository,
                addressRepository,
                new ShippingMapperImpl(),
                shippingProperties,
                accessControlService,
                orderService
        );
        order = order(1L, OrderStatus.CREATED);
    }

    // handles setAddress.
    @Test
    void setAddressCreatesAddressAndDefaultShipment() {
        SetShippingAddressRequest request = addressRequest();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(addressRepository.save(any(ShippingAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipmentRepository.findByOrder(order)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = shippingService.setAddress(request);

        assertEquals(ShippingMethod.STANDARD_POST, dto.method());
        assertEquals(new BigDecimal("4.99"), dto.shippingCost());
        verify(accessControlService).requireOrderOwnerOrAdmin(order);
        verify(orderService).updateShippingAmount(1L, new BigDecimal("4.99"));
    }

    // handles setAddress.
    @Test
    void setAddressSetsAddressSnapshotOnShipment() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(addressRepository.save(any(ShippingAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipmentRepository.findByOrder(order)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shippingService.setAddress(addressRequest());

        ArgumentCaptor<Shipment> captor = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository).save(captor.capture());
        assertEquals("User, Line 1, City, State 12345, DE", captor.getValue().getAddressSnapshot());
        verify(addressRepository, never()).delete(any(ShippingAddress.class));
    }

    // handles setAddress.
    @Test
    void setAddressReplacesPreviousAddressAndDeletesOldRow() {
        ShippingAddress previous = ShippingAddress.builder()
                .fullName("Old User")
                .line1("Old Line")
                .city("Old City")
                .state("Old State")
                .postalCode("00000")
                .country("PL")
                .build();
        Shipment shipment = Shipment.builder()
                .order(order)
                .address(previous)
                .method(ShippingMethod.DHL)
                .status(ShippingStatus.PENDING)
                .cost(new BigDecimal("9.99"))
                .currency("EUR")
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(addressRepository.save(any(ShippingAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shipmentRepository.findByOrder(order)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = shippingService.setAddress(addressRequest());

        assertEquals("User", dto.address().fullName());
        assertEquals("User, Line 1, City, State 12345, DE", shipment.getAddressSnapshot());
        assertEquals("Line 1", shipment.getAddress().getLine1());
        verify(addressRepository).delete(previous);
        verify(orderService).updateShippingAmount(1L, new BigDecimal("9.99"));
    }

    // handles setAddress.
    @Test
    void setAddressRejectsMissingOrderInvalidAddressAndNonCreatedOrder() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> shippingService.setAddress(new SetShippingAddressRequest(404L, "A", "B", null, "C", "D", "E", "F")));

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        assertThrows(BadRequestException.class, () -> shippingService.setAddress(new SetShippingAddressRequest(1L, "", "B", null, "C", "D", "E", "F")));

        Order paid = order(2L, OrderStatus.PAID);
        when(orderRepository.findById(2L)).thenReturn(Optional.of(paid));
        assertThrows(BusinessRuleException.class, () -> shippingService.setAddress(new SetShippingAddressRequest(2L, "A", "B", null, "C", "D", "E", "F")));

        Order pendingPayment = order(3L, OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findById(3L)).thenReturn(Optional.of(pendingPayment));
        assertThrows(BusinessRuleException.class, () -> shippingService.setAddress(new SetShippingAddressRequest(3L, "A", "B", null, "C", "D", "E", "F")));

        verify(shipmentRepository, never()).save(any(Shipment.class));
    }

    // handles setMethod.
    @Test
    void setMethodCreatesOrUpdatesShipmentCost() {
        SetShippingMethodRequest request = new SetShippingMethodRequest(1L, ShippingMethod.DHL);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrder(order)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = shippingService.setMethod(request);

        assertEquals(ShippingMethod.DHL, dto.method());
        assertEquals(new BigDecimal("9.99"), dto.shippingCost());
        verify(orderService).updateShippingAmount(1L, new BigDecimal("9.99"));
    }

    // handles setMethod.
    @Test
    void setMethodIsFreeWhenSubtotalAboveThreshold() {
        order.setSubtotalAmount(new BigDecimal("150.00"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrder(order)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = shippingService.setMethod(new SetShippingMethodRequest(1L, ShippingMethod.DHL));

        assertEquals(ShippingMethod.DHL, dto.method());
        assertEquals(BigDecimal.ZERO, dto.shippingCost());
        verify(orderService).updateShippingAmount(1L, BigDecimal.ZERO);
    }

    // handles setMethod.
    @Test
    void setMethodIsFreeWhenSubtotalExactlyAtThreshold() {
        order.setSubtotalAmount(new BigDecimal("100.00"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrder(order)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = shippingService.setMethod(new SetShippingMethodRequest(1L, ShippingMethod.STANDARD_POST));

        assertEquals(BigDecimal.ZERO, dto.shippingCost());
        verify(orderService).updateShippingAmount(1L, BigDecimal.ZERO);
    }

    // handles setMethod.
    @Test
    void setMethodChargesWhenSubtotalBelowThreshold() {
        order.setSubtotalAmount(new BigDecimal("99.99"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrder(order)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = shippingService.setMethod(new SetShippingMethodRequest(1L, ShippingMethod.STANDARD_POST));

        assertEquals(new BigDecimal("4.99"), dto.shippingCost());
    }

    // handles setMethod.
    @Test
    void setMethodRejectsNonCreatedOrders() {
        Order pendingPayment = order(2L, OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findById(2L)).thenReturn(Optional.of(pendingPayment));
        assertThrows(BusinessRuleException.class,
                () -> shippingService.setMethod(new SetShippingMethodRequest(2L, ShippingMethod.DHL)));

        Order paid = order(3L, OrderStatus.PAID);
        when(orderRepository.findById(3L)).thenReturn(Optional.of(paid));
        assertThrows(BusinessRuleException.class,
                () -> shippingService.setMethod(new SetShippingMethodRequest(3L, ShippingMethod.DHL)));

        verify(shipmentRepository, never()).save(any(Shipment.class));
    }

    // handles getByOrder.
    @Test
    void getByOrderReturnsEmptyShipmentDtoWhenShipmentMissing() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrder(order)).thenReturn(Optional.empty());

        var dto = shippingService.getByOrder(1L);

        assertEquals(1L, dto.orderId());
        assertNull(dto.method());
        assertEquals("EUR", dto.currency());
    }

    // handles getByOrder.
    @Test
    void getByOrderReturnsExistingShipment() {
        Shipment shipment = Shipment.builder()
                .order(order)
                .method(ShippingMethod.LOCAL_PICKUP)
                .status(ShippingStatus.PENDING)
                .cost(BigDecimal.ZERO)
                .currency("EUR")
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrder(order)).thenReturn(Optional.of(shipment));

        var dto = shippingService.getByOrder(1L);

        assertEquals(ShippingMethod.LOCAL_PICKUP, dto.method());
        assertEquals(BigDecimal.ZERO, dto.shippingCost());
    }

    // handles updateStatus.
    @Test
    void updateStatusRequiresAdminAndUpdatesShipment() {
        Shipment shipment = Shipment.builder()
                .order(order)
                .method(ShippingMethod.DHL)
                .status(ShippingStatus.PENDING)
                .cost(new BigDecimal("9.99"))
                .currency("EUR")
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrder(order)).thenReturn(Optional.of(shipment));

        var dto = shippingService.updateStatus(1L, ShippingStatus.SHIPPED, "track-1");

        assertEquals(ShippingStatus.SHIPPED, dto.shippingStatus());
        assertEquals("track-1", dto.trackingNumber());
        verify(accessControlService).requireAdmin();
        verify(shipmentRepository).save(shipment);
        assertSame(order, shipment.getOrder());
    }

    private SetShippingAddressRequest addressRequest() {
        return new SetShippingAddressRequest(1L, "User", "Line 1", null, "City", "State", "12345", "DE");
    }

    private Order order(Long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setUser(User.builder().id(1L).email("user@example.com").build());
        order.setStatus(status);
        order.setSubtotalAmount(BigDecimal.ZERO);
        return order;
    }
}
