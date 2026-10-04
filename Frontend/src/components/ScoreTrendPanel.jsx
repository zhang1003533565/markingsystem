import { useMemo, useState } from 'react';
import EChartPanel from './EChartPanel';

const CATEGORY_META = [
  { key: 'overall', label: '综合', short: '综合', color: '#3b82f6', area: true },
  { key: 'moral', label: '德育', short: '德育', color: '#f59e0b' },
  { key: 'intellectual', label: '智育', short: '智育', color: '#10b981' },
  { key: 'physical', label: '体育', short: '体育', color: '#14b8a6' }
];

function round1(value) {
  if (value == null || !Number.isFinite(Number(value))) {
    return null;
  }
  return Math.round(Number(value) * 10) / 10;
}

function formatScore(value) {
  const num = round1(value);
  return num == null ? '—' : num.toFixed(1);
}

function formatDelta(value) {
  const num = round1(value);
  if (num == null || num === 0) {
    return { text: '0.0', positive: false, neutral: true };
  }
  const positive = num > 0;
  return {
    text: `${positive ? '+' : ''}${num.toFixed(1)}`,
    positive,
    neutral: false
  };
}

function formatSemesterLabel(semester) {
  const text = String(semester || '').trim();
  if (!text) return '-';
  // 支持 2023-2024-1 / 2023-2024-2 / 2023-2024；必须吃掉学期号，避免两学期都显示成「23-24」
  const matched = text.match(/^(\d{4})\s*[-–]\s*(\d{2,4})(?:\s*[-–./]\s*([12]))?/);
  if (matched) {
    const start = matched[1].slice(2);
    const endRaw = matched[2];
    const end = endRaw.length === 4 ? endRaw.slice(2) : endRaw;
    const term = matched[3];
    return term ? `${start}-${end} ${term}学期` : `${start}-${end}`;
  }
  return text;
}

function guessNextSemesterLabel(lastSemester) {
  const text = String(lastSemester || '').trim();
  const matched = text.match(/^(\d{4})\s*[-–]\s*(\d{2,4})(?:\s*[-–./]\s*([12]))?/);
  if (!matched || !matched[3]) {
    return '下期';
  }
  const startYear = Number(matched[1]);
  const endRaw = matched[2];
  const endYear = endRaw.length === 4 ? Number(endRaw) : Number(`${String(startYear).slice(0, 2)}${endRaw}`);
  const term = Number(matched[3]);
  if (term === 1) {
    return formatSemesterLabel(`${startYear}-${endYear}-2`);
  }
  return formatSemesterLabel(`${startYear + 1}-${endYear + 1}-1`);
}

function sortSemestersAsc(left, right) {
  return String(left || '').localeCompare(String(right || ''), 'zh-Hans-CN', { numeric: true });
}

/**
 * 将门户成绩列表聚合为按学期的趋势点。
 * scores: [{ subjectName, semester, score }]
 */
export function buildScoreTrendPoints(scores = []) {
  const semesterMap = new Map();

  for (const item of scores) {
    const semester = String(item?.semester || '').trim();
    if (!semester) continue;
    const current = semesterMap.get(semester) || {
      semester,
      moral: null,
      intellectual: null,
      physical: null
    };
    const score = item?.score == null ? null : Number(item.score);
    if (!Number.isFinite(score)) {
      semesterMap.set(semester, current);
      continue;
    }
    if (item.subjectName === '德育') current.moral = score;
    if (item.subjectName === '智育') current.intellectual = score;
    if (item.subjectName === '体育') current.physical = score;
    semesterMap.set(semester, current);
  }

  return Array.from(semesterMap.values())
    .sort((a, b) => sortSemestersAsc(a.semester, b.semester))
    .map((row) => {
      const parts = [row.moral, row.intellectual, row.physical].filter((v) => Number.isFinite(v));
      return {
        semester: row.semester,
        label: formatSemesterLabel(row.semester),
        moral: round1(row.moral),
        intellectual: round1(row.intellectual),
        physical: round1(row.physical),
        overall: parts.length ? round1(parts.reduce((sum, v) => sum + v, 0) / parts.length) : null
      };
    })
    .filter((row) => row.overall != null || row.moral != null || row.intellectual != null || row.physical != null);
}

