import { Alert, Button, Input, Modal, Space, Typography, Upload } from 'antd'
import { FileTextOutlined, InfoCircleOutlined, UploadOutlined } from './AcademicIcons'

const { Text, Title } = Typography
const { TextArea } = Input

const isVisibleTemplateItem = (item) =>
  item &&
  item.status !== 'inactive' &&
  String(item.itemName || '').trim() &&
  (item.levelRules || []).some(
    (rule) =>
      String(rule?.levelName || '').trim() &&
      (rule.authorRules || []).some((authorRule) => String(authorRule?.authorLabel || '').trim()),
  )

const normalizeVisibleTemplateGroups = (groups = []) =>
  groups
    .map((group) => ({
      ...group,
      items: (group.items || []).filter(isVisibleTemplateItem),
    }))
    .filter((group) => String(group.groupName || '').trim() && group.items.length)

function StudentExtraScoreFormModal({
  open,
  title,
  editingRecord,
  currentTemplate,
  templateGroups,
  formState,
  setFormState,
  submitting,
  onCancel,
  onSubmit,
  canSubmitNow,
}) {
  const visibleTemplateGroups = normalizeVisibleTemplateGroups(templateGroups)
  const selectedItem = formState.itemId
    ? visibleTemplateGroups
        .flatMap((group) => group.items || [])
        .find((item) => Number(item.id) === Number(formState.itemId))
    : null
  const levelOptions = selectedItem?.levelRules || []
  const selectedLevelRule =
    levelOptions.find((rule) => Number(rule.id) === Number(formState.levelRuleId)) || null
  const authorOptions = selectedLevelRule?.authorRules || []
  const selectedAuthorRule =
    authorOptions.find((rule) => Number(rule.id) === Number(formState.authorRuleId)) || null
  const selectedDirection = selectedItem?.scoreDirection || 'add'
  const calculatedScore =
    selectedLevelRule && selectedAuthorRule
      ? Number(
          ((selectedAuthorRule.mode || 'ratio') === 'fixed'
            ? Number(selectedAuthorRule.fixedScore || 0)
            : (Number(selectedLevelRule.score || 0) * Number(selectedAuthorRule.ratio || 0)) / 100
          ).toFixed(2),
        )
      : null
  const scoreDescription =
    selectedLevelRule && selectedAuthorRule
      ? (selectedAuthorRule.mode || 'ratio') === 'fixed'
        ? `固定分值 ${selectedAuthorRule.fixedScore || 0}`
        : `基础分 ${selectedLevelRule.score} × 作者比例 ${selectedAuthorRule.ratio || 0}%`
      : '系统将按模板规则自动计算，不需要手动填写分值。'

  return (
    <Modal
      title={null}
      open={open}
      onCancel={onCancel}
      footer={null}
      width={860}
      destroyOnHidden={false}
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Title level={4} style={{ margin: 0 }}>
            {editingRecord ? `编辑${title}申请` : `提交${title}申请`}
          </Title>
          <Button onClick={onCancel}>返回列表</Button>
        </div>

        {currentTemplate ? (
          <Alert
            type="info"
            showIcon
            icon={<InfoCircleOutlined />}
            title={`按模板“${currentTemplate.name}”提交`}
          />
        ) : null}

        <div>
          <Text strong>规则大类 / 小类</Text>
          <div style={{ display: 'grid', gap: 12, marginTop: 8 }}>
            <select
              value={formState.itemId || ''}
              onChange={(e) => {
                const nextItemId = e.target.value || null
                setFormState((prev) => ({
                  ...prev,
                  itemId: nextItemId,
                  levelRuleId: null,
                  authorRuleId: null,
                }))
              }}
            >
              <option value="">请选择小类</option>
              {visibleTemplateGroups.map((group) => (
                <optgroup key={group.id} label={group.groupName}>
                  {(group.items || []).map((item) => (
                    <option key={item.id} value={item.id}>
                      {group.groupName} / {item.itemName}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>

            <select
              value={formState.levelRuleId || ''}
              onChange={(e) =>
                setFormState((prev) => ({
                  ...prev,
                  levelRuleId: e.target.value || null,
                  authorRuleId: null,
                }))
              }
              disabled={!selectedItem}
            >
              <option value="">请选择级别规则</option>
              {levelOptions.map((rule) => (
                <option key={rule.id} value={rule.id}>
                  {rule.levelName}
                </option>
              ))}
            </select>

            <select
              value={formState.authorRuleId || ''}
              onChange={(e) =>
                setFormState((prev) => ({ ...prev, authorRuleId: e.target.value || null }))
              }
              disabled={!selectedLevelRule}
            >
              <option value="">请选择作者规则</option>
              {authorOptions.map((rule) => (
                <option key={rule.id} value={rule.id}>
                  {rule.authorLabel} /{' '}
                  {(rule.mode || 'ratio') === 'fixed'
                    ? `${rule.fixedScore || 0} 分`
                    : `${rule.ratio || 0}%`}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <Text strong>证书文件</Text>
          <Upload
            beforeUpload={() => false}
            fileList={formState.certFileList}
            onChange={({ fileList }) =>
              setFormState((prev) => ({ ...prev, certFileList: fileList.slice(-1) }))
            }
            maxCount={1}
          >
            <Button icon={<FileTextOutlined />}>选择证书文件</Button>
          </Upload>
        </div>

        <div>
          <Text strong>证书名称</Text>
          <Input
            value={formState.certName}
            onChange={(e) => setFormState((prev) => ({ ...prev, certName: e.target.value }))}
            placeholder="例如：全国大学生英语竞赛二等奖"
          />
        </div>

        <div>
          <Text strong>获得时间</Text>
          <Input
            type="date"
            value={formState.awardTime}
            onChange={(e) => setFormState((prev) => ({ ...prev, awardTime: e.target.value }))}
          />
        </div>

        <Alert
          type={calculatedScore ? 'success' : 'info'}
          showIcon
          title={
            calculatedScore
              ? `自动计算${selectedDirection === 'deduct' ? '减分' : '加分'}：${calculatedScore} 分`
              : '请选择小类、级别规则和作者规则后自动计算分值'
          }
          description={scoreDescription}
        />

        <div>
          <Text strong>具体说明</Text>
          <TextArea
            rows={4}
            value={formState.detailDesc}
            onChange={(e) => setFormState((prev) => ({ ...prev, detailDesc: e.target.value }))}
            placeholder="请描述获奖、活动或竞赛的具体情况"
          />
        </div>

        <div>
          <Text strong>凭证文字说明</Text>
          <TextArea
            rows={3}
            value={formState.proofText}
            onChange={(e) => setFormState((prev) => ({ ...prev, proofText: e.target.value }))}
            placeholder="例如：附上获奖证书、成绩单、活动照片等"
          />
        </div>

        <div>
          <Text strong>凭证图片</Text>
          <Upload
            listType="text"
            beforeUpload={() => false}
            fileList={formState.proofImageList}
            onChange={({ fileList }) =>
              setFormState((prev) => ({ ...prev, proofImageList: fileList }))
            }
            multiple
          >
            <Button icon={<UploadOutlined />}>上传凭证图片</Button>
          </Upload>
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
          <Button onClick={onCancel}>取消</Button>
          <Button type="primary" loading={submitting} onClick={onSubmit} disabled={!canSubmitNow}>
            {editingRecord ? '重新提交' : '提交申请'}
          </Button>
        </div>
      </Space>
    </Modal>
  )
}

export default StudentExtraScoreFormModal
