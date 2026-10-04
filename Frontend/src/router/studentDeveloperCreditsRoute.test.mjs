import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const appSource = readFileSync(new URL('../App.jsx', import.meta.url), 'utf8')
const routerSource = readFileSync(new URL('./index.jsx', import.meta.url), 'utf8')

assert.match(
  routerSource,
  /path:\s*['"]\/student\/developer-credits['"]/,
  '学生端开发者致谢页需要写入路由配置表',
)

assert.match(
  appSource,
  /path=['"]\/student\/developer-credits['"]/,
  '学生端开发者致谢页需要在 App.jsx 的实际 Route 列表中注册',
)

assert.match(
  appSource,
  /renderStudentRoute\(['"]\/student\/developer-credits['"]\)/,
  '学生端开发者致谢页需要通过学生端布局渲染',
)
