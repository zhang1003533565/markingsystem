import { useEffect, useState } from 'react';
import {
  EditOutlined,
  UserOutlined,
  LockOutlined,
  IdcardOutlined,
  BookOutlined,
  BankOutlined,
  TeamOutlined,
  SafetyCertificateOutlined,
  MobileOutlined,
  MailOutlined,
  UploadOutlined
} from '../../components/AcademicIcons';
import { Modal, Form, Input, Upload, message, Skeleton } from 'antd';
import StudentLayout from '../../components/StudentLayout';
import EChartPanel from '../../components/EChartPanel';
import { getStudentByNo } from '../../api/user/student';
import {
  getStudentProfileAnalysis,
  changeStudentPassword,
  uploadStudentAvatar,
  updateStudentProfile,
  sendStudentEmailVerificationCode,
  bindStudentEmail
} from '../../api/student/portal';
import { getStoredUser, setStoredUser, withAuthToken } from '../../utils/authStorage';
import './Profile.css';

const formatScore = (value) => Number(value || 0).toFixed(2);
const clampRadarValue = (value) => Math.max(0, Math.min(100, Number(value || 0)));
const REASON_PREVIEW_COUNT = 3;

const RADAR_KEYS = ['思想品德', '发展潜力', '综合素质', '技能素养', '文体素养', '实践服务', '科研创新', '学业成绩'];

function formatAnalysisTime(value) {
  if (!value) return '';
  return String(value).replace('T', ' ').slice(0, 16);
}

function getInitials(name) {
  const text = String(name || '').trim();
  if (!text) return '学';
  return text.slice(0, 1);
}

function isEmptyValue(value) {
  const text = String(value || '').trim();
  return !text || text === '未填写' || text === '未绑定' || text === '未提供' || text === '未维护' || text === '未分配';
}

const validatePasswordStrength = (value) => {
  if (!value) {
    return '请输入新密码';
  }
  if (value.length < 8 || value.length > 16) {
    return '密码长度必须为8-16位';
  }
  if (!/[a-z]/.test(value)) {
    return '密码必须包含小写字母';
  }
  if (!/[A-Z]/.test(value)) {
    return '密码必须包含大写字母';
  }
  if (!/\d/.test(value)) {
    return '密码必须包含数字';
  }
  if (!/[^A-Za-z0-9]/.test(value)) {
    return '密码必须包含特殊符号';
  }
  for (let index = 0; index < value.length - 2; index += 1) {
    const segment = value.slice(index, index + 3);
    if (!/^\d{3}$/.test(segment)) {
      continue;
    }
    const digits = segment.split('').map(Number);
    if ((digits[1] === digits[0] + 1 && digits[2] === digits[1] + 1)
      || (digits[1] === digits[0] - 1 && digits[2] === digits[1] - 1)) {
      return '密码中的数字不能连续递增或递减';
    }
  }
  return '';
};

