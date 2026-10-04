import { flushSync } from 'react-dom'

/**
 * 发展助手相关路由。
 * 导航到这些页面时启用 View Transition 转场动画。
 * 注：热门岗位 / 我的收藏 / 成长链路 / 学习资源 / 时光机 已收编进发展助手内部视图，
 * 仅保留独立入口：发展助手工作台 与 AI 对话。
 */
const ASSISTANT_PATHS = [
  '/student/assistant',
  '/student/ai-chat',
]

export const isAssistantPath = (path) => ASSISTANT_PATHS.includes(path)

/**
 * 带 View Transition 的导航。
 * - 浏览器支持（Chrome/Edge 111+、Safari 18+、Firefox 141+）时：
 *   用 startViewTransition 包裹导航，flushSync 保证 React 先同步提交新 DOM，
 *   浏览器再截取新旧快照运行动画。
 * - 不支持时回退为普通导航，行为完全不变。
 */
export function navigateWithViewTransition(navigate, path, options) {
  const doNavigate = () => navigate(path, options)
  if (typeof document !== 'undefined' && typeof document.startViewTransition === 'function') {
    document.startViewTransition(() => {
      flushSync(doNavigate)
    })
    return
  }
  doNavigate()
}
