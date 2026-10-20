import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Empty,
  Spin,
  Tabs,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd';
import {
  DownloadOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import StudentLayout from '../../components/StudentLayout';
import EChartPanel from '../../components/EChartPanel';
import {
  getGrowthSummary,
  getGrowthTimeline,
  getGrowthResumeMaterials,
} from '../../api/student/growthPortfolio';
import './GrowthPortfolio.css';

const { CheckableTag } = Tag;

/** 事件类型展示元数据（与后端 StudentGrowthPortfolioService 的 type 一一对应） */
const EVENT_TYPE_META = {
  course: { label: '课程', color: '#1677ff' },
  competition: { label: '竞赛', color: '#fa8c16' },
  honor: { label: '荣誉', color: '#f5222d' },
  volunteer: { label: '志愿活动', color: '#52c41a' },
  practice: { label: '社会实践', color: '#13c2c2' },
  skill: { label: '技能证书', color: '#722ed1' },
  secondClass: { label: '第二课堂', color: '#eb2f96' },
  other: { label: '其他', color: '#8a9ab5' },
};

const RESUME_SECTION_META = [
  { key: 'honors', label: '荣誉与竞赛' },
  { key: 'practice', label: '实践经历' },
  { key: 'skills', label: '技能证书' },
];

const formatScore = (score) => (score === null || score === undefined ? '' : `${score} 分`);

function GrowthPortfolio() {
  const [loading, setLoading] = useState(true);
  const [summary, setSummary] = useState(null);
  const [timeline, setTimeline] = useState([]);
  const [resume, setResume] = useState(null);
  const [activeTab, setActiveTab] = useState('timeline');
  const [typeFilter, setTypeFilter] = useState([]);

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [summaryRes, timelineRes, resumeRes] = await Promise.all([
        getGrowthSummary(),
        getGrowthTimeline(),
        getGrowthResumeMaterials(),
      ]);
      if (summaryRes?.code === 200) {
        setSummary(summaryRes.data || null);
      } else {
        message.error(summaryRes?.message || '成长档案概览加载失败');
      }
      if (timelineRes?.code === 200) {
        setTimeline(Array.isArray(timelineRes.data) ? timelineRes.data : []);
      }
      if (resumeRes?.code === 200) {
        setResume(resumeRes.data || null);
      }
    } catch (error) {
      message.error('成长档案加载失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  /** 时间轴按 groupKey（学年/学期）分组，保持后端返回的时间倒序 */
  const groupedEvents = useMemo(() => {
    const visible = typeFilter.length === 0
      ? timeline
      : timeline.filter((event) => typeFilter.includes(event.type));
    const groups = [];
    const indexByKey = new Map();
    visible.forEach((event) => {
      const key = event.groupKey || '其他时期';
      if (!indexByKey.has(key)) {
        indexByKey.set(key, groups.length);
        groups.push({ key, events: [] });
      }
      groups[indexByKey.get(key)].events.push(event);
    });
    return groups;
  }, [timeline, typeFilter]);

  /** 综测快照图：逐学年德智体堆叠 + 总分折线 */
  const snapshotChartOption = useMemo(() => {
    const rows = summary?.yearlySnapshots || [];
    if (rows.length === 0) return null;
    return {
      tooltip: { trigger: 'axis' },
      legend: { data: ['德育', '智育', '体育', '综测总分'] },
      grid: { left: 48, right: 48, top: 40, bottom: 32 },
      xAxis: { type: 'category', data: rows.map((row) => row.academicYear) },
      yAxis: [
        { type: 'value', name: '分项', max: 100 },
        { type: 'value', name: '总分' },
      ],
      series: [
        { name: '德育', type: 'bar', stack: 'dimension', data: rows.map((row) => row.moralScore) },
        { name: '智育', type: 'bar', stack: 'dimension', data: rows.map((row) => row.intellectualScore) },
        { name: '体育', type: 'bar', stack: 'dimension', data: rows.map((row) => row.physicalScore) },
        {
          name: '综测总分',
          type: 'line',
          yAxisIndex: 1,
          symbolSize: 8,
          data: rows.map((row) => row.totalScore),
        },
      ],
    };
  }, [summary]);

  /** 学期图：课程均分折线 + 二课学分柱状（按学期对齐） */
  const semesterChartOption = useMemo(() => {
    const courseRows = summary?.semesterCourseAverages || [];
    const secondClassRows = summary?.secondClassSeries || [];
    const semesters = Array.from(new Set([
      ...courseRows.map((row) => row.semester),
      ...secondClassRows.map((row) => row.semester),
    ])).filter(Boolean).sort();
    if (semesters.length === 0) return null;
    const courseBySemester = new Map(courseRows.map((row) => [row.semester, row.average]));
    const secondClassBySemester = new Map(secondClassRows.map((row) => [row.semester, row.secondClassScore]));
    return {
      tooltip: { trigger: 'axis' },
      legend: { data: ['课程均分', '第二课堂学分'] },
      grid: { left: 48, right: 48, top: 40, bottom: 32 },
      xAxis: { type: 'category', data: semesters },
      yAxis: [
        { type: 'value', name: '课程均分', min: 0, max: 100 },
        { type: 'value', name: '二课学分' },
      ],
      series: [
        {
          name: '课程均分',
          type: 'line',
          symbolSize: 8,
          data: semesters.map((semester) => courseBySemester.get(semester) ?? null),
        },
        {
          name: '第二课堂学分',
          type: 'bar',
          yAxisIndex: 1,
          barMaxWidth: 26,
          itemStyle: { color: '#eb2f96' },
          data: semesters.map((semester) => secondClassBySemester.get(semester) ?? null),
        },
      ],
    };
  }, [summary]);

  const toggleType = (type, checked) => {
    setTypeFilter((prev) => (checked ? [...prev, type] : prev.filter((item) => item !== type)));
  };

  const copyText = async (text) => {
    try {
      await navigator.clipboard.writeText(text);
      message.success('已复制到剪贴板');
    } catch (error) {
      message.error('复制失败，请手动选择文本复制');
    }
  };

  const copySection = (sectionKey) => {
    const section = resume?.sections?.[sectionKey];
    if (!section || section.length === 0) {
      message.warning('该板块暂无素材');
      return;
    }
    const text = section
      .map((item) => (typeof item === 'string' ? item : item.text))
      .join('\n');
    copyText(text);
  };

  const handlePrint = () => {
    if (!summary) {
      message.warning('数据尚未加载完成');
      return;
    }
    window.print();
  };

  const stats = summary?.stats || {};
  const basic = summary?.basic || {};
  const resumeSections = resume?.sections || {};

  const statCards = [
    { label: '课程成绩', value: stats.courseCount ? `${stats.courseAverage ?? 0} 分` : '暂无', desc: `共 ${stats.courseCount ?? 0} 门 / ${stats.semesterCount ?? 0} 个学期` },
    { label: '竞赛获奖', value: `${stats.competitionCount ?? 0} 项`, desc: '已通过审核' },
    { label: '荣誉表彰', value: `${stats.honorCount ?? 0} 项`, desc: '已通过审核' },
    { label: '志愿活动', value: `${stats.volunteerCount ?? 0} 项`, desc: '已通过审核' },
    { label: '社会实践', value: `${stats.practiceCount ?? 0} 项`, desc: '已通过审核' },
    { label: '技能证书', value: `${stats.skillCount ?? 0} 项`, desc: '已通过审核' },
    { label: '第二课堂', value: stats.secondClassTotal ? `${stats.secondClassTotal} 分` : '暂无', desc: '累计学分' },
  ];

  return (
    <StudentLayout activePath="/student/growth-portfolio">
      <div className="growth-portfolio-page">
        <div className="growth-portfolio-toolbar">
          <div>
            <h2 className="growth-portfolio-title">我的成长档案</h2>
            <p className="growth-portfolio-subtitle">
              {basic.name || '同学'}
              {basic.studentNo ? ` · ${basic.studentNo}` : ''}
              {basic.majorName ? ` · ${basic.majorName}` : ''}
              {basic.className ? ` · ${basic.className}` : ''}
              ，自动整合课程、竞赛、实践、志愿、技能与荣誉。
            </p>
          </div>
          <div className="growth-portfolio-actions">
            <Button icon={<ReloadOutlined />} onClick={loadAll} disabled={loading}>
              刷新
            </Button>
            <Button type="primary" icon={<DownloadOutlined />} onClick={handlePrint} disabled={loading}>
              导出 PDF 成长报告
            </Button>
          </div>
        </div>

        <Spin spinning={loading}>
          <div className="growth-stat-cards">
            {statCards.map((card) => (
              <div className="growth-stat-card" key={card.label}>
                <span className="growth-stat-label">{card.label}</span>
                <strong className="growth-stat-value">{card.value}</strong>
                <span className="growth-stat-desc">{card.desc}</span>
              </div>
            ))}
          </div>

          <div className="growth-chart-row">
            <div className="growth-chart-card">
              <h3>逐学年综合素质测评</h3>
              {snapshotChartOption ? (
                <EChartPanel option={snapshotChartOption} height={300} />
              ) : (
                <Empty description="暂无综测快照数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}
            </div>
            <div className="growth-chart-card">
              <h3>学期课程均分与二课学分</h3>
              {semesterChartOption ? (
                <EChartPanel option={semesterChartOption} height={300} />
              ) : (
                <Empty description="暂无学期成绩数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}
            </div>
          </div>

          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={[
              {
                key: 'timeline',
                label: '成长时间轴',
                children: (
                  <div className="growth-timeline-panel">
                    <div className="growth-type-filter">
                      {Object.entries(EVENT_TYPE_META).map(([type, meta]) => (
                        <CheckableTag
                          key={type}
                          checked={typeFilter.includes(type)}
                          onChange={(checked) => toggleType(type, checked)}
                        >
                          {meta.label}
                        </CheckableTag>
                      ))}
                      {typeFilter.length > 0 && (
                        <Button size="small" type="link" onClick={() => setTypeFilter([])}>
                          清除筛选
                        </Button>
                      )}
                    </div>
                    {groupedEvents.length === 0 ? (
                      <Empty description="该筛选条件下暂无成长记录" />
                    ) : (
                      groupedEvents.map((group) => (
                        <div className="growth-timeline-group" key={group.key}>
                          <h4 className="growth-timeline-group-title">{group.key}</h4>
                          <Timeline
                            items={group.events.map((event) => {
                              const meta = EVENT_TYPE_META[event.type] || EVENT_TYPE_META.other;
                              return {
                                color: meta.color,
                                children: (
                                  <div className="growth-timeline-item">
                                    <div className="growth-timeline-item-head">
                                      <Tag color={meta.color} bordered={false}>{event.typeLabel || meta.label}</Tag>
                                      {event.date && <span className="growth-timeline-date">{event.date}</span>}
                                      {event.score !== null && event.score !== undefined && (
                                        <span className="growth-timeline-score">{formatScore(event.score)}</span>
                                      )}
                                    </div>
                                    <div className="growth-timeline-item-title">{event.title}</div>
                                    {event.subtitle && (
                                      <div className="growth-timeline-item-subtitle">{event.subtitle}</div>
                                    )}
                                    {event.description && (
                                      <div className="growth-timeline-item-desc">{event.description}</div>
                                    )}
                                    {Array.isArray(event.courses) && event.courses.length > 0 && (
                                      <div className="growth-timeline-courses">
                                        {event.courses.map((course) => (
                                          <span className="growth-course-chip" key={`${event.id}-${course.subjectName}`}>
                                            {course.subjectName} {course.score}
                                          </span>
                                        ))}
                                      </div>
                                    )}
                                  </div>
                                ),
                              };
                            })}
                          />
                        </div>
                      ))
                    )}
                  </div>
                ),
              },
              {
                key: 'resume',
                label: '求职简历素材',
                children: (
                  <div className="growth-resume-panel">
                    <div className="growth-resume-section">
                      <div className="growth-resume-section-head">
                        <h4>教育经历</h4>
                        <Button size="small" onClick={() => copySection('education')}>复制本板块</Button>
                      </div>
                      {(resumeSections.education || []).length === 0 ? (
                        <Empty description="暂无教育经历素材" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                      ) : (
                        (resumeSections.education || []).map((line, index) => (
                          <Typography.Paragraph
                            key={`education-${index}`}
                            className="growth-resume-line"
                            copyable={{ text: line }}
                          >
                            {line}
                          </Typography.Paragraph>
                        ))
                      )}
                    </div>
                    {RESUME_SECTION_META.map(({ key, label }) => {
                      const items = resumeSections[key] || [];
                      return (
                        <div className="growth-resume-section" key={key}>
                          <div className="growth-resume-section-head">
                            <h4>{label}</h4>
                            <Button size="small" onClick={() => copySection(key)}>复制本板块</Button>
                          </div>
                          {items.length === 0 ? (
                            <Empty description={`暂无${label}素材`} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                          ) : (
                            items.map((item) => (
                              <Typography.Paragraph
                                key={item.id}
                                className="growth-resume-line"
                                copyable={{ text: item.text }}
                              >
                                {item.text}
                              </Typography.Paragraph>
                            ))
                          )}
                        </div>
                      );
                    })}
                    <p className="growth-resume-tip">
                      仅统计已通过审核的附加分记录；复制后可直接粘贴到简历工具中继续编辑。
                    </p>
                  </div>
                ),
              },
            ]}
          />
        </Spin>

        {/* A4 打印版成长报告：屏幕上隐藏，浏览器打印（另存为 PDF）时渲染 */}
        <div className="growth-print-report">
          <h1>个人成长报告</h1>
          <div className="growth-print-basic">
            {[basic.name, basic.studentNo, basic.collegeName, basic.majorName, basic.className, basic.grade]
              .filter(Boolean)
              .join(' · ')}
          </div>
          <p className="growth-print-intro">
            本报告由综测管理系统自动生成，整合课程学习、竞赛荣誉、社会实践、志愿服务、技能证书与第二课堂记录。
          </p>

          <h2>成长概览</h2>
          <table className="growth-print-table">
            <tbody>
              <tr>
                <td>课程平均分</td>
                <td>{stats.courseAverage ?? '-'}（{stats.courseCount ?? 0} 门）</td>
                <td>第二课堂累计</td>
                <td>{stats.secondClassTotal ?? '-'} 分</td>
              </tr>
              <tr>
                <td>竞赛获奖</td>
                <td>{stats.competitionCount ?? 0} 项</td>
                <td>荣誉表彰</td>
                <td>{stats.honorCount ?? 0} 项</td>
              </tr>
              <tr>
                <td>志愿活动</td>
                <td>{stats.volunteerCount ?? 0} 项</td>
                <td>社会实践 / 技能证书</td>
                <td>{stats.practiceCount ?? 0} 项 / {stats.skillCount ?? 0} 项</td>
              </tr>
            </tbody>
          </table>

          {(summary?.yearlySnapshots || []).length > 0 && (
            <>
              <h2>历年综合素质测评</h2>
              <table className="growth-print-table">
                <thead>
                  <tr>
                    <th>学年</th>
                    <th>德育</th>
                    <th>智育</th>
                    <th>体育</th>
                    <th>综测总分</th>
                    <th>班级排名</th>
                  </tr>
                </thead>
                <tbody>
                  {summary.yearlySnapshots.map((row) => (
                    <tr key={row.academicYear}>
                      <td>{row.academicYear}</td>
                      <td>{row.moralScore ?? '-'}</td>
                      <td>{row.intellectualScore ?? '-'}</td>
                      <td>{row.physicalScore ?? '-'}</td>
                      <td>{row.totalScore ?? '-'}</td>
                      <td>{row.rankNo ?? '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}

          <h2>时间轴纪要</h2>
          {timeline.filter((event) => event.type !== 'course').length === 0 ? (
            <p className="growth-print-empty">暂无获奖、实践、证书类记录。</p>
          ) : (
            <ul className="growth-print-timeline">
              {timeline
                .filter((event) => event.type !== 'course')
                .slice(0, 40)
                .map((event) => (
                  <li key={event.id}>
                    <span className="growth-print-event-type">[{event.typeLabel}]</span>
                    <span>{event.date || event.groupKey || ''} {event.title}</span>
                    {event.subtitle ? <span className="growth-print-event-sub">（{event.subtitle}）</span> : null}
                  </li>
                ))}
            </ul>
          )}

          {RESUME_SECTION_META.map(({ key, label }) => {
            const items = resumeSections[key] || [];
            if (items.length === 0) return null;
            return (
              <div key={key}>
                <h2>{label}</h2>
                <ul className="growth-print-plain-list">
                  {items.map((item) => (
                    <li key={item.id}>{item.text}</li>
                  ))}
                </ul>
              </div>
            );
          })}

          <p className="growth-print-footer">
            生成时间：{new Date().toLocaleString('zh-CN')} · 数据以学校综测系统审核通过结果为准
          </p>
        </div>
      </div>
    </StudentLayout>
  );
}

export default GrowthPortfolio;
