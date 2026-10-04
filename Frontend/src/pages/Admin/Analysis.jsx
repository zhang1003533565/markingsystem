import { useEffect, useMemo, useState } from 'react';
import { BulbOutlined, HistoryOutlined, ReloadOutlined, SettingOutlined } from '../../components/AcademicIcons';
import { Alert, Button, Card, Collapse, Divider, Drawer, Empty, Input, Progress, Select, Slider, Space, Spin, Statistic, Switch, Table, Tag, Typography, message, Form, InputNumber } from 'antd';
import {
  cancelAdminAnalysisTask,
  getAdminAnalysisTask,
  getAdminAnalysisOverview,
  getAdminStudentAnalysis,
  getAdminStudentAnalysisHistory,
  getStudentList,
  refreshAdminAnalysisOverview,
  refreshAdminStudentAnalysis
} from '../../api/admin/student';
import { getTeacherPortraitRules, saveTeacherPortraitRules, getDefaultPortraitRules, togglePortraitRuleEnabled } from '../../api/teacher/portraitRule';
import EChartPanel from '../../components/EChartPanel';
import { confirmAction } from '../../components/confirmDialogService';
import { getStoredUser } from '../../utils/authStorage';
import './Analysis.css';

const { Title, Text } = Typography;
const ANALYSIS_TASK_STORAGE_KEY = 'admin_analysis_batch_task';
const DEFAULT_PORTRAIT_RULES = [
  { typeKey: 'all_round', typeName: '全面优秀型', moralWeight: 20, intellectualWeight: 25, physicalWeight: 15, extraScoreWeight: 20, excellentCourseWeight: 20, keywordList: [], keywordBonus: 5 },
  { typeKey: 'academic', typeName: '学术升学型', moralWeight: 5, intellectualWeight: 40, physicalWeight: 5, extraScoreWeight: 10, excellentCourseWeight: 40, keywordList: [], keywordBonus: 5 },
  { typeKey: 'competition', typeName: '复合竞赛型', moralWeight: 5, intellectualWeight: 15, physicalWeight: 5, extraScoreWeight: 45, excellentCourseWeight: 30, keywordList: [], keywordBonus: 5 },
  { typeKey: 'technical', typeName: '技术专长型', moralWeight: 5, intellectualWeight: 35, physicalWeight: 5, extraScoreWeight: 10, excellentCourseWeight: 45, keywordList: [], keywordBonus: 5 },
  { typeKey: 'employment', typeName: '就业实践型', moralWeight: 10, intellectualWeight: 20, physicalWeight: 5, extraScoreWeight: 45, excellentCourseWeight: 20, keywordList: [], keywordBonus: 5 },
  { typeKey: 'civil_service', typeName: '稳定考公型', moralWeight: 60, intellectualWeight: 10, physicalWeight: 20, extraScoreWeight: 5, excellentCourseWeight: 5, keywordList: [], keywordBonus: 5 },
  { typeKey: 'potential', typeName: '发展潜力型', moralWeight: 10, intellectualWeight: 30, physicalWeight: 10, extraScoreWeight: 10, excellentCourseWeight: 40, keywordList: [], keywordBonus: 5 },
];
const DEFAULT_RULE_BY_TYPE = new Map(DEFAULT_PORTRAIT_RULES.map((rule) => [rule.typeKey, rule]));

const RADAR_KEYS = ['思想品德', '学业成绩', '科研创新', '实践服务', '文体素养', '技能素养', '综合素质', '发展潜力'];

const clonePortraitRules = (rules = DEFAULT_PORTRAIT_RULES) => (
  (rules?.length ? rules : DEFAULT_PORTRAIT_RULES).map((rule) => ({
    ...(DEFAULT_RULE_BY_TYPE.get(rule.typeKey) || {}),
    ...rule,
    keywordList: Array.isArray(rule.keywordList)
      ? [...rule.keywordList]
      : [...(DEFAULT_RULE_BY_TYPE.get(rule.typeKey)?.keywordList || [])],
  }))
);

