package com.markingsystem.controller;

import com.markingsystem.entity.Role;
import com.markingsystem.entity.UserRole;
import com.markingsystem.enums.RoleType;
import com.markingsystem.service.RoleService;
import com.markingsystem.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/roles")
public class RoleController {
    
    @Autowired
    private RoleService roleService;
    
    @Autowired
    private UserRoleService userRoleService;
    
    // 获取所有角色
    @GetMapping
    public ResponseEntity<List<Role>> getAllRoles() {
        return ResponseEntity.ok(roleService.findAll());
    }
    
    // 获取角色枚举类型
    @GetMapping("/types")
    public ResponseEntity<List<Map<String, String>>> getRoleTypes() {
        List<Map<String, String>> types = Arrays.stream(RoleType.values())
                .map(type -> Map.of(
                        "name", type.name(),
                        "displayName", type.getDisplayName()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(types);
    }
    
    // 根据ID获取角色
    @GetMapping("/{id}")
    public ResponseEntity<Role> getRoleById(@PathVariable Long id) {
        return roleService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    // 创建角色
    @PostMapping
    public ResponseEntity<Role> createRole(@RequestBody Role role) {
        if (roleService.existsByName(role.getName())) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(roleService.save(role));
    }
    
    // 更新角色
    @PutMapping("/{id}")
    public ResponseEntity<Role> updateRole(@PathVariable Long id, @RequestBody Role role) {
        return roleService.findById(id)
                .map(existing -> {
                    existing.setName(role.getName());
                    existing.setDescription(role.getDescription());
                    return ResponseEntity.ok(roleService.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    // 删除角色
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteById(id);
        return ResponseEntity.ok().build();
    }
    
    // 获取用户的所有角色
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserRole>> getUserRoles(@PathVariable Long userId) {
        return ResponseEntity.ok(userRoleService.findByUserId(userId));
    }
    
    // 获取用户的角色名称列表
    @GetMapping("/user/{userId}/names")
    public ResponseEntity<List<String>> getUserRoleNames(@PathVariable Long userId) {
        return ResponseEntity.ok(userRoleService.findRoleNamesByUserId(userId));
    }
    
    // 为用户分配角色
    @PostMapping("/user/{userId}/assign/{roleId}")
    public ResponseEntity<UserRole> assignRole(@PathVariable Long userId, @PathVariable Long roleId) {
        try {
            return ResponseEntity.ok(userRoleService.assignRole(userId, roleId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    // 为用户分配角色（通过角色类型）
    @PostMapping("/user/{userId}/assign-type/{roleType}")
    public ResponseEntity<UserRole> assignRoleByType(@PathVariable Long userId, @PathVariable String roleType) {
        try {
            RoleType type = RoleType.fromString(roleType);
            return ResponseEntity.ok(userRoleService.assignRoleByType(userId, type));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    // 移除用户的角色
    @DeleteMapping("/user/{userId}/remove/{roleId}")
    public ResponseEntity<Void> removeRole(@PathVariable Long userId, @PathVariable Long roleId) {
        userRoleService.removeRole(userId, roleId);
        return ResponseEntity.ok().build();
    }
    
    // 移除用户的角色（通过角色类型）
    @DeleteMapping("/user/{userId}/remove-type/{roleType}")
    public ResponseEntity<Void> removeRoleByType(@PathVariable Long userId, @PathVariable String roleType) {
        try {
            RoleType type = RoleType.fromString(roleType);
            userRoleService.removeRoleByType(userId, type);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    // 检查用户是否有某个角色
    @GetMapping("/user/{userId}/has/{roleType}")
    public ResponseEntity<Boolean> hasRole(@PathVariable Long userId, @PathVariable String roleType) {
        try {
            RoleType type = RoleType.fromString(roleType);
            return ResponseEntity.ok(userRoleService.hasRole(userId, type));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(false);
        }
    }
}
