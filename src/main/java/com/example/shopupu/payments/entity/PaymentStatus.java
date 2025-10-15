package com.example.shopupu.payments.entity;

/** RU: Статусы платежа. EN: Payment statuses. */
public enum PaymentStatus {
    NEW,              // RU: создан, но ещё не инициирован у провайдера / EN: created, not yet started
    PENDING,          // RU: нужна 3DS/подтверждение / EN: needs user action (3DS/SCA)
    AUTHORIZED,       // RU: заморожены средства / EN: funds authorized (hold)
    CAPTURED,         // RU: списание прошло / EN: captured (charged)
    CANCELED,         // RU: отменён / EN: canceled
    FAILED,           // RU: ошибка / EN: failed
    REFUNDED          // RU: возвращён / EN: refunded
}
