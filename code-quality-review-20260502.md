# Lumos AI 代码质量审查报告

**审查日期**: 2026-05-02
**审查范围**: 前端 (Vue 3 + TypeScript)、后端 (Go/Gin)、跨栈一致性
**审查工具**: Claude Code + gstack 分析代理

---

## 总体评分

| 维度 | 前端 | 后端 | 综合 |
|------|------|------|------|
| 代码清晰/可读/可维护 | 3/10 | 4/10 | **3.5/10** |
| 命名一致性 | 4/10 | 4/10 | **4/10** |
| 重复代码 | 2/10 | 3/10 | **2.5/10** |
| 模块边界 | 4/10 | 3/10 | **3.5/10** |
| 过度复杂实现 | 4/10 | 4/10 | **4/10** |
| **综合** | **3.4/10** | **3.6/10** | **3.5/10** |

---

## 一、代码是否清晰、可读、可维护

### 前端：3/10

**核心问题：巨型组件（God Component）**

| 文件 | 行数 | 问题 |
|------|------|------|
| `DramaManagement.vue` | **7,066** | 75 个 ref、168 个 ElMessage 调用，涵盖角色/场景/道具/分镜/图片生成等 8 个功能域 |
| `ProductionWorkshopStage.vue` | **6,389** | 工作台视图、镜头列表、拖拽、对话编辑、视频生成等 |
| `EpisodeWorkflow.vue` | **6,104** | 资产解析管道、镜头编辑、分镜生成、AI 续写等 |
| `flow.ts` (Pinia store) | **3,670** | 画布布局引擎、节点创建、边管理、缓存、迁移、保存/加载 |
| `TiptapPromptEditor.vue` | **3,380** | 富文本编辑器、资产管理、拖拽、token 计数 |

`DramaManagement.vue` 的 `<script setup>` 块从第 436 行延伸到约 7066 行，开发人员无法在脑中形成完整的心智模型。

**典型反例** — `ensureDialogModelSelected()`（第 809-857 行）：
50 行函数手动检查 9 个独立的 ref 值与对应选项列表，9 个 if 块结构完全相同。可以简化为 5 行循环。

### 后端：4/10

**核心问题：巨型 Handler 文件**

| 文件 | 行数 | 问题 |
|------|------|------|
| `drama.go` | **4,435** | 40+ handler 方法、20+ 辅助函数、10+ 请求结构体，业务逻辑应在 service 层 |
| `image_generation_service.go` | **4,341** | 图片生成服务 |
| `drama_service.go` | **4,327** | 剧目服务 |
| `dify_service.go` | **2,849** | Dify 服务 |
| `storyboard.go` (handler) | **1,880** | 分镜 handler |

Handler 层直接执行 132 次 `h.db.*` 数据库调用，绕过 Service 层。例如 `drama.go` 第 167-190 行直接查询 `OrganizationMember` 和 `Organization` 进行权限校验。

**具体示例** — `SmartCreationChat`（drama.go 第 430-556 行）：126 行函数包含请求解析、URL 解析、ES 记录、AI 配置查找、流式响应、再次 ES 记录。

### 亮点

- 后端 `pkg/response` 包设计良好：分页辅助、自动 Sentry 上报、URL 解析
- 后端 `pkg/apperr` 包：清晰的错误类型层次
- 前端 `request.ts` 的错误处理管道设计合理：自动 401 刷新重试、中文友好错误消息

---

## 二、命名是否统一

### 前端：4/10

**中英文混用**：大量硬编码中文字符串未走 i18n：

- `DramaManagement.vue` 第 737 行：`ElMessage.warning('未找到项目ID')`
- `DramaManagement.vue` 第 2070 行：`ElMessage.error('图片大小不能超过10MB')`
- `agent.ts` 第 24 行：`AGENT_UNAVAILABLE_MESSAGE = '当前系统未配置任何智能体...'`
- `request.ts` 第 16-51 行：所有错误消息硬编码中文（`'请求参数错误'`、`'请先登录'`）
- `types/video.ts` 第 112-127 行：`VIDEO_ASPECT_RATIOS` 包含中文标签（`'16:9 (横屏)'`）

**命名不一致**：
- `editingCharacterId` vs `sceneID` — 同一文件中 id/ID 大小写不统一
- `normalizeCharacterFieldValue`（第 1164 行）实际也用于场景和道具，名不副实
- `DERIVED_CHARACTER_REFERENCE_TASK_ID` 在组件内部使用 SCREAMING_SNAKE_CASE，与局部变量命名风格不一致

### 后端：4/10

**模型命名违反 Go 规范**：
- `COMMON_MODEL`（全大写）违反 Go PascalCase 命名规范（`common.go` 第 9 行）
- `COMMON_MODEL.ID` 序列化为 `json:"ID"`（大写），其他模型用 `json:"id"`（小写）
- `nine_gird.go` — **拼写错误**："gird" 应为 "grid"

