import { useEffect, useMemo, useRef, useState } from 'react';
import { Avatar, Card, Spin, Table, Tag, message } from 'antd';
import StudentLayout from '../../components/StudentLayout';
import CompositeScoreCategoryCard from '../../components/composite-score/CompositeScoreCategoryCard';
import CompositeScoreDetailDrawer from '../../components/composite-score/CompositeScoreDetailDrawer';
import ScoreSimulatorPanel from '../../components/composite-score/ScoreSimulatorPanel';
import {
  getStudentCompositeBaseScoreDetails,
  getStudentCompositeExtraScoreDetails,
  getStudentPortalClassLeaderboard,
  getStudentCompositeScoreSnapshot,
  getStudentPortalGradeLeaderboard,
  getStudentPortalInfo,
  getStudentAcademicYears,
  getStudentSecondClassScores,
  getStudentVisibleSubjectScores
} from '../../api/student/portal';
import { getStoredUser, withAuthToken } from '../../utils/authStorage';
import { buildCompositeDetailParams } from '../../utils/compositeScoreDetails';
import './Scores.css';

const sortSemester = (left, right) => String(left || '').localeCompare(String(right || ''), 'zh-Hans-CN', { numeric: true });

const formatNumber = (value, digits = 1) => {
  const number = Number(value);
  return Number.isFinite(number) ? number.toFixed(digits) : '-';
};

const resolveLevelTag = (value) => {
  const score = Number(value || 0);
  if (score >= 90) return { text: '优秀', color: 'success' };
  if (score >= 80) return { text: '良好', color: 'processing' };
  if (score >= 60) return { text: '达标', color: 'warning' };
  return { text: '不及格', color: 'error' };
};

