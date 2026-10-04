package com.markingsystem.service;

import com.markingsystem.entity.SystemSetting;
import com.markingsystem.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingService {

    public static final String FORCE_EMAIL_BINDING_REQUIRED_KEY = "force_email_binding_required";

    private final SystemSettingRepository systemSettingRepository;

    public SystemSettingService(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    public boolean isForceEmailBindingRequired() {
        return systemSettingRepository.findBySettingKey(FORCE_EMAIL_BINDING_REQUIRED_KEY)
                .map(SystemSetting::getSettingValue)
                .map(value -> !"false".equalsIgnoreCase(value.trim()))
                .orElse(true);
    }

    @Transactional
    public boolean setForceEmailBindingRequired(boolean required) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(FORCE_EMAIL_BINDING_REQUIRED_KEY)
                .orElseGet(() -> {
                    SystemSetting nextSetting = new SystemSetting();
                    nextSetting.setSettingKey(FORCE_EMAIL_BINDING_REQUIRED_KEY);
                    nextSetting.setDescription("控制学生和班委是否必须先绑定邮箱");
                    return nextSetting;
                });
        setting.setSettingValue(Boolean.toString(required));
        systemSettingRepository.save(setting);
        return required;
    }
}
