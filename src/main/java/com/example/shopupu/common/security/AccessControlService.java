package com.example.shopupu.common.security;

import com.example.shopupu.common.exception.ForbiddenOperationException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.orders.entity.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final UserRepository userRepository;

    public User currentUser() {
        String email = currentEmail();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }

    public String currentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ForbiddenOperationException("Authentication is required");
        }
        return authentication.getName();
    }

    public boolean isAdmin(User user) {
        if (user.getRoles() == null) {
            return false;
        }
        for (var role : user.getRoles()) {
            if ("ADMIN".equalsIgnoreCase(role.getName())) {
                return true;
            }
        }
        return false;
    }

    public void requireAdmin() {
        if (!isAdmin(currentUser())) {
            throw new ForbiddenOperationException("Admin privileges are required");
        }
    }

    public void requireOrderOwnerOrAdmin(Order order) {
        User current = currentUser();
        if (!isOrderOwner(order, current) && !isAdmin(current)) {
            throw new ForbiddenOperationException("Access denied to this order");
        }
    }

    public void requireSameUserOrAdmin(User user) {
        User current = currentUser();
        boolean sameUser = user.getId() != null && user.getId().equals(current.getId());
        if (!sameUser && !isAdmin(current)) {
            throw new ForbiddenOperationException("Access denied to this user");
        }
    }

    private boolean isOrderOwner(Order order, User current) {
        return order.getUser() != null
                && order.getUser().getId() != null
                && order.getUser().getId().equals(current.getId());
    }
}
