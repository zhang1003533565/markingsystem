package com.markingsystem.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordServiceTest {

    private final PasswordService passwordService = new PasswordService(new BCryptPasswordEncoder());

    @Test
    void matchesBcryptPassword() {
        String encoded = passwordService.encode("admin123");

        assertTrue(passwordService.matches("admin123", encoded));
        assertFalse(passwordService.matches("wrong-password", encoded));
    }

    @Test
    void rejectsLegacyPlainTextPassword() {
        assertFalse(passwordService.matches("123456", "123456"));
        assertFalse(passwordService.shouldUpgrade("123456"));
    }

    @Test
    void rejectsBlankOrNullPasswords() {
        assertFalse(passwordService.matches(null, "123456"));
        assertFalse(passwordService.matches("123456", null));
        assertFalse(passwordService.matches("123456", ""));
    }
}
