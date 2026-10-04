import { useMemo, useRef, useState } from 'react';
import {
  AimOutlined,
  BarChartOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CompassOutlined,
  CrownOutlined,
  ExperimentOutlined,
  FireOutlined,
  HistoryOutlined,
  RadarChartOutlined,
  ReloadOutlined,
  RocketFilled,
  RocketOutlined,
  ThunderboltOutlined,
  TrophyOutlined
} from '@ant-design/icons';
import { Slider, Tag, Timeline } from 'antd';
import StudentLayout from '../../components/StudentLayout';
import EChartPanel from '../../components/EChartPanel';
import { simulateTimeMachine } from '../../api/student/timeMachine';
import './TimeMachine.css';

const RADAR_KEYS = ['思想品德', '发展潜力', '综合素质', '技能素养', '文体素养', '实践服务', '科研创新', '学业成绩'];

const clampRadarValue = (value) => Math.max(0, Math.min(100, Number(value || 0)));

const escapeHtml = (value) => String(value ?? '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;');

const PRESETS = [
  { key: 'calm', label: '保持现状', icon: <HistoryOutlined />, params: { scoreDelta: 0, competitionCount: 0, practiceCount: 0, certCount: 0 } },
  { key: 'study', label: '学霸养成', icon: <CrownOutlined />, params: { scoreDelta: 6, competitionCount: 1, practiceCount: 0, certCount: 1 } },
  { key: 'contest', label: '竞赛冲刺', icon: <FireOutlined />, params: { scoreDelta: 2, competitionCount: 3, practiceCount: 0, certCount: 1 } },
  { key: 'allround', label: '全面开花', icon: <CompassOutlined />, params: { scoreDelta: 3, competitionCount: 2, practiceCount: 3, certCount: 2 } }
];

const SLIDERS = [
  {
    key: 'scoreDelta',
    title: '平均分提升',
    desc: '把各科均分再往上拉一点',
    icon: <AimOutlined />,
    min: 0,
    max: 10,
    step: 1,
    marks: { 0: '0', 5: '5', 10: '10' },
    unit: '分'
  },
  {
    key: 'competitionCount',
    title: '多参加竞赛',
    desc: '挑战杯、蓝桥杯、数模……',
    icon: <TrophyOutlined />,
    min: 0,
    max: 3,
    step: 1,
    marks: { 0: '0', 3: '3' },
    unit: '项'
  },
  {
    key: 'practiceCount',
    title: '实践与志愿',
    desc: '志愿时长、社会实践、学生工作',
    icon: <ExperimentOutlined />,
    min: 0,
    max: 5,
    step: 1,
    marks: { 0: '0', 5: '5' },
    unit: '项'
  },
  {
    key: 'certCount',
    title: '专业证书',
    desc: '四六级、教资、软考、职业资格',
    icon: <CheckCircleOutlined />,
    min: 0,
    max: 3,
    step: 1,
    marks: { 0: '0', 3: '3' },
    unit: '本'
  }
];

const PATH_COLORS = {
  考研: '#158FED',
  考公: '#0d2b47',
  竞赛发展: '#31506e',
  直接就业: '#0f6fc0'
};

const WARP_STEPS = ['读取你的真实画像', '推演四条发展路径', '校准未来时间线'];

