import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Card, Select, InputNumber, Radio, Tag, Spin, Button, Empty, message } from 'antd';
import { simulateScore, reverseScore } from '../../api/student/scoreSimulator';
import './ScoreSimulatorPanel.css';

const CATEGORY_OPTIONS = [
  { value: 'MORAL', label: '德育附加分' },
  { value: 'INTELLECTUAL', label: '智育附加分' },
  { value: 'PHYSICAL', label: '体育附加分' }
];

const formatNumber = (value, digits = 2) => {
  const number = Number(value);
  return Number.isFinite(number) ? number.toFixed(digits) : '-';
};

const formatSigned = (value, digits = 2) => {
  const number = Number(value);
  if (!Number.isFinite(number)) return '-';
  const text = number.toFixed(digits);
  return number > 0 ? `+${text}` : text;
};

function ScoreSimulatorPanel({ academicYear }) {
  // 基线（无调整）与课程清单
  const [baseline, setBaseline] = useState(null);
  const [loadingBaseline, setLoadingBaseline] = useState(true);

  // 正向模拟状态
  const [courseOverrides, setCourseOverrides] = useState({});
  const [extraDeltas, setExtraDeltas] = useState({ MORAL: 0, INTELLECTUAL: 0, PHYSICAL: 0 });
  const [simulateResult, setSimulateResult] = useState(null);
  const [simulating, setSimulating] = useState(false);

  // 反向计算状态
  const [targetPercentile, setTargetPercentile] = useState(10);
  const [solveFor, setSolveFor] = useState('EXTRA');
  const [reverseCategory, setReverseCategory] = useState('MORAL');
  const [reverseSubjectId, setReverseSubjectId] = useState(null);
  const [reverseResult, setReverseResult] = useState(null);
  const [reversing, setReversing] = useState(false);

  const debounceRef = useRef(null);

  const courses = useMemo(() => (baseline?.courses || []), [baseline]);
  const adjustableCourses = useMemo(
    () => courses.filter((course) => course.category === 'INTELLECTUAL' || course.category === 'PHYSICAL'),
    [courses]
  );

  const buildAdjustments = useCallback(() => {
    const adjustments = [];
    Object.entries(courseOverrides).forEach(([subjectId, newScore]) => {
      if (newScore !== null && newScore !== undefined && newScore !== '') {
        adjustments.push({ type: 'COURSE', subjectId: Number(subjectId), newScore: Number(newScore) });
      }
    });
    Object.entries(extraDeltas).forEach(([category, delta]) => {
      if (delta) {
        adjustments.push({ type: 'EXTRA', category, delta: Number(delta) });
      }
    });
    return adjustments;
  }, [courseOverrides, extraDeltas]);

  const hasAdjustment = useMemo(
    () => buildAdjustments().length > 0,
    [buildAdjustments]
  );

  // 载入基线：无调整模拟一次，拿到 before + courses
  useEffect(() => {
    let cancelled = false;
    setLoadingBaseline(true);
    setCourseOverrides({});
    setExtraDeltas({ MORAL: 0, INTELLECTUAL: 0, PHYSICAL: 0 });
    setSimulateResult(null);
    setReverseResult(null);
    simulateScore({ academicYear, adjustments: [] })
      .then((res) => {
        if (cancelled) return;
        setBaseline(res?.data || null);
        setSimulateResult(res?.data || null);
      })
      .catch(() => {
        if (!cancelled) setBaseline(null);
      })
      .finally(() => {
        if (!cancelled) setLoadingBaseline(false);
      });
    return () => { cancelled = true; };
  }, [academicYear]);

  // 调整变化时防抖调用正向模拟
  useEffect(() => {
    if (loadingBaseline) return;
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!hasAdjustment) {
      setSimulateResult(baseline);
      return;
    }
    setSimulating(true);
    debounceRef.current = setTimeout(() => {
      simulateScore({ academicYear, adjustments: buildAdjustments() })
        .then((res) => setSimulateResult(res?.data || baseline))
        .catch(() => setSimulateResult(baseline))
        .finally(() => setSimulating(false));
    }, 400);
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseOverrides, extraDeltas, hasAdjustment, loadingBaseline]);

  const handleCourseChange = (subjectId, value) => {
    setCourseOverrides((prev) => {
      const next = { ...prev };
      if (value === null || value === undefined) {
        delete next[subjectId];
      } else {
        next[subjectId] = value;
      }
      return next;
    });
  };

  const handleExtraChange = (category, value) => {
    setExtraDeltas((prev) => ({ ...prev, [category]: value || 0 }));
  };

  const resetForward = () => {
    setCourseOverrides({});
    setExtraDeltas({ MORAL: 0, INTELLECTUAL: 0, PHYSICAL: 0 });
  };

  const runReverse = () => {
    if (!targetPercentile || targetPercentile <= 0 || targetPercentile > 100) {
      message.warning('请输入 1-100 之间的目标百分比');
      return;
    }
    const payload = { academicYear, targetPercentile: Number(targetPercentile), solveFor };
    if (solveFor === 'EXTRA') {
      payload.category = reverseCategory;
    } else {
      if (!reverseSubjectId) {
        message.warning('请选择要反解的课程');
        return;
      }
      payload.subjectId = Number(reverseSubjectId);
    }
    setReversing(true);
    reverseScore(payload)
      .then((res) => setReverseResult(res?.data || null))
      .catch(() => setReverseResult(null))
      .finally(() => setReversing(false));
  };

  if (loadingBaseline) {
    return (
      <Card variant="borderless" className="scores-content-card">
        <div className="score-sim-loading"><Spin size="large" /></div>
      </Card>
    );
  }

  if (!baseline) {
    return (
      <Card variant="borderless" className="scores-content-card">
        <Empty description="暂无可模拟的综合测评数据，请先由管理员刷新成绩快照" />
      </Card>
    );
  }

  const afterResult = simulateResult?.after || baseline.after || baseline.before;
  const beforeData = simulateResult?.before || baseline.before;
  const delta = simulateResult?.delta;
  const rankChange = simulateResult?.rankChange;

  return (
    <div className="score-sim">
      {/* 基线概览 */}
      <div className="score-sim-summary">
        <div className="score-sim-summary-item">
          <span className="score-sim-summary-label">班级</span>
          <span className="score-sim-summary-value">{baseline.className || '-'}</span>
        </div>
        <div className="score-sim-summary-item">
          <span className="score-sim-summary-label">班级人数</span>
          <span className="score-sim-summary-value">{baseline.classSize ?? '-'}</span>
        </div>
        <div className="score-sim-summary-item">
          <span className="score-sim-summary-label">当前综合总分</span>
          <span className="score-sim-summary-value primary">{formatNumber(baseline.before?.totalScore)}</span>
        </div>
        <div className="score-sim-summary-item">
          <span className="score-sim-summary-label">当前名次</span>
          <span className="score-sim-summary-value">
            {baseline.before?.rank ?? '-'}
            <span className="score-sim-summary-sub"> / {baseline.classSize ?? '-'}</span>
          </span>
        </div>
      </div>

      {/* 正向模拟 */}
      <Card variant="borderless" className="scores-content-card score-sim-card">
        <div className="scores-card-header">
          <h3 className="scores-card-title">正向模拟 · 调整课程分 / 附加分</h3>
          {hasAdjustment ? (
            <Button size="small" type="link" onClick={resetForward}>重置</Button>
          ) : null}
        </div>
        <div className="scores-card-body">
          <div className="score-sim-grid">
            <div className="score-sim-block">
              <div className="score-sim-block-title">课程分数</div>
              {adjustableCourses.length ? (
                <div className="score-sim-course-list">
                  {adjustableCourses.map((course) => (
                    <div key={course.subjectId} className="score-sim-course-row">
                      <span className="score-sim-course-name">{course.subjectName}</span>
                      <Tag className="score-sim-course-tag">{course.categoryLabel}</Tag>
                      <span className="score-sim-course-current">{formatNumber(course.score, 1)} 分</span>
                      <InputNumber
                        min={0}
                        max={100}
                        step={1}
                        placeholder="目标分"
                        value={courseOverrides[course.subjectId] ?? null}
                        onChange={(value) => handleCourseChange(course.subjectId, value)}
                        style={{ width: 110 }}
                      />
                    </div>
                  ))}
                </div>
              ) : <div className="score-sim-hint">当前学年无可调整的课程成绩</div>}
            </div>

            <div className="score-sim-block">
              <div className="score-sim-block-title">附加分增量</div>
              <div className="score-sim-extra-list">
                {CATEGORY_OPTIONS.map((option) => (
                  <div key={option.value} className="score-sim-extra-row">
                    <span className="score-sim-course-name">{option.label}</span>
                    <InputNumber
                      step={0.5}
                      placeholder="可为负"
                      value={extraDeltas[option.value] || null}
                      onChange={(value) => handleExtraChange(option.value, value)}
                      style={{ width: 140 }}
                    />
                  </div>
                ))}
              </div>
              <div className="score-sim-hint">模拟值不落库，仅用于查看影响。</div>
            </div>
          </div>

          {/* 结果对比 */}
          <div className="score-sim-result">
            <div className={`score-sim-result-col${simulating ? ' dim' : ''}`}>
              <div className="score-sim-result-label">模拟后综合总分</div>
              <div className="score-sim-result-value">
                {formatNumber(afterResult?.totalScore)}
                {hasAdjustment ? (
                  <span className={`score-sim-delta ${Number(delta) > 0 ? 'up' : Number(delta) < 0 ? 'down' : ''}`}>
                    {formatSigned(delta)}
                  </span>
                ) : null}
              </div>
              <div className="score-sim-result-sub">调整前 {formatNumber(beforeData?.totalScore)}</div>
            </div>
            <div className={`score-sim-result-col${simulating ? ' dim' : ''}`}>
              <div className="score-sim-result-label">模拟后班级名次</div>
              <div className="score-sim-result-value">
                {afterResult?.rank ?? '-'}
                <span className="score-sim-result-total"> / {afterResult?.classSize ?? baseline.classSize ?? '-'}</span>
                {hasAdjustment && Number(rankChange) !== 0 ? (
                  <span className={`score-sim-delta ${Number(rankChange) > 0 ? 'up' : 'down'}`}>
                    {Number(rankChange) > 0 ? `↑${rankChange}` : `↓${Math.abs(rankChange)}`}
                  </span>
                ) : null}
              </div>
              <div className="score-sim-result-sub">
                所处前 {formatNumber(afterResult?.percentile, 1)}% · 调整前第 {beforeData?.rank ?? '-'} 名
              </div>
            </div>
          </div>
        </div>
      </Card>

      {/* 反向计算 */}
      <Card variant="borderless" className="scores-content-card score-sim-card">
        <div className="scores-card-header">
          <h3 className="scores-card-title">反向计算 · 想进班级前百分之几</h3>
        </div>
        <div className="scores-card-body">
          <div className="score-sim-reverse-controls">
            <div className="score-sim-field">
              <label>目标名次（班级前 __ %）</label>
              <InputNumber
                min={1}
                max={100}
                step={1}
                value={targetPercentile}
                onChange={(value) => setTargetPercentile(value)}
                addonAfter="%"
                style={{ width: 160 }}
              />
            </div>
            <div className="score-sim-field">
              <label>反解口径</label>
              <Radio.Group value={solveFor} onChange={(e) => setSolveFor(e.target.value)}>
                <Radio.Button value="EXTRA">增加附加分</Radio.Button>
                <Radio.Button value="COURSE">提高课程分</Radio.Button>
              </Radio.Group>
            </div>
            {solveFor === 'EXTRA' ? (
              <div className="score-sim-field">
                <label>附加分类型</label>
                <Select
                  value={reverseCategory}
                  onChange={setReverseCategory}
                  options={CATEGORY_OPTIONS}
                  style={{ width: 160 }}
                />
              </div>
            ) : (
              <div className="score-sim-field">
                <label>选择课程</label>
                <Select
                  value={reverseSubjectId}
                  onChange={setReverseSubjectId}
                  placeholder="请选择课程"
                  style={{ width: 200 }}
                  options={adjustableCourses.map((course) => ({
                    value: course.subjectId,
                    label: `${course.subjectName}（${course.categoryLabel} ${formatNumber(course.score, 1)}）`
                  }))}
                />
              </div>
            )}
            <div className="score-sim-field score-sim-field-action">
              <Button type="primary" onClick={runReverse} loading={reversing}>计算</Button>
            </div>
          </div>

          {reverseResult ? (
            <div className="score-sim-reverse-result">
              <div className="score-sim-reverse-meta">
                <span>目标：班级前 {formatNumber(reverseResult.targetPercentile, 0)}%（第 {reverseResult.targetRank} 名）</span>
                <span>目标线总分：{formatNumber(reverseResult.thresholdTotal)}</span>
                <span>当前差距：{formatNumber(reverseResult.gap)}</span>
              </div>
              {reverseResult.message ? (
                <div className="score-sim-message">{reverseResult.message}</div>
              ) : null}
              {reverseResult.feasible && reverseResult.solveFor === 'EXTRA' ? (
                <div className="score-sim-answer">
                  需增加 <strong>{CATEGORY_OPTIONS.find((o) => o.value === reverseResult.category)?.label || reverseResult.category}</strong>
                  ：<strong className="answer-number">{formatNumber(reverseResult.neededExtra)}</strong> 分
                  {reverseResult.projectedAfter ? (
                    <span className="score-sim-projected">
                      预计总分 {formatNumber(reverseResult.projectedAfter.totalScore)}，
                      名次第 {reverseResult.projectedAfter.rank} / {reverseResult.projectedAfter.classSize}
                    </span>
                  ) : null}
                </div>
              ) : null}
              {reverseResult.solveFor === 'COURSE' && reverseResult.feasible ? (
                <div className="score-sim-answer">
                  需将 <strong>{reverseResult.subjectName}</strong> 提高到
                  ：<strong className="answer-number">{formatNumber(reverseResult.requiredScore, 1)}</strong> 分
                  （当前 {formatNumber(reverseResult.currentScore, 1)}，需 +{formatNumber(reverseResult.requiredDelta, 1)}）
                  {reverseResult.projectedAfter ? (
                    <span className="score-sim-projected">
                      预计总分 {formatNumber(reverseResult.projectedAfter.totalScore)}，
                      名次第 {reverseResult.projectedAfter.rank} / {reverseResult.projectedAfter.classSize}
                    </span>
                  ) : null}
                </div>
              ) : null}
              {reverseResult.solveFor === 'COURSE' && reverseResult.cappedAt100 ? (
                <div className="score-sim-answer infeasible">
                  即使 <strong>{reverseResult.subjectName}</strong> 提到满分 100 分仍差
                  <strong className="answer-number"> {formatNumber(reverseResult.remainingGap)} </strong>分，建议结合增加附加分。
                  {reverseResult.projectedAfter ? (
                    <span className="score-sim-projected">
                      满分后可达总分 {formatNumber(reverseResult.projectedAfter.totalScore)}，
                      名次第 {reverseResult.projectedAfter.rank} / {reverseResult.projectedAfter.classSize}
                    </span>
                  ) : null}
                </div>
              ) : null}
              {reverseResult.feasible === false && reverseResult.solveFor === 'EXTRA' ? (
                <div className="score-sim-answer infeasible">无法通过该方式达成目标。</div>
              ) : null}
            </div>
          ) : (
            <div className="score-sim-hint">设置目标百分比后点击"计算"，查看达成目标所需的附加分或课程分。</div>
          )}
        </div>
      </Card>
    </div>
  );
}

export default ScoreSimulatorPanel;
