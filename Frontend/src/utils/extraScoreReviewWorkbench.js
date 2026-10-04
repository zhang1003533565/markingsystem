const numberValue = (value) => Number(value || 0)

export const summarizeReviewWorkbenchClasses = (classes = []) =>
  classes.reduce(
    (summary, row) => ({
      classCount: summary.classCount + 1,
      studentCount: summary.studentCount + numberValue(row.submittedStudentCount),
      totalRecords: summary.totalRecords + numberValue(row.totalRecords),
      actionablePending: summary.actionablePending + numberValue(row.actionablePending),
      pending: summary.pending + numberValue(row.pending),
      waitingCommittee: summary.waitingCommittee + numberValue(row.waitingCommittee),
      approved: summary.approved + numberValue(row.approved),
      returned: summary.returned + numberValue(row.returned),
    }),
    {
      classCount: 0,
      studentCount: 0,
      totalRecords: 0,
      actionablePending: 0,
      pending: 0,
      waitingCommittee: 0,
      approved: 0,
      returned: 0,
    },
  )

export const filterReviewWorkbenchClasses = (classes = [], view = 'all') => {
  const predicates = {
    actionable: (row) => numberValue(row.actionablePending) > 0,
    pending: (row) => numberValue(row.pending) > 0,
    waiting: (row) => numberValue(row.waitingCommittee) > 0,
    approved: (row) => numberValue(row.approved) > 0,
    returned: (row) => numberValue(row.returned) > 0,
  }
  const predicate = predicates[view]
  return predicate ? classes.filter(predicate) : classes
}

export const getReviewStatusForWorkbenchView = (view) => {
  if (view === 'approved') return 'approved'
  if (view === 'returned') return 'returned'
  if (view === 'actionable' || view === 'pending' || view === 'waiting') return 'pending'
  return 'all'
}
