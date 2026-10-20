package com.markingsystem.repository;

import com.markingsystem.entity.SystemMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemMessageRepository extends JpaRepository<SystemMessage, Long> {

    List<SystemMessage> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<SystemMessage> findAllByOrderByCreatedAtDescIdDesc();

    long countByUserIdAndReadFalse(Long userId);

    long countByReadFalse();
}
