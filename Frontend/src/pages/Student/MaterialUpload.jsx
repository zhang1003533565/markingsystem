import { useState } from 'react';
import { Card, Upload, Input, Button, Table, Tag, Space, Alert, message, Divider, Tooltip, Descriptions, Badge } from 'antd';
import { UploadOutlined, SafetyCertificateOutlined, SearchOutlined, FileOutlined, CheckCircleOutlined, WarningOutlined, ReloadOutlined, EyeOutlined } from '@ant-design/icons';
</parameter>
import request from '../../utils/request';
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

const riskColorMap = {
  LOW: 'green',
  MEDIUM: 'orange',
  HIGH: 'red',
  CLEAN: 'green',
  DUPLICATE: 'red'
};

const riskLabelMap = {
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
  CLEAN: '正常',
  DUPLICATE: '证书编号重复'
};

function MaterialUpload() {
  const [certNo, setCertNo] = useState('');
  const [certName, setCertName] = useState('');
  const [uploading, setUploading] = useState(false);
  const [fileList, setFileList] = useState([]);
  const [history, setHistory] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  const beforeUpload = () => false;

  const fetchHistory = async () => {
    setLoadingHistory(true);
    try {
      const resp = await request.get('/material-chain/list/student');
      setHistory(resp.data || []);
    } catch (e) {
      console.error(e);
    } finally {
      setLoadingHistory(false);
    }
  };

  const handleUpload = async () => {
    if (fileList.length === 0) {
      message.warning('请先选择要上传的证明材料');
      return;
    }
    setUploading(true);
    const formData = new FormData();
    formData.append('file', fileList[0]);
    if (certNo.trim()) formData.append('certNo', certNo.trim());
    if (certName.trim()) formData.append('certName', certName.trim());
    try {
      const resp = await request.post('/material-chain/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      const d = resp.data;
      message.success('上传成功');
      if (d.tamperRisk && d.tamperRisk !== 'LOW') {
        message.warning(`篡改检测风险: ${d.tamperRisk} — ${d.tamperDetail || ''}`);
      }
      if (d.certDuplicateRisk === 'DUPLICATE') {
        message.error(`证书编号已被他人使用: ${d.certDuplicateDetail}`);
      }
      setCertNo('');
      setCertName('');
      setFileList([]);
      fetchHistory();
    } catch (e) {
      console.error(e);
    } finally {
      setUploading(false);
    }
  };

  const handleModify = async (record) => {
    if (fileList.length === 0) {
      message.warning('请先选择要替换的新文件');
      return;
    }
    setUploading(true);
    const formData = new FormData();
    formData.append('file', fileList[0]);
    formData.append('certNo', record.certNo || '');
    formData.append('certName', record.certName || '');
    try {
      await request.post(`/material-chain/modify/${record.id}`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      message.success('修改已记录，版本号+1');
      setFileList([]);
      fetchHistory();
    } catch (e) {
      console.error(e);
    } finally {
      setUploading(false);
    }
  };

  const columns = [
    {
      title: '材料链编号',
      dataIndex: 'chainCode',
      key: 'chainCode',
      width: 180,
      render: (v) => <code style={{ fontSize: 12 }}>{v}</code>
    },
    {
      title: '证书编号',
      dataIndex: 'certNo',
      key: 'certNo',
      width: 150,
      render: (v) => v || <span style={{ color: '#999' }}>—</span>
    },
    {
      title: '证书名称',
      dataIndex: 'certName',
      key: 'certName',
      width: 160,
      ellipsis: true,
      render: (v) => v || <span style={{ color: '#999' }}>—</span>
    },
    {
      title: '文件',
      dataIndex: 'fileName',
      key: 'fileName',
      width: 160,
      ellipsis: true,
      render: (v, rec) => (
        <Tooltip title={rec.fileHash}>
          <FileOutlined /> {v}
        </Tooltip>
      )
    },
    {
      title: '阶段',
      dataIndex: 'uploadStage',
      key: 'uploadStage',
      width: 130,
      render: (v) => <Tag color={stageColorMap[v] || 'default'}>{stageLabelMap[v] || v}</Tag>
    },
    {
      title: '篡改风险',
      dataIndex: 'tamperRisk',
      key: 'tamperRisk',
      width: 100,
      render: (v) => <Tag color={riskColorMap[v] || 'default'}>{riskLabelMap[v] || v}</Tag>
    },
    {
      title: '证书查重',
      dataIndex: 'certDuplicateRisk',
      key: 'certDuplicateRisk',
      width: 130,
      render: (v) => (
        <Tag color={riskColorMap[v] || 'default'}>
          {v === 'DUPLICATE' ? <WarningOutlined /> : <CheckCircleOutlined />} {riskLabelMap[v]}
        </Tag>
      )
    },
    {
      title: '版本',
      dataIndex: 'revisionNo',
      key: 'revisionNo',
      width: 80,
      align: 'center',
      render: (v) => <Badge count={v} style={{ backgroundColor: '#1890ff' }} />
    },
    {
      title: '上传时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (v) => v ? new Date(v).toLocaleString() : '—'
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      fixed: 'right',
      render: (_, rec) => (
        <Space size={4}>
          <Button size="small" icon={<EyeOutlined />} onClick={() => window.open(`/admin/material-trace/${rec.chainCode}`, '_blank')}>追溯</Button>
          <Button
            size="small"
            onClick={() => {
              const url = rec.fileUrl;
              if (url.startsWith('/api')) {
                window.open(url, '_blank');
              } else {
                window.open(url, '_blank');
              }
            }}
          >预览</Button>
        </Space>
      )
    }
  ];

  return (
    <div className="material-upload-page" style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <Card
        title={
          <Space>
            <SafetyCertificateOutlined style={{ color: '#1890ff' }} />
            <span>材料链 · 综测证明上传（防刷分 · 全程留痕）</span>
          </Space>
        }
        bordered={false}
        style={{ marginBottom: 16 }}
      >
        <Alert
          message="上传说明"
          description="每次上传都会生成一条唯一材料链编号（MC-xxx），记录证书编号、文件 SHA-256 哈希、篡改检测结果和每一步操作日志；多人共用证书编号将被自动识别为高风险。"
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
        />

        <Upload
          listType="text"
          beforeUpload={beforeUpload}
          fileList={fileList}
          onChange={({ fileList: newList }) => setFileList(newList.slice(-1))}
          accept=".jpg,.jpeg,.png,.pdf,.bmp,.webp"
          multiple={false}
        >
          <Button icon={<UploadOutlined />}>选择证明材料</Button>
          <span style={{ color: '#999', marginLeft: 8 }}>支持图片 / PDF（单文件 ≤ 50MB）</span>
        </Upload>

        <Divider />

        <Descriptions column={2} size="small" bordered>
          <Descriptions.Item label="证书编号">
            <Input
              placeholder="例如：CET6-202406-123456"
              value={certNo}
              onChange={(e) => setCertNo(e.target.value)}
              allowClear
            />
          </Descriptions.Item>
          <Descriptions.Item label="证书名称">
            <Input
              placeholder="例如：全国大学英语六级考试成绩报告单"
              value={certName}
              onChange={(e) => setCertName(e.target.value)}
              allowClear
            />
          </Descriptions.Item>
        </Descriptions>

        <div style={{ marginTop: 16, textAlign: 'right' }}>
          <Space>
            <Button
              loading={uploading}
              type="primary"
              icon={<UploadOutlined />}
              onClick={handleUpload}
              disabled={fileList.length === 0}
            >
              提交到材料链
            </Button>
          </Space>
        </div>
      </Card>

      <Card
        title={
          <Space>
            <FileOutlined />
            <span>我的材料链记录</span>
          </Space>
        }
        extra={
          <Button icon={<ReloadOutlined />} onClick={fetchHistory} loading={loadingHistory}>刷新</Button>
        }
        bordered={false}
      >
        <Table
          rowKey="id"
          dataSource={history}
          columns={columns}
          loading={loadingHistory}
          scroll={{ x: 1300 }}
          pagination={{ pageSize: 10, showSizeChanger: false }}
        />
      </Card>
    </div>
  );
}

export default MaterialUpload;
