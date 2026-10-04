const SAFE_TAGS = new Set([
  'P',
  'BR',
  'STRONG',
  'B',
  'EM',
  'I',
  'U',
  'S',
  'UL',
  'OL',
  'LI',
  'H1',
  'H2',
  'H3',
  'H4',
  'BLOCKQUOTE',
  'A',
  'IMG',
  'VIDEO',
  'SOURCE',
  'SPAN'
]);

function isSafeUrl(value) {
  const normalized = String(value || '').trim().toLowerCase();
  return normalized.startsWith('http://')
    || normalized.startsWith('https://')
    || normalized.startsWith('/')
    || normalized.startsWith('data:image/');
}

function sanitizeAnnouncementHtml(html) {
  const template = document.createElement('template');
  template.innerHTML = html || '<p>暂无内容</p>';

  const walk = (node) => {
    [...node.children].forEach((element) => {
      if (!SAFE_TAGS.has(element.tagName)) {
        element.remove();
        return;
      }

      [...element.attributes].forEach((attribute) => {
        const name = attribute.name.toLowerCase();
        const keep = ['href', 'src', 'alt', 'title', 'width', 'height'].includes(name);
        if (!keep || ((name === 'href' || name === 'src') && !isSafeUrl(attribute.value))) {
          element.removeAttribute(attribute.name);
        }
      });
      walk(element);
    });
  };

  walk(template.content);
  return template.innerHTML;
}

function AnnouncementArticle({ announcement, showMeta = true, className = '' }) {
  if (!announcement) {
    return null;
  }

  const wrapperClassName = ['announcement-article', className].filter(Boolean).join(' ');

  return (
    <div className={wrapperClassName}>
      <div className="announcement-article-header">
        <h3>{announcement.title || '未命名公告'}</h3>
        {showMeta ? (
          <div className="announcement-article-meta">
            {announcement.publisherName ? <span>发布人：{announcement.publisherName}</span> : null}
            {announcement.publishTime ? <span>发布时间：{announcement.publishTime}</span> : null}
          </div>
        ) : null}
      </div>
      <div
        className="announcement-rich-content announcement-article-body"
        dangerouslySetInnerHTML={{ __html: sanitizeAnnouncementHtml(announcement.content) }}
      />
    </div>
  );
}

export default AnnouncementArticle;
