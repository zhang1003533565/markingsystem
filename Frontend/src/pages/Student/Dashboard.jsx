import { useCallback, useEffect, useMemo, useState } from 'react';
import { Steps, message, Modal } from 'antd';
import { useNavigate } from 'react-router-dom';
import AnnouncementDetailDrawer from '../../components/AnnouncementDetailDrawer';
import StudentLayout from '../../components/StudentLayout';
import EChartPanel from '../../components/EChartPanel';
import ScoreTrendPanel from '../../components/ScoreTrendPanel';
import HomeFusedArt from '../../components/HomeFusedArt';
import { getAnnouncementDetail, getMyAnnouncements } from '../../api/announcement';
import {
  getStudentCompositeScoreSnapshot,
  getStudentHomeSummary,
  getStudentPortalInfo,
  getStudentPortalScores,
  getStudentPortalTrend,
  getStudentProfileAnalysis,
  getStudentWeeklyJobRecommendations,
  refreshStudentWeeklyJobRecommendations,
  getStudentAcademicYears
} from '../../api/student/portal';
import { getCurrentExtraScoreApplicationProcess } from '../../api/student/extraScore';
import { getStoredUser, withAuthToken } from '../../utils/authStorage';
import { DEFAULT_JOB_RECOMMENDATIONS } from '../../constants/jobRecommendations';
import './Dashboard.css';

const scoreMeta = {
  德育: { color: '#ffbf3d', extraColor: '#f97316', muted: '#fff4d6' },
  智育: { color: '#4f84ff', extraColor: '#7c3aed', muted: '#eaf1ff' },
  体育: { color: '#43c78f', extraColor: '#0f9f75', muted: '#e7faf2' }
};

const extraTypeMeta = {
  MORAL: { label: '德育类', icon: 'moral' },
  INTELLECTUAL: { label: '智育类', icon: 'intellectual' },
  PHYSICAL: { label: '体育类', icon: 'physical' }
};

const RADAR_KEYS = ['思想品德', '发展潜力', '综合素质', '技能素养', '文体素养', '实践服务', '科研创新', '学业成绩'];
const clampRadarValue = (value) => Math.max(0, Math.min(100, Number(value || 0)));

function buildExtraScoreTimeline(record) {
  const isDraft = record?.status === 'draft' || record?.reviewStage === 'draft';
  return [
    {
      title: '提交申请',
      status: isDraft ? 'process' : 'finish',
      description: isDraft
        ? '草稿已保存，尚未统一提交审核'
        : (record?.uploadTime ? `学生于 ${formatDateTime(record.uploadTime)} 提交` : '已提交')
    },
    {
      title: '班委初审',
      status: record?.committeeStatus === 'approved'
        ? 'finish'
        : record?.committeeStatus === 'returned'
          ? 'error'
          : record?.reviewStage === 'committee_pending'
            ? 'process'
            : record?.reviewStage === 'teacher_pending' || record?.status === 'approved'
              ? 'finish'
              : 'wait',
      description: record?.committeeReviewerName
        ? `${record.committeeReviewerName}${record.committeeReviewedAt ? ` · ${formatDateTime(record.committeeReviewedAt)}` : ''}${record.committeeComment ? ` · ${record.committeeComment}` : ''}`
        : (isDraft ? '统一提交后进入班委初审' : (record?.reviewStage === 'committee_pending' ? '等待班委处理' : '已进入下一阶段'))
    },
    {
      title: '老师终审',
      status: record?.teacherStatus === 'approved'
        ? 'finish'
        : record?.teacherStatus === 'returned'
          ? 'error'
          : record?.reviewStage === 'teacher_pending'
            ? 'process'
            : record?.status === 'approved'
              ? 'finish'
              : 'wait',
      description: record?.teacherReviewerName
        ? `${record.teacherReviewerName}${record.teacherReviewedAt ? ` · ${formatDateTime(record.teacherReviewedAt)}` : ''}${record.teacherComment ? ` · ${record.teacherComment}` : ''}`
        : (record?.reviewStage === 'teacher_pending' ? '等待老师处理' : '待班委通过后进入')
    },
    {
      title: '最终结果',
      status: record?.status === 'approved'
        ? 'finish'
        : record?.status === 'returned' || record?.status === 'rejected'
          ? 'error'
          : 'wait',
      description: record?.status === 'approved'
        ? '已生效'
        : record?.returnReason || '等待完成'
    }
  ];
}

const EMPTY_DASHBOARD_DATA = {
  studentInfo: null,
  scores: [],
  trendPoints: [],
  announcements: [],
  currentExtraProcess: null,
  approvedExtraScoreByType: {
    MORAL: 0,
    INTELLECTUAL: 0,
    PHYSICAL: 0
  },
  compositeSnapshot: null,
  analysis: null,
  homeSummary: null,
  jobRecommendations: [],
  academicYears: []
};

const DEFAULT_APPROVED_EXTRA_SCORE_BY_TYPE = {
  MORAL: 0,
  INTELLECTUAL: 0,
  PHYSICAL: 0
};

const SCORE_SNAPSHOT_ITEMS = [
  {
    type: 'MORAL',
    label: '德育',
    baseFields: ['weightedMoralBaseScore', 'moralBaseScore'],
    extraFields: ['weightedMoralExtraScore', 'moralExtraScore']
  },
  {
    type: 'INTELLECTUAL',
    label: '智育',
    baseFields: ['weightedIntellectualBaseScore', 'intellectualBaseScore'],
    extraFields: ['weightedIntellectualExtraScore', 'intellectualExtraScore']
  },
  {
    type: 'PHYSICAL',
    label: '体育',
    baseFields: ['weightedPhysicalBaseScore', 'physicalBaseScore'],
    extraFields: ['weightedPhysicalExtraScore', 'physicalExtraScore']
  }
];

