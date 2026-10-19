import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Timeline, Tag, Button, Space, Descriptions, Alert, Divider, Input, Row, Col, Badge, Modal, Form, Select, message } from 'antd';
import { ArrowLeftOutlined, SafetyCertificateOutlined, FileOutlined, UploadOutlined, CheckCircleOutlined, CloseCircleOutlined, EditOutlined, HistoryOutlined, UserOutlined, AuditOutlined } from '@ant-design/icons';
import request from '../../utils/request';
import { getStoredUser } from '../../utils/authStorage';
import './MaterialUpload.css';

const stageColorMap = {
  SUBMITTED: 'blue',
  MODIFIED: 'geekblue',
  INITIAL_REVIEW_PASS: 'green',
  INITIAL_REVIEW_REJECT: 'red',
  REEXAMINE_PASS: 'green',
  REEXAMINE_REJECT: 'red',
  FINAL_REVIEW_PASS: 'success',
  FINAL_REVIEW_REJECT: 'red'
};

const stageLabelMap = {
  SUBMITTED: '已提交',
  MODIFIED: '已修改',
  INITIAL_REVIEW_PASS: '初审通过',
  INITIAL_REVIEW_REJECT: '初审驳回',
  REEXAMINE_PASS: '复审通过',
  REEXAMINE_REJECT: '复审驳回',
  FINAL_REVIEW_PASS: '终审通过',
  FINAL_REVIEW_REJECT: '终审驳回'
};

const eventTypeMap = {
  UPLOAD: { label: '初次提交', color: 'blue', icon: <UploadOutlined /> },
  MODIFY: { label: '修改材料', color: 'geekblue', icon: <EditOutlined /> },
  INITIAL_REVIEW_PASS: { label: '班委初审通过', color: 'green', icon: <CheckCircleOutlined /> },
  INITIAL_REVIEW_REJECT: { label: '班委初审驳回', color: 'red', icon: <CloseCircleOutlined /> },
  REEXAMINE_PASS: { label: '复审通过', color: 'green', icon: <CheckCircleOutlined /> },
  REEXAMINE_REJECT: { label: '复审驳回', color: 'red', icon: <CloseCircleOutlined /> },
  FINAL_REVIEW_PASS: { label: '老师终审通过', color: 'success', icon: <CheckCircleOutlined /> },
  FINAL_REVIEW_REJECT: { label: '老师终审驳回', color: 'red', icon: <CloseCircleOutlined /> }
};

