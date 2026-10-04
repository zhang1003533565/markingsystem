package com.markingsystem.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 时光机推演结果。
 *
 * <p>包含「现状 vs 假设」的路径概率对比、八维雷达对比、未来时间线、AI 点评与建议。
 * 纯内存计算/返回，不落库。
 */
@Data
public class TimeMachineResultDTO {

    /** 当前画像主导类型（考研 / 考公 / 竞赛型人才，来自已有画像分析） */
    private String primaryType;

    /** 假设调整项的简要描述（供页面回显，如“平均分 +3 / 竞赛 +2”） */
    private String adjustmentSummary;

    /** 当前八维雷达（key=维度名, value=0~100） */
    private Map<String, Double> currentRadar = new LinkedHashMap<>();

    /** 推演后八维雷达（key=维度名, value=0~100） */
    private Map<String, Double> simulatedRadar = new LinkedHashMap<>();

    /** 各发展方向概率（现状 vs 假设），value 为 0~100 的整数 */
    private List<PathProbability> paths = new ArrayList<>();

    /** 未来时间线（按时间先后排列） */
    private List<TimelineEvent> timeline = new ArrayList<>();

    /** AI 点评（假设情景下的整体判断） */
    private String summary;

    /** 可执行建议 */
    private List<String> suggestions = new ArrayList<>();

    /** 结果来源：deepseek / rules */
    private String source;

    /** 使用的模型名称（规则兜底时为 rule-engine） */
    private String model;

    @Data
    public static class PathProbability {
        private String name;
        private Integer current;
        private Integer simulated;
        /** 变化原因（简要，供前端展示） */
        private String reason;
    }

    @Data
    public static class TimelineEvent {
        /** 阶段标签，如“大三上学期”“大四 9 月” */
        private String stage;
        /** 事件标题 */
        private String title;
        /** 事件描述 */
        private String description;
    }
}