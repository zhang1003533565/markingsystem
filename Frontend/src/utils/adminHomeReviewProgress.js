export const buildCurrentReviewProgressList = (rows = []) => {
  return rows
    .filter((item) => Boolean(item.reviewEnabled))
    .map((item) => {
      const total = Number(item.total || 0);
      const stageTotal = Number(item.draft || 0)
        + Number(item.committee || 0)
        + Number(item.teacher || 0)
        + Number(item.completed || 0);
      const hasApplications = total > 0 || stageTotal > 0;
      return {
        ...item,
        studentCount: Number(item.studentCount || 0),
        total,
        draft: Number(item.draft || 0),
        committee: Number(item.committee || 0),
        teacher: Number(item.teacher || 0),
        completed: Number(item.completed || 0),
        returned: Number(item.returned || 0),
        appliedStudentCount: Number(item.appliedStudentCount || 0),
        unsubmittedCount: Number(item.unsubmittedCount || 0),
        currentProgress: Number(item.currentProgress || 0),
        currentProgressTotal: Number(item.currentProgressTotal || item.studentCount || item.total || 1),
        daysUntilDeadline: Number(item.daysUntilDeadline ?? 9999),
        reviewInProgress: Boolean(item.reviewInProgress),
        needsTeacherAction: Boolean(item.needsTeacherAction),
        needsAttention: Boolean(item.needsAttention),
        currentStage: item.currentStage || '学生申请',
        currentStageKey: item.currentStageKey || 'student_apply',
        statusLabel: item.statusLabel || '正常进行中',
        attentionLevel: item.attentionLevel || 'normal',
        attentionReason: item.attentionReason || '',
        reviewActionLabel: item.reviewActionLabel || (hasApplications ? '进入审核' : '查看周期'),
        reviewActionPath:
          item.reviewActionPath ||
          (hasApplications ? '/admin/extra-score-review' : '/admin/extra-score-submission-windows')
      };
    })
    .sort(
      (a, b) =>
        b.teacher - a.teacher ||
        b.committee - a.committee ||
        Number(b.needsAttention) - Number(a.needsAttention) ||
        b.total - a.total ||
        String(a.className).localeCompare(String(b.className), 'zh-CN')
    );
};