function TimeMachine({ embedded = false, active = true }) {
  const [params, setParams] = useState({ scoreDelta: 3, competitionCount: 1, practiceCount: 1, certCount: 1 });
  const [activePreset, setActivePreset] = useState(null);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [warpStep, setWarpStep] = useState(0);
  const reportRef = useRef(null);
  const requestIdRef = useRef(0);

  const applyParams = (next) => {
    setParams(next);
    setActivePreset(PRESETS.find((preset) =>
      preset.params.scoreDelta === next.scoreDelta
      && preset.params.competitionCount === next.competitionCount
      && preset.params.practiceCount === next.practiceCount
      && preset.params.certCount === next.certCount
    )?.key ?? null);
  };

  const handleSimulate = async () => {
    if (loading) return;
    const payload = params;
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setLoading(true);
    setResult(null);
    setError(null);
    setWarpStep(0);
    try {
      const res = await simulateTimeMachine(payload);
      if (requestId !== requestIdRef.current) return;
      const nextResult = res?.data;
      if (!nextResult || !Array.isArray(nextResult.paths) || nextResult.paths.length !== 4) {
        setError('未返回推演结果，请稍后重试');
        return;
      }
      setResult(nextResult);
      requestAnimationFrame(() => {
        if (requestId === requestIdRef.current) {
          reportRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      });
    } catch (err) {
      if (requestId === requestIdRef.current) {
        setError(err?.response?.data?.message || err?.message || '推演失败，请稍后重试');
      }
    } finally {
      if (requestId === requestIdRef.current) {
        setLoading(false);
      }
    }
  };

  const handlePreset = (preset) => {
    applyParams(preset.params);
  };

  const handleReset = () => {
    requestIdRef.current += 1;
    applyParams({ scoreDelta: 0, competitionCount: 0, practiceCount: 0, certCount: 0 });
    setResult(null);
    setError(null);
    setLoading(false);
  };

  const probabilityOption = useMemo(() => {
    const paths = result?.paths || [];
    if (!paths.length) return null;
    return {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        backgroundColor: '#ffffff',
        borderColor: '#e2e8f0',
        textStyle: { color: '#334155', fontSize: 12 },
        confine: true,
        extraCssText: 'max-width:280px;white-space:normal;',
        formatter(params) {
          const items = Array.isArray(params) ? params : [params];
          let html = '';
          items.forEach((item) => {
            html += `${item.marker}${item.seriesName}：${item.value}%<br/>`;
          });
          const path = paths[items[0]?.dataIndex];
          if (path?.reason) {
            html += `<div style="color:#64748b;font-size:12px;margin-top:4px;line-height:1.5;word-break:break-word;overflow-wrap:anywhere">${escapeHtml(path.reason)}</div>`;
          }
          return html;
        }
      },
      legend: {
        data: ['当前', '推演后'],
        top: 0,
        right: 0,
        textStyle: { color: '#64748b', fontSize: 12 }
      },
      grid: { left: 40, right: 16, top: 36, bottom: 28 },
      xAxis: {
        type: 'category',
        data: paths.map((p) => p.name),
        axisLine: { lineStyle: { color: '#dbe7fb' } },
        axisLabel: { color: '#334155', fontSize: 13 }
      },
      yAxis: {
        type: 'value',
        max: 100,
        splitLine: { lineStyle: { color: '#eef2f9' } },
        axisLabel: { formatter: '{value}%', color: '#94a3b8', fontSize: 11 }
      },
      series: [
        {
          name: '当前',
          type: 'bar',
          barWidth: 18,
          itemStyle: { color: '#dbe3f0', borderRadius: [4, 4, 0, 0] },
          emphasis: { itemStyle: { color: '#c7d4e8' } },
          data: paths.map((p) => p.current || 0)
        },
        {
          name: '推演后',
          type: 'bar',
          barWidth: 18,
          itemStyle: { color: '#2563eb', borderRadius: [4, 4, 0, 0], shadowBlur: 8, shadowColor: 'rgba(37,99,235,0.35)' },
          data: paths.map((p) => p.simulated || 0)
        }
      ]
    };
  }, [result]);

  const radarOption = useMemo(() => {
    const current = result?.currentRadar || {};
    const simulated = result?.simulatedRadar || {};
    const hasData = RADAR_KEYS.some((key) => current[key] != null || simulated[key] != null);
    if (!hasData) return null;
    return {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        backgroundColor: '#ffffff',
        borderColor: '#e2e8f0',
        textStyle: { color: '#334155', fontSize: 12 },
        formatter(params) {
          return `${params.marker}${params.name}<br/>`
            + RADAR_KEYS.map((label, index) => `${label}：${Number(params.value[index] || 0).toFixed(0)}`).join('<br/>');
        }
      },
      legend: {
        data: ['当前', '推演后'],
        top: 0,
        right: 0,
        textStyle: { color: '#64748b', fontSize: 12 }
      },
      radar: {
        radius: '56%',
        center: ['50%', '56%'],
        splitNumber: 4,
        axisName: { color: '#334155', fontSize: 12 },
        axisLine: { lineStyle: { color: '#dbe7fb' } },
        splitLine: { lineStyle: { color: '#e8eef9' } },
        splitArea: {
          areaStyle: {
            color: ['rgba(37,99,235,0.03)', 'rgba(37,99,235,0.07)']
          }
        },
        indicator: RADAR_KEYS.map((key) => ({ name: key, max: 100 }))
      },
      series: [
        {
          type: 'radar',
          data: [
            {
              value: RADAR_KEYS.map((key) => clampRadarValue(current[key])),
              name: '当前',
              symbol: 'circle',
              symbolSize: 4,
              lineStyle: { color: '#c7d4e8', width: 2 },
              itemStyle: { color: '#94a3b8' },
              areaStyle: { color: 'rgba(148,163,184,0.12)' }
            },
            {
              value: RADAR_KEYS.map((key) => clampRadarValue(simulated[key])),
              name: '推演后',
              symbol: 'circle',
              symbolSize: 4,
              lineStyle: { color: '#2563eb', width: 2 },
              itemStyle: { color: '#2563eb' },
              areaStyle: { color: 'rgba(37,99,235,0.18)' }
            }
          ]
        }
      ]
    };
  }, [result]);

  // current / simulated 各自被后端归一化为总和 100，平均差值恒为 0；
  // 改用「概率增厚合计」（正增量之和），反映推演把多少概率质量推向有利路径。
  const totalGain = (paths) => {
    if (!paths?.length) return 0;
    return paths.reduce((sum, p) => sum + Math.max(0, (p.simulated || 0) - (p.current || 0)), 0);
  };

  const renderSlider = (item) => {
    const value = params[item.key];
    return (
      <div className="tm-slider" key={item.key}>
        <div className="tm-slider-top">
          <span className="tm-slider-title">
            <i className="tm-slider-icon">{item.icon}</i>
            {item.title}
          </span>
          <span className="tm-slider-value">
            +{value} <em>{item.unit}</em>
          </span>
        </div>
        <Slider
          min={item.min}
          max={item.max}
          step={item.step}
          marks={item.marks}
          value={value}
          onChange={(v) => applyParams({ ...params, [item.key]: v })}
        />
        <p className="tm-slider-desc">{item.desc}</p>
      </div>
    );
  };

