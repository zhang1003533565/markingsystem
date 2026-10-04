package com.markingsystem.service;

import com.markingsystem.entity.SystemEmail;
import com.markingsystem.repository.SystemEmailRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SystemEmailService {

    @Autowired
    private SystemEmailRepository systemEmailRepository;

    public List<SystemEmail> list() {
        return systemEmailRepository.findAll();
    }

    public List<SystemEmail> listActive() {
        return systemEmailRepository.findByIsActiveTrue();
    }

    public Optional<SystemEmail> getById(Long id) {
        return systemEmailRepository.findById(id);
    }

    @Transactional
    public SystemEmail add(String email, String password) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱地址不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }

        Optional<SystemEmail> existing = systemEmailRepository.findByEmailAndIsActiveTrue(email);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("该邮箱已存在");
        }

        long count = systemEmailRepository.count();
        SystemEmail systemEmail = new SystemEmail();
        systemEmail.setEmail(email.trim());
        systemEmail.setPassword(password);
        systemEmail.setIsActive(true);
        systemEmail.setIsDefault(count == 0);
        return systemEmailRepository.save(systemEmail);
    }

    @Transactional
    public void remove(Long id) {
        SystemEmail email = systemEmailRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("邮箱不存在"));
        systemEmailRepository.delete(email);
    }

    @Transactional
    public SystemEmail setDefault(Long id) {
        SystemEmail target = systemEmailRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("邮箱不存在"));

        List<SystemEmail> allEmails = systemEmailRepository.findAll();
        for (SystemEmail email : allEmails) {
            email.setIsDefault(false);
        }
        systemEmailRepository.saveAll(allEmails);

        target.setIsDefault(true);
        return systemEmailRepository.save(target);
    }

    public Optional<SystemEmail> getDefaultEmail() {
        return systemEmailRepository.findFirstByIsDefaultTrueAndIsActiveTrueOrderByIdAsc();
    }

    public Optional<SystemEmail> getActiveEmail() {
        return systemEmailRepository.findFirstByIsDefaultTrueAndIsActiveTrueOrderByIdAsc()
                .or(systemEmailRepository::findFirstByIsActiveTrueOrderByIdAsc);
    }
}
