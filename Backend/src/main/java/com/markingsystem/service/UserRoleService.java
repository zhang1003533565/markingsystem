package com.markingsystem.service;

import com.markingsystem.entity.Role;
import com.markingsystem.entity.UserRole;
import com.markingsystem.enums.RoleType;
import com.markingsystem.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserRoleService {
    
    @Autowired
    private UserRoleRepository userRoleRepository;
    
    @Autowired
    private RoleService roleService;
    
    public List<UserRole> findByUserId(Long userId) {
        return userRoleRepository.findByUserId(userId);
    }
    
    public List<String> findRoleNamesByUserId(Long userId) {
        return findByUserId(userId).stream()
                .map(ur -> ur.getRole().getName())
                .collect(Collectors.toList());
    }
    
    public boolean hasRole(Long userId, RoleType roleType) {
        Optional<Role> role = roleService.findByRoleType(roleType);
        if (role.isEmpty()) {
            return false;
        }
        return userRoleRepository.existsByUserIdAndRoleId(userId, role.get().getId());
    }
    
    @Transactional
    public UserRole assignRole(Long userId, Long roleId) {
        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            throw new IllegalArgumentException("用户已拥有该角色");
        }
        
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        return userRoleRepository.save(userRole);
    }
    
    @Transactional
    public UserRole assignRoleByType(Long userId, RoleType roleType) {
        Role role = roleService.findByRoleType(roleType)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + roleType));
        return assignRole(userId, role.getId());
    }
    
    @Transactional
    public void removeRole(Long userId, Long roleId) {
        userRoleRepository.deleteByUserIdAndRoleId(userId, roleId);
    }
    
    @Transactional
    public void removeRoleByType(Long userId, RoleType roleType) {
        Role role = roleService.findByRoleType(roleType)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + roleType));
        removeRole(userId, role.getId());
    }
    
    @Transactional
    public void removeAllRoles(Long userId) {
        userRoleRepository.deleteByUserId(userId);
    }

    @Transactional
    public void removeAllRoles(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userRoleRepository.deleteByUserIdIn(userIds);
    }
}