function buildTrendSummary(points) {
  if (!points.length) {
    return null;
  }

  const withOverall = points.filter((p) => p.overall != null);
  const series = withOverall.length ? withOverall : points.map((p) => {
    const parts = [p.moral, p.intellectual, p.physical].filter((v) => v != null);
    return {
      ...p,
      overall: parts.length ? round1(parts.reduce((sum, v) => sum + v, 0) / parts.length) : null
    };
  }).filter((p) => p.overall != null);

  if (!series.length) {
    return null;
  }

  const first = series[0];
  const last = series[series.length - 1];
  const prev = series.length > 1 ? series[series.length - 2] : null;

  let best = series[0];
  let worst = series[0];
  for (const point of series) {
    if (point.overall > best.overall) best = point;
    if (point.overall < worst.overall) worst = point;
  }

  const mean = round1(series.reduce((sum, p) => sum + p.overall, 0) / series.length);

  const categoryDeltas = {};
  for (const meta of CATEGORY_META) {
    const start = first[meta.key];
    const end = last[meta.key];
    categoryDeltas[meta.key] = (start != null && end != null) ? round1(end - start) : null;
  }

  let prediction = null;
  if (prev?.overall != null && last.overall != null) {
    prediction = round1(last.overall + (last.overall - prev.overall));
  }

  return {
    currentOverall: last.overall,
    delta: prev?.overall != null ? round1(last.overall - prev.overall) : null,
    best: { semester: best.label, score: best.overall },
    worst: { semester: worst.label, score: worst.overall },
    categoryDeltas,
    mean,
    prediction,
    nextLabel: guessNextSemesterLabel(last.semester)
  };
}

