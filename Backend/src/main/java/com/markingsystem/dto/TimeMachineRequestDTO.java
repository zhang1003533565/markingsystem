package com.markingsystem.dto;

import lombok.Data;

/**
 * AI 时光机（反事实推演）请求参数。
 *
 * <p>学生调整若干个「如果……」假设维度，服务端基于真实画像数据做未来路径推演。
 * 全部字段可选，缺省视为不做该维度调整。
 */
@Data
public class TimeMachineRequestDTO {

    /** 平均成绩提升幅度（百分制，0~10 分） */
    private Double scoreDelta;

    /** 假设接下来(在校期间)新增参加的竞赛数量（0~3） */
    private Integer competitionCount;

    /** 假设新增的志愿服务 / 实践活动数量（0~5） */
    private Integer practiceCount;

    /** 假设新考取的专业证书数量（0~3） */
    private Integer certCount;

    public double scoreDeltaValue() {
        return scoreDelta == null ? 0D : Math.max(0D, Math.min(10D, scoreDelta));
    }

    public int competitionCountValue() {
        return competitionCount == null ? 0 : Math.max(0, Math.min(3, competitionCount));
    }

    public int practiceCountValue() {
        return practiceCount == null ? 0 : Math.max(0, Math.min(5, practiceCount));
    }

    public int certCountValue() {
        return certCount == null ? 0 : Math.max(0, Math.min(3, certCount));
    }

    public boolean hasAnyAdjustment() {
        return scoreDeltaValue() > 0 || competitionCountValue() > 0
                || practiceCountValue() > 0 || certCountValue() > 0;
    }
}