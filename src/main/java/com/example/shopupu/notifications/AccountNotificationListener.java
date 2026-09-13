package com.example.shopupu.notifications;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AccountNotificationListener {
    private final NotificationService sender;
    private final NotificationDelivery delivery;
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountNotification(AccountNotificationEvent event) {
        delivery.enqueue(event.kind().name(), () -> {
            if (event.kind() == AccountNotificationEvent.Kind.PASSWORD_RESET) sender.sendPasswordReset(event.email(), event.token());
            else sender.sendEmailVerification(event.email(), event.token());
        });
    }
}
