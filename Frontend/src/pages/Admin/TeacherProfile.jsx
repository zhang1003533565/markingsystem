import { useEffect, useMemo, useRef, useState } from 'react';
import {
  BankOutlined,
  CalendarOutlined,
  IdcardOutlined,
  LockOutlined,
  MailOutlined,
  MobileOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  UserOutlined
} from '../../components/AcademicIcons';
import { Avatar, Button, Card, Col, Descriptions, Divider, Form, Input, Modal, Row, Skeleton, Space, Tag, Typography, message } from 'antd';
import { getClassOptions } from '../../api/admin/class';
import { getCurrentAdminUser, updateCurrentUserEmailConfig, uploadAdminAvatar, updateCurrentUserPhone } from '../../api/admin/user';
import { changeCurrentUserPassword } from '../../api/account';
import { getStoredUser, updateStoredUser, withAuthToken } from '../../utils/authStorage';
import '../Student/Profile.css';
import './TeacherProfile.css';

const { Title, Text } = Typography;

const formatDateTime = (value) => {
  if (!value) {
    return '-';
  }
  return String(value).replace('T', ' ').slice(0, 19);
};

function TeacherProfile() {
  const [storedUser] = useState(() => getStoredUser());
  const [profile, setProfile] = useState(null);
  const [classes, setClasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [savingEmail, setSavingEmail] = useState(false);
  const [emailForm] = Form.useForm();
  const smtpSectionRef = useRef(null);
  const avatarInputRef = useRef(null);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [passwordModalVisible, setPasswordModalVisible] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);
  const [passwordForm] = Form.useForm();
  const [phoneModalVisible, setPhoneModalVisible] = useState(false);
  const [savingPhone, setSavingPhone] = useState(false);
  const [phoneForm] = Form.useForm();

  useEffect(() => {
    Promise.all([
      getCurrentAdminUser().catch(() => null),
      getClassOptions().catch(() => [])
    ])
      .then(([profileRes, classRes]) => {
        const nextProfile = profileRes?.data || profileRes || null;
        setProfile(nextProfile);
        if (nextProfile?.email) {
          emailForm.setFieldsValue({ email: nextProfile.email });
        }
        setClasses(Array.isArray(classRes?.data) ? classRes.data : (Array.isArray(classRes) ? classRes : []));
      })
      .catch(() => {
        message.error('教师信息加载失败');
      })
      .finally(() => {
        setLoading(false);
      });
  }, [emailForm]);

  const currentUser = profile || storedUser;
  const displayName = currentUser.nickname || currentUser.username || '教师';
  const teacherNo = currentUser.username || '-';
  const email = currentUser.email || '未绑定';
  const phone = currentUser.phone || '未填写';
  const avatarUrl = currentUser.avatarUrl || null;
  const teacherId = currentUser.teacherId || currentUser.id || storedUser.teacherId || storedUser.id;
  const managedClassNames = useMemo(() => classes.map((item) => item.name).filter(Boolean), [classes]);

  const roleLabel = currentUser.role === 'ADMIN' ? '系统管理员' : '教师';
  const accountStatus = currentUser.passwordResetRequired ? '待重置密码' : '正常';
  const emailConfigured = Boolean(currentUser.emailConfigured);

  const handleSaveEmailConfig = async (values) => {
    setSavingEmail(true);
    try {
      const res = await updateCurrentUserEmailConfig({
        email: values.email?.trim(),
        password: values.password
      });
      const nextProfile = res?.data || res || null;
      setProfile(nextProfile);
      emailForm.setFieldsValue({ email: nextProfile?.email || values.email, password: '' });
      message.success('SMTP发信配置已保存');
    } catch (error) {
      message.error(error?.response?.data?.message || error?.message || 'SMTP发信配置保存失败');
    } finally {
      setSavingEmail(false);
    }
  };

  const handleAvatarClick = () => {
    avatarInputRef.current?.click();
  };

  const handleAvatarChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      message.error('头像图片不能超过 5MB');
      e.target.value = '';
      return;
    }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      message.error('头像仅支持 JPG、PNG、WEBP 图片');
      e.target.value = '';
      return;
    }
    setUploadingAvatar(true);
    try {
      const res = await uploadAdminAvatar(file);
      const newAvatarUrl = res?.data?.avatarUrl;
      if (newAvatarUrl) {
        setProfile((prev) => ({ ...prev, avatarUrl: newAvatarUrl }));
        updateStoredUser({ avatarUrl: newAvatarUrl });
      }
      message.success('头像上传成功');
    } catch (error) {
      message.error(error?.response?.data?.message || error?.message || '头像上传失败');
    } finally {
      setUploadingAvatar(false);
      e.target.value = '';
    }
  };

  const handleChangePassword = async (values) => {
    setChangingPassword(true);
    try {
      await changeCurrentUserPassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword
      });
      message.success('密码修改成功，下次登录请使用新密码');
      setPasswordModalVisible(false);
      passwordForm.resetFields();
    } catch (error) {
      message.error(error?.response?.data?.message || error?.message || '密码修改失败');
    } finally {
      setChangingPassword(false);
    }
  };

  const handleSavePhone = async (values) => {
    setSavingPhone(true);
    try {
      const res = await updateCurrentUserPhone({ phone: values.phone?.trim() || '' });
      const nextProfile = res?.data || res || null;
      setProfile(nextProfile);
      updateStoredUser({ phone: values.phone?.trim() || '' });
      message.success('手机号保存成功');
      setPhoneModalVisible(false);
      phoneForm.resetFields();
    } catch (error) {
      message.error(error?.response?.data?.message || error?.message || '手机号保存失败');
    } finally {
      setSavingPhone(false);
    }
  };

  return (
    <div className="section profile-container teacher-profile-container">
      <Card className="profile-info-card glass-effect teacher-profile-hero" variant="borderless">
        {loading ? (
          <Skeleton active avatar paragraph={{ rows: 4 }} />
        ) : (
          <Row gutter={24} align="middle">
            <Col xs={24} sm={8} md={5}>
              <div className="profile-avatar-section teacher-avatar-section">
                <div
                  className="profile-avatar-wrapper"
                  style={{ cursor: 'pointer', position: 'relative' }}
                  onClick={handleAvatarClick}
                  title="点击更换头像"
                >
                  <Avatar
                    size={112}
                    src={withAuthToken(avatarUrl) ? withAuthToken(avatarUrl) : undefined}
                    icon={!avatarUrl ? <UserOutlined style={{ fontSize: 54, color: '#1d4ed8' }} /> : undefined}
                    className="profile-avatar teacher-profile-avatar"
                  />
                  {uploadingAvatar && (
                    <div style={{
                      position: 'absolute', inset: 0, borderRadius: '50%',
                      background: 'rgba(0,0,0,0.45)', display: 'grid', placeItems: 'center',
                      color: '#fff', fontSize: 13
                    }}>
                      上传中...
                    </div>
                  )}
                  {!uploadingAvatar && (
                    <div style={{
                      position: 'absolute', bottom: 0, left: '50%', transform: 'translateX(-50%)',
                      background: 'rgba(0,0,0,0.55)', color: '#fff', fontSize: 11,
                      padding: '2px 10px', borderRadius: '0 0 56px 56px', whiteSpace: 'nowrap'
                    }}>
                      更换头像
                    </div>
                  )}
                </div>
                <input
                  ref={avatarInputRef}
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  style={{ display: 'none' }}
                  onChange={handleAvatarChange}
                />
              </div>
            </Col>
            <Col xs={24} sm={16} md={19}>
              <div className="profile-user-info">
                <Title level={3} style={{ marginBottom: 8 }}>{displayName}</Title>
                <Text type="secondary" style={{ display: 'block', marginBottom: 16, fontSize: 16 }}>
                  {roleLabel} / 账号 {teacherNo}
                </Text>
                <div className="profile-basic-info">
                  <Space size={12} wrap>
                    <Tag color="#2563eb" icon={<IdcardOutlined />}>教师ID {teacherId || '-'}</Tag>
                    <Tag color="#0f766e" icon={<MailOutlined />}>{email}</Tag>
                    <Tag color="#7c3aed" icon={<MobileOutlined />}>{phone}</Tag>
                    <Tag color={emailConfigured ? 'success' : 'warning'} icon={<MailOutlined />}>
                      {emailConfigured ? 'SMTP已配置' : 'SMTP未配置'}
                    </Tag>
                    <Tag color={currentUser.passwordResetRequired ? 'warning' : 'success'} icon={<SafetyCertificateOutlined />}>
                      {accountStatus}
                    </Tag>
                  </Space>
                </div>
                <Space size={12} style={{ marginTop: 16 }}>
                  <Button size="small" onClick={() => setPasswordModalVisible(true)}>修改密码</Button>
                  <Button size="small" onClick={() => {
                    smtpSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
                  }}>
                    {currentUser.email ? '修改邮箱' : '绑定邮箱'}
                  </Button>
                  <Button size="small" icon={<MobileOutlined />} onClick={() => {
                    phoneForm.setFieldsValue({ phone: currentUser.phone || '' });
                    setPhoneModalVisible(true);
                  }}>
                    {currentUser.phone ? '修改手机' : '绑定手机'}
                  </Button>
                </Space>
              </div>
            </Col>
          </Row>
        )}
      </Card>

      <Card className="profile-archive-card glass-effect" variant="borderless">
        <Title level={3} style={{ marginBottom: 24 }}>
          <IdcardOutlined style={{ marginRight: 12, color: '#2563eb' }} />
          教师档案信息
        </Title>
        <Descriptions
          bordered
          column={{ xs: 1, sm: 1, md: 1, lg: 2 }}
          className="profile-descriptions-modern"
          size="middle"
        >
          <Descriptions.Item label={<span><UserOutlined /> 姓名</span>}>
            <Text strong>{displayName}</Text>
          </Descriptions.Item>
          <Descriptions.Item label={<span><IdcardOutlined /> 登录账号</span>}>
            {teacherNo}
          </Descriptions.Item>
          <Descriptions.Item label={<span><BankOutlined /> 角色</span>}>
            {roleLabel}
          </Descriptions.Item>
          <Descriptions.Item label={<span><MailOutlined /> 邮箱</span>}>
            {email}
          </Descriptions.Item>
          <Descriptions.Item label={<span><MobileOutlined /> 手机号</span>}>
            <Space>
              <Text>{phone}</Text>
              <Button type="link" size="small" onClick={() => {
                phoneForm.setFieldsValue({ phone: currentUser.phone || '' });
                setPhoneModalVisible(true);
              }}>
                {currentUser.phone ? '修改' : '绑定'}
              </Button>
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label={<span><MailOutlined /> SMTP</span>}>
            <Tag color={emailConfigured ? 'success' : 'warning'}>{emailConfigured ? '已配置授权码' : '未配置授权码'}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label={<span><TeamOutlined /> 负责班级</span>}>
            {managedClassNames.length ? managedClassNames.join('、') : '暂未分配'}
          </Descriptions.Item>
          <Descriptions.Item label={<span><CalendarOutlined /> 创建时间</span>}>
            {formatDateTime(currentUser.createdAt)}
          </Descriptions.Item>
          <Descriptions.Item label={<span><CalendarOutlined /> 更新时间</span>}>
            {formatDateTime(currentUser.updatedAt)}
          </Descriptions.Item>
          <Descriptions.Item label={<span><SafetyCertificateOutlined /> 账号状态</span>}>
            <Tag color={currentUser.passwordResetRequired ? 'warning' : 'success'}>{accountStatus}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label={<span><LockOutlined /> 登录密码</span>}>
            <Space>
              <Tag color={currentUser.passwordResetRequired ? 'warning' : 'success'}>
                {currentUser.passwordResetRequired ? '需要重置' : '正常'}
              </Tag>
              <Button type="link" size="small" onClick={() => setPasswordModalVisible(true)}>修改密码</Button>
            </Space>
          </Descriptions.Item>
        </Descriptions>

        <Divider style={{ margin: '32px 0' }} />

        <div ref={smtpSectionRef}>
          <Title level={4}>
            <Space>
              <MailOutlined style={{ color: '#2563eb' }} />
              <span>SMTP发信配置</span>
            </Space>
          </Title>
          <Text type="secondary" className="teacher-smtp-help">
            用于以教师邮箱发送公告和催读邮件。当前沿用系统 SMTP 服务器配置，仅需填写邮箱地址和授权码。
          </Text>
          <Form
            form={emailForm}
            layout="vertical"
            className="teacher-smtp-form"
            onFinish={handleSaveEmailConfig}
            initialValues={{ email: currentUser.email || '' }}
          >
            <Form.Item
              label="发信邮箱"
              name="email"
              rules={[
                { required: true, message: '请输入发信邮箱' },
                { type: 'email', message: '请输入正确的邮箱地址' }
              ]}
            >
              <Input prefix={<MailOutlined />} placeholder="例如：teacher@qq.com" />
            </Form.Item>
            <Form.Item
              label="SMTP授权码"
              name="password"
              extra={emailConfigured ? '已配置过授权码；重新保存会覆盖原授权码。' : '请填写邮箱服务商生成的 SMTP 授权码，不是登录密码。'}
              rules={[{ required: true, message: '请输入SMTP授权码' }]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="请输入SMTP授权码" />
            </Form.Item>
            <Space wrap>
              <Button type="primary" htmlType="submit" loading={savingEmail}>
                保存SMTP配置
              </Button>
              <Tag color={emailConfigured ? 'success' : 'warning'}>
                {emailConfigured ? '教师邮箱可用于发信' : '教师邮箱暂不可用于发信'}
              </Tag>
            </Space>
          </Form>
        </div>
      </Card>

      <Modal
        title="修改密码"
        open={passwordModalVisible}
        onCancel={() => { setPasswordModalVisible(false); passwordForm.resetFields(); }}
        footer={null}
        destroyOnHidden
      >
        <Form form={passwordForm} layout="vertical" onFinish={handleChangePassword}>
          <Form.Item
            label="当前密码"
            name="oldPassword"
            rules={[{ required: true, message: '请输入当前密码' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="请输入当前密码" />
          </Form.Item>
          <Form.Item
            label="新密码"
            name="newPassword"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, message: '密码至少6位' }
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="请输入新密码" />
          </Form.Item>
          <Form.Item
            label="确认新密码"
            name="confirmPassword"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请确认新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'));
                }
              })
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="再次输入新密码" />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={changingPassword}>确认修改</Button>
              <Button onClick={() => { setPasswordModalVisible(false); passwordForm.resetFields(); }}>取消</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={currentUser.phone ? '修改手机号' : '绑定手机号'}
        open={phoneModalVisible}
        onCancel={() => { setPhoneModalVisible(false); phoneForm.resetFields(); }}
        footer={null}
        destroyOnHidden
      >
        <Form form={phoneForm} layout="vertical" onFinish={handleSavePhone}>
          <Form.Item
            label="手机号码"
            name="phone"
            rules={[
              { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码' }
            ]}
            extra="手机号将对学生可见，方便学生联系老师。留空则清除手机号。"
          >
            <Input prefix={<MobileOutlined />} placeholder="请输入手机号码" maxLength={11} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={savingPhone}>保存</Button>
              <Button onClick={() => { setPhoneModalVisible(false); phoneForm.resetFields(); }}>取消</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export default TeacherProfile;
