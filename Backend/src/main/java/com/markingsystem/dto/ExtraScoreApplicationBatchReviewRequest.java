package com.markingsystem.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExtraScoreApplicationBatchReviewRequest {
    private List<Long> applicationIds;

    /** 批量驳回/退回补材料时必填：return=驳回，supplement=退回补充材料。 */
    private String action;

    /** 批量驳回/退回补材料时必填的审核意见。 */
    private String comment;
}
