package com.markingsystem.dto.assistant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 对话推送给前端的发展助手页面卡片（结构化导航，非 Markdown）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantPageCard {
    /** hotJobs | favorites | growthPath | resources */
    private String view;
    /** 岗位推荐 ID；列表页可为空 */
    private Long recommendationId;
    private String title;
    private String subtitle;
    private String jobTitle;
}
