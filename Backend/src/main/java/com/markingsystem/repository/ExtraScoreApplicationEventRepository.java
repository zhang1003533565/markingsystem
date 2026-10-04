package com.markingsystem.repository;

import com.markingsystem.entity.ExtraScoreApplicationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ExtraScoreApplicationEventRepository extends JpaRepository<ExtraScoreApplicationEvent, Long> {
    List<ExtraScoreApplicationEvent> findByApplicationIdOrderByOccurredAtDescIdDesc(Long applicationId);
    boolean existsByApplicationId(Long applicationId);
    boolean existsByApplicationIdAndEventTypeIn(Long applicationId, Collection<String> eventTypes);
    void deleteByApplicationId(Long applicationId);
}
