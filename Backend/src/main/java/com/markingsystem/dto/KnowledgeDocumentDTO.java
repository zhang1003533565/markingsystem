package com.markingsystem.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档传输对象。
 */
@Data
public class KnowledgeDocumentDTO {

    private Long id;

    private Long kbId;

    /** 展示名称 */
    private String name;

    /** 原始文件名 */
    private String fileName;

    /** 文件类型：pdf / docx / txt / md / manual */
    private String fileType;

    /** 处理状态：PROCESSING / SUCCESS / FAILED */
    private String status;

    /** 字符数 */
    private Integer charCount;

    /** 分段数 */
    private Integer segmentCount;

    /** 失败原因 */
    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}