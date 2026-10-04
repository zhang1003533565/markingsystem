/**
 * 发展助手 / 岗位成长链路共享工具。
 *
 * 在原五个页面（AssistantHome / GrowthPath / DevelopmentResources / HotJobs /
 * MyJobFavorites）中被复制粘贴，这里统一抽取，避免改一处漏改五处的后续问题。
 */
import { LinkOutlined, VideoCameraOutlined } from '@ant-design/icons'

/** 学习资源六维标签，与后端 sortOrder（1-6）一一对应 */
export const DIMENSION_TAGS = {
  1: { label: '理论', color: 'blue' },
  2: { label: '实战', color: 'green' },
  3: { label: '技能', color: 'purple' },
  4: { label: '项目', color: 'cyan' },
  5: { label: '行业', color: 'orange' },
  6: { label: '学习路径', color: 'red' },
}

/** 按 sortOrder 取维度标签；缺省时按索引兜底；再兜底为「推荐」 */
export function getResourceTypeTag(resource, index) {
  return (
    DIMENSION_TAGS[resource?.sortOrder] ||
    DIMENSION_TAGS[(index ?? 0) + 1] ||
    { label: '推荐', color: 'default' }
  )
}

/** 解析技能文本（支持中英文逗号、顿号分隔） */
export function parseSkills(skillsText) {
  return String(skillsText || '')
    .split(/[,，、]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

/** 按换行解析文本并去序号前缀，通常用于任务/路径步骤 */
export function parseLines(text) {
  return String(text || '')
    .split(/\r?\n/)
    .map((line) => line.trim().replace(/^\d+[.、)]\s*/, ''))
    .filter(Boolean)
}

/** 是否为合法 http(s) 外链 */
export function isHttpUrl(value) {
  return /^https?:\/\//i.test(String(value || '').trim())
}

/** 去掉标题里的【平台】前缀，得到更干净的搜索关键词 */
function resourceSearchKeyword(resource) {
  const raw = String(resource?.title || '').trim()
  const stripped = raw.replace(/^【[^】]{1,20}】\s*/, '').trim()
  return stripped || raw || '学习资源'
}

/**
 * 当详情外链缺失时，按标题/来源推断平台并生成可点击的平台搜索页。
 * 用于兜底展示「原资源」，避免卡片无外链按钮。
 */
export function buildResourceSearchFallbackLink(resource) {
  const text = `${resource?.title || ''} ${resource?.guide || ''}`
  const q = encodeURIComponent(resourceSearchKeyword(resource))
  if (/bilibili|B站|哔哩/i.test(text)) {
    return `https://search.bilibili.com/all?keyword=${q}`
  }
  if (/juejin|掘金/i.test(text)) {
    return `https://juejin.cn/search?query=${q}&type=0`
  }
  if (/csdn/i.test(text)) {
    return `https://so.csdn.net/so/search?q=${q}&t=blog`
  }
  if (/zhihu|知乎/i.test(text)) {
    return `https://www.zhihu.com/search?type=content&q=${q}`
  }
  if (/cnblogs|博客园/i.test(text)) {
    return `https://zzk.cnblogs.com/s?w=${q}`
  }
  if (/segmentfault|思否/i.test(text)) {
    return `https://segmentfault.com/search?q=${q}`
  }
  if (/runoob|菜鸟/i.test(text)) {
    return `https://www.runoob.com/?s=${q}`
  }
  if (/wangdoc|网道/i.test(text)) {
    return `https://wangdoc.com/`
  }
  if (/mozilla|mdn/i.test(text)) {
    return `https://developer.mozilla.org/zh-CN/search?q=${q}`
  }
  return `https://www.bing.com/search?q=${q}`
}

/**
 * 资源外链：优先真实详情 link；缺失时回落到平台搜索页，保证卡片总能打开来源。
 */
export function resolveResourceOpenLink(resource) {
  const link = String(resource?.link || '').trim()
  if (isHttpUrl(link)) return link
  return buildResourceSearchFallbackLink(resource)
}

/**
 * 按链接域名判断资源来源类型，动态展示来源标签与外链文案：
 * - B站视频 → 「UP主」/「视频链接」
 * - 掘金 / CSDN / 知乎 / 博客园 / SegmentFault → 「作者」/「原文链接」
 * - 菜鸟教程 / 网道 / MDN → 「文档」/「文档链接」
 * - 平台搜索兜底 → 「来源」/「原资源」
 * - 其他有效 http(s) 链接 → 「来源」/「外链」
 * - 无有效链接（纯图文微课）→ 「来源」，不展示外链
 */
export function getResourceSourceMeta(link) {
  const url = String(link || '').trim()
  if (/bilibili\.com\/video/i.test(url)) {
    return { guideLabel: 'UP主', guideIcon: VideoCameraOutlined, linkLabel: '视频链接', linkTitle: '点击前往视频', linkIcon: VideoCameraOutlined }
  }
  if (/(juejin\.cn\/post|blog\.csdn\.net|zhuanlan\.zhihu\.com\/p\/|cnblogs\.com\/.+\/(p|articles)\/|segmentfault\.com\/a\/)/i.test(url)) {
    return { guideLabel: '作者', guideIcon: LinkOutlined, linkLabel: '原文链接', linkTitle: '点击前往原文', linkIcon: LinkOutlined }
  }
  if (/(developer\.mozilla\.org\/.+\/docs|runoob\.com\/.+\.htm|wangdoc\.com\/.+)/i.test(url)) {
    return { guideLabel: '文档', guideIcon: LinkOutlined, linkLabel: '文档链接', linkTitle: '点击前往文档', linkIcon: LinkOutlined }
  }
  if (/(search\.bilibili\.com|juejin\.cn\/search|so\.csdn\.net|zhihu\.com\/search|zzk\.cnblogs\.com|segmentfault\.com\/search|bing\.com\/search)/i.test(url)) {
    return { guideLabel: '来源', guideIcon: LinkOutlined, linkLabel: '原资源', linkTitle: '详情链暂缺，已跳转到来源平台搜索', linkIcon: LinkOutlined }
  }
  if (/^https?:\/\//i.test(url)) {
    return { guideLabel: '来源', guideIcon: LinkOutlined, linkLabel: '原资源', linkTitle: '点击打开外链', linkIcon: LinkOutlined }
  }
  return { guideLabel: '来源', guideIcon: LinkOutlined, linkLabel: '原资源', linkTitle: '点击打开外链', linkIcon: LinkOutlined }
}

/**
 * 生成岗位在招聘平台的搜索跳转链接。
 * 岗位数据为 AI/示例生成，无真实职位 ID，按岗位名在 Boss 直聘做关键词检索跳转。
 */
export function resolveJobSearchLink(job) {
  const keyword = String(job?.jobTitle || '软件工程师').trim() || '软件工程师'
  return `https://www.zhipin.com/web/geek/job?query=${encodeURIComponent(keyword)}`
}