package com.markingsystem.service;

import com.markingsystem.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentAccountSyncServiceTest {

    private final PasswordService passwordService = new PasswordService(new BCryptPasswordEncoder());
    private final StudentAccountSyncService service = new StudentAccountSyncService(
            null,
            null,
            null,
            null,
            passwordService
    );

    @Test
    void requiresPasswordChangeWhenImportedPasswordStillMatches() {
        User user = user(passwordService.encode("Init123!"), false);

        assertTrue(service.shouldRequireInitialPasswordChange(user, "Init123!"));
    }

    @Test
    void keepsPasswordResetClearedWhenPasswordWasAlreadyChanged() {
        User user = user(passwordService.encode("Changed123!"), false);

        assertFalse(service.shouldRequireInitialPasswordChange(user, "Init123!"));
    }

    @Test
    void keepsExistingPasswordResetRequirement() {
        User user = user(passwordService.encode("Init123!"), true);

        assertFalse(service.shouldRequireInitialPasswordChange(user, "Init123!"));
    }

    private User user(String password, boolean passwordResetRequired) {
        User user = new User();
        user.setPassword(password);
        user.setPasswordResetRequired(passwordResetRequired);
        return user;
    }
}
