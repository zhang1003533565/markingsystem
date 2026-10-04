package com.markingsystem.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExtraScoreApplicationBatchReviewRequest {
    private List<Long> applicationIds;
}
