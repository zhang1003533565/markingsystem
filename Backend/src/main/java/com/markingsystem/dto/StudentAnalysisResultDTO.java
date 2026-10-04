package com.markingsystem.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class StudentAnalysisResultDTO {
    private String primaryType;
    private List<String> secondaryTypes = new ArrayList<>();
    private Integer confidence;
    private String summary;
    private List<String> reasons = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private Map<String, Object> featureSnapshot = new LinkedHashMap<>();
    private Map<String, Double> radarDimensions = new LinkedHashMap<>();
    private String source;
    private String model;
    private LocalDateTime generatedAt;
}
