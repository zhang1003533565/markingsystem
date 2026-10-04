package com.markingsystem.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 知识库传输对象（含文档数 / 分段数统计，供列表卡片展示）。
 */
@Data
public class KnowledgeBaseDTO {

    private Long id;

    private String name;

    private String description;

    private String color;

    private String icon;

    private Boolean enabled;

    /** 文档数 */
    private Long docCount;

    /** 分段数 */
    private Long segmentCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}