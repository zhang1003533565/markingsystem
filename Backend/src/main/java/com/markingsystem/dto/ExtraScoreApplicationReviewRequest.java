package com.markingsystem.dto;

import lombok.Data;

@Data
public class ExtraScoreApplicationReviewRequest {
    private String action;
    private String comment;
    private Double reviewScore;
}
