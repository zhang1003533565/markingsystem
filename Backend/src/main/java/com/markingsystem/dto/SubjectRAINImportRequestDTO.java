package com.markingsystem.dto;

import lombok.Data;

import java.util.List;

@Data
public class SubjectRAINImportRequestDTO {

    /**
     * 科目列表
     */
    private List<String> subjects;

    /**
     * 是否确认导入
     */
    private boolean confirm;

    /**
     * 学期（用于成绩导入）
     */
    private String semester;

} 
