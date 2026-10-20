package com.markingsystem.dto;

import lombok.Data;

/**
 * 综测成绩模拟器：反向计算请求。
 * 给定目标名次百分比（如班级前 10%），反解需要增加的附加分或需要达到的课程分数。
 */
@Data
public class ScoreReverseRequest {

    /** 学年，如 2024-2025；为空则取最近一次快照学年。 */
    private String academicYear;

    /** 目标百分比，如 10 表示进入班级前 10%。取值 (0, 100]。 */
    private Double targetPercentile;

    /** 反解口径：EXTRA（附加分）或 COURSE（课程分数）。 */
    private String solveFor;

    /** solveFor=EXTRA 必填：MORAL / INTELLECTUAL / PHYSICAL。 */
    private String category;

    /** solveFor=COURSE 必填：科目 id。 */
    private Long subjectId;
}
