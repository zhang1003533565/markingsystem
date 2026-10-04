package com.markingsystem.repository;

import com.markingsystem.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 知识库仓储。
 */
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
}