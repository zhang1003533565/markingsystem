package com.markingsystem.dto;

import lombok.Data;

import java.util.List;

/**
 * 文档导入结果（上传 PDF / Word / TXT 后返回文档与拆分明细）。
 *
 * <p>交互方式类似 MaxKB：文件被拆成多个知识分段，每个分段包含标题、正文、来源与向量化状态。
 */
@Data
public class DocumentImportResult {

    /** 原始文件名 */
    private String fileName;

    /** 成功导入的分段数 */
    private int imported;

    /** 其中成功向量化的分段数（异步向量化时上传响应通常为 0） */
    private int embedded;

    /** 是否因单文档上限截断（超过 DOC_MAX_CHUNKS 的段落被丢弃） */
    private boolean truncated;

    /** 导入的文档信息（含处理状态，失败时含错误信息） */
    private KnowledgeDocumentDTO document;

    /** 分段明细（上传响应精简为空列表，避免大包体；请从文档分段列表查看） */
    private List<KnowledgeSegmentDTO> chunks;

    public DocumentImportResult() {
    }
}
