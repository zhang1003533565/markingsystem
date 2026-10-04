package com.markingsystem.repository;

import com.markingsystem.entity.GradeInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeInfoRepository extends JpaRepository<GradeInfo, Long> {

    Optional<GradeInfo> findByName(String name);

    List<GradeInfo> findByStatus(String status);

    boolean existsByName(String name);
}