**JSON tag 不一致**：

| 模型 | JSON tag | 风格 |
|------|----------|------|
| `User.OrgId` | `json:"orgId"` | camelCase |
| `ApiCallSession.OrgId` | `json:"orgId"` | camelCase |
| `Drama.OrgId` | `json:"org_id"` | snake_case |
| `AIServiceConfig.OrgId` | `json:"org_id"` | snake_case |
| `ApiCallSession.UserId` | `json:"userId"` | camelCase |
| `Drama.UserId` | `json:"user_id"` | snake_case |

**Import 别名使用数字后缀**（`routes.go`）：`handlers2`、`middlewares2`、`services2`、`storage2` — Go 反模式。

### 跨栈命名不一致

- `nine_gird` 拼写错误从前端贯穿到后端到数据库列名
- 前端 `Drama.id` 类型为 `string`，后端为 `uint` — 前后端 ID 类型不匹配
- `orgId`（camelCase）vs `org_id`（snake_case）导致前端需要手动归一化

---

## 三、是否存在重复代码

### 前端：2/10 — **最严重的问题**

**角色/场景/道具三重复制**（约 800-1000 行可消除的重复代码）：

在 `DramaManagement.vue` 中，以下模式被复制了 3 次，仅替换资产类型名：

| 重复模式 | 行范围 | 每份行数 |
|----------|--------|----------|
| 树子节点映射 (`characterChildrenMap` 等) | 1098-1226 | ~15 行 × 3 |
| 树行计算属性 (`characterTreeRows` 等) | 1115-1308 | ~50 行 × 3 |
| `isBase*` 函数 | 1160-1316 | ~3 行 × 3 |
| `hasCoreFieldChanged` | 1168-1332 | ~5 行 × 3 |
| `getDescendants` | 1174-1370 | ~20 行 × 3 |
| 剧集出现映射 | 1462-1565 | ~30 行 × 3 |
| 剧集显示函数 | 1614-1638 | ~10 行 × 3 |
| 对话框模型/分辨率/质量计算属性 | 915-949 | ~6 个计算属性 |
| 图片任务删除 | 2541-2575 | ~15 行 × 3 |
| 参考任务切换 | 2482-2492 | ~5 行 × 3 |

**`fixImageUrl` 重复定义** — 3 个文件各自独立实现：
- `DramaManagement.vue` 第 1969 行
- `EpisodeWorkflow.vue` 第 1256 行
- `DramaWorkflow.vue` 第 684 行

同时 `imageCacheBuster` 和 `refreshImageCache` 也被重复定义。

**`CharactersTab` / `ScenesTab` / `PropsTab` 组件** — 近乎相同的模板，仅实体名不同，可合并为 `AssetTab.vue`。

**`AssetConfigDialog` 模板** — 在 `DramaManagement.vue` 中被使用 3 次（第 242-363 行），近乎相同的 props 和事件。

### 后端：3/10

**`ensureDramaView`/`ensureDramaEdit` 复制 20 次** — 10 个 Handler 文件中各有 2 个几乎相同的权限检查函数：

| Handler | 文件 | ensureView | ensureEdit |
|---------|------|-----------|-----------|
| DramaHandler | drama.go | 126 | 140 |
| StoryboardHandler | storyboard.go | 67 | 80 |
| NineGridHandler | nine_grid.go | 63 | 76 |
| ImageGenerationHandler | image_generation.go | 348 | 361 |
| EpisodeHandler | episodes.go | 48 | 61 |
| VideoGenerationHandler | video_generation.go | 118 | 131 |
| ScriptGenerationHandler | script_generation.go | 47 | — |
| SceneHandler | scene.go | 52 | — |
| PropsHandler | props.go | 48 | — |
| UploadHandler | upload.go | 35 | — |

**`DramaService` 重复实例化** — 10 个 Handler 各自调用 `services.NewDramaService(db, log)`，而非共享单一实例。

**`ensureEpisodeAccess` 复制 4 次** — NineGridHandler、StoryboardHandler、EpisodeHandler、ImageGenerationHandler 各自定义。

---

## 四、模块边界是否清楚

### 前端：4/10

**业务逻辑混在组件中**：`DramaManagement.vue` 包含大量应在 store 或 composable 中的逻辑：
- 资产 CRUD 操作（直接调用 API）
- 图片生成编排（第 2700-2800+ 行）
- 树构建算法（第 1098-1370 行）
- 剧集出现追踪（第 1462-1565 行）
- 文件上传与校验（第 1997-2091 行）

