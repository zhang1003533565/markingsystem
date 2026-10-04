package com.markingsystem.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class SecondClassScoreImportResponseDTO {

    private String errorMessage;

    private Integer totalRowCount;

    private Integer successRowCount;

    private Integer createdCount;

    private Integer updatedCount;

    private Integer skippedCount;

    private List<String> errors;

    private List<Map<String, Object>> errorSummaries;

    private List<Map<String, Object>> importDetails;
}
