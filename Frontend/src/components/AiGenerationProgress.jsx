import { useEffect, useState } from 'react';
import { Progress } from 'antd';
import './AiGenerationProgress.css';

/** 学习资源生成阶段文案（按模拟进度切换，非后端真实百分比） */
const DEFAULT_STAGES = [
  { max: 12, label: '排队等待 AI 通道…' },
  { max: 35, label: '联网检索岗位相关资料…' },
  { max: 58, label: '整理六维学习资源…' },
  { max: 78, label: '撰写图文微课内容…' },
  { max: 92, label: '校验推荐链接可用性…' },
  { max: 100, label: '即将完成…' },
];

/**
 * AI 长耗时任务的进度展示。
 * 后端异步生成无真实百分比，这里用缓动模拟进度，避免用户误以为卡住。
 *
 * @param {boolean} active 是否处于生成中
 * @param {string} [title] 主提示
 * @param {string} [hint] 次要说明
 * @param {'block'|'inline'} [variant] block=整块加载区；inline=列表上方细条
 * @param {Array<{max:number,label:string}>} [stages]
 */
function AiGenerationProgress({
  active,
  title = 'AI 正在生成学习资源',
  hint = '后台异步处理中，通常 1–3 分钟，可稍候本页',
  variant = 'block',
  stages = DEFAULT_STAGES,
}) {
  // 父级在 inactive 时通常会卸载本组件；挂载即从 4% 开始
  const [percent, setPercent] = useState(4);

  useEffect(() => {
    if (!active) return undefined;

    const startedAt = Date.now();
    // 模拟进度：仅在文档可见时计时/更新，页面隐藏（切后台标签页）时暂停 setInterval，
    // 避免 hidden 的生成视图每 400ms 触发 React 协调空转。
    let timer = null;
    const startTimer = () => {
      if (typeof document === 'undefined' || document.hidden) return;
      if (timer) return;
      timer = window.setInterval(() => {
        const elapsed = Date.now() - startedAt;
        const eased = 92 * (1 - Math.exp(-elapsed / 55000));
        const lateBoost = elapsed > 150000 ? Math.min(4, (elapsed - 150000) / 60000) : 0;
        setPercent(Math.min(96, Math.round(eased + lateBoost)));
      }, 400);
    };
    const stopTimer = () => {
      if (timer) {
        window.clearInterval(timer);
        timer = null;
      }
    };

    const handleVisibility = () => {
      if (document.hidden) {
        stopTimer();
      } else {
        startTimer();
      }
    };

    startTimer();
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', handleVisibility);
    }
    return () => {
      stopTimer();
      if (typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', handleVisibility);
      }
    };
  }, [active]);

  if (!active) return null;

  const stage =
    stages.find((item) => percent <= item.max)?.label ||
    stages[stages.length - 1]?.label ||
    '处理中…';

  return (
    <div
      className={
        variant === 'inline' ? 'ai-gen-progress ai-gen-progress--inline' : 'ai-gen-progress'
      }
      role="status"
      aria-live="polite"
      aria-busy="true"
    >
      <div className="ai-gen-progress-copy">
        <p className="ai-gen-progress-title">{title}</p>
        <p className="ai-gen-progress-stage">{stage}</p>
        {hint ? <p className="ai-gen-progress-hint">{hint}</p> : null}
      </div>
      <Progress
        percent={percent}
        status="active"
        strokeColor={{ from: '#1677ff', to: '#36cfc9' }}
        format={(value) => `${value}%`}
      />
    </div>
  );
}

export default AiGenerationProgress;
