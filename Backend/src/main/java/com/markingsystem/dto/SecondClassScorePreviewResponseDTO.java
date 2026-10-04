package com.markingsystem.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SecondClassScorePreviewResponseDTO {

    private String errorMessage;

    private List<String> headers;

    private Integer rowCount;

    private List<Map<String, Object>> previewRows;

    private Map<String, String> defaultColumns;
}
