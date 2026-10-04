package com.markingsystem.dto;

/**
 * 学习进度打卡请求体。
 * 用于标记某项学习内容完成 / 取消完成。
 */
public class LearningProgressRequest {

    /** 收藏岗位 recommendationId（每日打卡传 0） */
    private Long recommendationId;

    /** 记录类型：RESOURCE / TASK_STEP / PATH_STEP */
    private String itemType;

    /** 内容唯一键：资源 id、步骤序号、或打卡日期 */
    private String itemKey;

    /** 内容标题（展示用，可为空） */
    private String itemTitle;

    public LearningProgressRequest() {
    }

    public Long getRecommendationId() {
        return recommendationId;
    }

    public void setRecommendationId(Long recommendationId) {
        this.recommendationId = recommendationId;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getItemKey() {
        return itemKey;
    }

    public void setItemKey(String itemKey) {
        this.itemKey = itemKey;
    }

    public String getItemTitle() {
        return itemTitle;
    }

    public void setItemTitle(String itemTitle) {
        this.itemTitle = itemTitle;
    }
}
