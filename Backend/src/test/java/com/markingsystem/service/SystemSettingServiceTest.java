package com.markingsystem.service;

import com.markingsystem.entity.SystemSetting;
import com.markingsystem.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemSettingServiceTest {

    @Test
    void defaultsToRequiredEmailBindingWhenSettingIsMissing() {
        SystemSettingRepository repository = repository(new HashMap<>());
        SystemSettingService service = new SystemSettingService(repository);

        assertTrue(service.isForceEmailBindingRequired());
    }

    @Test
    void savesEmailBindingRequirementOnExistingSetting() {
        SystemSetting existing = new SystemSetting();
        existing.setSettingKey(SystemSettingService.FORCE_EMAIL_BINDING_REQUIRED_KEY);
        existing.setSettingValue("true");
        Map<String, SystemSetting> settings = new HashMap<>();
        settings.put(SystemSettingService.FORCE_EMAIL_BINDING_REQUIRED_KEY, existing);
        SystemSettingRepository repository = repository(settings);
        SystemSettingService service = new SystemSettingService(repository);

        assertFalse(service.setForceEmailBindingRequired(false));
        assertEquals("false", existing.getSettingValue());
        assertEquals(existing, settings.get(SystemSettingService.FORCE_EMAIL_BINDING_REQUIRED_KEY));
    }

    private SystemSettingRepository repository(Map<String, SystemSetting> settings) {
        return (SystemSettingRepository) Proxy.newProxyInstance(
                SystemSettingRepository.class.getClassLoader(),
                new Class<?>[]{SystemSettingRepository.class},
                (proxy, method, args) -> {
                    if ("findBySettingKey".equals(method.getName())) {
                        return Optional.ofNullable(settings.get((String) args[0]));
                    }
                    if ("save".equals(method.getName())) {
                        SystemSetting setting = (SystemSetting) args[0];
                        settings.put(setting.getSettingKey(), setting);
                        return setting;
                    }
                    if ("toString".equals(method.getName())) {
                        return "InMemorySystemSettingRepository";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
