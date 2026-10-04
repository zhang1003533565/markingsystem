package com.markingsystem.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExtraScoreSubmissionWindowRequest {
    private String academicYear;
    private String className;
    private List<String> classNames;
    private Boolean enabled;
    private String startTime;
    private String endTime;
}
