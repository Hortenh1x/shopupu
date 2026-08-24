package com.example.shopupu.payments.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.shopupu.payments.entity.PaymentStatus;
import org.junit.jupiter.api.Test;

class ProviderStatusMappingTest {

    @Test
    void monobankInvoiceStatusesMapToLocalStatuses() {
        assertEquals(PaymentStatus.PENDING, MonobankPaymentGatewayClient.mapInvoiceStatus("created"));
        assertEquals(PaymentStatus.PENDING, MonobankPaymentGatewayClient.mapInvoiceStatus("processing"));
        assertEquals(PaymentStatus.PENDING, MonobankPaymentGatewayClient.mapInvoiceStatus("hold"));
        assertEquals(PaymentStatus.SUCCEEDED, MonobankPaymentGatewayClient.mapInvoiceStatus("success"));
        assertEquals(PaymentStatus.FAILED, MonobankPaymentGatewayClient.mapInvoiceStatus("failure"));
        assertEquals(PaymentStatus.REFUNDED, MonobankPaymentGatewayClient.mapInvoiceStatus("reversed"));
        assertEquals(PaymentStatus.EXPIRED, MonobankPaymentGatewayClient.mapInvoiceStatus("expired"));
        assertNull(MonobankPaymentGatewayClient.mapInvoiceStatus("some-new-status"));
        assertNull(MonobankPaymentGatewayClient.mapInvoiceStatus(null));
    }

    @Test
    void fondyOrderStatusesMapToLocalStatuses() {
        assertEquals(PaymentStatus.PENDING, FondyPaymentGatewayClient.mapOrderStatus("created"));
        assertEquals(PaymentStatus.PENDING, FondyPaymentGatewayClient.mapOrderStatus("processing"));
        assertEquals(PaymentStatus.SUCCEEDED, FondyPaymentGatewayClient.mapOrderStatus("approved"));
        assertEquals(PaymentStatus.FAILED, FondyPaymentGatewayClient.mapOrderStatus("declined"));
        assertEquals(PaymentStatus.EXPIRED, FondyPaymentGatewayClient.mapOrderStatus("expired"));
        assertEquals(PaymentStatus.REFUNDED, FondyPaymentGatewayClient.mapOrderStatus("reversed"));
        assertNull(FondyPaymentGatewayClient.mapOrderStatus("unexpected"));
        assertNull(FondyPaymentGatewayClient.mapOrderStatus(null));
    }
}