function StudentScores() {
  const user = getStoredUser();
  const [loading, setLoading] = useState(true);
  const [studentInfo, setStudentInfo] = useState({
    id: user.id,
    name: user.nickname,
    studentNo: user.studentNo,
    className: user.className,
    teacherId: user.teacherId
  });
  const [subjectScores, setSubjectScores] = useState([]);
  const [secondClassScores, setSecondClassScores] = useState([]);
  const [compositeSnapshot, setCompositeSnapshot] = useState(null);
  const [classLeaderboard, setClassLeaderboard] = useState([]);
  const [gradeLeaderboard, setGradeLeaderboard] = useState([]);
  const [classLeaderboardPage, setClassLeaderboardPage] = useState({ page: 1, pageSize: 10, total: 0 });
  const [gradeLeaderboardPage, setGradeLeaderboardPage] = useState({ page: 1, pageSize: 10, total: 0 });
  const [overviewClassLeaderboard, setOverviewClassLeaderboard] = useState([]);
  const [overviewGradeLeaderboard, setOverviewGradeLeaderboard] = useState([]);
  const [myClassRank, setMyClassRank] = useState(null);
  const [myGradeRank, setMyGradeRank] = useState(null);
  const [leaderboardLoading, setLeaderboardLoading] = useState(false);
  const [gradeLeaderboardLoading, setGradeLeaderboardLoading] = useState(false);
  const [secondClassLoading, setSecondClassLoading] = useState(false);
  const [academicYears, setAcademicYears] = useState([]);
  const [selectedSemester, setSelectedSemester] = useState('');
  const [secondClassScope, setSecondClassScope] = useState('class'); // class | grade
  const [activeTab, setActiveTab] = useState('overview'); // overview | courses | secondClass | leaderboard | gradeLeaderboard
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailType, setDetailType] = useState('base');
  const [detailCategory, setDetailCategory] = useState('INTELLECTUAL');
  const [detailRows, setDetailRows] = useState([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailStudent, setDetailStudent] = useState(null);
  const loadedTabKeysRef = useRef({});

  const loadData = async () => {
    if (!user.studentNo) {
      message.warning('当前账号缺少学号信息');
      setLoading(false);
      return;
    }

    setLoading(true);
    try {
      const [infoRes, compositeRes, academicYearRes] = await Promise.all([
        getStudentPortalInfo().catch(() => ({ data: null })),
        getStudentCompositeScoreSnapshot().catch(() => ({ data: null })),
        getStudentAcademicYears({ silentError: true }).catch(() => ({ data: [] }))
      ]);

      if (infoRes?.data) {
        setStudentInfo((prev) => ({ ...prev, ...infoRes.data }));
      }
      setCompositeSnapshot(compositeRes?.data || null);
      const yearList = Array.isArray(academicYearRes?.data) ? academicYearRes.data : [];
      setAcademicYears(yearList.filter((item) => item?.status !== 'inactive'));
    } catch (error) {
      message.error(error?.response?.data?.message || '获取成绩数据失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [user.studentNo]);

  const leaderboardParams = useMemo(() => ({
    semester: selectedSemester || undefined,
    includeExtraScore: false
  }), [selectedSemester]);

  const loadCourseScores = async (force = false) => {
    if ((!force && subjectScores.length) || !user.studentNo) {
      return;
    }
    try {
      const subjectScoreRes = await getStudentVisibleSubjectScores();
      setSubjectScores(Array.isArray(subjectScoreRes) ? subjectScoreRes : []);
    } catch (error) {
      message.error(error?.response?.data?.message || '获取课程成绩失败');
    }
  };

  const loadSecondClassScores = async () => {
    if (!user.studentNo) {
      return;
    }
    setSecondClassLoading(true);
    try {
      const response = await getStudentSecondClassScores({
        academicYear: selectedSemester || undefined,
        scope: secondClassScope
      });
      setSecondClassScores(Array.isArray(response?.data) ? response.data : []);
    } catch (error) {
      message.error(error?.response?.data?.message || '获取二课成绩失败');
    } finally {
      setSecondClassLoading(false);
    }
  };

  const normalizePageData = (data) => ({
    records: Array.isArray(data?.records) ? data.records : Array.isArray(data) ? data : [],
    total: Number(data?.total ?? (Array.isArray(data) ? data.length : 0)),
    page: Number(data?.page ?? 0),
    size: Number(data?.size ?? 10),
    current: data?.current || null
  });

  const loadClassLeaderboard = async (page = classLeaderboardPage.page, pageSize = classLeaderboardPage.pageSize, overview = false) => {
    if (!studentInfo?.classId) {
      if (overview) {
        setOverviewClassLeaderboard([]);
      } else {
        setClassLeaderboard([]);
        setMyClassRank(null);
        setClassLeaderboardPage((prev) => ({ ...prev, total: 0 }));
      }
      return;
    }
    if (!overview) {
      setLeaderboardLoading(true);
    }
    try {
      const res = await getStudentPortalClassLeaderboard({
        classId: studentInfo.classId,
        ...leaderboardParams,
        page: Math.max(page - 1, 0),
        size: pageSize
      });
      const pageData = normalizePageData(res?.data);
      setMyClassRank(pageData.current);
      if (overview) {
        setOverviewClassLeaderboard(pageData.records);
        setClassLeaderboardPage((prev) => ({ ...prev, total: pageData.total }));
      } else {
        setClassLeaderboard(pageData.records);
        setClassLeaderboardPage({ page: pageData.page + 1, pageSize: pageData.size, total: pageData.total });
      }
    } catch (error) {
      message.error(error?.response?.data?.message || '获取班级榜单失败');
    } finally {
      if (!overview) {
        setLeaderboardLoading(false);
      }
    }
  };

  const loadGradeLeaderboard = async (page = gradeLeaderboardPage.page, pageSize = gradeLeaderboardPage.pageSize, overview = false) => {
    if (!user.studentNo) {
      if (overview) {
        setOverviewGradeLeaderboard([]);
      } else {
        setGradeLeaderboard([]);
        setMyGradeRank(null);
        setGradeLeaderboardPage((prev) => ({ ...prev, total: 0 }));
      }
      return;
    }
    if (!overview) {
      setGradeLeaderboardLoading(true);
    }
    try {
      const res = await getStudentPortalGradeLeaderboard({
        studentNo: user.studentNo,
        ...leaderboardParams,
        page: Math.max(page - 1, 0),
        size: pageSize
      });
      const pageData = normalizePageData(res?.data);
      setMyGradeRank(pageData.current);
      if (overview) {
        setOverviewGradeLeaderboard(pageData.records);
        setGradeLeaderboardPage((prev) => ({ ...prev, total: pageData.total }));
      } else {
        setGradeLeaderboard(pageData.records);
        setGradeLeaderboardPage({ page: pageData.page + 1, pageSize: pageData.size, total: pageData.total });
      }
    } catch (error) {
      message.error(error?.response?.data?.message || '获取年级榜单失败');
    } finally {
      if (!overview) {
        setGradeLeaderboardLoading(false);
      }
    }
  };

  const handleRefresh = () => {
    loadData();
    if (activeTab === 'overview') {
      loadClassLeaderboard(1, 5, true);
      loadGradeLeaderboard(1, 5, true);
      loadCourseScores(true);
      loadSecondClassScores();
    }
    if (activeTab === 'courses') {
      loadCourseScores(true);
    }
    if (activeTab === 'secondClass') {
      loadSecondClassScores();
    }
    if (activeTab === 'leaderboard') {
      loadClassLeaderboard(classLeaderboardPage.page, classLeaderboardPage.pageSize);
    }
    if (activeTab === 'gradeLeaderboard') {
      loadGradeLeaderboard(gradeLeaderboardPage.page, gradeLeaderboardPage.pageSize);
    }
  };

  const subjectScoresBySemester = useMemo(() => {
    const semesterMap = new Map();
    for (const item of subjectScores) {
      const semester = item.semester || '未标记学年';
      const current = semesterMap.get(semester) || { semester, items: [], total: 0, count: 0 };
      current.items.push(item);
      current.total += Number(item.score || 0);
      current.count += 1;
      semesterMap.set(semester, current);
    }
    return Array.from(semesterMap.values())
      .map((item) => ({
        ...item,
        average: item.count ? item.total / item.count : 0
      }))
      .sort((a, b) => sortSemester(b.semester, a.semester));
  }, [subjectScores]);

  const semesterOptions = useMemo(() => {
    const activeYearNames = academicYears.map((item) => item.name).filter(Boolean);
    if (activeYearNames.length) {
      return activeYearNames.sort((a, b) => sortSemester(b, a));
    }
    const scoreSemesters = new Set([
      ...subjectScoresBySemester.map((item) => item.semester)
    ].filter(Boolean));
    return Array.from(scoreSemesters).sort((a, b) => sortSemester(b, a));
  }, [academicYears, subjectScoresBySemester]);

  useEffect(() => {
    if (!selectedSemester && semesterOptions.length) {
      const currentAcademicYear = academicYears.find((item) => item.current && item.status !== 'inactive');
      setSelectedSemester(currentAcademicYear?.name || semesterOptions[0]);
    }
  }, [academicYears, selectedSemester, semesterOptions]);

  const lazyLoadKey = useMemo(() => {
    const semester = selectedSemester || 'all';
    if (activeTab === 'overview') {
      if (!user.studentNo || !studentInfo?.classId) {
        return '';
      }
      return `${activeTab}:${user.studentNo}:${studentInfo.classId}:${semester}:top5`;
    }
    if (activeTab === 'courses') {
      if (!user.studentNo) {
        return '';
      }
      return `${activeTab}:${user.studentNo}`;
    }
    if (activeTab === 'secondClass') {
      if (!user.studentNo || !selectedSemester) {
        return '';
      }
      return `${activeTab}:${user.studentNo}:${semester}:${secondClassScope}`;
    }
    if (activeTab === 'leaderboard') {
      if (!studentInfo?.classId) {
        return '';
      }
      return `${activeTab}:${studentInfo.classId}:${semester}:${classLeaderboardPage.pageSize}`;
    }
    if (activeTab === 'gradeLeaderboard') {
      if (!user.studentNo) {
        return '';
      }
      return `${activeTab}:${user.studentNo}:${semester}:${gradeLeaderboardPage.pageSize}`;
    }
    return '';
  }, [
    activeTab,
    classLeaderboardPage.pageSize,
    gradeLeaderboardPage.pageSize,
    secondClassScope,
    selectedSemester,
    studentInfo?.classId,
    user.studentNo
  ]);

  useEffect(() => {
    const waitingForDefaultSemester = !selectedSemester && semesterOptions.length > 0;
    if (loading || waitingForDefaultSemester || !lazyLoadKey) {
      return;
    }
    if (loadedTabKeysRef.current[activeTab] === lazyLoadKey) {
      return;
    }
    loadedTabKeysRef.current[activeTab] = lazyLoadKey;

    if (activeTab === 'overview') {
      loadClassLeaderboard(1, 5, true);
      loadGradeLeaderboard(1, 5, true);
      loadCourseScores();
      loadSecondClassScores();
    }
    if (activeTab === 'courses') {
      loadCourseScores();
    }
    if (activeTab === 'secondClass') {
      loadSecondClassScores();
    }
    if (activeTab === 'leaderboard') {
      loadClassLeaderboard(1, classLeaderboardPage.pageSize);
    }
    if (activeTab === 'gradeLeaderboard') {
      loadGradeLeaderboard(1, gradeLeaderboardPage.pageSize);
    }
  }, [activeTab, lazyLoadKey, loading, selectedSemester, semesterOptions.length]);

  const filteredSubjectScores = useMemo(() => subjectScores.filter((item) => {
    if (selectedSemester) {
      return item.semester === selectedSemester;
    }
    return true;
  }), [subjectScores, selectedSemester]);

  const overviewCourseScores = useMemo(
    () => filteredSubjectScores.slice(0, 5),
    [filteredSubjectScores]
  );

  const currentSecondClassScore = useMemo(
    () => secondClassScores.find((item) => String(item.studentNo) === String(user.studentNo)) || null,
    [secondClassScores, user.studentNo]
  );

  const compositeSummary = useMemo(() => {
    const resolveSnapshotNumber = (...fields) => {
      for (const field of fields) {
        const value = Number(compositeSnapshot?.[field]);
        if (Number.isFinite(value)) {
          return value;
        }
      }
      return 0;
    };

    const weightedBaseScore = resolveSnapshotNumber('weightedMoralBaseScore', 'moralBaseScore')
      + resolveSnapshotNumber('weightedIntellectualBaseScore', 'intellectualBaseScore')
      + resolveSnapshotNumber('weightedPhysicalBaseScore', 'physicalBaseScore');
    const weightedExtraScore = resolveSnapshotNumber('weightedMoralExtraScore', 'moralExtraScore')
      + resolveSnapshotNumber('weightedIntellectualExtraScore', 'intellectualExtraScore')
      + resolveSnapshotNumber('weightedPhysicalExtraScore', 'physicalExtraScore');
    const items = [
      {
        category: 'MORAL',
        label: '德育',
        baseScore: resolveSnapshotNumber('weightedMoralBaseScore', 'moralBaseScore'),
        extraScore: resolveSnapshotNumber('weightedMoralExtraScore', 'moralExtraScore')
      },
      {
        category: 'INTELLECTUAL',
        label: '智育',
        baseScore: resolveSnapshotNumber('weightedIntellectualBaseScore', 'intellectualBaseScore'),
        extraScore: resolveSnapshotNumber('weightedIntellectualExtraScore', 'intellectualExtraScore')
      },
      {
        category: 'PHYSICAL',
        label: '体育',
        baseScore: resolveSnapshotNumber('weightedPhysicalBaseScore', 'physicalBaseScore'),
        extraScore: resolveSnapshotNumber('weightedPhysicalExtraScore', 'physicalExtraScore')
      }
    ];
    const snapshotTotalScore = Number(compositeSnapshot?.totalScore);
    const totalScore = Number.isFinite(snapshotTotalScore) ? snapshotTotalScore : weightedBaseScore + weightedExtraScore;

    return {
      academicYear: compositeSnapshot?.academicYear || selectedSemester || '-',
      totalScore,
      baseScore: weightedBaseScore,
      extraScore: weightedExtraScore,
      items
    };
  }, [compositeSnapshot, selectedSemester]);

  const physicalBaseWeight = useMemo(() => {
    const configuredWeightValue = compositeSnapshot?.weight?.physicalBaseWeight;
    const configuredWeight = configuredWeightValue === null || configuredWeightValue === undefined
      ? Number.NaN
      : Number(configuredWeightValue);
    if (Number.isFinite(configuredWeight)) return configuredWeight;

    const rawScore = Number(compositeSnapshot?.physicalBaseScore);
    const weightedScore = Number(compositeSnapshot?.weightedPhysicalBaseScore);
    return Number.isFinite(rawScore) && rawScore !== 0 && Number.isFinite(weightedScore)
      ? weightedScore / rawScore * 100
      : null;
  }, [compositeSnapshot]);

  const courseTableColumns = [
    {
      title: '学期',
      dataIndex: 'semester',
      key: 'semester',
      width: 160
    },
    {
      title: '课程名称',
      key: 'subjectName',
      render: (_, record) => record.subject?.subjectName || '-'
    },
    {
      title: '课程类别',
      key: 'subjectCategory',
      render: (_, record) => record.subject?.category === 'NON_SUBJECT' ? '无关项' : '科目项'
    },
    {
      title: '成绩',
      dataIndex: 'score',
      key: 'score',
      render: (value) => `${formatNumber(value)} 分`
    },
    {
      title: '等级',
      key: 'level',
      render: (_, record) => {
        const tag = resolveLevelTag(record.score);
        return <Tag color={tag.color}>{tag.text}</Tag>;
      }
    }
  ];

  const secondClassTableColumns = [
    {
      title: '班级',
      dataIndex: 'className',
      key: 'className',
      width: 180,
      render: (value) => value || '-'
    },
    {
      title: '姓名',
      dataIndex: 'studentName',
      key: 'studentName',
      width: 120,
      render: (value) => value || '-'
    },
    {
      title: '学号',
      dataIndex: 'studentNo',
      key: 'studentNo',
      width: 150,
      render: (value) => value || '-'
    },
    {
      title: '学年',
      dataIndex: 'academicYear',
      key: 'academicYear',
      width: 160,
      render: (value) => value || '-'
    },
    {
      title: '真实二课分',
      dataIndex: 'secondClassScore',
      key: 'secondClassScore',
      render: (value) => `${formatNumber(value, 2)} 分`
    },
    {
      title: '换算二课分',
      dataIndex: 'convertedMoralScore',
      key: 'convertedMoralScore',
      render: (value) => `${formatNumber(value, 2)} 分`
    },
    {
      title: '德育总分',
      dataIndex: 'moralTotalScore',
      key: 'moralTotalScore',
      render: (value) => <strong>{formatNumber(value, 2)} 分</strong>
    }
  ];

  const hasExportData = filteredSubjectScores.length || compositeSnapshot;
  const pageScopeLabel = `当前学年：${selectedSemester || '-'}`;
  const primaryScopeLabel = selectedSemester || '-';
  const gradeLabel = [
    studentInfo?.majorName || studentInfo?.major,
    studentInfo?.grade ? `${studentInfo.grade}级` : ''
  ].filter(Boolean).join(' · ') || '当前年级';

  const renderLeaderboardDetailScore = (value, type, category, record) => (
    <button
      type="button"
      className={`scores-leaderboard-score-link ${type}`}
      onClick={() => handleOpenCompositeDetails(type, category, record)}
      aria-label={`查看${record?.studentName || record?.studentNo || '学生'}的${category}${type === 'base' ? '基础分' : '附加分'}细则`}
    >
      {formatNumber(value, 2)}
    </button>
  );

  const leaderboardColumns = [
    {
      title: '排名',
      dataIndex: 'rank',
      key: 'rank',
      width: 80,
      render: (value) => <strong style={{ color: value <= 3 ? '#2563eb' : '#0f172a' }}>{value}</strong>
    },
    {
      title: '学号',
      dataIndex: 'studentNo',
      key: 'studentNo',
      width: 140
    },
    {
      title: '姓名',
      dataIndex: 'studentName',
      key: 'studentName',
      render: (value, record) => (
        <span style={{ fontWeight: String(record.studentNo) === String(user.studentNo) ? 700 : 500 }}>
          {value}
          {String(record.studentNo) === String(user.studentNo) ? '（我）' : ''}
        </span>
      )
    },
    {
      title: '德育',
      children: [
        {
          title: '基础分',
          dataIndex: 'moralBaseScore',
          key: 'moralBaseScore',
          render: (value, record) => renderLeaderboardDetailScore(value, 'base', 'MORAL', record)
        },
        {
          title: '附加分',
          dataIndex: 'moralExtraScore',
          key: 'moralExtraScore',
          render: (value, record) => renderLeaderboardDetailScore(value, 'extra', 'MORAL', record)
        },
        {
          title: '小计',
          dataIndex: 'moralTotalScore',
          key: 'moralTotalScore',
          render: (value) => formatNumber(value, 2)
        }
      ]
    },
    {
      title: '智育',
      children: [
        {
          title: '基础分',
          dataIndex: 'intellectualBaseScore',
          key: 'intellectualBaseScore',
          render: (value, record) => renderLeaderboardDetailScore(value, 'base', 'INTELLECTUAL', record)
        },
        {
          title: '附加分',
          dataIndex: 'intellectualExtraScore',
          key: 'intellectualExtraScore',
          render: (value, record) => renderLeaderboardDetailScore(value, 'extra', 'INTELLECTUAL', record)
        },
        {
          title: '小计',
          dataIndex: 'intellectualTotalScore',
          key: 'intellectualTotalScore',
          render: (value) => formatNumber(value, 2)
        }
      ]
    },
    {
      title: '体育',
      children: [
        {
          title: '基础分',
          dataIndex: 'physicalBaseScore',
          key: 'physicalBaseScore',
          render: (value, record) => renderLeaderboardDetailScore(value, 'base', 'PHYSICAL', record)
        },
        {
          title: '附加分',
          dataIndex: 'physicalExtraScore',
          key: 'physicalExtraScore',
          render: (value, record) => renderLeaderboardDetailScore(value, 'extra', 'PHYSICAL', record)
        },
        {
          title: '小计',
          dataIndex: 'physicalTotalScore',
          key: 'physicalTotalScore',
          render: (value) => formatNumber(value, 2)
        }
      ]
    },
    {
      title: '综合分',
      dataIndex: 'totalScore',
      key: 'totalScore',
      render: (value) => <strong>{formatNumber(value, 2)}</strong>
    }
  ];

  const gradeLeaderboardColumns = [
    leaderboardColumns[0],
    leaderboardColumns[1],
    leaderboardColumns[2],
    {
      title: '班级',
      dataIndex: 'className',
      key: 'className',
      width: 140,
      render: (value) => value || '-'
    },
    ...leaderboardColumns.slice(3)
  ];
  const countTableLeafColumns = (columns) => columns.reduce((total, column) => (
    total + (Array.isArray(column.children) ? countTableLeafColumns(column.children) : 1)
  ), 0);
  const leaderboardColumnCount = countTableLeafColumns(leaderboardColumns);
  const gradeLeaderboardColumnCount = countTableLeafColumns(gradeLeaderboardColumns);

  async function handleOpenCompositeDetails(type, category, targetStudent = null) {
    const academicYear = selectedSemester || compositeSummary.academicYear;
    const nextDetailStudent = targetStudent ? {
      ...targetStudent,
      studentNo: targetStudent.studentNo,
      name: targetStudent.studentName || targetStudent.name || targetStudent.studentNo || '同学',
      className: targetStudent.className || studentClass
    } : {
      studentNo,
      name: studentName,
      className: studentClass
    };
    setDetailOpen(true);
    setDetailType(type);
    setDetailCategory(category);
    setDetailStudent(nextDetailStudent);
    setDetailRows([]);
    setDetailLoading(true);
    try {
      const params = buildCompositeDetailParams(academicYear, category, nextDetailStudent.studentNo);
      const response = type === 'base'
        ? await getStudentCompositeBaseScoreDetails(params)
        : await getStudentCompositeExtraScoreDetails(params);
      setDetailRows(Array.isArray(response?.data) ? response.data : []);
    } catch (error) {
      message.error(error?.response?.data?.message || '获取综测明细失败');
      setDetailRows([]);
    } finally {
      setDetailLoading(false);
    }
  }

  const closeCompositeDetails = () => {
    setDetailOpen(false);
    setDetailRows([]);
    setDetailStudent(null);
  };

  const handleExport = () => {
    if (!hasExportData) {
      message.info('暂无可导出的成绩数据');
      return;
    }

    const rows = [
      ['筛选范围', pageScopeLabel],
      [],
      ['课程成绩'],
      ['学期', '课程名称', '课程类别', '成绩'],
      ...filteredSubjectScores.map((record) => [
        record.semester || '-',
        record.subject?.subjectName || '-',
        record.subject?.category === 'NON_SUBJECT' ? '无关项' : '科目项',
        formatNumber(record.score)
      ])
    ];

    rows.push([]);
    rows.push(['综合测评成绩']);
    rows.push(['快照学年', '综合总分', '基础分合计', '附加分合计']);
    rows.push([
      compositeSummary.academicYear,
      formatNumber(compositeSummary.totalScore),
      formatNumber(compositeSummary.baseScore),
      formatNumber(compositeSummary.extraScore)
    ]);
    rows.push([]);
    rows.push(['分类', '基础分', '附加分', '小计']);
    compositeSummary.items.forEach((item) => {
      rows.push([
        item.label,
        formatNumber(item.baseScore),
        formatNumber(item.extraScore),
        formatNumber(item.baseScore + item.extraScore)
      ]);
    });

    const csv = '\ufeff' + rows.map((row) => row.join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${user.studentNo || 'student'}-${selectedSemester || 'all'}-scores.csv`;
    link.click();
    window.URL.revokeObjectURL(url);
  };

  const classTopFive = overviewClassLeaderboard.slice(0, 5);
  const gradeTopFive = overviewGradeLeaderboard.slice(0, 5);

  const studentName = studentInfo?.studentName || studentInfo?.name || user.nickname || '同学';
  const studentNo = studentInfo?.studentNo || user.studentNo || '-';
  const studentClass = studentInfo?.className || user.className || '未分配班级';
  const studentMajor = studentInfo?.major || '计算机科学与技术';
  return (
    <StudentLayout activePath="/student/scores" breadcrumb="学生个人中心 / 我的成绩">
      <div className="scores-page">
        <div className="scores-page-actions scores-page-actions-only">
          <button className="scores-export-btn" onClick={handleExport}>
            导出 CSV
          </button>
          <button className="scores-refresh-btn" onClick={handleRefresh}>
            刷新
          </button>
        </div>

        <Card variant="borderless" className="scores-student-hero-card">
          <div className="scores-student-hero">
            <div className="scores-student-main">
              <Avatar size={86} src={withAuthToken(user.avatarUrl)} className="scores-student-avatar" />
              <div className="scores-student-meta">
                <div className="scores-student-name-row">
                  <h2>{studentName}</h2>
                  <Tag color="success">已关联学号</Tag>
                </div>
                <div className="scores-student-facts">
                  <span>学号：{studentNo}</span>
                  <span>班级：{studentClass}</span>
                  <span>专业：{studentMajor}</span>
                </div>
              </div>
            </div>
            <div className="scores-student-ranks">
              <div className="scores-rank-box blue">
                <div>
                  <div className="scores-rank-box-label">班级排名</div>
                  <div className="scores-rank-box-value">{myClassRank?.rank || '-'} <span>/ {classLeaderboardPage.total || '-'}</span></div>
                </div>
              </div>
              <div className="scores-rank-box green">
                <div>
                  <div className="scores-rank-box-label">年级排名</div>
                  <div className="scores-rank-box-value">{myGradeRank?.rank || '-'} <span>/ {gradeLeaderboardPage.total || '-'}</span></div>
                </div>
              </div>
            </div>
          </div>
        </Card>

        <Card variant="borderless" className="scores-filter-card scores-filter-card-modern">
          <div className="scores-filter-bar">
            <div className="scores-filter-group">
              <div className="scores-filter-inline">
                <label className="scores-filter-label">学年筛选：</label>
                <select className="scores-select" value={selectedSemester} onChange={(e) => setSelectedSemester(e.target.value)}>
                  {semesterOptions.map((semester) => (
                    <option key={semester} value={semester}>{semester}</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="scores-filter-quick">
              <div className="scores-chip-group">
                <span className="scores-chip-label">学年</span>
                {semesterOptions.slice(0, 3).map((semester) => (
                  <button key={semester} type="button" className={`scores-chip ${selectedSemester === semester ? 'active' : ''}`} onClick={() => setSelectedSemester(semester)}>
                    {semester}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </Card>
        {loading ? (
          <Card variant="borderless" className="scores-content-card">
            <div className="scores-loading">
              <Spin size="large" />
            </div>
          </Card>
        ) : (
          <>
            <div className="scores-tabs">
              <button
                className={`scores-tab ${activeTab === 'overview' ? 'active' : ''}`}
                onClick={() => setActiveTab('overview')}
              >
                总览
              </button>
              <button
                className={`scores-tab ${activeTab === 'courses' ? 'active' : ''}`}
                onClick={() => setActiveTab('courses')}
              >
                课程成绩
              </button>
              <button
                className={`scores-tab ${activeTab === 'secondClass' ? 'active' : ''}`}
                onClick={() => setActiveTab('secondClass')}
              >
                二课成绩
              </button>
              <button
                className={`scores-tab ${activeTab === 'leaderboard' ? 'active' : ''}`}
                onClick={() => setActiveTab('leaderboard')}
              >
                班级榜单
              </button>
              <button
                className={`scores-tab ${activeTab === 'gradeLeaderboard' ? 'active' : ''}`}
                onClick={() => setActiveTab('gradeLeaderboard')}
              >
                年级榜单
              </button>
              <button
                className={`scores-tab ${activeTab === 'simulate' ? 'active' : ''}`}
                onClick={() => setActiveTab('simulate')}
              >
                成绩模拟
              </button>
            </div>

            {/* 总览 */}
            {activeTab === 'overview' && (
              <div className="scores-overview-dashboard">
                <section className="scores-dashboard-panel scores-composite-panel">
                  <h3>综合测评</h3>
                  <div className="scores-composite-grid">
                    <div><span>综合总分</span><strong className="primary">{formatNumber(compositeSummary.totalScore)}</strong></div>
                    <div><span>基础分</span><strong>{formatNumber(compositeSummary.baseScore)}</strong></div>
                    <div><span>附加分</span><strong className="success">{formatNumber(compositeSummary.extraScore)}</strong></div>
                    {compositeSummary.items.map((item) => (
                      <CompositeScoreCategoryCard
                        key={item.category || item.label}
                        category={item.category}
                        label={item.label}
                        baseScore={item.baseScore}
                        extraScore={item.extraScore}
                        totalScore={Number(item.baseScore || 0) + Number(item.extraScore || 0)}
                        onOpenBase={() => handleOpenCompositeDetails('base', item.category)}
                        onOpenExtra={() => handleOpenCompositeDetails('extra', item.category)}
                      />
                    ))}
                  </div>
                </section>

                <div className="scores-dashboard-main">
                  <section className="scores-dashboard-panel scores-course-preview">
                    <h3>课程成绩（预览）</h3>
                    <div className="scores-preview-table">
                      <div className="scores-preview-row header">
                        <span>学期</span><span>课程名称</span><span>课程类别</span><span>分数</span><span>等级</span>
                      </div>
                      {overviewCourseScores.map((record) => {
                        const level = resolveLevelTag(record.score);
                        return (
                          <div key={record.id} className="scores-preview-row">
                            <span>{record.semester || '-'}</span>
                            <span>{record.subject?.subjectName || '-'}</span>
                            <span>{record.subject?.category === 'NON_SUBJECT' ? '无关项' : '科目项'}</span>
                            <span>{formatNumber(record.score)}</span>
                            <span><Tag color={level.color}>{level.text}</Tag></span>
                          </div>
                        );
                      })}
                      {!overviewCourseScores.length ? <div className="scores-dashboard-empty">当前学年暂无课程成绩</div> : null}
                    </div>
                    <button type="button" className="scores-dashboard-link" onClick={() => setActiveTab('courses')}>查看全部课程成绩</button>
                  </section>

                  <div className="scores-dashboard-side">
                    <section className="scores-dashboard-panel scores-second-class-summary">
                      <h3>二课成绩</h3>
                      <div className="scores-second-class-grid">
                        <div><span>真实二课分</span><strong>{currentSecondClassScore ? formatNumber(currentSecondClassScore.secondClassScore, 2) : '-'}</strong></div>
                        <div><span>换算二课分</span><strong>{currentSecondClassScore ? formatNumber(currentSecondClassScore.convertedMoralScore, 2) : '-'}</strong></div>
                        <div><span>德育总分</span><strong>{currentSecondClassScore ? formatNumber(currentSecondClassScore.moralTotalScore, 2) : '-'}</strong></div>
                      </div>
                    </section>

                    <div className="scores-dashboard-rankings">
                      <section className="scores-dashboard-panel scores-dashboard-ranking">
                        <h3>班级榜单 TOP 5</h3>
                        {classTopFive.map((item) => (
                          <div key={`${item.studentNo}-class`} className={`scores-dashboard-rank-row ${String(item.studentNo) === String(user.studentNo) ? 'current' : ''}`}>
                            <span>{item.rank}</span><span>{item.studentName}</span><strong>{formatNumber(item.totalScore)}</strong>
                          </div>
                        ))}
                        {!classTopFive.length ? <div className="scores-dashboard-empty">暂无班级榜单数据</div> : null}
                      </section>
                      <section className="scores-dashboard-panel scores-dashboard-ranking grade">
                        <h3>年级榜单 TOP 5</h3>
                        {gradeTopFive.map((item) => (
                          <div key={`${item.studentNo}-grade`} className={`scores-dashboard-rank-row ${String(item.studentNo) === String(user.studentNo) ? 'current' : ''}`}>
                            <span>{item.rank}</span><span>{item.studentName}</span><span>{item.className || '-'}</span><strong>{formatNumber(item.totalScore)}</strong>
                          </div>
                        ))}
                        {!gradeTopFive.length ? <div className="scores-dashboard-empty">暂无年级榜单数据</div> : null}
                      </section>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* 课程成绩 */}
            {activeTab === 'courses' && (
              <Card variant="borderless" className="scores-content-card">
                <div className="scores-card-header">
                  <h3 className="scores-card-title">全部课程成绩</h3>
                </div>
                <div className="scores-card-body">
                  <Table
                    rowKey="id"
                    columns={courseTableColumns}
                    dataSource={filteredSubjectScores}
                    pagination={{ pageSize: 10 }}
                    scroll={{ x: 720 }}
                    className="scores-table"
                  />
                </div>
              </Card>
            )}

            {/* 二课成绩 */}
            {activeTab === 'secondClass' && (
              <Card variant="borderless" className="scores-content-card">
                <div className="scores-card-header">
                  <h3 className="scores-card-title">
                    二课成绩 · {secondClassScope === 'grade' ? gradeLabel : studentClass} · {primaryScopeLabel}
                  </h3>
                  <div className="scores-chip-group">
                    <button
                      type="button"
                      className={`scores-chip ${secondClassScope === 'class' ? 'active' : ''}`}
                      onClick={() => setSecondClassScope('class')}
                    >
                      本班
                    </button>
                    <button
                      type="button"
                      className={`scores-chip ${secondClassScope === 'grade' ? 'active' : ''}`}
                      onClick={() => setSecondClassScope('grade')}
                    >
                      全年级
                    </button>
                  </div>
                </div>
                <div className="scores-card-body">
                  <Table
                    rowKey="id"
                    columns={secondClassTableColumns}
                    dataSource={secondClassScores}
                    loading={secondClassLoading}
                    pagination={{ pageSize: 10, showSizeChanger: false }}
                    scroll={{ x: 1120 }}
                    className="scores-table"
                    rowClassName={(record) => String(record.studentNo) === String(user.studentNo) ? 'scores-self-row' : ''}
                  />
                </div>
              </Card>
            )}

            {/* 班级榜单 */}
            {activeTab === 'leaderboard' && (
              <Card variant="borderless" className="scores-content-card">
                <div className="scores-card-header">
                  <h3 className="scores-card-title">班级总成绩榜单 · {primaryScopeLabel}</h3>
                </div>
                <div className="scores-card-body">
                  <Table
                    rowKey="studentNo"
                    columns={leaderboardColumns}
                    dataSource={classLeaderboard}
                    loading={leaderboardLoading}
                    pagination={{
                      current: classLeaderboardPage.page,
                      pageSize: classLeaderboardPage.pageSize,
                      total: classLeaderboardPage.total,
                      showSizeChanger: true
                    }}
                    onChange={(pagination) => loadClassLeaderboard(pagination.current, pagination.pageSize)}
                    scroll={{ x: 1320 }}
                    className="scores-table"
                    rowClassName={(record) => String(record.studentNo) === String(user.studentNo) ? 'scores-self-row' : ''}
                    summary={() => myClassRank ? (
                      <Table.Summary fixed>
                        <Table.Summary.Row>
                          <Table.Summary.Cell index={0} colSpan={leaderboardColumnCount}>
                            当前我的班级排名：第 {myClassRank.rank} 名，当前总分 {formatNumber(myClassRank.totalScore)}
                          </Table.Summary.Cell>
                        </Table.Summary.Row>
                      </Table.Summary>
                    ) : null}
                  />
                </div>
              </Card>
            )}

            {/* 成绩模拟 */}
            {activeTab === 'simulate' && (
              <ScoreSimulatorPanel academicYear={selectedSemester} />
            )}

            {activeTab === 'gradeLeaderboard' && (
              <Card variant="borderless" className="scores-content-card">
                <div className="scores-card-header">
                  <h3 className="scores-card-title">年级总成绩榜单 · {gradeLabel} · {primaryScopeLabel}</h3>
                </div>
                <div className="scores-card-body">
                  <Table
                    rowKey="studentNo"
                    columns={gradeLeaderboardColumns}
                    dataSource={gradeLeaderboard}
                    loading={gradeLeaderboardLoading}
                    pagination={{
                      current: gradeLeaderboardPage.page,
                      pageSize: gradeLeaderboardPage.pageSize,
                      total: gradeLeaderboardPage.total,
                      showSizeChanger: true
                    }}
                    onChange={(pagination) => loadGradeLeaderboard(pagination.current, pagination.pageSize)}
                    scroll={{ x: 1460 }}
                    className="scores-table"
                    rowClassName={(record) => String(record.studentNo) === String(user.studentNo) ? 'scores-self-row' : ''}
                    summary={() => myGradeRank ? (
                      <Table.Summary fixed>
                        <Table.Summary.Row>
                          <Table.Summary.Cell index={0} colSpan={gradeLeaderboardColumnCount}>
                            当前我的年级排名：第 {myGradeRank.rank} 名，当前总分 {formatNumber(myGradeRank.totalScore)}
                          </Table.Summary.Cell>
                        </Table.Summary.Row>
                      </Table.Summary>
                    ) : null}
                  />
                </div>
              </Card>
            )}
          </>
        )}

        <CompositeScoreDetailDrawer
          open={detailOpen}
          onClose={closeCompositeDetails}
          studentName={detailStudent?.name || studentName}
          className={detailStudent?.className || studentClass}
          academicYear={selectedSemester || compositeSummary.academicYear}
          type={detailType}
          category={detailCategory}
          rows={detailRows}
          loading={detailLoading}
          finalScore={detailCategory === 'PHYSICAL' && detailType === 'base'
            ? (detailStudent?.physicalBaseScore
              ?? compositeSummary.items.find((item) => item.category === 'PHYSICAL')?.baseScore)
            : null}
          weight={physicalBaseWeight}
        />
      </div>
    </StudentLayout>
  );
}

export default StudentScores;