function WarpLoading({ warpStep, setWarpStep }) {
  return (
    <div className="tm-warp">
      <div className="tm-warp-scene">
        <div className="tm-warp-orbit tm-warp-orbit-1" />
        <div className="tm-warp-orbit tm-warp-orbit-2" />
        <RocketFilled className="tm-warp-rocket" />
      </div>
      <p className="tm-warp-title">正在穿越时空…</p>
      <div className="tm-warp-steps">
        {WARP_STEPS.map((step, index) => (
          <span
            key={step}
            className={`tm-warp-step ${index === warpStep ? 'active' : index < warpStep ? 'done' : ''}`}
            onAnimationEnd={index < 2 ? () => setWarpStep((w) => Math.max(w, index + 1)) : undefined}
          >
            {index < warpStep ? <CheckCircleOutlined /> : index === warpStep ? <RocketOutlined /> : <ClockCircleOutlined />}
            {step}
          </span>
        ))}
      </div>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="tm-empty">
      <div className="tm-empty-illustration">
        <div className="tm-empty-ring" />
        <RocketOutlined className="tm-empty-rocket" />
        <HistoryOutlined className="tm-empty-gear" />
      </div>
      <h3>未来尚未生成</h3>
      <p>在上方设定你的「如果……」，点击「穿越未来」<br />看看考研、考公、竞赛与就业的走向会如何变化。</p>
    </div>
  );
}

  const pageContent = (
    <div className="time-machine-page">
      {!embedded && (
        <header className="tm-hero">
          <div className="tm-hero-inner">
            <span className="tm-eyebrow"><ThunderboltOutlined /> TIME MACHINE · 未来推演实验室</span>
            <h1 className="tm-hero-title">
              如果当年稍微<span className="tm-hero-accent">再努力一点</span><br />
              未来的我会在哪里？
            </h1>
            <p className="tm-hero-desc">
              基于你的真实成绩与实践活动画像，AI 实时推演考研、考公、竞赛与就业四条路径的走向。拖一拖「如果」，看看命运的分叉口。
            </p>
            <div className="tm-hero-meta">
              {result ? (
                <Tag className="tm-hero-chip" icon={<TrophyOutlined />} color="geekblue">
                  当前画像 · {result.primaryType || '考研'}
                </Tag>
              ) : null}
              <span className="tm-hero-note"><RocketOutlined /> 实时计算 · 结果仅作规划参考</span>
            </div>
          </div>
          <div className="tm-hero-decor" aria-hidden="true">
            <div className="tm-orbit tm-orbit-outer" />
            <div className="tm-orbit tm-orbit-inner" />
            <RocketFilled className="tm-hero-rocket" />
          </div>
        </header>
      )}

      {/* 控制台：设定假设 */}
        <section className="tm-deck">
          <div className="tm-deck-head">
            <div>
              <h2 className="tm-section-title"><ExperimentOutlined /> 设定你的平行世界</h2>
              <p className="tm-section-desc">4 个调整维度，选一套预设或手动微调</p>
            </div>
            <div className="tm-presets">
              {PRESETS.map((preset) => (
                <button
                  type="button"
                  key={preset.key}
                  className={`tm-preset ${activePreset === preset.key ? 'active' : ''}`}
                  onClick={() => handlePreset(preset)}
                  disabled={loading}
                >
                  {preset.icon}
                  {preset.label}
                </button>
              ))}
            </div>
          </div>

          <div className="tm-sliders">{SLIDERS.map(renderSlider)}</div>

          <div className="tm-deck-actions">
            <button
              type="button"
              className={`tm-cta ${loading ? 'loading' : ''}`}
              onClick={() => handleSimulate()}
              disabled={loading}
            >
              {loading ? (
                <><RocketFilled className="tm-cta-rocket spin" /> 正在穿越未来…</>
              ) : (
                <><RocketFilled className="tm-cta-rocket" /> 穿越未来</>
              )}
            </button>
            <button type="button" className="tm-ghost" onClick={handleReset} disabled={loading}>
              <ReloadOutlined /> 重置
            </button>
          </div>
        </section>

        {/* 结果区 */}
        {!loading && !result && !error ? <EmptyState /> : null}

        {error && !result ? (
          <section className="tm-deck tm-error" style={{ '--i': 0 }}>
            <h3 className="tm-section-title"><CompassOutlined /> 推演暂时走偏了</h3>
            <p className="tm-section-desc">{error}</p>
            <p className="tm-section-desc">可稍后重试；若提示缺少画像，请先在「个人中心」生成画像，或直接点击上方「穿越未来」重新推演。</p>
          </section>
        ) : null}

        {loading ? (
          <section className="tm-deck"><WarpLoading warpStep={warpStep} setWarpStep={setWarpStep} /></section>
        ) : null}

        {!loading && result ? (
          <section className="tm-report" ref={reportRef}>
            {/* 报告头 */}
            <div className="tm-report-head" style={{ '--i': 0 }}>
              <div className="tm-from-orbit">
                <span className="tm-from-badge">当前</span>
                <b>{result.primaryType || '考研'}画像</b>
              </div>
              <div className="tm-jump">
                <RocketFilled />
                <span className="tm-jump-label">推演条件</span>
                <b>{result.adjustmentSummary || '维持现状'}</b>
              </div>
              <div className="tm-delta-block">
                <span className="tm-delta-label">路径概率增厚合计</span>
                <b className={`tm-delta-value ${totalGain(result.paths) > 0 ? 'up' : 'flat'}`}>
                  {totalGain(result.paths) > 0 ? `+${totalGain(result.paths)}%` : `${totalGain(result.paths)}%`}
                </b>
              </div>
            </div>

            {/* AI 点评 */}
            {result.summary ? (
              <div className="tm-ai-callout" style={{ '--i': 1 }}>
                <span className="tm-ai-avatar"><RocketFilled /></span>
                <div className="tm-ai-body">
                  <span className="tm-ai-tag">AI 洞察</span>
                  <p>{result.summary}</p>
                </div>
              </div>
            ) : null}

            {/* 图表区 */}
            <div className="tm-charts" style={{ '--i': 2 }}>
              <div className="tm-chart-panel">
                <h3 className="tm-chart-title"><BarChartOutlined /> 发展路径概率</h3>
                {probabilityOption ? <EChartPanel option={probabilityOption} height={300} visible={active} /> : <p className="tm-chart-empty">暂无路径数据</p>}
              </div>
              <div className="tm-chart-panel">
                <h3 className="tm-chart-title"><RadarChartOutlined /> 八维素质对比</h3>
                {radarOption ? <EChartPanel option={radarOption} height={300} visible={active} /> : <p className="tm-chart-empty">暂无雷达数据</p>}
              </div>
            </div>

            {/* 时间线 */}
            {result.timeline?.length ? (
              <div className="tm-timeline-block" style={{ '--i': 3 }}>
                <h3 className="tm-chart-title"><ClockCircleOutlined /> 未来时间线</h3>
                <Timeline
                  className="tm-timeline"
                  items={result.timeline.map((event, index) => ({
                    color: index === 0 ? '#2563eb' : '#b6c7e8',
                    dot: index === 0 ? <RocketFilled /> : <ClockCircleOutlined />,
                    children: (
                      <div className="tm-timeline-item">
                        <span className="tm-timeline-stage">{event.stage}</span>
                        <b>{event.title}</b>
                        <p>{event.description}</p>
                      </div>
                    )
                  }))}
                />
              </div>
            ) : null}

            {/* 建议 */}
            {result.suggestions?.length ? (
              <div className="tm-suggest-block" style={{ '--i': 4 }}>
                <h3 className="tm-chart-title"><ThunderboltOutlined /> 给你的行动建议</h3>
                <ul className="tm-suggest-list">
                  {result.suggestions.map((suggestion, index) => (
                    <li key={index}><span className="tm-suggest-badge">{index + 1}</span>{suggestion}</li>
                  ))}
                </ul>
              </div>
            ) : null}

            <p className="tm-disclaimer" style={{ '--i': 5 }}>
              推演结果基于历史数据与假设参数估算，仅供参考，不代表真实未来。
              {result.source === 'rules'
                ? '（当前为离线规则推演，配置 DeepSeek 后可获得更细腻的 AI 推演）'
                : '（由 DeepSeek AI 生成）'}
            </p>
          </section>
        ) : null}
      </div>
  );

  if (embedded) {
    return pageContent;
  }

  return (
    <StudentLayout activePath="/student/time-machine">
      {pageContent}
    </StudentLayout>
  );
}

export default TimeMachine;
