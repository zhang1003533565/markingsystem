export const isWithdrawalPending = (record) =>
  String(record?.withdrawalStatus || '').toLowerCase() === 'pending'

export const canHandleWithdrawalReview = (record, { isCommittee, isTeacher } = {}) =>
  Boolean((isCommittee || isTeacher) && isWithdrawalPending(record) && record?.canReviewWithdrawal)

export const getWithdrawalReviewHint = (record, { isCommittee, isTeacher } = {}) => {
  if (!isWithdrawalPending(record)) {
    return ''
  }
  if (record?.canReviewWithdrawal) {
    return ''
  }
  if (isTeacher) {
    return '等待班委处理撤回'
  }
  if (!isCommittee) {
    return '等待班委处理撤回'
  }
  return '需其他班委处理'
}
