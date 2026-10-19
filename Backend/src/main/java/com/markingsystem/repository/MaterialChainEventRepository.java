package com.markingsystem.repository;

import com.markingsystem.entity.MaterialChainEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialChainEventRepository extends JpaRepository<MaterialChainEvent, Long> {

    List<MaterialChainEvent> findByChainFileIdOrderByOccurredAtAsc(Long chainFileId);

    List<MaterialChainEvent> findByChainFileIdOrderByOccurredAtDesc(Long chainFileId);

    List<MaterialChainEvent> findByChainFileIdAndEventType(Long chainFileId, String eventType);

    long countByChainFileId(Long chainFileId);
}
