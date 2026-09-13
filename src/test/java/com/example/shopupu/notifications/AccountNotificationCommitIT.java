package com.example.shopupu.notifications;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.example.shopupu.auth.service.AuthService;
import com.example.shopupu.identity.service.UserService;
import com.example.shopupu.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class AccountNotificationCommitIT extends PostgresContainerSupport {
    @Autowired AuthService auth;
    @Autowired UserService users;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean NotificationService sender;
    @Test void resetEmailIsOnlyDispatchedAfterCommitAndNeverOnRollback() {
        var user = users.registerUser("mail-" + System.nanoTime() + "@example.com", "A quiet mail fixture meadow");
        when(sender.isAvailable()).thenReturn(true);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            auth.forgotPassword(user.getEmail());
            verify(sender, never()).sendPasswordReset(anyString(), anyString());
            tx.setRollbackOnly();
        });
        verify(sender, never()).sendPasswordReset(anyString(), anyString());
        auth.forgotPassword(user.getEmail());
        verify(sender, timeout(5000).times(1)).sendPasswordReset(eq(user.getEmail()), anyString());
    }
}
