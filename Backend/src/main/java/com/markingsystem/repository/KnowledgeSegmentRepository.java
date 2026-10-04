package com.markingsystem.repository;

import com.markingsystem.entity.KnowledgeSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 知识库分段仓储。
 */
public interface KnowledgeSegmentRepository extends JpaRepository<KnowledgeSegment, Long> {

    List<KnowledgeSegment> findByKbIdOrderByUpdatedAtDesc(Long kbId);

    List<KnowledgeSegment> findByDocumentIdOrderByIdAsc(Long documentId);

    List<KnowledgeSegment> findByEnabledTrueOrderByUpdatedAtDesc();

    List<KnowledgeSegment> findByKbIdInAndEnabledTrue(Collection<Long> kbIds);

    List<KnowledgeSegment> findByKbIdAndEnabledTrue(Long kbId);

    long countByKbId(Long kbId);

    long countByKbIdAndEnabledTrue(Long kbId);
}