function buildChartOption(points, summary) {
  const labels = points.map((p) => p.label);
  const hasPrediction = summary?.prediction != null;
  if (hasPrediction) {
    labels.push(summary.nextLabel);
  }

  const series = CATEGORY_META.map((meta) => {
    const values = points.map((p) => p[meta.key]);
    if (hasPrediction) {
      values.push(null);
    }

    return {
      name: meta.label === '综合' ? '综合平均分' : meta.label,
      type: 'line',
      data: values,
      smooth: 0.25,
      showSymbol: true,
      symbol: 'circle',
      symbolSize: meta.area ? 8 : 5,
      z: meta.area ? 5 : 3,
      lineStyle: {
        width: meta.area ? 3.2 : 2,
        color: meta.color,
        opacity: meta.area ? 1 : 0.85
      },
      itemStyle: { color: meta.color },
      areaStyle: meta.area
        ? {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(37, 99, 235, 0.28)' },
                { offset: 1, color: 'rgba(37, 99, 235, 0.02)' }
              ]
            }
          }
        : undefined,
      connectNulls: false,
      markPoint: meta.area
        ? {
            symbol: 'circle',
            symbolSize: 0.1,
            label: {
              show: true,
              position: 'top',
              distance: 8,
              color: '#2563eb',
              fontWeight: 700,
              fontSize: 12,
              formatter: ({ value }) => (value == null ? '' : Number(value).toFixed(1))
            },
            data: [{
              coord: [points.length - 1, points[points.length - 1]?.overall],
              value: points[points.length - 1]?.overall
            }]
          }
        : undefined,
      markLine: meta.area && summary?.mean != null
        ? {
            silent: true,
            symbol: 'none',
            label: {
              formatter: `均值 ${summary.mean.toFixed(1)}`,
              position: 'insideEndTop',
              color: '#64748b',
              fontSize: 11
            },
            lineStyle: { type: 'dashed', color: '#93c5fd', width: 1.4 },
            data: [{ yAxis: summary.mean }]
          }
        : undefined
    };
  });

  if (hasPrediction) {
    const predictData = points.map(() => null);
    predictData[points.length - 1] = points[points.length - 1]?.overall;
    predictData.push(summary.prediction);
    series.push({
      name: '下期预测',
      type: 'line',
      data: predictData,
      smooth: false,
      showSymbol: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { type: 'dashed', width: 2, color: '#8b5cf6' },
      itemStyle: { color: '#8b5cf6' },
      z: 4
    });
  }

  const numericValues = points.flatMap((p) => [p.overall, p.moral, p.intellectual, p.physical])
    .filter((v) => v != null);
  if (summary?.prediction != null) numericValues.push(summary.prediction);
  const minScore = numericValues.length ? Math.max(0, Math.floor(Math.min(...numericValues) / 5) * 5 - 5) : 60;
  const maxScore = numericValues.length ? Math.min(100, Math.ceil(Math.max(...numericValues) / 5) * 5 + 5) : 100;

  return {
    color: [...CATEGORY_META.map((m) => m.color), '#8b5cf6'],
    grid: { left: 36, right: 18, top: 42, bottom: 28 },
    legend: {
      top: 0,
      left: 'center',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#64748b', fontSize: 11 }
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(148,163,184,0.35)',
      borderWidth: 1,
      textStyle: { color: '#1e293b', fontSize: 12 },
      formatter: (params) => {
        if (!Array.isArray(params) || !params.length) return '';
        const axisLabel = params[0].axisValue;
        const pointIndex = params[0].dataIndex;
        const point = points[pointIndex];
        const lines = [`<div style="font-weight:700;margin-bottom:6px">${axisLabel}</div>`];

        if (point) {
          for (const meta of CATEGORY_META) {
            const value = point[meta.key];
            const prev = pointIndex > 0 ? points[pointIndex - 1]?.[meta.key] : null;
            const delta = (value != null && prev != null) ? round1(value - prev) : null;
            const deltaText = delta == null
              ? ''
              : `<span style="color:${delta >= 0 ? '#16a34a' : '#dc2626'};margin-left:6px">${delta >= 0 ? '↑' : '↓'} ${Math.abs(delta).toFixed(1)}</span>`;
            lines.push(
              `<div style="display:flex;justify-content:space-between;gap:16px;margin:2px 0">`
              + `<span><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${meta.color};margin-right:6px"></span>${meta.label}</span>`
              + `<span>${formatScore(value)}${deltaText}</span></div>`
            );
          }
          if (hasPrediction && pointIndex === points.length - 1) {
            lines.push(`<div style="margin-top:6px;color:#7c3aed">下期预测: ${formatScore(summary.prediction)}</div>`);
          }
        } else if (hasPrediction && pointIndex === labels.length - 1) {
          lines.push(`<div style="color:#7c3aed">下期预测: ${formatScore(summary.prediction)}</div>`);
        }

        return lines.join('');
      }
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: labels,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(148,163,184,0.45)' } },
      axisLabel: {
        color: '#64748b',
        fontSize: 11,
        hideOverlap: true
      }
    },
    yAxis: {
      type: 'value',
      min: minScore,
      max: maxScore,
      splitNumber: 5,
      axisLabel: { color: '#94a3b8', fontSize: 11 },
      splitLine: { lineStyle: { color: 'rgba(148,163,184,0.28)', type: 'dashed' } }
    },
    series
  };
}

function normalizeTrendPoints(rawPoints = []) {
  return (Array.isArray(rawPoints) ? rawPoints : [])
    .map((item) => {
      const semester = String(item?.semester || item?.academicYear || '').trim();
      if (!semester) return null;
      const moral = round1(item?.moral);
      const intellectual = round1(item?.intellectual);
      const physical = round1(item?.physical);
      const parts = [moral, intellectual, physical].filter((v) => v != null);
      const overall = item?.overall != null
        ? round1(item.overall)
        : (parts.length ? round1(parts.reduce((sum, v) => sum + v, 0) / parts.length) : null);
      if (overall == null && moral == null && intellectual == null && physical == null) {
        return null;
      }
      return {
        semester,
        label: formatSemesterLabel(semester),
        moral,
        intellectual,
        physical,
        overall
      };
    })
    .filter(Boolean)
    .sort((a, b) => sortSemestersAsc(a.semester, b.semester));
}

