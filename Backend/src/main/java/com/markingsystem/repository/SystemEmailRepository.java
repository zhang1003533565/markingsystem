package com.markingsystem.repository;

import com.markingsystem.entity.SystemEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemEmailRepository extends JpaRepository<SystemEmail, Long> {
    List<SystemEmail> findByIsActiveTrue();

    Optional<SystemEmail> findFirstByIsDefaultTrueAndIsActiveTrueOrderByIdAsc();

    Optional<SystemEmail> findFirstByIsActiveTrueOrderByIdAsc();

    Optional<SystemEmail> findByEmailAndIsActiveTrue(String email);
}