**`flow.ts` Store 职责混乱**（3,670 行）：
- 画布布局算法（第 202-529 行）— 纯计算，应为工具函数
- 节点/边迁移（第 645-686 行）— 数据转换，可独立
- 错误处理辅助（第 855-896 行）— 应在 utils 模块
- IndexedDB 缓存（第 817-827 行）— 应为独立缓存模块

**Store 与组件职责重叠**：`episode.ts` store 提供了 `CachedEpisode` 的 CRUD 操作，但 `DramaManagement.vue` 也直接调用 `dramaAPI.saveCharacters`、`episodesAPI.create` 等，形成两条代码路径。

### 后端：3/10

**Handler 绕过 Service 层直接访问数据库** — 132 次 `h.db.*` 调用：

Handler 层直接执行：
- 权限检查（查询 `OrganizationMember`、`Organization`）
- 数据查询（查询 `Episode`、`Scene`、`Prop`、`ResourceAppearance`、`Character`、`ImageGeneration`）
- 事务操作（`h.db.Transaction(...)`）
- 直接更新（`h.db.Model(...).Update(...)`）

`DramaHandler` 同时持有 `db *gorm.DB` 和 `dramaService *services.DramaService` 两个字段并交替使用。

**Setter 注入反模式**（手动 DI）— `routes.go` 中约 25 个 `Set*` 调用：
```go
dramaHandler.SetDramaDynamicService(dramaDynamicService)
dramaHandler.SetDramaService(...)
dramaHandler.GetDramaService().SetStorage(localStorage)
imageGenHandler.SetDramaDynamicService(dramaDynamicService)
// ... 20+ more
```

**Handler 之间循环依赖**：`DramaHandler` 在 `processSeedanceStoryboardImport` 中创建 `ConverterHandler` 实例（第 1510 行：`NewConverterHandler(h.db, h.aiService)`），说明 `ConvertText` 应在 service 而非 handler 中。

**基础设施泄漏到 Handler 层**：Handler 直接 import `infrastructure/storage`、`infrastructure/elasticsearch`、`infrastructure/external/ffmpeg`。

---

## 五、是否有过度复杂或难以理解的实现

### 前端：4/10

**`ensureDialogModelSelected()`**（DramaManagement.vue 第 809-857 行）：50 行函数中 9 个结构相同的 if 块，可简化为 5 行循环。

**`pickLocalFile()`**（第 1997-2058 行）：60 行函数实现自定义文件选择器，包含焦点探测、8 次重试循环（120ms 间隔）和手动清理，过度复杂。

**`ensureCharacterIdForDialog()`**（第 2657-2698 行）：保存角色后等待 800ms、重新加载所有数据、然后按名称匹配查找新创建的角色 — 存在竞态条件：
- `setTimeout(r, 800)` 是定时炸弹
- 按名称匹配 (`c.name === newCharacter.value.name`) 在重名时不可靠
- 应直接使用 API 返回的 ID

**错误检测靠字符串匹配**（flow.ts 第 881-896 行）：`isCanvasOccupiedError()` 和 `isCanvasRevisionConflictError()` 通过匹配中文字符串（`'其他用户'`、`'版本'`）判断错误类型，后端消息变更会导致静默失败。

**画布布局引擎嵌入 Store**（flow.ts 第 450-529 行）：100+ 行布局算法混合响应式状态，应提取为纯工具函数。

### 后端：4/10

**4+ 层嵌套** — `processSeedanceStoryboardImport`（drama.go 第 1486-1625 行）：
```
if importAssets {
    if isDeleteMode {
        if len(characters) > 0 { ... }
        if len(scenes) > 0 { ... }
        if len(props) > 0 { ... }
    } else {
        if err := appendIncremental(...); err != nil { ... }
    }
}
if importStoryboards {
    if isDeleteMode { ... } else { ... }
}
```

**SSE 解析重复实现** — 至少 2 处手动解析 SSE 事件：
1. `StreamSeedanceStoryboard`（drama.go 第 1029-1042 行）：bufio.Scanner
2. `parseAgentSSEEvents`（agent_message_elasticsearch.go 第 409-431 行）：双换行分割

**敏感键检测过度激进**（agent_message_elasticsearch.go 第 119 行）：
- `normalized == "data"` 会屏蔽任何名为 "data" 的 JSON 键
- `strings.Contains(normalized, "token")` 会屏蔽 `pagination_token`、`next_token` 等非密钥字段

**Handler 中的 goroutine 使用 `context.Background()`**：
- `go h.processSeedanceStoryboardImport(...)`（drama.go 第 1477 行）
- `convertSeedanceTextWithRetry`（第 1639 行）使用 `context.Background()` + `time.Sleep` 重试，无上下文取消

**`parseSeedanceMetadataDuration`**（drama.go 第 2529-2573 行）：44 行函数 switch 7 种数值类型 + `json.Number` + `string`，可用 `fmt.Sprintf` + `strconv.Atoi` 简化。

---

