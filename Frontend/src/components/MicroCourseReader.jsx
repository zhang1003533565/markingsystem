import { useEffect, useMemo } from 'react';
import { Modal, Button } from 'antd';
import {
  BookOutlined,
  CheckCircleOutlined,
  LinkOutlined,
} from '../components/AcademicIcons';
import { FireOutlined } from '@ant-design/icons';
import { resolveResourceOpenLink, isHttpUrl } from '../utils/jobAssistant';
import './MicroCourseReader.css';

/**
 * 极简 Markdown 渲染（无需第三方依赖）：
 * - `#` / `##` / `###` 标题
 * - `- ` / `1. ` 列表
 * - `**加粗**`、`` `代码` ``
 * - 空行分段
 */
function renderMarkdown(content) {
  const source = String(content || '');
  const lines = source.split(/\r?\n/);
  const blocks = [];
  let para = [];
  let list = null;

  const flushPara = () => {
    if (para.length) {
      blocks.push({ type: 'para', text: para.join(' ') });
      para = [];
    }
  };
  const flushList = () => {
    if (list) {
      blocks.push({ type: 'list', items: list });
      list = null;
    }
  };

  // 先做 HTML 转义再套 Markdown 行内标记，防止 AI 输出中夹带的
  // <script> / <img onerror=...> 等标签经 dangerouslySetInnerHTML 执行
  const escapeHtml = (text) =>
    String(text)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');

  const inline = (text) =>
    escapeHtml(text)
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/`([^`]+)`/g, '<code>$1</code>');

  lines.forEach((raw) => {
    const line = raw.trim();
    if (!line) {
      flushPara();
      flushList();
      return;
    }
    if (/^###\s+/.test(line)) {
      flushPara(); flushList();
      blocks.push({ type: 'h3', text: inline(line.replace(/^###\s+/, '')) });
      return;
    }
    if (/^##\s+/.test(line)) {
      flushPara(); flushList();
      blocks.push({ type: 'h2', text: inline(line.replace(/^##\s+/, '')) });
      return;
    }
    if (/^#\s+/.test(line)) {
      flushPara(); flushList();
      blocks.push({ type: 'h1', text: inline(line.replace(/^#\s+/, '')) });
      return;
    }
    const bullet = line.match(/^[-*•]\s+(.+)$/);
    if (bullet) {
      flushPara();
      if (!list) list = [];
      list.push(inline(bullet[1]));
      return;
    }
    const ordered = line.match(/^\d+[.、)]\s+(.+)$/);
    if (ordered) {
      flushPara();
      if (!list) list = [];
      list.push(inline(ordered[1]));
      return;
    }
    flushList();
    para.push(line);
  });
  flushPara();
  flushList();
  return blocks;
}

/**
 * 图文微课内嵌阅读器。
 * 在系统内直接渲染 AI 生成的图文微课正文，学生阅读后可标记「本章完成」，
 * 驱动该学习资源的学习进度。
 */
function MicroCourseReader({
  resource,
  open,
  onClose,
  done,
  onToggleDone,
  submitting,
  onUnlock,
}) {
  const blocks = useMemo(
    () => (open && resource ? renderMarkdown(resource.content) : []),
    [open, resource]
  );

  // 弹窗打开即视为「解锁」该资源；解锁仅触发一次（id 变化或由关闭→打开时）
  useEffect(() => {
    if (open && resource?.id != null) {
      onUnlock?.(resource);
    }
    // 只在 (open, resource.id) 变化时触发，避免内部 state 引起重放
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, resource?.id]);

  const handleToggleDone = () => {
    if (submitting) return;
    onToggleDone?.();
  };

  const link = resolveResourceOpenLink(resource);

  return (
    <Modal
      title={
        <span className="microcourse-modal-title">
          <BookOutlined />
          图文微课 · {resource?.title || '学习资源'}
        </span>
      }
      open={open}
      onCancel={onClose}
      width={760}
      footer={null}
      className="microcourse-modal"
      destroyOnClose
    >
      <div className="microcourse-body">
        <div className="microcourse-meta">
          <span className="microcourse-meta-desc">{resource?.description}</span>
          {resource?.guide && (
            <div className="microcourse-guide">
              <LinkOutlined />
              {resource.guide}
            </div>
          )}
        </div>

        {blocks.length > 0 ? (
          <article className="microcourse-content">
            {blocks.map((block, index) => {
              if (block.type === 'h1') {
                return <h1 key={index} dangerouslySetInnerHTML={{ __html: block.text }} />;
              }
              if (block.type === 'h2') {
                return <h2 key={index} dangerouslySetInnerHTML={{ __html: block.text }} />;
              }
              if (block.type === 'h3') {
                return <h3 key={index} dangerouslySetInnerHTML={{ __html: block.text }} />;
              }
              if (block.type === 'para') {
                return <p key={index} dangerouslySetInnerHTML={{ __html: block.text }} />;
              }
              if (block.type === 'list') {
                return (
                  <ul key={index}>
                    {block.items.map((item, i) => (
                      <li key={i} dangerouslySetInnerHTML={{ __html: item }} />
                    ))}
                  </ul>
                );
              }
              return null;
            })}
          </article>
        ) : (
          <div className="microcourse-empty">该资源暂无图文微课内容，可先完成其他学习任务。</div>
        )}

        <div className="microcourse-footer">
          <div className="microcourse-footer-links">
            <a
              className="microcourse-ext"
              href={link}
              target="_blank"
              rel="noreferrer"
              title={isHttpUrl(resource?.link) ? '点击前往资源地址' : '详情链暂缺，已跳转到来源平台搜索'}
            >
              <LinkOutlined /> 前往原资源
            </a>
          </div>
          <Button
            type={done ? 'default' : 'primary'}
            size="large"
            icon={done ? <CheckCircleOutlined /> : <FireOutlined />}
            loading={submitting}
            onClick={handleToggleDone}
            className="microcourse-done-btn"
          >
            {done ? '已完成本章，点击取消' : '我学完了，完成本章'}
          </Button>
        </div>
        {done && (
          <div className="microcourse-done-tip">
            <CheckCircleOutlined /> 已完成本章学习，进度已更新！
          </div>
        )}
      </div>
    </Modal>
  );
}

export default MicroCourseReader;
