package com.markingsystem.repository;

import com.markingsystem.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    
    List<UserRole> findByUserId(Long userId);
    
    Optional<UserRole> findByUserIdAndRoleId(Long userId, Long roleId);
    
    boolean existsByUserIdAndRoleId(Long userId, Long roleId);
    
    void deleteByUserIdAndRoleId(Long userId, Long roleId);
    
    void deleteByUserId(Long userId);

    void deleteByUserIdIn(List<Long> userIds);
}