## 六、额外发现：安全与类型安全

### 安全问题（按严重程度排序）

| 严重度 | 问题 | 位置 |
|--------|------|------|
| **严重** | 生产密钥提交到 Git — `server/configs/config.yaml` 包含腾讯云 COS/Captcha/SES 密钥、Elasticsearch 密码、Anthropic token 等 | `server/configs/config.yaml` |
| **严重** | API Key 序列化到 JSON 响应 — `AIServiceConfig.api_key` 暴露给前端 | `ai_config.go:15` |
| **严重** | 静态文件无认证 — `/static` 和 `/data` 路径无需 JWT | `routes.go:30-33` |
| **高** | 默认 JWT Secret 硬编码 — 未设置环境变量时使用已知默认值 | `config.go:236` |
| **高** | v-html XSS 风险 — 10 处 `v-html` 渲染动态内容，ChatPanel 渲染 AI 生成的 markdown | `ChatPanel.vue:31,45,66,78` |
| **中** | Dify 回调密钥通过 URL 参数传递 — 会出现在访问日志中 | `dify_callback.go:31-34` |

### 类型安全问题

| 问题 | 影响 |
|------|------|
| 前端 `Drama.id`/`Episode.id`/`Scene.id` 类型为 `string`，后端为 `uint` | `===` 比较可能静默失败 |
| `flow.ts` 30+ 处 `as any` 类型断言 | API 响应类型不完整或错误 |
| `Storyboard` 类型使用 `[key: string]: any` | 完全禁用该核心类型的类型检查 |
| `drama.ts` API 调用大量 `any` 参数 | 写操作无编译时校验 |
| `videoCapability.ts` 几乎所有参数为 `any` | 失去类型安全保护 |

### 死代码

- `routes.go` 第 372-384 行：整个 `/ai-chat` 路由组被注释
- `ai_config.go` 第 36-89 行：`ExtraPrice` 和 `AIServiceProvider` 结构体被注释（约 50 行）
- `agent.ts` 类型：`ParsedPrompt` 与 `UnifiedPrompt` 近乎相同
- `agent.ts` 的 `Episode` 类型与 `drama.ts` 的 `Episode` 重复定义

---

## 七、优先改进建议（按影响排序）

### P0 — 必须立即修复

1. **从 Git 移除 `server/configs/config.yaml` 并轮换所有泄露的密钥** — 添加到 `.gitignore`，使用环境变量管理秘密
2. **API Key 不应序列化到 JSON** — `AIServiceConfig.api_key` 添加 `json:"-"` tag，列表接口返回掩码值
3. **静态文件路径添加认证中间件** — 或使用签名 URL

### P1 — 高影响改进

4. **提取 `useAssetCRUD<T>(assetType)` composable** — 消除 DramaManagement.vue 中约 800-1000 行三重复制代码（树构建、后代查找、字段变更检测、对话框模型选择、图片任务管理、剧集显示）
5. **将所有 Handler 中的 DB 访问移至 Service 层** — 消除 132 次 `h.db.*` 直接调用，这是最大的架构改进
6. **提取权限检查为共享中间件或辅助函数** — `ensureDramaView/Edit` 复制 20 次，应合并为 1 个
7. **拆分 DramaManagement.vue** — 至少拆为资产管理、剧集管理、分镜管理、图片生成对话框、项目概览 5 个模块
8. **拆分 drama.go** — 拆为 `drama_crud.go`、`drama_seedance.go`、`drama_smart_creation.go`、`drama_assets.go` 等

### P2 — 中等影响改进

9. **共享 Service 实例** — `DramaService` 在 routes.go 中创建一次，注入给所有 Handler
10. **统一 JSON tag 命名风格** — 全部使用 snake_case，修复 `COMMON_MODEL.ID` → `json:"id"`
11. **修复 `nine_gird` 拼写错误** — 需数据库迁移，但越晚修复成本越高
12. **替换 `fixImageUrl` 为 `useMediaPreviewUrl` composable** — composable 已存在但 3 个组件未使用
13. **采用构造函数注入代替 setter 注入** — 替换 `Set*` 方法为构造参数
14. **修复前端 `any` 类型** — 从 `Storyboard.characters?: any` 和 API 调用签名开始
15. **前后端 ID 类型对齐** — 统一为 `string`（前端）与 `number`（后端），或前端使用 `${id}` 转换

### P3 — 改善体验

16. **中文字符串走 i18n** — 从 `request.ts` 和 `DramaManagement.vue` 开始
17. **删除注释代码** — `routes.go` 的 `/ai-chat`、`ai_config.go` 的 `ExtraPrice`
18. **Go module 名称** — `github.com/drama-generator/backend` 改为与仓库路径一致
19. **合并 Docker Compose 文件** — 3 个独立 compose 文件合并为项目根目录的 1 个
