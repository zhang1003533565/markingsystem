package com.markingsystem.repository;

import com.markingsystem.entity.KnowledgeChatLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * AI 对话日志仓储。
 */
public interface KnowledgeChatLogRepository extends JpaRepository<KnowledgeChatLog, Long> {

    List<KnowledgeChatLog> findTop500ByOrderByIdDesc();

    List<KnowledgeChatLog> findTop5000ByOrderByIdDesc();
}