package com.markingsystem.service;

import com.markingsystem.entity.Role;
import com.markingsystem.enums.RoleType;
import com.markingsystem.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class RoleService {
    
    @Autowired
    private RoleRepository roleRepository;
    
    @PostConstruct
    @Transactional
    public void initRoles() {
        // 初始化四个默认角色
        for (RoleType roleType : RoleType.values()) {
            if (!roleRepository.existsByName(roleType.name())) {
                Role role = new Role();
                role.setName(roleType.name());
                role.setDescription(roleType.getDisplayName());
                roleRepository.save(role);
            }
        }
    }
    
    public List<Role> findAll() {
        return roleRepository.findAll();
    }
    
    public Optional<Role> findById(Long id) {
        return roleRepository.findById(id);
    }
    
    public Optional<Role> findByName(String name) {
        return roleRepository.findByName(name);
    }
    
    public Optional<Role> findByRoleType(RoleType roleType) {
        return roleRepository.findByName(roleType.name());
    }
    
    @Transactional
    public Role save(Role role) {
        return roleRepository.save(role);
    }
    
    @Transactional
    public void deleteById(Long id) {
        roleRepository.deleteById(id);
    }
    
    public boolean existsByName(String name) {
        return roleRepository.existsByName(name);
    }
}
