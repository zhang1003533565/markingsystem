import { useCallback, useEffect, useMemo, useState } from 'react';
import { message } from 'antd';
import {
  getDailyCheckinOverview,
  markProgressCompleted,
} from '../api/student/learningProgress';
import './DailyCheckinCard.css';

/**
 * 完全参照洛谷打卡：
 * 未打卡：欢迎回来 + 大号日期（左右竖排月/星期）+ 倒计时 +「点击打卡」
 * 已打卡：「XX 的运势」+ § 档位 § + 宜/忌（大吉无忌、大凶无宜）+ 连续天数
 */

const FORTUNE_LEVELS = [
  { level: '大吉', tone: 'great', yiCount: 2, jiCount: 0 },
  { level: '中吉', tone: 'good', yiCount: 2, jiCount: 2 },
  { level: '小吉', tone: 'mild', yiCount: 2, jiCount: 2 },
  { level: '中平', tone: 'plain', yiCount: 2, jiCount: 2 },
  { level: '小凶', tone: 'warn', yiCount: 2, jiCount: 2 },
  { level: '凶', tone: 'bad', yiCount: 1, jiCount: 2 },
  { level: '大凶', tone: 'awful', yiCount: 0, jiCount: 2 },
];

const CHECKIN_EVENTS = [
  { name: '完成微课', yi: '知识一次进脑', ji: '看到一半就走神' },
  { name: '刷题巩固', yi: '手感火热连对', ji: '越刷越糊' },
  { name: '整理错题', yi: '薄弱点一目了然', ji: '本子越整理越乱' },
  { name: '练手仿真', yi: '操作行云流水', ji: '步骤全忘光' },
  { name: '请教同学', yi: '一点就通', ji: '越问越懵' },
  { name: '规划明日', yi: '节奏清晰有底', ji: '计划写完就鸽' },
  { name: '复盘进度', yi: '收获感拉满', ji: '越看越焦虑' },
  { name: '专注一小时', yi: '心流状态在线', ji: '三分钟就刷手机' },
  { name: '早睡充电', yi: '明天精神加倍', ji: '越躺越清醒' },
  { name: '收藏岗位', yi: '目标更清晰', ji: '收藏夹吃灰' },
  { name: '生成资源', yi: '路径马上可用', ji: '额度刚好用完' },
  { name: '跟成长链路', yi: '稳步解锁下一关', ji: '卡在第一关不动' },
  { name: '问 AI 助手', yi: '一问就点醒', ji: '答非所问' },
  { name: '改简历表述', yi: '关键词精准命中', ji: '越改越空' },
  { name: '看热门岗位', yi: '发现心仪方向', ji: '越看越内耗' },
  { name: '写学习笔记', yi: '日后复习省心', ji: '写完就找不到' },
];

const WEEKDAY_CN = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
const MONTH_CN = ['一月', '二月', '三月', '四月', '五月', '六月', '七月', '八月', '九月', '十月', '十一月', '十二月'];

