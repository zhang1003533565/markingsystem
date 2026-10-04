const toNumber = (value) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

const roundScore = (value) => Math.round(value * 100) / 100

export const splitExtraScoreResultRows = (rows) => {
  const safeRows = Array.isArray(rows) ? rows : []
  return {
    countedRows: safeRows.filter((row) => row?.counted !== false),
    excludedRows: safeRows.filter((row) => row?.counted === false),
  }
}

export const resolveExtraScoreReplacement = (rows, record) => {
  if (record?.countedApplicationId === null || record?.countedApplicationId === undefined) {
    return null
  }

  const replacementId = String(record.countedApplicationId)
  return (Array.isArray(rows) ? rows : []).find((row) => String(row?.id) === replacementId) || null
}

export const buildExtraScoreResultSummary = (rows) => {
  const safeRows = Array.isArray(rows) ? rows : []
  const { countedRows, excludedRows } = splitExtraScoreResultRows(safeRows)
  return {
    totalCount: safeRows.length,
    countedCount: countedRows.length,
    excludedCount: excludedRows.length,
    originalScore: roundScore(
      safeRows.reduce(
        (sum, row) => sum + toNumber(row?.originalSignedScore ?? row?.expectedScore),
        0,
      ),
    ),
    countedScore: roundScore(
      countedRows.reduce(
        (sum, row) => sum + toNumber(row?.effectiveSignedScore ?? row?.signedScore),
        0,
      ),
    ),
    excludedOriginalScore: roundScore(
      excludedRows.reduce(
        (sum, row) => sum + toNumber(row?.originalSignedScore ?? row?.expectedScore),
        0,
      ),
    ),
  }
}
