package com.markingsystem.repository;

import com.markingsystem.entity.College;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollegeRepository extends JpaRepository<College, Long> {

    List<College> findByStatus(String status);

    List<College> findByNameContaining(String name);
}