function hashString(input) {
  let h = 2166136261;
  for (let i = 0; i < input.length; i += 1) {
    h ^= input.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function mulberry32(seed) {
  let t = seed >>> 0;
  return () => {
    t += 0x6d2b79f5;
    let r = Math.imul(t ^ (t >>> 15), 1 | t);
    r ^= r + Math.imul(r ^ (r >>> 7), 61 | r);
    return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
  };
}

function getTodayKey() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function daysUntil(target) {
  const now = new Date();
  now.setHours(0, 0, 0, 0);
  const end = new Date(target);
  end.setHours(0, 0, 0, 0);
  return Math.max(0, Math.round((end - now) / 86400000));
}

function nextWeekday(from, weekday) {
  const d = new Date(from);
  d.setHours(0, 0, 0, 0);
  const delta = (weekday - d.getDay() + 7) % 7 || 7;
  d.setDate(d.getDate() + delta);
  return d;
}

function pickEvents(rng, count, used) {
  const pool = CHECKIN_EVENTS.filter((item) => !used.has(item.name));
  const picked = [];
  for (let i = 0; i < count && pool.length > 0; i += 1) {
    const index = Math.floor(rng() * pool.length);
    const [item] = pool.splice(index, 1);
    used.add(item.name);
    picked.push(item);
  }
  return picked;
}

function getDailyFortune(dateKey) {
  const rng = mulberry32(hashString(`gpath-luogu-punch-${dateKey}`));
  const levelMeta = FORTUNE_LEVELS[Math.floor(rng() * FORTUNE_LEVELS.length)];
  const used = new Set();
  const yiEvents = pickEvents(rng, levelMeta.yiCount, used).map((item) => ({
    name: item.name,
    tip: item.yi,
  }));
  const jiEvents = pickEvents(rng, levelMeta.jiCount, used).map((item) => ({
    name: item.name,
    tip: item.ji,
  }));
  return {
    level: levelMeta.level,
    tone: levelMeta.tone,
    yi: yiEvents,
    ji: jiEvents,
  };
}

function FortuneItem({ kind, name, tip }) {
  return (
    <div className={`daily-checkin-item is-${kind}`}>
      <strong>
        {kind === 'yi' ? '宜' : '忌'}
        ：
        {name}
      </strong>
      <em>{tip}</em>
    </div>
  );
}

/**
 * 洛谷同款每日打卡
 * @param {'bar' | 'square'} variant
 */
export default function DailyCheckinCard({
  className = '',
  id,
  variant = 'bar',
  userName = '同学',
}) {
  const todayKey = useMemo(() => getTodayKey(), []);
  const fortune = useMemo(() => getDailyFortune(todayKey), [todayKey]);
  const isSquare = variant === 'square';
  const displayName = String(userName || '同学').trim() || '同学';

  const todayMeta = useMemo(() => {
    const d = new Date();
    const dayNum = String(d.getDate()).padStart(2, '0');
    const daysInMonth = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
    const monthLabel = `${MONTH_CN[d.getMonth()]}${daysInMonth <= 30 ? '小' : '大'}`;
    const weekday = WEEKDAY_CN[d.getDay()];
    const nextMonday = nextWeekday(d, 1);
    const monthEnd = new Date(d.getFullYear(), d.getMonth() + 1, 0);
    return {
      dayNum,
      monthLabel,
      weekday,
      countdowns: [
        { label: '距本周岗位刷新', days: daysUntil(nextMonday) },
        { label: '距本月末', days: daysUntil(monthEnd) },
      ],
    };
  }, []);

  const [overview, setOverview] = useState({
    todayChecked: false,
    streakDays: 0,
    recent7: [],
  });
  const [submitting, setSubmitting] = useState(false);

  const loadOverview = useCallback(async () => {
    try {
      const res = await getDailyCheckinOverview();
      if (res?.data) setOverview(res.data);
    } catch {
      // 打卡概览失败不阻塞页面
    }
  }, []);

  useEffect(() => {
    loadOverview();
  }, [loadOverview]);

  const handleCheckin = async () => {
    if (submitting || overview.todayChecked) return;
    setSubmitting(true);
    try {
      await markProgressCompleted({
        recommendationId: 0,
        itemType: 'DAILY_CHECKIN',
        itemKey: todayKey,
        itemTitle: `每日打卡-${todayKey}`,
      });
      message.success(`打卡成功 · 今日运势「${fortune.level}」`);
      await loadOverview();
    } catch {
      // 错误已由拦截器提示
    } finally {
      setSubmitting(false);
    }
  };

  const showResult = overview.todayChecked;
  const isAwful = fortune.level === '大凶';
  const isGreat = fortune.level === '大吉';

  return (
    <section
      id={id}
      className={`daily-checkin ${isSquare ? 'is-square' : ''} ${showResult ? 'is-result' : 'is-entry'} ${className}`.trim()}
      aria-label="每日打卡"
    >
      {!showResult ? (
        <div className="daily-checkin-entry">
          <p className="daily-checkin-hello">
            欢迎回来，
            <span>{displayName}</span>
          </p>

          <div className="daily-checkin-date" aria-label={`${todayMeta.monthLabel} ${todayMeta.dayNum} ${todayMeta.weekday}`}>
            <span className="daily-checkin-date-side is-month">{todayMeta.monthLabel}</span>
            <strong className="daily-checkin-date-num">{todayMeta.dayNum}</strong>
            <span className="daily-checkin-date-side is-week">{todayMeta.weekday}</span>
          </div>

          <div className="daily-checkin-countdowns">
            {todayMeta.countdowns.map((item) => (
              <p key={item.label}>
                {item.label}
                {' '}
                还剩
                {' '}
                <em>{item.days}</em>
                {' '}
                天
              </p>
            ))}
          </div>

          <button
            type="button"
            className="daily-checkin-punch"
            onClick={handleCheckin}
            disabled={submitting}
          >
            {submitting ? '打卡中…' : '点击打卡'}
          </button>
        </div>
      ) : (
        <div className="daily-checkin-result">
          <h2 className="daily-checkin-result-title">
            <span>{displayName}</span>
            {' '}
            的运势
          </h2>

          <p className={`daily-checkin-result-level tone-${fortune.tone}`}>
            §
            {' '}
            {fortune.level}
            {' '}
            §
          </p>

          <div
            className={`daily-checkin-body ${
              isAwful || isGreat || fortune.yi.length === 0 || fortune.ji.length === 0
                ? 'is-banner'
                : ''
            }`}
          >
            {isAwful ? (
              <>
                <p className="daily-checkin-banner is-bad">诸事不宜</p>
                <div className="daily-checkin-list">
                  {fortune.ji.map((item) => (
                    <FortuneItem key={`ji-${item.name}`} kind="ji" name={item.name} tip={item.tip} />
                  ))}
                </div>
              </>
            ) : null}

            {isGreat ? (
              <>
                <div className="daily-checkin-list">
                  {fortune.yi.map((item) => (
                    <FortuneItem key={`yi-${item.name}`} kind="yi" name={item.name} tip={item.tip} />
                  ))}
                </div>
                <p className="daily-checkin-banner is-good">万事皆宜</p>
              </>
            ) : null}

            {!isAwful && !isGreat ? (
              <>
                <div className="daily-checkin-list is-yi">
                  {fortune.yi.map((item) => (
                    <FortuneItem key={`yi-${item.name}`} kind="yi" name={item.name} tip={item.tip} />
                  ))}
                </div>
                <div className="daily-checkin-list is-ji">
                  {fortune.ji.map((item) => (
                    <FortuneItem key={`ji-${item.name}`} kind="ji" name={item.name} tip={item.tip} />
                  ))}
                </div>
              </>
            ) : null}
          </div>

          <p className="daily-checkin-streak-line">
            你已经连续打卡了
            {' '}
            <strong>{overview.streakDays}</strong>
            {' '}
            天
          </p>
        </div>
      )}
    </section>
  );
}