const restoreDefaultRulesKeepingKeywords = (defaults, currentRules) => {
  const currentByType = new Map(clonePortraitRules(currentRules).map((rule) => [rule.typeKey, rule]));
  return clonePortraitRules(defaults).map((rule) => {
    const currentRule = currentByType.get(rule.typeKey);
    return {
      ...rule,
      keywordList: Array.isArray(currentRule?.keywordList) ? [...currentRule.keywordList] : rule.keywordList,
    };
  });
};

function Analysis() {
  const user = getStoredUser();
  const isTeacher = user?.role === 'TEACHER';
  const [keyword, setKeyword] = useState('');
  const [classFilter, setClassFilter] = useState('');
  const [loading, setLoading] = useState(false);
  const [students, setStudents] = useState([]);
  const [overview, setOverview] = useState(null);
  const [overviewLoading, setOverviewLoading] = useState(false);
  const [selectedStudent, setSelectedStudent] = useState(null);
  const [analysis, setAnalysis] = useState(null);
  const [history, setHistory] = useState([]);
  const [analysisLoading, setAnalysisLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [batchTask, setBatchTask] = useState(null);
  const [rulesEnabled, setRulesEnabled] = useState(false);
  const [customRules, setCustomRules] = useState(() => clonePortraitRules());
  const [defaultRules, setDefaultRules] = useState(() => clonePortraitRules());
  const [loadingRules, setLoadingRules] = useState(false);
  const [ruleConfigOpen, setRuleConfigOpen] = useState(false);

  useEffect(() => {
    if (isTeacher) {
      loadPortraitRules();
    }
  }, [isTeacher]);

  const loadPortraitRules = async () => {
    setLoadingRules(true);
    try {
      // 并行加载当前规则和默认规则
      const [res, defaultRes] = await Promise.all([
        getTeacherPortraitRules(),
        getDefaultPortraitRules()
      ]);
      const data = res?.data || {};
      const nextDefaultRules = clonePortraitRules(defaultRes?.data?.rules);
      setRulesEnabled(data.enabled || false);
      setDefaultRules(nextDefaultRules);
      setCustomRules(data.rules?.length ? clonePortraitRules(data.rules) : clonePortraitRules(nextDefaultRules));
    } catch (error) {
      console.error('加载画像规则失败', error);
    } finally {
      setLoadingRules(false);
    }
  };

  const handleSaveRules = async () => {
    try {
      // 直接保存百分比值，后端也直接存储百分比
      await saveTeacherPortraitRules({
        enabled: rulesEnabled,
        rules: customRules,
      });
      message.success('规则保存成功');
      setRuleConfigOpen(false);
    } catch {
      message.error('规则保存失败');
    }
  };

  const handleToggleRules = async (checked) => {
    try {
      await togglePortraitRuleEnabled(checked);
      setRulesEnabled(checked);
      message.success(checked ? '已启用自定义规则' : '已关闭自定义规则');
    } catch {
      message.error('操作失败');
    }
  };

  const handleRestoreDefaultRules = () => {
    setCustomRules(prev => restoreDefaultRulesKeepingKeywords(defaultRules, prev));
    message.success('已恢复默认规则');
  };

  const updateRule = (typeKey, field, value) => {
    setCustomRules(prev => clonePortraitRules(prev).map(rule =>
      rule.typeKey === typeKey ? { ...rule, [field]: value } : rule
    ));
  };

  useEffect(() => {
    try {
      const savedTask = window.localStorage.getItem(ANALYSIS_TASK_STORAGE_KEY);
      if (!savedTask) {
        return;
      }
      const parsedTask = JSON.parse(savedTask);
      if (parsedTask?.taskId) {
        setBatchTask(parsedTask);
      }
    } catch {
      window.localStorage.removeItem(ANALYSIS_TASK_STORAGE_KEY);
    }
  }, []);

  const loadStudents = async (nextKeyword = keyword, nextClassName = classFilter) => {
    setLoading(true);
    try {
      const response = await getStudentList({ page: 0, size: 100, keyword: nextKeyword || undefined });
      let rows = response?.content || [];
      if (nextClassName) {
        rows = rows.filter((item) => item.className === nextClassName);
      }
      setStudents(rows);
    } catch {
      setStudents([]);
    } finally {
      setLoading(false);
    }
  };

  const loadOverview = async (nextKeyword = keyword, nextClassName = classFilter) => {
    setOverviewLoading(true);
    try {
      const response = await getAdminAnalysisOverview({
        keyword: nextKeyword || undefined,
        className: nextClassName || undefined
      });
      setOverview(response?.data || null);
    } catch {
      setOverview(null);
    } finally {
      setOverviewLoading(false);
    }
  };

  const reloadPageData = async (nextKeyword = keyword, nextClassName = classFilter) => {
    await Promise.all([
      loadStudents(nextKeyword, nextClassName),
      loadOverview(nextKeyword, nextClassName)
    ]);
  };

  const handleResetFilters = () => {
    setKeyword('');
    setClassFilter('');
    reloadPageData('', '');
  };

  useEffect(() => {
    reloadPageData('', '');
  }, []);

  useEffect(() => {
    if (!batchTask?.taskId || ['completed', 'failed', 'cancelled'].includes(batchTask.status)) {
      return undefined;
    }

    const timer = window.setInterval(async () => {
      try {
        const response = await getAdminAnalysisTask(batchTask.taskId);
        const nextTask = response?.data || null;
        setBatchTask(nextTask);
        if (nextTask && ['completed', 'failed', 'cancelled'].includes(nextTask.status)) {
          await reloadPageData();
          if (nextTask.status === 'completed') {
            message.success(`批量分析完成，本次更新 ${nextTask.successCount || 0} 人`);
          } else if (nextTask.status === 'cancelled') {
            message.warning('批量分析已终止');
          } else {
            message.error(nextTask.message || '批量分析失败');
          }
        }
      } catch {
        setBatchTask(null);
        window.localStorage.removeItem(ANALYSIS_TASK_STORAGE_KEY);
        window.clearInterval(timer);
      }
    }, 1000);

    return () => window.clearInterval(timer);
  }, [batchTask?.taskId, batchTask?.status]);

  useEffect(() => {
    if (!batchTask?.taskId) {
      window.localStorage.removeItem(ANALYSIS_TASK_STORAGE_KEY);
      return;
    }
    window.localStorage.setItem(ANALYSIS_TASK_STORAGE_KEY, JSON.stringify(batchTask));
    if (['completed', 'failed', 'cancelled'].includes(batchTask.status)) {
      window.localStorage.removeItem(ANALYSIS_TASK_STORAGE_KEY);
    }
  }, [batchTask]);

  const classOptions = useMemo(() => {
    const values = [...new Set(students.map((item) => item.className).filter(Boolean))];
    return values.map((value) => ({ value, label: value }));
  }, [students]);

  const stats = useMemo(() => ({
    totalStudents: overview?.totalStudents || students.length,
    classCount: overview?.classCount || new Set(students.map((item) => item.className).filter(Boolean)).size,
    analyzedStudents: overview?.analyzedStudents || 0
  }), [overview, students]);

  const loadStudentDetails = async (studentNo) => {
    const [latestResponse, historyResponse] = await Promise.all([
      getAdminStudentAnalysis(studentNo).catch(() => ({ data: null })),
      getAdminStudentAnalysisHistory(studentNo).catch(() => ({ data: [] }))
    ]);
    setAnalysis(latestResponse?.data || null);
    setHistory(historyResponse?.data || []);
  };

  const handleAnalyze = async (record) => {
    setSelectedStudent(record);
    setDrawerOpen(true);
    setAnalysis(null);
    setHistory([]);
    setAnalysisLoading(true);
    try {
      await loadStudentDetails(record.studentNo);
    } catch {
      message.error('读取画像历史失败');
    } finally {
      setAnalysisLoading(false);
    }
  };

  const handleRefreshStudent = async () => {
    if (!selectedStudent?.studentNo) {
      return;
    }
    setAnalysisLoading(true);
    try {
      const response = await refreshAdminStudentAnalysis(selectedStudent.studentNo);
      setAnalysis(response?.data || null);
      const historyResponse = await getAdminStudentAnalysisHistory(selectedStudent.studentNo);
      setHistory(historyResponse?.data || []);
      await loadOverview();
      message.success('画像已更新');
    } catch {
      message.error('画像更新失败');
    } finally {
      setAnalysisLoading(false);
    }
  };

  const handleBatchRefresh = async () => {
    setOverviewLoading(true);
    try {
      const response = await refreshAdminAnalysisOverview({
        keyword: keyword || undefined,
        className: classFilter || undefined
      });
      setBatchTask(response?.data || null);
      message.success('批量分析任务已启动');
    } catch {
      message.error('批量分析失败');
    } finally {
      setOverviewLoading(false);
    }
  };

  const handleSelectedBatchRefresh = async () => {
    if (!selectedRowKeys.length) {
      message.warning('请先勾选学生');
      return;
    }
    setOverviewLoading(true);
    try {
      const studentNos = students
        .filter((item) => selectedRowKeys.includes(item.id))
        .map((item) => item.studentNo)
        .filter(Boolean);
      const response = await refreshAdminAnalysisOverview({
        keyword: keyword || undefined,
        className: classFilter || undefined,
        studentNos
      });
      setBatchTask(response?.data || null);
      message.success('所选学生分析任务已启动');
    } catch {
      message.error('勾选学生批量分析失败');
    } finally {
      setOverviewLoading(false);
    }
  };

  const confirmBatchRefresh = async () => {
    const confirmed = await confirmAction({
      title: '批量更新学生画像？',
      message: '系统将按照当前筛选条件，对匹配到的学生重新执行画像分析。',
      confirmText: '开始分析',
      tone: 'primary'
    });
    if (confirmed) {
      await handleBatchRefresh();
    }
  };

  const confirmSelectedBatchRefresh = async () => {
    const confirmed = await confirmAction({
      title: `分析已选中的 ${selectedRowKeys.length} 名学生？`,
      message: '系统将重新生成所选学生的画像分析结果。',
      confirmText: '开始分析',
      tone: 'primary'
    });
    if (confirmed) {
      await handleSelectedBatchRefresh();
    }
  };

  const handleCancelTask = async () => {
    if (!batchTask?.taskId) {
      return;
    }
    try {
      const response = await cancelAdminAnalysisTask(batchTask.taskId);
      setBatchTask(response?.data || null);
      message.success('终止指令已发送');
    } catch {
      message.error('终止任务失败');
    }
  };

  const columns = [
    { title: '学号', dataIndex: 'studentNo', key: 'studentNo', width: 160 },
    { title: '姓名', dataIndex: 'name', key: 'name', width: 120 },
    { title: '班级', dataIndex: 'className', key: 'className', width: 180, render: (value) => value || '-' },
    { title: '联系方式', dataIndex: 'phone', key: 'phone', render: (value) => value || '-' },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Button type="primary" onClick={() => handleAnalyze(record)}>
          查看画像
        </Button>
      ),
    },
  ];

  const colorMap = {
    考研: 'blue',
    考公: 'gold',
    竞赛型人才: 'magenta',
  };

  const radarOption = useMemo(() => {
    const dims = analysis?.radarDimensions || {};
    if (!RADAR_KEYS.some((key) => dims[key] != null)) {
      return null;
    }
    return {
      tooltip: {},
      radar: {
        indicator: RADAR_KEYS.map((key) => ({ name: key, max: 100 })),
        shape: 'polygon',
        splitNumber: 5,
        axisName: { color: '#475569', fontSize: 12 },
        splitLine: { lineStyle: { color: '#e2e8f0' } },
        splitArea: { areaStyle: { color: ['#f8fafc', '#ffffff'] } },
        axisLine: { lineStyle: { color: '#e2e8f0' } },
      },
      series: [{
        type: 'radar',
        data: [{
          value: RADAR_KEYS.map((key) => dims[key] ?? 0),
          name: selectedStudent?.name || '学生',
          areaStyle: { opacity: 0.2 },
          lineStyle: { width: 2 },
        }],
      }],
    };
  }, [analysis, selectedStudent]);

  const batchProgress = batchTask?.totalCount
    ? Math.round(((batchTask.processedCount || 0) / batchTask.totalCount) * 100)
    : 0;
  const canCancelTask = Boolean(batchTask && !['completed', 'failed', 'cancelled'].includes(batchTask.status));

  return (
    <div className="analysis-page">
      <Card variant="borderless" className="analysis-control-card">
        <div className="analysis-toolbar">
          <div className="analysis-filter-fields">
            <label className="analysis-filter-item">
              <span>学号/姓名</span>
              <Input
                allowClear
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                onPressEnter={() => reloadPageData(keyword, classFilter)}
                placeholder="请输入学号或姓名"
              />
            </label>
            <label className="analysis-filter-item">
              <span>班级</span>
              <Select
                allowClear
                placeholder="请选择班级"
                value={classFilter || undefined}
                onChange={(value) => setClassFilter(value || '')}
                options={classOptions}
              />
            </label>
            <Button type="primary" onClick={() => reloadPageData(keyword, classFilter)}>查询</Button>
            <Button onClick={handleResetFilters}>重置</Button>
          </div>

          <div className="analysis-toolbar-actions">
            <Button type="primary" ghost loading={overviewLoading} onClick={confirmBatchRefresh}>
              批量分析
            </Button>
            <Button
              disabled={!selectedRowKeys.length}
              loading={overviewLoading}
              onClick={confirmSelectedBatchRefresh}
            >
              分析选中
            </Button>
            <Button danger disabled={!canCancelTask} onClick={handleCancelTask}>终止任务</Button>
            {isTeacher && (
              <>
                <Switch
                  checked={rulesEnabled}
                  onChange={handleToggleRules}
                  checkedChildren="规则已启用"
                  unCheckedChildren="规则已关闭"
                  loading={loadingRules}
                />
                <Button icon={<SettingOutlined />} onClick={() => setRuleConfigOpen(true)} disabled={loadingRules}>
                  画像规则
                </Button>
              </>
            )}
          </div>
        </div>

        <div className="analysis-summary-grid">
          <div className="analysis-summary-item"><Statistic title="学生总数" value={stats.totalStudents} suffix="人" /></div>
          <div className="analysis-summary-item"><Statistic title="已生成画像" value={stats.analyzedStudents} suffix="人" /></div>
          <div className="analysis-summary-item"><Statistic title="画像生成中" value={batchTask?.status === 'running' ? Math.max((batchTask.totalCount || 0) - (batchTask.processedCount || 0), 0) : 0} suffix="人" /></div>
          <div className="analysis-summary-item"><Statistic title="待更新" value={Math.max(stats.totalStudents - stats.analyzedStudents, 0)} suffix="人" /></div>
        </div>

        {overviewLoading ? (
          <div className="analysis-distribution-row"><Spin size="small" /></div>
        ) : overview?.typeDistribution?.length ? (
          <div className="analysis-distribution-row">
            {overview.typeDistribution.map((item) => (
              <span key={item.type}>{item.type}：<strong>{item.count || 0}</strong> 人（{item.ratio || 0}%）</span>
            ))}
            <span>覆盖班级：<strong>{stats.classCount}</strong> 个</span>
          </div>
        ) : null}

        {batchTask && (
          <div className="analysis-task-strip">
            <Tag color={batchTask.status === 'running' ? 'processing' : batchTask.status === 'completed' ? 'success' : batchTask.status === 'cancelled' ? 'warning' : 'error'}>
              {batchTask.status}
            </Tag>
            <Progress percent={batchProgress} size="small" />
            <Text type="secondary">已处理 {batchTask.processedCount || 0}/{batchTask.totalCount || 0}，成功 {batchTask.successCount || 0}</Text>
          </div>
        )}
      </Card>

      <Card variant="borderless" className="analysis-table-card">
        <div className="analysis-table-title">学生列表</div>
        <Table
          rowKey="id"
          loading={loading}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys,
            selections: [Table.SELECTION_ALL, Table.SELECTION_INVERT, Table.SELECTION_NONE]
          }}
          columns={columns}
          dataSource={students}
          pagination={{ pageSize: 10 }}
          scroll={{ x: 860 }}
        />
      </Card>

      <Drawer
        open={drawerOpen}
        size="large"
        onClose={() => setDrawerOpen(false)}
        title={selectedStudent ? `${selectedStudent.name} 的发展画像` : '学生发展画像'}
        className="analysis-drawer"
        extra={(
          <Button type="primary" icon={<ReloadOutlined />} loading={analysisLoading} onClick={handleRefreshStudent}>
            立即更新
          </Button>
        )}
      >
        {analysisLoading ? (
          <div style={{ padding: '48px 0', textAlign: 'center' }}><Spin /></div>
        ) : !analysis ? (
          <Empty description="暂无已保存画像，请先点击右上角立即更新" />
        ) : (
          <Space orientation="vertical" size={16} style={{ width: '100%' }}>
            <Card variant="borderless" className="analysis-result-card">
              <Space orientation="vertical" size={10}>
                <Space>
                  <BulbOutlined style={{ color: '#d48806' }} />
                  <Text type="secondary">当前保存结果</Text>
                </Space>
                <Title level={2} style={{ margin: 0 }}>{analysis.primaryType}</Title>
                <Space wrap>
                  <Tag color={colorMap[analysis.primaryType] || 'default'}>置信度 {analysis.confidence || 0}%</Tag>
                  <Tag>{analysis.source || '-'}</Tag>
                  <Tag>{analysis.model || '-'}</Tag>
                  <Tag>{analysis.generatedAt ? `生成时间 ${String(analysis.generatedAt).replace('T', ' ').slice(0, 19)}` : '未记录'}</Tag>
                </Space>
                <Text>{analysis.summary}</Text>
              </Space>
            </Card>

            {radarOption ? (
              <Card title="八维素质雷达图" variant="borderless" className="analysis-content-card analysis-radar-card">
                <EChartPanel option={radarOption} height={360} />
              </Card>
            ) : null}

            <Card title="判断依据" variant="borderless" className="analysis-content-card">
              {(analysis.reasons || []).length ? (
                <div style={{ display: 'grid', gap: 8 }}>
                  {(analysis.reasons || []).map((item, index) => (
                    <Text key={`reason-${index}`}>{item}</Text>
                  ))}
                </div>
              ) : (
                <Empty description="暂无判断依据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}
            </Card>

            <Card title="建议动作" variant="borderless" className="analysis-content-card">
              {(analysis.suggestions || []).length ? (
                <div style={{ display: 'grid', gap: 8 }}>
                  {(analysis.suggestions || []).map((item, index) => (
                    <Text key={`suggestion-${index}`}>{item}</Text>
                  ))}
                </div>
              ) : (
                <Empty description="暂无建议动作" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}
            </Card>

            <Card title="历史记录" variant="borderless" className="analysis-content-card">
              {(history || []).length ? (
                <div style={{ display: 'grid', gap: 16 }}>
                  {history.map((item, index) => (
                    <div key={`${item.generatedAt || 'history'}-${index}`} style={{ paddingBottom: 12, borderBottom: '1px solid #f1f5f9' }}>
                      <Space orientation="vertical" size={4}>
                        <Space wrap>
                          <Tag icon={<HistoryOutlined />}>{item.generatedAt ? String(item.generatedAt).replace('T', ' ').slice(0, 19) : '未记录'}</Tag>
                          <Tag color={colorMap[item.primaryType] || 'default'}>{item.primaryType}</Tag>
                          <Tag>{item.confidence || 0}%</Tag>
                        </Space>
                        <Text type="secondary">{item.summary}</Text>
                      </Space>
                    </div>
                  ))}
                </div>
              ) : (
                <Empty description="暂无历史记录" />
              )}
            </Card>
          </Space>
        )}
      </Drawer>

      <Drawer
        title="调整画像规则参数"
        open={ruleConfigOpen}
        onClose={() => setRuleConfigOpen(false)}
        width={600}
        footer={
          <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
            <Button onClick={handleRestoreDefaultRules}>恢复默认值</Button>
            <Button onClick={() => setRuleConfigOpen(false)}>取消</Button>
            <Button type="primary" onClick={handleSaveRules}>保存规则</Button>
          </Space>
        }
      >
        <Collapse defaultActiveKey={['all_round', 'academic']}>
          {customRules?.map((rule) => {
            // 计算权重总和
            const weights = [
              rule.moralWeight,
              rule.intellectualWeight,
              rule.physicalWeight,
              rule.extraScoreWeight,
              rule.excellentCourseWeight,
            ].filter(v => v !== undefined && v !== null);
            const totalWeight = weights.reduce((sum, w) => sum + w, 0);

            return (
            <Collapse.Panel
              key={rule.typeKey}
              header={
                <Space>
                  <Text strong>{rule.typeName || rule.typeKey}</Text>
                  <Tag color={totalWeight > 100 ? "red" : totalWeight === 100 ? "green" : "orange"}>
                    总权重: {totalWeight}%
                  </Tag>
                </Space>
              }
            >
              <Form layout="vertical" size="small">
                <Alert
                  message={`${rule.typeName || rule.typeKey}权重配置`}
                  description="每个类型使用同一套比例项。权重总和建议为100%，总和超过100%会导致分数溢出。"
                  type="info"
                  showIcon
                  style={{ marginBottom: 16 }}
                />
                <Form.Item label="德育权重">
                  <Slider
                    min={0}
                    max={100}
                    step={1}
                    value={rule.moralWeight ?? 0}
                    onChange={(val) => updateRule(rule.typeKey, 'moralWeight', val)}
                    marks={{ 0: '0%', 50: '50%', 100: '100%' }}
                  />
                  <Text type="secondary">当前值: {rule.moralWeight ?? 0}%</Text>
                </Form.Item>
                <Form.Item label="智育权重">
                  <Slider
                    min={0}
                    max={100}
                    step={1}
                    value={rule.intellectualWeight ?? 0}
                    onChange={(val) => updateRule(rule.typeKey, 'intellectualWeight', val)}
                    marks={{ 0: '0%', 50: '50%', 100: '100%' }}
                  />
                  <Text type="secondary">当前值: {rule.intellectualWeight ?? 0}%</Text>
                </Form.Item>
                <Form.Item label="体育权重">
                  <Slider
                    min={0}
                    max={100}
                    step={1}
                    value={rule.physicalWeight ?? 0}
                    onChange={(val) => updateRule(rule.typeKey, 'physicalWeight', val)}
                    marks={{ 0: '0%', 50: '50%', 100: '100%' }}
                  />
                  <Text type="secondary">当前值: {rule.physicalWeight ?? 0}%</Text>
                </Form.Item>
                <Form.Item label="附加分权重">
                  <Slider
                    min={0}
                    max={100}
                    step={1}
                    value={rule.extraScoreWeight ?? 0}
                    onChange={(val) => updateRule(rule.typeKey, 'extraScoreWeight', val)}
                    marks={{ 0: '0%', 50: '50%', 100: '100%' }}
                  />
                  <Text type="secondary">当前值: {rule.extraScoreWeight ?? 0}%</Text>
                </Form.Item>
                <Form.Item label="优秀课程权重">
                  <Slider
                    min={0}
                    max={100}
                    step={1}
                    value={rule.excellentCourseWeight ?? 0}
                    onChange={(val) => updateRule(rule.typeKey, 'excellentCourseWeight', val)}
                    marks={{ 0: '0%', 50: '50%', 100: '100%' }}
                  />
                  <Text type="secondary">当前值: {rule.excellentCourseWeight ?? 0}%</Text>
                </Form.Item>
                <Divider style={{ margin: '16px 0' }} />
                <Text strong>标签匹配加分</Text>
                <Form.Item label="标签列表（逗号分隔）">
                  <Input
                    placeholder="如: 优秀, 三好学生, 奖学金"
                    value={Array.isArray(rule.keywordList) ? rule.keywordList.join(', ') : ''}
                    onChange={(e) => updateRule(rule.typeKey, 'keywordList', e.target.value.split(',').map(k => k.trim()).filter(k => k))}
                  />
                </Form.Item>
                <Form.Item label="标签匹配权重（每个标签贡献的百分比）">
                  <Slider
                    min={0}
                    max={50}
                    step={1}
                    value={rule.keywordBonus ?? 5}
                    onChange={(val) => updateRule(rule.typeKey, 'keywordBonus', val)}
                    marks={{ 0: '0%', 25: '25%', 50: '50%' }}
                  />
                  <Text type="secondary">当前值: {rule.keywordBonus ?? 5}%（每个匹配标签）</Text>
                </Form.Item>
              </Form>
            </Collapse.Panel>
            );
          })}
        </Collapse>
      </Drawer>
    </div>
  );
}

export default Analysis;