function MaterialTrace() {
  const { chainCode } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [trace, setTrace] = useState([]);
  const [showReviewModal, setShowReviewModal] = useState(false);
  const [reviewForm] = Form.useForm();
  const [reviewTarget, setReviewTarget] = useState(null);

  const user = getStoredUser();
  const isAdminOrTeacher = user && (user.role === 'ADMIN' || user.role === 'TEACHER');

  useEffect(() => {
    if (chainCode) fetchTrace();
  }, [chainCode]);

  const fetchTrace = async () => {
    setLoading(true);
    try {
      const resp = await request.get(`/material-chain/trace/${chainCode}`);
      setTrace(resp.data || []);
    } catch (e) {
      console.error(e);
      message.error('获取追溯链失败');
    } finally {
      setLoading(false);
    }
  };

  if (!chainCode) {
    return (
      <div className="material-trace-page">
        <Alert
          type="warning"
          showIcon
          message="缺少材料链编号"
          description="请从材料列表点击「追溯」按钮进入。"
        />
        <div style={{ marginTop: 16 }}>
          <Button onClick={() => navigate(-1)} icon={<ArrowLeftOutlined />}>返回</Button>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="material-trace-page">
        <Card loading title="加载中..." />
      </div>
    );
  }

  if (trace.length === 0) {
    return (
      <div className="material-trace-page">
        <Alert type="error" showIcon message="未找到对应的材料链记录" />
        <div style={{ marginTop: 16 }}>
          <Button onClick={() => navigate(-1)} icon={<ArrowLeftOutlined />}>返回</Button>
        </div>
      </div>
    );
  }

  const [fileInfo, ...events] = trace;

  const handleReviewClick = (targetStage) => {
    setReviewTarget(targetStage);
    reviewForm.resetFields();
    setShowReviewModal(true);
  };

  const submitReview = async (formValues) => {
    try {
      await request.post('/material-chain/review/' + fileInfo.id, null, {
        params: {
          stage: reviewTarget,
          pass: formValues.pass,
          comment: formValues.comment
        }
      });
      message.success('审核操作已写入材料链日志');
      setShowReviewModal(false);
      fetchTrace();
    } catch (e) {
      console.error(e);
    }
  };

  const renderTimeline = () => {
    const items = [];

    if (fileInfo) {
      items.push({
        color: stageColorMap[fileInfo.uploadStage] || 'blue',
        dot: <SafetyCertificateOutlined style={{ fontSize: 16 }} />,
        children: (
          <div>
            <div style={{ fontWeight: 600, fontSize: 15 }}>
              <Tag color={stageColorMap[fileInfo.uploadStage] || 'default'}>
                {stageLabelMap[fileInfo.uploadStage] || fileInfo.uploadStage}
              </Tag>
              材料当前状态
            </div>
            <div style={{ color: '#666', marginTop: 4, fontSize: 13 }}>
              文件哈希: <code style={{ fontSize: 12, background: '#f5f5f5', padding: '1px 5px', borderRadius: 3 }}>{fileInfo.fileHash || '—'}</code>
              <Divider type="vertical" />
              版本: v{fileInfo.revisionNo || 1}
              <Divider type="vertical" />
              学生: {fileInfo.studentNo}
              <Divider type="vertical" />
              更新: {fileInfo.updatedAt ? new Date(fileInfo.updatedAt).toLocaleString() : '—'}
            </div>
            {fileInfo.tamperDetail && fileInfo.tamperRisk !== 'LOW' && (
              <div style={{ marginTop: 6 }}>
                <Alert
                  type={fileInfo.tamperRisk === 'HIGH' ? 'error' : 'warning'}
                  showIcon
                  message={`篡改检测风险: ${fileInfo.tamperRisk}`}
                  description={fileInfo.tamperDetail}
                  style={{ padding: '4px 12px' }}
                />
              </div>
            )}
            {fileInfo.certDuplicateRisk === 'DUPLICATE' && (
              <div style={{ marginTop: 6 }}>
                <Alert
                  type="error"
                  showIcon
                  message="证书编号查重发现问题"
                  description={fileInfo.certDuplicateDetail}
                  style={{ padding: '4px 12px' }}
                />
              </div>
            )}
          </div>
        )
      });
    }

    for (const ev of events) {
      const meta = eventTypeMap[ev.eventType] || { label: ev.eventType, color: 'gray', icon: <HistoryOutlined /> };
      items.push({
        color: meta.color,
        dot: meta.icon,
        children: (
          <div>
            <div style={{ fontWeight: 500 }}>
              <Tag color={meta.color}>{meta.label}</Tag>
              {ev.revisionNo ? `（v${ev.revisionNo}）` : ''}
              <span style={{ color: '#999', marginLeft: 8 }}>
                {ev.occurredAt ? new Date(ev.occurredAt).toLocaleString() : '—'}
              </span>
            </div>
            <div style={{ color: '#666', fontSize: 13, marginTop: 2 }}>
              <UserOutlined /> 操作人: {ev.operatorName || '—'} {ev.operatorRole ? `(${ev.operatorRole})` : ''}
              {ev.fromStage && ev.toStage && (
                <>
                  <Divider type="vertical" />
                  <HistoryOutlined /> {stageLabelMap[ev.fromStage] || ev.fromStage} → {stageLabelMap[ev.toStage] || ev.toStage}
                </>
              )}
            </div>
            {ev.comment && (
              <div style={{ marginTop: 4, color: '#333', background: '#f9f9f9', padding: '6px 10px', borderRadius: 4, borderLeft: '3px solid #1890ff' }}>
                {ev.comment}
              </div>
            )}
            {ev.fileHashSnapshot && (
              <div style={{ marginTop: 4, color: '#999', fontSize: 12 }}>
                当时文件哈希: <code style={{ fontSize: 11, background: '#f5f5f5', padding: '1px 5px', borderRadius: 3 }}>{ev.fileHashSnapshot}</code>
              </div>
            )}
          </div>
        )
      });
    }

    return items;
  };

  const isReviewOpen = (fileInfo?.uploadStage || '').endsWith('REJECT') === false
    && fileInfo?.uploadStage !== 'FINAL_REVIEW_PASS'
    && fileInfo?.uploadStage !== 'INITIAL_REVIEW_PASS';

  return (
    <div className="material-trace-page">
      <div className="trace-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
          <Button
            ghost
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate(-1)}
            style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.5)' }}
          >
            返回
          </Button>
          <span style={{ fontSize: 14, opacity: 0.9 }}>材料链追溯 · 全程留痕 · 防刷分</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
          <SafetyCertificateOutlined style={{ fontSize: 24 }} />
          <span className="trace-code">{fileInfo.chainCode}</span>
          <Tag color="cyan" style={{ marginTop: 0 }}>v{fileInfo.revisionNo}</Tag>
          <Badge
            count={events.length}
            style={{ backgroundColor: '#fff', color: '#1890ff' }}
            title="操作日志条数"
          />
        </div>
      </div>

      <Row gutter={16}>
        <Col xs={24} lg={10}>
          <Card title={<Space><FileOutlined />材料元数据</Space>} bordered={false} style={{ marginBottom: 16 }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="证书编号">{fileInfo.certNo || '—'}</Descriptions.Item>
              <Descriptions.Item label="证书名称">{fileInfo.certName || '—'}</Descriptions.Item>
              <Descriptions.Item label="原始文件">{fileInfo.fileName || '—'}</Descriptions.Item>
              <Descriptions.Item label="文件类型">{fileInfo.fileType || '—'}</Descriptions.Item>
              <Descriptions.Item label="文件大小">{fileInfo.fileSize ? `${(fileInfo.fileSize / 1024).toFixed(1)} KB` : '—'}</Descriptions.Item>
              <Descriptions.Item label="文件 SHA-256">
                <code style={{ fontSize: 11, wordBreak: 'break-all' }}>{fileInfo.fileHash || '—'}</code>
              </Descriptions.Item>
              <Descriptions.Item label="篡改检测风险">
                <Tag color={fileInfo.tamperRisk === 'HIGH' ? 'red' : fileInfo.tamperRisk === 'MEDIUM' ? 'orange' : 'green'}>
                  {fileInfo.tamperRisk || 'LOW'}
                </Tag>
              </Descriptions.Item>
              {fileInfo.tamperDetail && (
                <Descriptions.Item label="篡改检测详情">{fileInfo.tamperDetail}</Descriptions.Item>
              )}
              <Descriptions.Item label="证书查重">
                <Tag color={fileInfo.certDuplicateRisk === 'DUPLICATE' ? 'red' : 'green'}>
                  {fileInfo.certDuplicateRisk === 'DUPLICATE' ? '重复（高风险）' : '正常'}
                </Tag>
              </Descriptions.Item>
              {fileInfo.certDuplicateDetail && (
                <Descriptions.Item label="查重详情">{fileInfo.certDuplicateDetail}</Descriptions.Item>
              )}
              <Descriptions.Item label="提交学号">{fileInfo.studentNo || '—'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{fileInfo.createdAt ? new Date(fileInfo.createdAt).toLocaleString() : '—'}</Descriptions.Item>
            </Descriptions>

            {fileInfo.fileUrl && (
              <div style={{ marginTop: 12 }}>
                <Button block onClick={() => window.open(fileInfo.fileUrl, '_blank')}>
                  预览原始文件
                </Button>
              </div>
            )}
          </Card>

          {isAdminOrTeacher && isReviewOpen && (
            <Card
              title={<Space><AuditOutlined />快速审核</Space>}
              bordered={false}
              style={{ marginBottom: 16 }}
            >
              <Space direction="vertical" style={{ width: '100%' }}>
                <Space wrap>
                  <Button type="primary" onClick={() => handleReviewClick('INITIAL')}>班委初审</Button>
                  <Button type="primary" onClick={() => handleReviewClick('REEXAMINE')}>复审</Button>
                  <Button type="primary" danger onClick={() => handleReviewClick('FINAL')}>终审</Button>
                </Space>
                <Alert
                  type="info"
                  showIcon
                  message="说明"
                  description="点击任一阶段按钮会弹出审核窗口，选择【通过/驳回】并填写意见后，操作将被写入材料链日志，不可删除。"
                />
              </Space>
            </Card>
          )}
        </Col>

        <Col xs={24} lg={14}>
          <Card
            title={<Space><HistoryOutlined />完整追溯链（{events.length + 1} 条记录）</Space>}
            bordered={false}
          >
            <Timeline items={renderTimeline()} />
          </Card>
        </Col>
      </Row>

      <Modal
        title={`${reviewTarget || ''}审核`}
        open={showReviewModal}
        onCancel={() => setShowReviewModal(false)}
        onOk={() => reviewForm.submit()}
        destroyOnClose
      >
        <Form
          form={reviewForm}
          layout="vertical"
          onFinish={submitReview}
          initialValues={{ pass: 'true' }}
        >
          <Form.Item label="审核结果" name="pass" rules={[{ required: true, message: '请选择审核结果' }]}>
            <Select>
              <Select.Option value="true">通过</Select.Option>
              <Select.Option value="false">驳回</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item label="审核意见" name="comment">
            <Input.TextArea rows={3} placeholder="选填，建议填写驳回原因或通过说明" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export default MaterialTrace;