function formatNumber(value, digits = 1) {
  const number = Number(value);
  return Number.isFinite(number) ? number.toFixed(digits) : '-';
}

function resolveSnapshotNumber(snapshot, fields) {
  for (const field of fields) {
    const rawValue = snapshot?.[field];
    if (rawValue === null || rawValue === undefined || rawValue === '') {
      continue;
    }
    const value = Number(rawValue);
    if (Number.isFinite(value)) {
      return value;
    }
  }
  return null;
}

function buildScoreRingBackground(item) {
  const meta = scoreMeta[item.label];
  const baseValue = Math.max(0, Math.min(100, Number(item.baseScore) || 0));
  const extraValue = Math.max(0, Number(item.extraScore) || 0);
  const baseEnd = baseValue * 3.6;
  const totalEnd = Math.max(baseEnd, Math.min(360, (baseValue + extraValue) * 3.6));
  return `conic-gradient(${meta.color} 0deg ${baseEnd}deg, ${meta.extraColor} ${baseEnd}deg ${totalEnd}deg, ${meta.muted} ${totalEnd}deg 360deg)`;
}

function formatDate(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(5, 10);
}

function formatDateTime(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 16);
}

function formatJobWeekRange(item) {
  if (!item?.weekStartDate || !item?.weekEndDate) {
    return '本周更新';
  }
  return `${formatDate(item.weekStartDate)} - ${formatDate(item.weekEndDate)}`;
}

function resolveJobSearchLink(job) {
  const keyword = String(job?.jobTitle || '软件工程师').trim() || '软件工程师';
  return `https://www.zhipin.com/web/geek/job?query=${encodeURIComponent(keyword)}`;
}

function sortAcademicPeriod(left, right) {
  return String(left || '').localeCompare(String(right || ''), 'zh-Hans-CN', { numeric: true });
}

function toRecordList(response) {
  if (Array.isArray(response?.data?.records)) {
    return response.data.records;
  }
  if (Array.isArray(response?.data)) {
    return response.data;
  }
  return [];
}

function getAnnouncementSnippet(content) {
  if (!content) {
    return '暂无内容摘要';
  }
  return String(content).replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 46) || '暂无内容摘要';
}

function getInitials(name) {
  const text = String(name || '').trim();
  return text ? text.charAt(0).toUpperCase() : 'S';
}

function getConfidenceLabel(value) {
  const score = Number(value) || 0;
  if (score >= 75) return '较高可信';
  if (score >= 50) return '中等可信';
  return '参考性有限';
}

function getConfidenceTone(value) {
  const score = Number(value) || 0;
  if (score >= 75) return 'high';
  if (score >= 50) return 'mid';
  return 'low';
}

/** Highlight numeric scores / percentages in AI analysis prose for scannability. */
function renderAnalysisText(text) {
  const raw = String(text || '');
  const parts = raw.split(/(\d+(?:\.\d+)?(?:\s*%|\s*分)?)/g);
  return parts.map((part, index) => {
    if (/^\d+(?:\.\d+)?(?:\s*%|\s*分)?$/.test(part)) {
      return (
        <strong key={`num-${index}`} className="analysis-detail-num">
          {part}
        </strong>
      );
    }
    return part;
  });
}

