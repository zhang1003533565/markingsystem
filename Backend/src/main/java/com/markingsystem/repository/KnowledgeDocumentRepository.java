package com.markingsystem.repository;

import com.markingsystem.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 知识库文档仓储。
 */
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByKbIdOrderByCreatedAtDesc(Long kbId);

    List<KnowledgeDocument> findByKbIdInOrderByCreatedAtDesc(Collection<Long> kbIds);

    long countByKbId(Long kbId);
}