package com.markingsystem.repository;

import com.markingsystem.entity.CompositeScoreRefreshLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompositeScoreRefreshLogRepository extends JpaRepository<CompositeScoreRefreshLog, Long> {

    Optional<CompositeScoreRefreshLog> findTopByAcademicYearAndScopeTypeAndScopeOwnerIdOrderByCreatedAtDesc(
            String academicYear,
            String scopeType,
            Long scopeOwnerId
    );

    List<CompositeScoreRefreshLog> findTop20ByAcademicYearAndScopeTypeAndScopeOwnerIdOrderByCreatedAtDesc(
            String academicYear,
            String scopeType,
            Long scopeOwnerId
    );
}
