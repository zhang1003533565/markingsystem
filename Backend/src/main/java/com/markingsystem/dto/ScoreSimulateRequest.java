package com.markingsystem.dto;

import lombok.Data;

import java.util.List;

/**
 * 综测成绩模拟器：正向模拟请求。
 * 学生修改若干门课程分数或增加若干类附加分，观察综合总分与班级名次变化（不落库）。
 */
@Data
public class ScoreSimulateRequest {

    /** 学年，如 2024-2025；为空则取最近一次快照学年。 */
    private String academicYear;

    /** 调整项列表，可为空（等价于查看当前基线）。 */
    private List<Adjustment> adjustments;

    @Data
    public static class Adjustment {
        /** COURSE：修改某门课程分数；EXTRA：增加某类附加分。 */
        private String type;
        /** EXTRA 必填：MORAL / INTELLECTUAL / PHYSICAL。 */
        private String category;
        /** COURSE 必填：科目 id。 */
        private Long subjectId;
        /** COURSE 必填：目标分数（0-100）。 */
        private Double newScore;
        /** EXTRA 必填：附加分增量（可为负表示扣分）。 */
        private Double delta;
    }
}
