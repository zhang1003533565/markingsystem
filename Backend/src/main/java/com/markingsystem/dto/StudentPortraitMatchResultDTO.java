package com.markingsystem.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class StudentPortraitMatchResultDTO {
    private Long studentId;
    private String studentNo;
    private String studentName;
    private String className;
    private String primaryType;
    private String summary;
    private List<String> reasons = new ArrayList<>();
    private List<String> dataSources = new ArrayList<>();
    private Map<String, Object> featureSnapshot = new LinkedHashMap<>();
    private List<PortraitMatchItemDTO> items = new ArrayList<>();
    private LocalDateTime generatedAt;

    @Data
    public static class PortraitMatchItemDTO {
        private String typeKey;
        private String typeName;
        private Double rawScore;
        private Double percentage;
        private List<String> reasons = new ArrayList<>();
    }
}