function Dashboard({ userRole }) {
  const navigate = useNavigate();
  const user = getStoredUser();
  const [loading, setLoading] = useState(true);
  const [detailVisible, setDetailVisible] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [currentAnnouncement, setCurrentAnnouncement] = useState(null);
  const [dashboardData, setDashboardData] = useState(EMPTY_DASHBOARD_DATA);
  const [jobRefreshing, setJobRefreshing] = useState(false);
  const [analysisDetailVisible, setAnalysisDetailVisible] = useState(false);
  const [analysisReasonsExpanded, setAnalysisReasonsExpanded] = useState(false);
  const [selectedAcademicYear, setSelectedAcademicYear] = useState(null);
  const [snapshotLoading, setSnapshotLoading] = useState(false);

  useEffect(() => {
    const fetchDashboard = async () => {
      if (!user.studentNo) {
        setLoading(false);
        return;
      }

      setLoading(true);
      try {
        const infoRes = await getStudentPortalInfo().catch(() => ({ data: null }));
        const studentInfo = infoRes?.data || null;

        const [
          scoresRes,
          trendRes,
          announcementsRes,
          analysisRes,
          homeSummaryRes,
          processRes,
          compositeRes,
          jobRecommendationRes,
          academicYearsRes
        ] = await Promise.all([
          getStudentPortalScores({ studentNo: user.studentNo }).catch(() => ({ data: [] })),
          studentInfo?.id
            ? getStudentPortalTrend(studentInfo.id).catch(() => ({ data: null }))
            : Promise.resolve({ data: null }),
          getMyAnnouncements().catch(() => ({ data: { records: [] } })),
          getStudentProfileAnalysis({ studentNo: user.studentNo }).catch(() => ({ data: null })),
          getStudentHomeSummary({ studentNo: user.studentNo }).catch(() => ({ data: null })),
          getCurrentExtraScoreApplicationProcess().catch(() => ({ data: null })),
          getStudentCompositeScoreSnapshot().catch(() => ({ data: null })),
          getStudentWeeklyJobRecommendations().catch(() => ({ data: [] })),
          getStudentAcademicYears({ silentError: true }).catch(() => ({ data: [] }))
        ]);

        const compositeSnapshot = compositeRes?.data || null;
        const academicYears = Array.isArray(academicYearsRes?.data) ? academicYearsRes.data : [];
        const defaultAcademicYear = compositeSnapshot?.academicYear
          || academicYears.find((item) => item?.current)?.name
          || academicYears[0]?.name
          || null;
        setSelectedAcademicYear(defaultAcademicYear);

        const trendPayload = trendRes?.data;
        let trendPoints = Array.isArray(trendPayload?.points)
          ? trendPayload.points
          : (Array.isArray(trendPayload) ? trendPayload : []);

        // 后端未回退快照时，用当前综测快照至少画出一个点，避免有成绩卡却空白趋势
        if (!trendPoints.length && compositeSnapshot?.academicYear) {
          const moral = Number(compositeSnapshot.moralBaseScore || 0) + Number(compositeSnapshot.moralExtraScore || 0);
          const intellectual = Number(compositeSnapshot.intellectualBaseScore || 0)
            + Number(compositeSnapshot.intellectualExtraScore || 0);
          const physical = Number(compositeSnapshot.physicalBaseScore || 0)
            + Number(compositeSnapshot.physicalExtraScore || 0);
          const parts = [moral, intellectual, physical].filter((v) => Number.isFinite(v));
          const hasAny = [compositeSnapshot.moralBaseScore, compositeSnapshot.intellectualBaseScore, compositeSnapshot.physicalBaseScore, compositeSnapshot.totalScore]
            .some((v) => v != null && Number.isFinite(Number(v)));
          if (hasAny) {
            trendPoints = [{
              semester: compositeSnapshot.academicYear,
              overall: parts.length
                ? parts.reduce((sum, v) => sum + v, 0) / parts.length
                : (compositeSnapshot.totalScore != null ? Number(compositeSnapshot.totalScore) : null),
              moral,
              intellectual,
              physical
            }];
          }
        }

        setDashboardData({
          studentInfo,
          scores: Array.isArray(scoresRes?.data) ? scoresRes.data : [],
          trendPoints,
          announcements: toRecordList(announcementsRes),
          currentExtraProcess: processRes?.data?.currentProcess || null,
          approvedExtraScoreByType: processRes?.data?.approvedExtraScoreByType || {
            MORAL: 0,
            INTELLECTUAL: 0,
            PHYSICAL: 0
          },
          compositeSnapshot,
          analysis: analysisRes?.data || null,
          homeSummary: homeSummaryRes?.data || null,
          jobRecommendations: Array.isArray(jobRecommendationRes?.data) ? jobRecommendationRes.data : [],
          academicYears
        });
      } finally {
        setLoading(false);
      }
    };

    fetchDashboard();
  }, [user.studentNo]);

  const openAnnouncementDetail = async (announcement) => {
    setCurrentAnnouncement(announcement);
    setDetailVisible(true);
    setDetailLoading(true);
    try {
      const res = await getAnnouncementDetail(announcement.id);
      setCurrentAnnouncement(res?.data || announcement);
      setDashboardData((prev) => ({
        ...prev,
        announcements: prev.announcements.map((item) => (
          item.id === announcement.id ? { ...item, read: true, readAt: res?.data?.readAt || item.readAt } : item
        ))
      }));
    } catch (error) {
      console.error('获取公告详情失败:', error);
    } finally {
      setDetailLoading(false);
    }
  };

  const handleRefreshJobRecommendations = async () => {
    setJobRefreshing(true);
    try {
      const refreshBtn = document.querySelector('.weekly-jobs-refresh');
      if (refreshBtn) refreshBtn.classList.add('loading');

      const response = await refreshStudentWeeklyJobRecommendations();
      const nextJobs = Array.isArray(response?.data) ? response.data : [];
      setDashboardData((prev) => ({
        ...prev,
        jobRecommendations: nextJobs
      }));
      message.success('热门岗位推荐已刷新');
    } catch (error) {
      console.error('刷新热门岗位推荐失败:', error);
      message.error(error?.response?.data?.message || error?.message || '热门岗位推荐刷新失败');
    } finally {
      setJobRefreshing(false);
      const refreshBtn = document.querySelector('.weekly-jobs-refresh');
      if (refreshBtn) refreshBtn.classList.remove('loading');
    }
  };

  const autoRefreshJobRecommendations = useCallback(async () => {
    try {
      const response = await getStudentWeeklyJobRecommendations();
      const nextJobs = Array.isArray(response?.data) ? response.data : [];
      setDashboardData((prev) => ({
        ...prev,
        jobRecommendations: nextJobs.length ? nextJobs : prev.jobRecommendations
      }));
    } catch (error) {
      console.warn('自动刷新热门岗位推荐失败:', error);
    }
  }, []);

  useEffect(() => {
    const intervalId = window.setInterval(autoRefreshJobRecommendations, 30 * 60 * 1000);
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        autoRefreshJobRecommendations();
      }
    };
    window.addEventListener('focus', autoRefreshJobRecommendations);
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener('focus', autoRefreshJobRecommendations);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [autoRefreshJobRecommendations]);

  const handleAcademicYearChange = async (year) => {
    if (!year || year === selectedAcademicYear) {
      return;
    }
    setSelectedAcademicYear(year);
    setSnapshotLoading(true);
    try {
      const response = await getStudentCompositeScoreSnapshot({ academicYear: year });
      setDashboardData((prev) => ({
        ...prev,
        compositeSnapshot: response?.data || null
      }));
    } catch (error) {
      console.error('切换学年综测快照失败:', error);
      message.error('切换学年失败，请稍后重试');
    } finally {
      setSnapshotLoading(false);
    }
  };

  const academicYearOptions = useMemo(() => {
    const names = dashboardData.academicYears
      .map((item) => item?.name)
      .filter(Boolean);
    if (selectedAcademicYear && !names.includes(selectedAcademicYear)) {
      names.push(selectedAcademicYear);
    }
    return Array.from(new Set(names)).sort((left, right) => sortAcademicPeriod(right, left));
  }, [dashboardData.academicYears, selectedAcademicYear]);

  const latestAcademicYearScores = useMemo(() => {
    const semesterMap = new Map();
    for (const item of dashboardData.scores) {
      const semester = item?.semester || '未标记学年';
      const current = semesterMap.get(semester) || { semester };
      current[item.subjectName] = item;
      if (item.totalScore != null) {
        current.totalScore = item.totalScore;
      }
      semesterMap.set(semester, current);
    }
    return Array.from(semesterMap.values()).sort((left, right) => sortAcademicPeriod(right.semester, left.semester))[0] || null;
  }, [dashboardData.scores]);

  const currentAcademicYearName = useMemo(
    () => dashboardData.academicYears.find((item) => item?.current)?.name || null,
    [dashboardData.academicYears]
  );

  // 仅当查看当前学年（或未指定学年）时，才允许回退到最新学期成绩与当前审核通过的附加分。
  // 查看历史学年时应严格反映所选学年的数据，避免切换学年后仍显示最新学年成绩。
  const allowLatestFallback = !selectedAcademicYear || selectedAcademicYear === currentAcademicYearName;

  const selectedYearScores = useMemo(() => {
    const relevantScores = selectedAcademicYear
      ? dashboardData.scores.filter((item) => String(item?.semester || '').includes(selectedAcademicYear))
      : dashboardData.scores;
    const semesterMap = new Map();
    for (const item of relevantScores) {
      const semester = item?.semester || '未标记学年';
      const current = semesterMap.get(semester) || { semester };
      current[item.subjectName] = item;
      if (item.totalScore != null) {
        current.totalScore = item.totalScore;
      }
      semesterMap.set(semester, current);
    }
    return Array.from(semesterMap.values()).sort((left, right) => sortAcademicPeriod(right.semester, left.semester))[0] || null;
  }, [dashboardData.scores, selectedAcademicYear]);

  const unreadAnnouncements = useMemo(
    () => dashboardData.announcements.filter((item) => item.read === false),
    [dashboardData.announcements]
  );

  const currentExtraProcess = dashboardData.currentExtraProcess;

  const approvedExtraScoreByType = useMemo(
    () => dashboardData.approvedExtraScoreByType || DEFAULT_APPROVED_EXTRA_SCORE_BY_TYPE,
    [dashboardData.approvedExtraScoreByType]
  );

  const scoreSnapshot = useMemo(
    () => SCORE_SNAPSHOT_ITEMS.map(({ type, label, baseFields, extraFields }) => {
      const snapshotBaseScore = resolveSnapshotNumber(dashboardData.compositeSnapshot, baseFields);
      const snapshotExtraScore = resolveSnapshotNumber(dashboardData.compositeSnapshot, extraFields);
      const fallbackYearScores = allowLatestFallback ? latestAcademicYearScores : selectedYearScores;
      const baseScore = snapshotBaseScore !== null
        ? snapshotBaseScore
        : Number(fallbackYearScores?.[label]?.score || 0);
      const extraScore = snapshotExtraScore !== null
        ? snapshotExtraScore
        : (allowLatestFallback ? Number(approvedExtraScoreByType[type] || 0) : 0);
      return {
        label,
        baseScore,
        extraScore,
        score: baseScore + extraScore
      };
    }),
    [allowLatestFallback, approvedExtraScoreByType, dashboardData.compositeSnapshot, latestAcademicYearScores, selectedYearScores]
  );

  const compositeScore = useMemo(() => {
    const snapshotTotalScore = Number(dashboardData.compositeSnapshot?.totalScore);
    if (Number.isFinite(snapshotTotalScore)) {
      return snapshotTotalScore;
    }
    if (allowLatestFallback) {
      const approvedExtraTotal = Object.values(approvedExtraScoreByType).reduce((sum, item) => sum + Number(item || 0), 0);
      if (dashboardData.homeSummary?.gradeRank && latestAcademicYearScores?.totalScore != null) {
        return Number(latestAcademicYearScores.totalScore) + approvedExtraTotal;
      }
    }
    const values = scoreSnapshot.map((item) => item.score).filter((item) => Number.isFinite(item) && item > 0);
    return values.length ? values.reduce((sum, item) => sum + item, 0) / values.length : 0;
  }, [allowLatestFallback, approvedExtraScoreByType, dashboardData.compositeSnapshot?.totalScore, dashboardData.homeSummary?.gradeRank, latestAcademicYearScores?.totalScore, scoreSnapshot]);

  const displayRank = useMemo(() => {
    const snapshotRank = dashboardData.compositeSnapshot?.rank;
    if (snapshotRank) {
      return snapshotRank;
    }
    return allowLatestFallback ? (dashboardData.homeSummary?.gradeRank || null) : null;
  }, [allowLatestFallback, dashboardData.compositeSnapshot?.rank, dashboardData.homeSummary?.gradeRank]);

  const radarDimensions = useMemo(() => dashboardData.analysis?.radarDimensions || {}, [dashboardData.analysis?.radarDimensions]);
  const hasRadarData = useMemo(() => RADAR_KEYS.some((key) => radarDimensions[key] != null), [radarDimensions]);

  const analysisRadarSorted = useMemo(() => (
    RADAR_KEYS
      .map((key) => ({ key, value: clampRadarValue(radarDimensions[key]) }))
      .sort((a, b) => b.value - a.value)
  ), [radarDimensions]);

  const analysisRadarAverage = useMemo(() => {
    if (!hasRadarData || !analysisRadarSorted.length) return null;
    const total = analysisRadarSorted.reduce((sum, item) => sum + item.value, 0);
    return total / analysisRadarSorted.length;
  }, [analysisRadarSorted, hasRadarData]);

  const analysisTopDim = analysisRadarSorted[0] || null;
  const analysisWeakDim = analysisRadarSorted.length
    ? analysisRadarSorted[analysisRadarSorted.length - 1]
    : null;

  const analysisReasons = dashboardData.analysis?.reasons || [];
  const analysisSuggestions = dashboardData.analysis?.suggestions || [];
  const visibleAnalysisReasons = analysisReasonsExpanded
    ? analysisReasons
    : analysisReasons.slice(0, 3);
  const hasMoreAnalysisReasons = analysisReasons.length > 3;
  const analysisConfidence = Number(dashboardData.analysis?.confidence) || 0;
  const analysisConfidenceTone = getConfidenceTone(analysisConfidence);
  const analysisConfidenceLabel = getConfidenceLabel(analysisConfidence);

  const radarOption = useMemo(() => ({
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        return RADAR_KEYS.map((label, index) => `${label}: ${Number(params.value[index] || 0).toFixed(2)}`).join('<br/>');
      }
    },
    radar: {
      radius: '72%',
      center: ['50%', '50%'],
      splitNumber: 4,
      axisName: { color: '#111827', fontSize: 12, fontWeight: 600, fontFamily: 'Outfit, sans-serif' },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      splitLine: { lineStyle: { color: '#e5e7eb' } },
      splitArea: {
        areaStyle: { color: ['#eff6ff', '#ecfdf5'] }
      },
      indicator: RADAR_KEYS.map((key) => ({ name: key, max: 100 }))
    },
    series: [{
      type: 'radar',
      data: [{
        value: RADAR_KEYS.map((key) => clampRadarValue(radarDimensions[key])),
        name: '个人得分',
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { color: '#3b82f6', width: 2 },
        itemStyle: { color: '#3b82f6' },
        areaStyle: { color: 'rgba(59, 130, 246, 0.22)' }
      }]
    }]
  }), [radarDimensions]);

  const quoteText = useMemo(() => {
    const summary = dashboardData.analysis?.summary;
    if (summary && summary.length <= 40) {
      return summary;
    }
    return '不积跬步，无以至千里；\n不积小流，无以成江海。';
  }, [dashboardData.analysis?.summary]);

  const displayName = dashboardData.studentInfo?.name || user.nickname || user.username || '同学';
  const studentNo = dashboardData.studentInfo?.studentNo || user.studentNo || '-';
  const className = dashboardData.studentInfo?.className || user.className || '未分配班级';
  const collegeName = dashboardData.studentInfo?.collegeName || dashboardData.studentInfo?.college || '学院信息待完善';
  const majorName = dashboardData.studentInfo?.majorName || dashboardData.studentInfo?.major || '专业信息待完善';
  const gradeName = dashboardData.homeSummary?.gradeKey || (studentNo && studentNo !== '-' ? `${String(studentNo).slice(0, 4)}级` : '年级信息待完善');
  const usingDefaultJobs = !dashboardData.jobRecommendations.some((job) => String(job?.jobTitle || '').trim());
  const jobRecommendations = usingDefaultJobs
    ? DEFAULT_JOB_RECOMMENDATIONS
    : dashboardData.jobRecommendations;

  return (
    <>
      <StudentLayout
        activePath="/student/home"
        userRole={userRole || user.role}
        notificationCount={unreadAnnouncements.length}
      >
        <div className="student-home-bento" aria-label="学生首页">
          <div className="home-bento-decor" aria-hidden="true">
            <span className="home-bento-blob home-bento-blob-a" />
            <span className="home-bento-blob home-bento-blob-b" />
            <span className="home-bento-blob home-bento-blob-c" />
          </div>

          {/* Row 1: 个人信息 | 成绩快照 | 公告 */}
          <section className="home-card profile-card">
            <div className="profile-card-top">
              <div className="profile-top-shapes" aria-hidden="true">
                <span className="shape shape-circle" />
                <span className="shape shape-square" />
                <span className="shape shape-ring" />
              </div>
              <div className="profile-identity">
                <div className="profile-avatar">
                  {user?.avatarUrl ? (
                    <img src={withAuthToken(user.avatarUrl)} alt="" />
                  ) : (
                    getInitials(displayName)
                  )}
                </div>
                <div className="profile-identity-text">
                  <div className="profile-name-row">
                    <h2>{displayName}</h2>
                    <span className="profile-role-pill">
                      {(userRole || user.role) === 'CLASS_COMMITTEE' ? '班委' : '学生'}
                    </span>
                  </div>
                  <p className="profile-identity-sub">
                    <span>{className}</span>
                    <span className="profile-identity-dot" aria-hidden="true" />
                    <span>{gradeName}</span>
                  </p>
                </div>
              </div>
            </div>
            <div className="profile-card-body">
              <HomeFusedArt variant="campus" />
              <dl className="profile-detail-list">
                <div className="profile-detail-primary">
                  <dt>学号</dt>
                  <dd>{studentNo}</dd>
                </div>
                <div className="profile-detail-grid">
                  <div>
                    <dt>院系</dt>
                    <dd>{collegeName}</dd>
                  </div>
                  <div>
                    <dt>专业</dt>
                    <dd>{majorName}</dd>
                  </div>
                  <div>
                    <dt>班级</dt>
                    <dd>{className}</dd>
                  </div>
                  <div>
                    <dt>年级</dt>
                    <dd>{gradeName}</dd>
                  </div>
                </div>
              </dl>
              <div className="quote-box">
                <span className="quote-mark" aria-hidden="true">“</span>
                <p>{quoteText}</p>
              </div>
            </div>
          </section>

          <section className="home-card scores-card">
            <div className="card-surface-shapes" aria-hidden="true">
              <span className="shape shape-circle" />
              <span className="shape shape-square" />
              <span className="shape shape-dot-grid" />
            </div>
            <HomeFusedArt variant="scores" />
            <div className="card-head compact-head">
              <h3>成绩快照</h3>
              {academicYearOptions.length > 0 ? (
                <div className="snapshot-year-select">
                  <select
                    value={selectedAcademicYear || ''}
                    onChange={(event) => handleAcademicYearChange(event.target.value)}
                    disabled={snapshotLoading}
                    aria-label="选择学年"
                  >
                    {academicYearOptions.map((year) => (
                      <option key={year} value={year}>{year}</option>
                    ))}
                  </select>
                </div>
              ) : (
                <span className="head-subtitle">{dashboardData.compositeSnapshot?.academicYear || '最新学年'}</span>
              )}
            </div>

            <div className="score-ring-grid compact-score-grid">
              {scoreSnapshot.map((item) => (
                <div key={item.label} className="ring-card compact-ring-card">
                  <div
                    className="score-ring compact-score-ring"
                    style={{ background: buildScoreRingBackground(item) }}
                  >
                    <div className="score-ring-inner">
                      <strong>{formatNumber(item.score)}</strong>
                    </div>
                  </div>
                  <div className="ring-label">{item.label}</div>
                  <div className="score-breakdown">
                    <span><i style={{ backgroundColor: scoreMeta[item.label].color }} />基础 {formatNumber(item.baseScore)}</span>
                    <span><i style={{ backgroundColor: scoreMeta[item.label].extraColor }} />附加 {formatNumber(item.extraScore)}</span>
                  </div>
                </div>
              ))}
            </div>

            <div className="snapshot-footer compact-snapshot-footer">
              <span>综合：{formatNumber(compositeScore)}</span>
              <span>排名：{displayRank ? `${displayRank} / ${dashboardData.homeSummary?.gradeStudentCount || '-'}` : '-'}</span>
            </div>
          </section>

          <section className="home-card overview-announcements">
            <div className="card-surface-shapes" aria-hidden="true">
              <span className="shape shape-circle" />
              <span className="shape shape-square" />
              <span className="shape shape-ring" />
              <span className="shape shape-dot-grid" />
            </div>
            <HomeFusedArt variant="notice" />
            <div className="card-head compact-head">
              <h3>我的公告</h3>
              <button type="button" className="head-link" onClick={() => navigate('/student/announcements')}>全部</button>
            </div>
            {dashboardData.announcements.length ? (
              <div className="announcement-list compact-announcement-list">
                {dashboardData.announcements.slice(0, 4).map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    className="announcement-item compact-announcement-item"
                    onClick={() => openAnnouncementDetail(item)}
                  >
                    <span className={`list-dot ${item.read === false ? 'red' : 'gray'}`} />
                    <div className="announcement-body">
                      <strong>{item.title || '未命名公告'}</strong>
                      <p>{item.summary || getAnnouncementSnippet(item.content)}</p>
                    </div>
                    <time>{formatDate(item.publishTime)}</time>
                  </button>
                ))}
              </div>
            ) : !loading ? (
              <div className="home-empty-scene">
                <div className="home-empty-scene-copy">
                  <strong>暂无公告</strong>
                  <span>有新通知时会显示在这里</span>
                </div>
                <div className="home-empty-hint-row">
                  <span>成绩变动</span>
                  <span>审核提醒</span>
                  <span>学院通知</span>
                </div>
              </div>
            ) : null}
          </section>

          {/* Row 2: AI 画像 | 雷达 */}
          <section className="home-card analysis-card analysis-promo">
            <div className="card-surface-shapes" aria-hidden="true">
              <span className="shape shape-circle" />
              <span className="shape shape-square" />
              <span className="shape shape-dot-grid" />
            </div>
            <div className="analysis-promo-copy">
              <div className="card-head">
                <h3>AI 学业/发展画像</h3>
              </div>
              {dashboardData.analysis ? (
                <>
                  <div className="analysis-chip-row">
                    <span className="analysis-chip">{dashboardData.analysis.primaryType || '学业发展型'}</span>
                    <span className="analysis-confidence">置信度 {dashboardData.analysis.confidence || 0}%</span>
                  </div>
                  <p className="analysis-summary">
                    {dashboardData.analysis.summary || '暂无分析摘要'}
                  </p>
                  {(dashboardData.analysis.suggestions || []).slice(0, 2).map((item, index) => (
                    <p key={`${item}-${index}`} className="analysis-tip">建议：{item}</p>
                  ))}
                  <div className="card-footnote">生成时间：{formatDateTime(dashboardData.analysis.generatedAt)}</div>
                  <button
                    type="button"
                    className="analysis-detail-btn flat-btn flat-btn-primary"
                    onClick={() => setAnalysisDetailVisible(true)}
                  >
                    查看画像
                  </button>
                </>
              ) : (
                <div className="home-empty-inline">
                  <strong>暂无最新 AI 画像分析</strong>
                  <span>结合综测与发展数据，生成个人画像与成长建议</span>
                </div>
              )}
            </div>
          </section>

          <section className="home-card radar-chart-card">
            <div className="card-head">
              <h3>八维素质雷达图</h3>
              <span className="head-subtitle">
                {dashboardData.analysis?.generatedAt ? formatDateTime(dashboardData.analysis.generatedAt) : '最新'}
              </span>
            </div>
            {hasRadarData ? (
              <div className="radar-chart-body">
                <EChartPanel option={radarOption} height={240} />
              </div>
            ) : (
              <div className="empty-state">暂无雷达图数据</div>
            )}
          </section>

          {/* Row 3: 趋势 | 岗位 */}
          <section className="home-card score-trend-card">
            <ScoreTrendPanel
              points={dashboardData.trendPoints}
              scores={dashboardData.scores}
              height={220}
            />
          </section>

          <section className="home-card weekly-jobs-card">
            <div className="card-surface-shapes" aria-hidden="true">
              <span className="shape shape-circle" />
              <span className="shape shape-square" />
            </div>
            <div className="weekly-jobs-banner">
              <div>
                <span className="weekly-jobs-kicker">CAREER SIGNAL</span>
                <h3 className="weekly-jobs-title">每周热门软工岗位</h3>
              </div>
            </div>
            <div className="weekly-jobs-actions">
              <span className="weekly-jobs-date">
                {usingDefaultJobs ? '示例数据 · 本周更新' : formatJobWeekRange(jobRecommendations[0])}
              </span>
              <button
                type="button"
                className="weekly-jobs-refresh flat-btn"
                onClick={handleRefreshJobRecommendations}
                disabled={jobRefreshing}
              >
                {jobRefreshing ? '刷新中...' : '刷新'}
              </button>
            </div>

            {jobRecommendations.length ? (
              <div className="weekly-job-grid">
                {jobRecommendations.slice(0, 4).map((job, index) => {
                  const skillList = String(job.skills || '')
                    .split(/[,，、]/)
                    .map((item) => item.trim())
                    .filter(Boolean)
                    .slice(0, 4);
                  return (
                    <article
                      key={job.id || `${job.jobTitle}-${index}`}
                      className="weekly-job-item"
                    >
                      <div className="weekly-job-rank">{String(index + 1).padStart(2, '0')}</div>
                      <div className="weekly-job-main">
                        <div className="weekly-job-title-row">
                          <h4>{job.jobTitle || '软件工程师'}</h4>
                          <span>{job.salary || '薪资面议'}</span>
                        </div>
                        <div className="weekly-job-skills">
                          {skillList.length ? skillList.map((skill) => (
                            <span key={`${job.id || job.jobTitle}-${skill}`}>{skill}</span>
                          )) : <span>技能要求待更新</span>}
                        </div>
                      </div>
                      <a
                        className="weekly-job-link flat-btn"
                        href={resolveJobSearchLink(job)}
                        target="_blank"
                        rel="noreferrer"
                      >
                        查看
                      </a>
                    </article>
                  );
                })}
              </div>
            ) : (
              <div className="weekly-jobs-empty">
                <strong>岗位推荐生成中</strong>
                <span>后端每周自动刷新，页面停留时也会自动检查最新推荐。</span>
              </div>
            )}
          </section>

          {/* Row 4: 附加分审核进度 */}
          <section className="home-card extra-card">
            <div className="card-surface-shapes" aria-hidden="true">
              <span className="shape shape-circle" />
              <span className="shape shape-square" />
            </div>
            <div className="card-head">
              <div className="head-title-row">
                <h3>附加分审核进度</h3>
                <span className="head-subtitle">
                  {currentExtraProcess ? currentExtraProcess.submissionPeriodLabel || '当前周期' : '当前周期'}
                </span>
              </div>
              <button type="button" className="head-link" onClick={() => navigate('/student/extra/process')}>
                查看流程
              </button>
            </div>

            {currentExtraProcess ? (
              <div className="extra-progress-panel">
                <div className="extra-progress-meta">
                  <span>
                    {formatDateTime(currentExtraProcess.submissionStartTime)} 至{' '}
                    {formatDateTime(currentExtraProcess.submissionEndTime)}
                  </span>
                  <strong>{currentExtraProcess.total} 条</strong>
                </div>
                <div className="extra-progress-types">
                  {Object.entries(currentExtraProcess.typeCounts || {}).map(([type, count]) => (
                    <span key={type}>{extraTypeMeta[type]?.label || type} {count} 条</span>
                  ))}
                </div>
                <div className="extra-progress-steps">
                  <Steps
                    size="small"
                    items={buildExtraScoreTimeline(currentExtraProcess).map((step) => ({
                      title: step.title,
                      status: step.status,
                      description: step.description
                    }))}
                  />
                </div>
              </div>
            ) : (
              <div className="home-empty-inline">
                <strong>暂无审核中的申请</strong>
                <span>提交附加分后，进度会显示在这里</span>
                <button
                  type="button"
                  className="flat-btn flat-btn-primary home-empty-cta"
                  onClick={() => navigate('/student/extra')}
                >
                  去申请附加分
                </button>
              </div>
            )}
          </section>
        </div>
      </StudentLayout>

      <Modal
        open={analysisDetailVisible}
        title="AI 学业/发展画像详情"
        onCancel={() => {
          setAnalysisDetailVisible(false);
          setAnalysisReasonsExpanded(false);
        }}
        afterOpenChange={(open) => {
          if (!open) setAnalysisReasonsExpanded(false);
        }}
        footer={null}
        width={720}
        centered
        className="analysis-detail-modal"
        destroyOnHidden
      >
        {dashboardData.analysis ? (
          <div className="analysis-detail-body">
            <header className="analysis-detail-hero">
              <div className="analysis-detail-hero-main">
                <span className="analysis-detail-kicker">主推方向</span>
                <div className="analysis-detail-chip-row">
                  <span className="analysis-chip analysis-detail-primary-chip">
                    {dashboardData.analysis.primaryType || '学业发展型'}
                  </span>
                  {(dashboardData.analysis.secondaryTypes || []).map((item, index) => (
                    <span key={`secondary-${index}`} className="analysis-detail-secondary-tag">
                      {item}
                    </span>
                  ))}
                </div>
              </div>
              <div
                className={`analysis-detail-confidence tone-${analysisConfidenceTone}`}
                aria-label={`置信度 ${analysisConfidence}%，${analysisConfidenceLabel}`}
              >
                <div className="analysis-detail-confidence-head">
                  <span>置信度</span>
                  <strong>{analysisConfidence}%</strong>
                </div>
                <div className="analysis-detail-confidence-track" aria-hidden="true">
                  <span
                    className="analysis-detail-confidence-fill"
                    style={{ width: `${Math.max(0, Math.min(100, analysisConfidence))}%` }}
                  />
                </div>
                <em className="analysis-detail-confidence-hint">{analysisConfidenceLabel}</em>
              </div>
            </header>

            {hasRadarData ? (
              <>
                <div className="analysis-detail-metrics" role="group" aria-label="八维摘要">
                  <div className="analysis-detail-metric">
                    <span>综合均分</span>
                    <strong>{analysisRadarAverage != null ? analysisRadarAverage.toFixed(0) : '—'}</strong>
                    <em>八维均值</em>
                  </div>
                  <div className="analysis-detail-metric">
                    <span>最强维度</span>
                    <strong>{analysisTopDim ? analysisTopDim.value.toFixed(0) : '—'}</strong>
                    <em>{analysisTopDim?.key || '—'}</em>
                  </div>
                  <div className="analysis-detail-metric is-weak">
                    <span>待提升</span>
                    <strong>{analysisWeakDim ? analysisWeakDim.value.toFixed(0) : '—'}</strong>
                    <em>{analysisWeakDim?.key || '—'}</em>
                  </div>
                </div>

                <div className="analysis-detail-bars" aria-label="八维得分条">
                  {RADAR_KEYS.map((key) => {
                    const value = clampRadarValue(radarDimensions[key]);
                    const isTop = analysisTopDim?.key === key;
                    const isWeak = analysisWeakDim?.key === key;
                    return (
                      <div
                        key={`bar-${key}`}
                        className={`analysis-detail-bar${isTop ? ' is-top' : ''}${isWeak ? ' is-weak' : ''}`}
                      >
                        <span className="analysis-detail-bar-label">{key}</span>
                        <span className="analysis-detail-bar-track" aria-hidden="true">
                          <span className="analysis-detail-bar-fill" style={{ width: `${value}%` }} />
                        </span>
                        <strong className="analysis-detail-bar-value">{value.toFixed(0)}</strong>
                      </div>
                    );
                  })}
                </div>
              </>
            ) : null}

            <div className="analysis-detail-section">
              <span className="analysis-detail-label">总体评价</span>
              <p className="analysis-detail-summary">
                {renderAnalysisText(dashboardData.analysis.summary || '暂无分析摘要')}
              </p>
            </div>

            <div className="analysis-detail-columns">
              {analysisReasons.length > 0 && (
                <div className="analysis-detail-section">
                  <div className="analysis-detail-label-row">
                    <span className="analysis-detail-label">分析原因</span>
                    <span className="analysis-detail-count">{analysisReasons.length}</span>
                  </div>
                  <ol className="analysis-detail-steps">
                    {visibleAnalysisReasons.map((item, index) => (
                      <li key={`reason-${index}`}>
                        <span className="analysis-detail-step-index">{index + 1}</span>
                        <p>{renderAnalysisText(item)}</p>
                      </li>
                    ))}
                  </ol>
                  {hasMoreAnalysisReasons ? (
                    <button
                      type="button"
                      className="analysis-detail-toggle"
                      onClick={() => setAnalysisReasonsExpanded((prev) => !prev)}
                    >
                      {analysisReasonsExpanded
                        ? '收起分析原因'
                        : `展开全部 ${analysisReasons.length} 条`}
                    </button>
                  ) : null}
                </div>
              )}

              {analysisSuggestions.length > 0 && (
                <div className="analysis-detail-section">
                  <div className="analysis-detail-label-row">
                    <span className="analysis-detail-label">发展建议</span>
                    <span className="analysis-detail-count is-suggestion">{analysisSuggestions.length}</span>
                  </div>
                  <ol className="analysis-detail-steps is-suggestion">
                    {analysisSuggestions.map((item, index) => (
                      <li key={`suggestion-${index}`}>
                        <span className="analysis-detail-step-index">{index + 1}</span>
                        <p>{renderAnalysisText(item)}</p>
                      </li>
                    ))}
                  </ol>
                </div>
              )}
            </div>

            <div className="analysis-detail-footer">
              <span>生成时间：{formatDateTime(dashboardData.analysis.generatedAt)}</span>
              <div className="analysis-detail-footer-actions">
                <button
                  type="button"
                  className="analysis-detail-footer-link"
                  onClick={() => {
                    setAnalysisDetailVisible(false);
                    navigate('/student/extra');
                  }}
                >
                  去申请附加分
                </button>
                <button
                  type="button"
                  className="flat-btn flat-btn-primary analysis-detail-footer-cta"
                  onClick={() => {
                    setAnalysisDetailVisible(false);
                    navigate('/student/profile');
                  }}
                >
                  查看完整画像
                </button>
              </div>
            </div>
          </div>
        ) : (
          <div className="empty-state">暂无画像数据</div>
        )}
      </Modal>

      <AnnouncementDetailDrawer
        open={detailVisible}
        announcement={currentAnnouncement}
        loading={detailLoading}
        onClose={() => {
          setDetailVisible(false);
          setDetailLoading(false);
          setCurrentAnnouncement(null);
        }}
      />
    </>
  );
}

export default Dashboard;
