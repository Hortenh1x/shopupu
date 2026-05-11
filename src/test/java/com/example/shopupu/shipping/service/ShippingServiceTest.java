package com.example.shopupu.shipping.service;

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
import com.example.shopupu.shipping.mapper.ShippingMapper;
import com.example.shopupu.shipping.repository.ShipmentRepository;
import com.example.shopupu.shipping.repository.ShippingAddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private ShippingService shippingService;
    private Order order;

    // handles setUp.
    @BeforeEach
    void setUp() {
        shippingService = new ShippingService(
                orderRepository,
                shipmentRepository,
                addressRepository,
                new ShippingMapper(),
                new ShippingProperties(),
                accessControlService,
                orderService
        );
        order = order(1L, OrderStatus.NEW);
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
    void setAddressRejectsMissingOrderInvalidAddressAndNonNewOrder() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> shippingService.setAddress(new SetShippingAddressRequest(404L, "A", "B", null, "C", "D", "E", "F")));

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        assertThrows(BadRequestException.class, () -> shippingService.setAddress(new SetShippingAddressRequest(1L, "", "B", null, "C", "D", "E", "F")));

        Order paid = order(2L, OrderStatus.PAID);
        when(orderRepository.findById(2L)).thenReturn(Optional.of(paid));
        assertThrows(BusinessRuleException.class, () -> shippingService.setAddress(new SetShippingAddressRequest(2L, "A", "B", null, "C", "D", "E", "F")));
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
    }

    private SetShippingAddressRequest addressRequest() {
        return new SetShippingAddressRequest(1L, "User", "Line 1", null, "City", "State", "12345", "DE");
    }

    private Order order(Long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setUser(User.builder().id(1L).email("user@example.com").build());
        order.setStatus(status);
        return order;
    }
}
