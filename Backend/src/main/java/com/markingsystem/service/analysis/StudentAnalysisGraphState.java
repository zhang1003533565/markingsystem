package com.markingsystem.service.analysis;

import com.markingsystem.dto.StudentAnalysisPayloadDTO;
import com.markingsystem.dto.StudentAnalysisResultDTO;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class StudentAnalysisGraphState {
    private StudentAnalysisPayloadDTO payload;
    private Map<String, Object> featureSnapshot = new LinkedHashMap<>();
    private StudentAnalysisResultDTO result;
    private boolean llmEnabled;
}
