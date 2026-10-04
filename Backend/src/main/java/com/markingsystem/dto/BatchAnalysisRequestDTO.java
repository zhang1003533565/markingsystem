package com.markingsystem.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchAnalysisRequestDTO {
    private String keyword;
    private String className;
    private List<String> studentNos = new ArrayList<>();
}