function StudentProfile() {
  const [user, setUser] = useState({});
  const [studentProfile, setStudentProfile] = useState(null);
  const [isPasswordModalVisible, setIsPasswordModalVisible] = useState(false);
  const [isEmailModalVisible, setIsEmailModalVisible] = useState(false);
  const [isContactModalVisible, setIsContactModalVisible] = useState(false);
  const [form] = Form.useForm();
  const [emailForm] = Form.useForm();
  const [contactForm] = Form.useForm();
  const [updatingContact, setUpdatingContact] = useState(false);
  const [sendingEmailCode, setSendingEmailCode] = useState(false);
  const [bindingEmail, setBindingEmail] = useState(false);
  const [analysis, setAnalysis] = useState(null);
  const [analysisLoading, setAnalysisLoading] = useState(false);
  const [reasonsExpanded, setReasonsExpanded] = useState(false);

  useEffect(() => {
    const storedUser = getStoredUser();
    setUser(storedUser);

    const studentNo = storedUser.studentNo || storedUser.username;
    if (!studentNo) {
      return;
    }

    getStudentByNo(studentNo)
      .then((res) => {
        const profile = res?.data || res || null;
        setStudentProfile(profile);
        const profileUser = profile?.user || {};
        if (profileUser.avatarUrl || profileUser.email) {
          setUser((prev) => {
            const nextUser = {
              ...prev,
              avatarUrl: profileUser.avatarUrl || prev.avatarUrl,
              email: profileUser.email || prev.email
            };
            setStoredUser(nextUser);
            return nextUser;
          });
        }
      })
      .catch(() => {
        setStudentProfile(null);
      });
  }, []);

  useEffect(() => {
    const storedUser = getStoredUser();
    const studentNo = storedUser.studentNo || storedUser.username;
    if (!studentNo) {
      return;
    }

    setAnalysisLoading(true);
    getStudentProfileAnalysis({ studentNo })
      .then((res) => {
        setAnalysis(res?.data || null);
      })
      .catch(() => {
        setAnalysis(null);
      })
      .finally(() => {
        setAnalysisLoading(false);
      });
  }, []);

  const handleAvatarUpload = async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    try {
      const res = await uploadStudentAvatar(formData);
      const avatarUrl = res?.data?.avatarUrl;
      if (!avatarUrl) {
        message.error('头像上传失败：未返回头像地址');
        return;
      }
      setUser((prev) => {
        const nextUser = { ...prev, avatarUrl };
        setStoredUser(nextUser);
        return nextUser;
      });
      setStudentProfile((prev) => (prev ? { ...prev, user: { ...(prev.user || {}), avatarUrl } } : prev));
      message.success('头像上传成功');
    } catch (error) {
      message.error(error?.response?.data?.message || '头像上传失败');
    }
  };

  const uploadProps = {
    name: 'file',
    customRequest: async ({ file, onSuccess, onError }) => {
      try {
        await handleAvatarUpload(file);
        onSuccess?.('ok');
      } catch (error) {
        onError?.(error);
      }
    },
    showUploadList: false,
    beforeUpload: (file) => {
      const isImage = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type);
      if (!isImage) {
        message.error('只能上传 JPG、PNG、WEBP 格式的图片');
        return Upload.LIST_IGNORE;
      }
      const isLt5M = file.size / 1024 / 1024 < 5;
      if (!isLt5M) {
        message.error('图片必须小于 5MB');
        return Upload.LIST_IGNORE;
      }
      return true;
    }
  };

  const handlePasswordSubmit = async (values) => {
    if (values.newPassword !== values.confirmPassword) {
      message.error('两次输入的新密码不一致');
      return;
    }
    try {
      const res = await changeStudentPassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword
      });
      if (res.code === 200) {
        message.success('密码修改成功，请妥善保管新密码');
        setIsPasswordModalVisible(false);
        form.resetFields();
      } else {
        message.error(res.message || '密码修改失败');
      }
    } catch (error) {
      message.error(error?.response?.data?.message || '密码修改失败');
    }
  };

  const openEmailModal = () => {
    emailForm.setFieldsValue({
      email: studentProfile?.user?.email || user?.email || '',
      code: ''
    });
    setIsEmailModalVisible(true);
  };

  const openContactModal = () => {
    contactForm.setFieldsValue({
      phone: studentProfile?.phone || ''
    });
    setIsContactModalVisible(true);
  };

  const handleContactSubmit = async (values) => {
    if (!studentProfile?.id) {
      message.error('学生信息缺少ID，无法保存联系方式');
      return;
    }
    setUpdatingContact(true);
    try {
      await updateStudentProfile(studentProfile.id, {
        phone: values.phone?.trim() || ''
      });
      setStudentProfile((prev) => (prev ? { ...prev, phone: values.phone?.trim() || '' } : prev));
      message.success('联系方式已保存');
      setIsContactModalVisible(false);
      contactForm.resetFields();
    } catch (error) {
      message.error(error?.response?.data?.message || '联系方式保存失败');
    } finally {
      setUpdatingContact(false);
    }
  };

  const handleSendEmailCode = async () => {
    if (sendingEmailCode) {
      message.info('验证码正在发送，请稍候');
      return;
    }
    try {
      const email = emailForm.getFieldValue('email');
      await emailForm.validateFields(['email']);
      setSendingEmailCode(true);
      await sendStudentEmailVerificationCode({ email });
      message.success('验证码已发送，请查看邮箱');
    } catch (error) {
      if (error?.errorFields) return;
      message.error(error?.response?.data?.message || error?.message || '验证码发送失败');
    } finally {
      setSendingEmailCode(false);
    }
  };

  const handleEmailSubmit = async (values) => {
    if (bindingEmail) {
      message.info('正在绑定邮箱，请稍候');
      return;
    }
    setBindingEmail(true);
    try {
      const res = await bindStudentEmail({
        email: values.email,
        code: values.code
      });
      const nextEmail = res?.data?.email || values.email;
      setUser((prev) => {
        const nextUser = { ...prev, email: nextEmail };
        setStoredUser(nextUser);
        return nextUser;
      });
      setStudentProfile((prev) => (prev ? { ...prev, user: { ...(prev.user || {}), email: nextEmail } } : prev));
      message.success('邮箱绑定成功');
      setIsEmailModalVisible(false);
      emailForm.resetFields();
    } catch (error) {
      message.error(error?.response?.data?.message || error?.message || '邮箱绑定失败');
    } finally {
      setBindingEmail(false);
    }
  };

  const displayName = studentProfile?.name || user.nickname || '未命名';
  const studentNo = user.studentNo || user.username || '学号未绑定';
  const teacherName = studentProfile?.teacher?.nickname || studentProfile?.teacher?.username || (user.teacherId ? `老师ID ${user.teacherId}` : '未分配');
  const teacherPhone = studentProfile?.teacher?.phone || '未提供';
  const collegeName = studentProfile?.college || '';
  const majorName = studentProfile?.major || '';
  const className = studentProfile?.className || user.className || '未维护';
  const hasPhone = Boolean(String(studentProfile?.phone || '').trim());
  const phoneText = hasPhone ? studentProfile.phone : '未填写';
  const emailText = studentProfile?.user?.email || user?.email || '未绑定';
  const hasBoundEmail = Boolean(studentProfile?.user?.email || user?.email);
  const avatarUrl = user.avatarUrl || studentProfile?.user?.avatarUrl || '';
  const roleLabel = (user.role === 'CLASS_COMMITTEE') ? '班委' : '学生';
  const needsPhone = !hasPhone;
  const needsEmail = !hasBoundEmail;

  const radarDimensions = analysis?.radarDimensions || {};
  const hasRadarData = RADAR_KEYS.some((key) => radarDimensions[key] != null);

  const radarSorted = hasRadarData
    ? RADAR_KEYS.map((key) => ({ key, value: clampRadarValue(radarDimensions[key]) })).sort((a, b) => b.value - a.value)
    : [];
  const strengthDims = radarSorted.slice(0, 3);
  const weaknessDims = radarSorted.slice(-2).reverse();
  const topMetric = radarSorted[0];
  const analysisReasonSteps = (analysis?.reasons || []).length
    ? analysis.reasons
    : (analysis?.suggestions || []);
  const visibleReasons = reasonsExpanded
    ? analysisReasonSteps
    : analysisReasonSteps.slice(0, REASON_PREVIEW_COUNT);
  const hasMoreReasons = analysisReasonSteps.length > REASON_PREVIEW_COUNT;
  const radarAverage = hasRadarData
    ? radarSorted.reduce((sum, item) => sum + item.value, 0) / radarSorted.length
    : 0;

  const primaryFacts = [
    { icon: <IdcardOutlined />, label: '学号', value: studentNo },
    { icon: <TeamOutlined />, label: '班级', value: className },
    { icon: <UserOutlined />, label: '所属老师', value: teacherName },
    { icon: <MobileOutlined />, label: '老师电话', value: teacherPhone, missing: isEmptyValue(teacherPhone) }
  ];

  const secondaryFacts = [
    collegeName ? { icon: <BankOutlined />, label: '学院', value: collegeName } : null,
    majorName ? { icon: <BookOutlined />, label: '专业', value: majorName } : null,
    { icon: <MobileOutlined />, label: '联系方式', value: phoneText, missing: needsPhone },
    { icon: <MailOutlined />, label: '邮箱', value: emailText, missing: needsEmail }
  ].filter(Boolean);

  const radarOption = {
    tooltip: {
      trigger: 'item',
      formatter: (params) => RADAR_KEYS.map((label, index) => `${label}: ${formatScore(params.value[index])}`).join('<br/>')
    },
    radar: {
      radius: '72%',
      center: ['50%', '52%'],
      splitNumber: 4,
      axisName: {
        color: '#4b5563',
        fontSize: 12,
        fontWeight: 600,
        padding: [2, 4]
      },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      splitLine: { lineStyle: { color: '#eef2f7' } },
      splitArea: {
        areaStyle: {
          color: ['rgba(59, 130, 246, 0.02)', 'rgba(59, 130, 246, 0.06)']
        }
      },
      indicator: RADAR_KEYS.map((key) => ({ name: key, max: 100 }))
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: RADAR_KEYS.map((key) => clampRadarValue(radarDimensions[key])),
            name: '个人得分',
            symbol: 'circle',
            symbolSize: 5,
            lineStyle: { color: '#3b82f6', width: 2.5 },
            itemStyle: { color: '#2563eb' },
            areaStyle: { color: 'rgba(59, 130, 246, 0.18)' }
          }
        ]
      }
    ]
  };

  return (
    <StudentLayout activePath="/student/profile" breadcrumb="学生个人中心 / 个人信息">
      <div className="profile-page" aria-label="个人中心">
        <div className="profile-page-decor" aria-hidden="true">
          <span className="profile-page-blob profile-page-blob-a" />
          <span className="profile-page-blob profile-page-blob-b" />
        </div>

        <section className="profile-panel profile-hero">
          <div className="profile-hero-band">
            <div className="profile-hero-shapes" aria-hidden="true">
              <span className="shape shape-circle" />
              <span className="shape shape-square" />
              <span className="shape shape-ring" />
            </div>
            <div className="profile-hero-identity">
              <Upload {...uploadProps} className="profile-avatar-uploader">
                <button type="button" className="profile-avatar-trigger" aria-label="更换头像">
                  <span className={`profile-avatar${avatarUrl ? '' : ' is-empty'}`}>
                    {avatarUrl ? (
                      <img src={withAuthToken(avatarUrl)} alt="" />
                    ) : (
                      <span>{getInitials(displayName)}</span>
                    )}
                    <span className="profile-avatar-overlay" aria-hidden="true">
                      <UploadOutlined />
                      <em>更换</em>
                    </span>
                  </span>
                  <span className="profile-avatar-hint">
                    <UploadOutlined />
                    更换头像
                  </span>
                </button>
              </Upload>
              <div className="profile-hero-text">
                <div className="profile-name-row">
                  <h1>{displayName}</h1>
                  <span className="profile-role-pill">{roleLabel}</span>
                </div>
                <p className="profile-hero-sub">
                  <span>{studentNo}</span>
                  <span className="profile-hero-dot" aria-hidden="true" />
                  <span>{className}</span>
                  {majorName ? (
                    <>
                      <span className="profile-hero-dot" aria-hidden="true" />
                      <span>{majorName}</span>
                    </>
                  ) : null}
                </p>
              </div>
            </div>
          </div>

          <div className="profile-hero-body">
            <dl className="profile-meta-grid">
              {[...primaryFacts, ...secondaryFacts].map((item) => (
                <div
                  key={item.label}
                  className={`profile-meta-item${item.missing ? ' is-missing' : ''}`}
                >
                  <dt>
                    <span className="profile-meta-icon">{item.icon}</span>
                    {item.label}
                  </dt>
                  <dd title={item.value}>{item.value}</dd>
                </div>
              ))}
            </dl>
          </div>
        </section>

        <section className="profile-panel profile-security">
          <div className="profile-panel-head">
            <div className="profile-panel-title">
              <SafetyCertificateOutlined />
              <h2>联系方式与账户安全</h2>
            </div>
            <p>手机号可直接维护；邮箱用于接收公告和账号通知，绑定或换绑需邮箱验证码。</p>
          </div>

          <div className="profile-security-grid">
            <div className={`profile-security-item${needsPhone ? ' is-attention' : ''}`}>
              <div className="profile-security-icon" aria-hidden="true">
                <MobileOutlined />
              </div>
              <div className="profile-security-copy">
                <span>手机号</span>
                <strong title={phoneText}>{phoneText}</strong>
              </div>
              <button
                type="button"
                className={`profile-action-btn${needsPhone ? ' is-primary' : ''}`}
                onClick={openContactModal}
              >
                <EditOutlined />
                {needsPhone ? '去填写' : '编辑'}
              </button>
            </div>

            <div className={`profile-security-item${needsEmail ? ' is-attention' : ''}`}>
              <div className="profile-security-icon" aria-hidden="true">
                <MailOutlined />
              </div>
              <div className="profile-security-copy">
                <span>邮箱</span>
                <strong title={emailText}>{emailText}</strong>
              </div>
              <button
                type="button"
                className={`profile-action-btn${needsEmail ? ' is-primary' : ''}`}
                onClick={openEmailModal}
              >
                <MailOutlined />
                {hasBoundEmail ? '换绑' : '去绑定'}
              </button>
            </div>

            <div className="profile-security-item">
              <div className="profile-security-icon" aria-hidden="true">
                <LockOutlined />
              </div>
              <div className="profile-security-copy">
                <span>登录密码</span>
                <strong>已设置</strong>
              </div>
              <button type="button" className="profile-action-btn" onClick={() => setIsPasswordModalVisible(true)}>
                <LockOutlined />
                修改
              </button>
            </div>
          </div>

        </section>

        <div className="profile-insight-grid">
          <section className="profile-panel profile-radar">
            <div className="profile-panel-head row">
              <div className="profile-panel-title">
                <h2>八维素质雷达图</h2>
                <span className="profile-soft-badge">AI 画像</span>
              </div>
              <div className="profile-radar-meta">
                {analysis?.generatedAt ? (
                  <span className="profile-radar-time">生成于 {formatAnalysisTime(analysis.generatedAt)}</span>
                ) : null}
                <span className="profile-source-chip">
                  {analysis?.source === 'java-graph+deepseek' ? 'DeepSeek' : (analysis?.source || 'AI 分析')}
                </span>
              </div>
            </div>

            {analysisLoading ? (
              <Skeleton active paragraph={{ rows: 8 }} />
            ) : hasRadarData ? (
              <>
                <div className="profile-radar-chart">
                  <EChartPanel option={radarOption} height={320} />
                </div>
                <div className="profile-radar-metrics">
                  <div className="profile-radar-metric">
                    <span>综合均分</span>
                    <strong>{radarAverage.toFixed(0)}</strong>
                    <em>八维均值</em>
                  </div>
                  <div className="profile-radar-metric">
                    <span>最强维度</span>
                    <strong>{topMetric ? topMetric.value.toFixed(0) : '—'}</strong>
                    <em>{topMetric?.key || '—'}</em>
                  </div>
                  <div className="profile-radar-metric">
                    <span>待提升</span>
                    <strong>{weaknessDims[0] ? weaknessDims[0].value.toFixed(0) : '—'}</strong>
                    <em>{weaknessDims[0]?.key || '—'}</em>
                  </div>
                </div>
              </>
            ) : (
              <div className="profile-empty-state">
                <p>暂无素质画像数据</p>
                <span>完成更多学业与实践记录后，将生成八维雷达分析。</span>
              </div>
            )}
          </section>

          <section className="profile-panel profile-analysis">
            <div className="profile-panel-head row">
              <div className="profile-panel-title">
                <h2>综合分析与建议</h2>
                <span className="profile-soft-badge">AI 建议</span>
              </div>
            </div>

            {analysisLoading ? (
              <Skeleton active paragraph={{ rows: 8 }} />
            ) : hasRadarData ? (
              <div className="profile-analysis-body">
                <div className="profile-analysis-block">
                  <span className="profile-block-label">总体评价</span>
                  <p>{analysis?.summary || '暂无分析摘要'}</p>
                </div>

                <div className="profile-dimension-board">
                  <div className="profile-dimension-col">
                    <span className="profile-block-label">优势维度</span>
                    <div className="profile-dimension-tags">
                      {strengthDims.map((item) => (
                        <span key={item.key} className="profile-dim-tag is-strong">
                          <em>{item.key}</em>
                          <strong>{item.value.toFixed(0)}</strong>
                        </span>
                      ))}
                    </div>
                  </div>
                  <div className="profile-dimension-col">
                    <span className="profile-block-label">待提升维度</span>
                    <div className="profile-dimension-tags">
                      {weaknessDims.map((item) => (
                        <span key={item.key} className="profile-dim-tag is-weak">
                          <em>{item.key}</em>
                          <strong>{item.value.toFixed(0)}</strong>
                        </span>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="profile-analysis-block">
                  <span className="profile-block-label">分析过程</span>
                  {analysisReasonSteps.length ? (
                    <>
                      <ol className="profile-reason-list">
                        {visibleReasons.map((item, index) => (
                          <li key={`analysis-reason-step-${index}`}>
                            <span className="profile-reason-index">{index + 1}</span>
                            <p>{item}</p>
                          </li>
                        ))}
                      </ol>
                      {hasMoreReasons ? (
                        <button
                          type="button"
                          className="profile-reason-toggle"
                          onClick={() => setReasonsExpanded((prev) => !prev)}
                        >
                          {reasonsExpanded
                            ? '收起分析过程'
                            : `展开全部 ${analysisReasonSteps.length} 条`}
                        </button>
                      ) : null}
                    </>
                  ) : (
                    <p className="profile-suggestion-empty">暂无分析原因与过程说明</p>
                  )}
                </div>
              </div>
            ) : (
              <div className="profile-empty-state">
                <p>暂无综合分析</p>
                <span>画像生成后，这里会给出优势维度与提升建议。</span>
              </div>
            )}
          </section>
        </div>

        <Modal
          title={<span><LockOutlined style={{ marginRight: 8, color: '#2563eb' }} />修改密码</span>}
          open={isPasswordModalVisible}
          onCancel={() => {
            setIsPasswordModalVisible(false);
            form.resetFields();
          }}
          onOk={() => form.submit()}
          okText="确认修改"
          cancelText="取消"
          okButtonProps={{ className: 'profile-modal-ok' }}
          destroyOnHidden
          centered
        >
          <Form form={form} layout="vertical" onFinish={handlePasswordSubmit} className="profile-password-form">
            <Form.Item
              name="oldPassword"
              label="原密码"
              rules={[{ required: true, message: '请输入原密码' }]}
            >
              <Input.Password placeholder="请输入当前使用的密码" />
            </Form.Item>
            <Form.Item
              name="newPassword"
              label="新密码"
              rules={[
                { required: true, message: '请输入新密码' },
                {
                  validator(_, value) {
                    const error = validatePasswordStrength(value);
                    return error ? Promise.reject(new Error(error)) : Promise.resolve();
                  }
                }
              ]}
            >
              <Input.Password placeholder="8-16位，含大小写、数字、特殊符号" />
            </Form.Item>
            <Form.Item
              name="confirmPassword"
              label="确认新密码"
              dependencies={['newPassword']}
              rules={[
                { required: true, message: '请再次输入新密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('newPassword') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('两次输入的新密码不一致'));
                  }
                })
              ]}
            >
              <Input.Password placeholder="请再次确认新密码" />
            </Form.Item>
          </Form>
        </Modal>

        <Modal
          title={<span><MobileOutlined style={{ marginRight: 8, color: '#2563eb' }} />编辑联系方式</span>}
          open={isContactModalVisible}
          onCancel={() => {
            setIsContactModalVisible(false);
            contactForm.resetFields();
          }}
          onOk={() => contactForm.submit()}
          okText="保存"
          cancelText="取消"
          confirmLoading={updatingContact}
          okButtonProps={{ className: 'profile-modal-ok' }}
          destroyOnHidden
          centered
        >
          <Form form={contactForm} layout="vertical" onFinish={handleContactSubmit} className="profile-password-form">
            <Form.Item
              name="phone"
              label="手机号"
              rules={[
                { required: true, message: '请输入手机号' },
                { pattern: /^1\d{10}$/, message: '请输入11位手机号' }
              ]}
            >
              <Input placeholder="请输入手机号" maxLength={11} />
            </Form.Item>
          </Form>
        </Modal>

        <Modal
          title={<span><MailOutlined style={{ marginRight: 8, color: '#2563eb' }} />{hasBoundEmail ? '换绑邮箱' : '绑定邮箱'}</span>}
          open={isEmailModalVisible}
          onCancel={() => {
            setIsEmailModalVisible(false);
            emailForm.resetFields();
          }}
          onOk={() => emailForm.submit()}
          okText="确认绑定"
          cancelText="取消"
          confirmLoading={bindingEmail}
          okButtonProps={{ className: 'profile-modal-ok' }}
          destroyOnHidden
          centered
        >
          <Form form={emailForm} layout="vertical" onFinish={handleEmailSubmit} className="profile-password-form">
            {hasBoundEmail ? (
              <div className="profile-current-email">当前邮箱：{emailText}</div>
            ) : null}
            <Form.Item
              name="email"
              label="邮箱地址"
              rules={[
                { required: true, message: '请输入邮箱地址' },
                { type: 'email', message: '邮箱格式不正确' }
              ]}
            >
              <Input placeholder="请输入要绑定的邮箱" />
            </Form.Item>
            <Form.Item
              name="code"
              label="邮箱验证码"
              rules={[
                { required: true, message: '请输入邮箱验证码' },
                { pattern: /^\d{6}$/, message: '请输入6位数字验证码' }
              ]}
            >
              <Input
                placeholder="请输入6位验证码"
                maxLength={6}
                addonAfter={(
                  <button
                    type="button"
                    className="profile-code-btn"
                    onClick={handleSendEmailCode}
                  >
                    {sendingEmailCode ? '发送中' : '获取验证码'}
                  </button>
                )}
              />
            </Form.Item>
          </Form>
        </Modal>
      </div>
    </StudentLayout>
  );
}

export default StudentProfile;
