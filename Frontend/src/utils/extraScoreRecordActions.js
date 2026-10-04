export const isApprovedReviewRecord = (record = {}) => record.recordReviewResult === 'approved'

const isDraftLikeRecord = (record = {}) => ['draft', 'returned', 'rejected'].includes(record.status)

export const canEditExtraScoreRecord = (record = {}, canSubmitNow = false) =>
  Boolean(canSubmitNow && isDraftLikeRecord(record))

export const canDeleteExtraScoreRecord = (record = {}, canSubmitNow = false) =>
  Boolean(
    canSubmitNow && isDraftLikeRecord(record) && !isApprovedReviewRecord(record),
  )

export const canSupplementExtraScoreRecord = (record = {}, canSubmitNow = false) =>
  Boolean(canSubmitNow && record.status === 'returned' && !isApprovedReviewRecord(record))