function ScoreTrendPanel({ scores = [], points: pointsProp, height = 240 }) {
  const [viewMode, setViewMode] = useState('chart');

  const points = useMemo(() => {
    if (Array.isArray(pointsProp) && pointsProp.length) {
      return normalizeTrendPoints(pointsProp);
    }
    return buildScoreTrendPoints(scores);
  }, [pointsProp, scores]);
  const summary = useMemo(() => buildTrendSummary(points), [points]);
  const chartOption = useMemo(
    () => (points.length ? buildChartOption(points, summary) : null),
    [points, summary]
  );

  const currentDelta = formatDelta(summary?.delta);

  return (
    <div className="score-trend-panel">
      <div className="card-head score-trend-card-head">
        <div className="head-title-row">
          <h3>成绩趋势</h3>
          <span className="head-subtitle">学期对比</span>
        </div>
        <div className="score-trend-view-toggle" role="tablist" aria-label="成绩趋势视图切换">
          <button
            type="button"
            role="tab"
            aria-selected={viewMode === 'chart'}
            className={viewMode === 'chart' ? 'active' : ''}
            onClick={() => setViewMode('chart')}
          >
            图表
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={viewMode === 'table'}
            className={viewMode === 'table' ? 'active' : ''}
            onClick={() => setViewMode('table')}
          >
            表格
          </button>
        </div>
      </div>

      {!points.length || !summary ? (
        <div className="score-trend-empty">暂无学年/学期成绩，生成综测快照或录入德育 / 智育 / 体育后即可查看趋势</div>
      ) : (
        <>
          <div className="score-trend-summary">
            <div className="score-trend-summary-main">
              <span className="score-trend-summary-label">当前综合均分</span>
              <div className="score-trend-summary-score-row">
                <strong className="score-trend-summary-score">{formatScore(summary.currentOverall)}</strong>
                <span
                  className={`score-trend-delta ${currentDelta.neutral ? 'neutral' : (currentDelta.positive ? 'up' : 'down')}`}
                >
                  {currentDelta.neutral ? '—' : (currentDelta.positive ? '↑' : '↓')}{' '}
                  {currentDelta.neutral ? '0.0' : Math.abs(Number(summary.delta || 0)).toFixed(1)}
                </span>
              </div>
              <div className="score-trend-summary-meta">
                <span>最佳 {summary.best.semester} · {formatScore(summary.best.score)}</span>
                <span>最低 {summary.worst.semester} · {formatScore(summary.worst.score)}</span>
              </div>
            </div>
            <div className="score-trend-delta-pills">
              {CATEGORY_META.map((meta) => {
                const delta = formatDelta(summary.categoryDeltas[meta.key]);
                return (
                  <div key={meta.key} className="score-trend-delta-pill">
                    <span className="score-trend-delta-pill-label">{meta.short}</span>
                    <span className={`score-trend-delta-pill-value ${delta.neutral ? 'neutral' : (delta.positive ? 'up' : 'down')}`}>
                      {delta.neutral ? '—' : (delta.positive ? '↑' : '↓')}
                      <em>{delta.neutral ? '0.0' : Math.abs(Number(summary.categoryDeltas[meta.key] || 0)).toFixed(1)}</em>
                    </span>
                  </div>
                );
              })}
            </div>
          </div>

          {viewMode === 'chart' ? (
            <div className="score-trend-chart-wrap">
              <EChartPanel option={chartOption} height={height} visible={viewMode === 'chart'} />
            </div>
          ) : (
            <div className="score-trend-table-wrap">
              <table className="score-trend-table">
                <thead>
                  <tr>
                    <th>学期</th>
                    <th>综合</th>
                    <th>德育</th>
                    <th>智育</th>
                    <th>体育</th>
                  </tr>
                </thead>
                <tbody>
                  {points.map((point) => (
                    <tr key={point.semester}>
                      <td>{point.label}</td>
                      <td>{formatScore(point.overall)}</td>
                      <td>{formatScore(point.moral)}</td>
                      <td>{formatScore(point.intellectual)}</td>
                      <td>{formatScore(point.physical)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  );
}

export default ScoreTrendPanel;
