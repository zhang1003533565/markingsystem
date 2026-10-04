package com.markingsystem.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SubjectRAINImportResponseDTO {

    /**
     * 新增的科目列表
     */
    private List<String> newSubjects;

    /**
     * 失败的科目列表
     */
    private List<String> failedSubjects;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 预览的科目列表（用于前端展示）
     */
    private List<String> previewSubjects;

    /**
     * 上传文件中的表头列表（用于字段映射）
     */
    private List<String> headers;

    /**
     * 班级字段可选值（用于前端筛选）
     */
    private List<String> classValues;

    /**
     * 不同字段对应的可选值
     */
    private java.util.Map<String, List<String>> classValuesByHeader;

    /**
     * 成绩导入统计信息
     */
    private Map<String, Object> scoreImportInfo;

    /**
     * 撤销导入统计信息
     */
    private Map<String, Object> undoImportInfo;

}
