# Lumos AI 性能与稳定性审计报告

**审查日期**: 2026-05-02
**审查范围**: 前端 (Vue 3 + TypeScript)、后端 (Go/Gin)
**审查维度**: 性能（低效循环、N+1查询、缓存、大数据退化）、稳定性与异常处理

---

## 第一部分：性能审计

### 总体评分

| 维度 | 前端 | 后端 | 综合 |
|------|------|------|------|
| 低效循环/重复计算 | 3/10 | 3/10 | **3/10** |
| 重复查询/API调用 | 2/10 | 2/10 | **2/10** |
| 同步阻塞 | 4/10 | 3/10 | **3.5/10** |
| 数据库查询合理性 | N/A | 2/10 | **2/10** |
| N+1 查询问题 | N/A | 1/10 | **1/10** |
| 缓存使用 | 3/10 | 1/10 | **2/10** |
| 大数据量退化 | 2/10 | 2/10 | **2/10** |
| **综合** | **2.8/10** | **1.9/10** | **2.3/10** |

---

## 一、是否存在低效循环、重复查询、大量同步阻塞

### 后端：N+1 查询问题（最严重）

#### 1.1 `getDrama` — 角色图片生成状态（最严重的 N+1）

**文件**: `server/application/services/drama_service.go`，第 1020-1048 行

每个剧集的每个角色执行 **1-2 次独立 DB 查询**（一次查 pending/processing，若未命中再查 failed）。中型项目（10 集 × 5 角色/集）≈ 50-100 次；大型项目（20 集 × 8 角色/集）≈ 160-320 次。加上同函数内其他循环（resource_appearances、resolveImageURLFromTaskID），`getDrama` 整体中型项目约 90-140 次 DB 查询，大型项目可达 300+ 次。

```go
for i := range drama.Episodes {
    for j := range drama.Episodes[i].Characters {
        err := s.db.Where("image_type = ? AND resource_id = ? AND (status = ? OR status = ?)",
            models.ImageTypeCharacter, drama.Episodes[i].Characters[j].ID, "pending", "processing").
            Order("created_at DESC").First(&imageGen).Error
        // ... 失败时第二次查询
    }
}
```

**修复**: 批量收集所有角色 ID，用 `resource_id IN (?)` 一次性查询，在 Go 中映射结果。

#### 1.2 `getDrama` — 每个剧集查询 ResourceAppearance + Character（N+1）

**文件**: `drama_service.go`，第 980-1017 行

每个剧集分别查询 `resource_appearances` 和 `characters`。10 个剧集 = 20 次查询。

**修复**: 一次性查询 `resource_appearances`，按 `episode_number` 在 Go 中分组。

#### 1.3 `resolveImageURLFromTaskID` 在循环中调用 — 每次最多 3 次 DB 查询

**文件**: `drama_service.go`，第 2016-2051 行

每次调用执行最多 3 次顺序 DB 查询（image_generations、video_generations、dify_workflow_tasks）。调用位置：

| 位置 | 行号 | 规模 |
|------|------|------|
| `getDrama` 角色循环 | 1052-1066 | 所有角色 |
| `GetCharacters` | 2150-2155 | 所有角色 |
| `GetScenes` | 2185-2193 | 所有场景 |
| `GetProps` | 2555-2559 | 所有道具 |

30 角色 + 15 场景 + 20 道具 = 最多 (30+15+20) × 3 = **195 次查询**。

**修复**: 批量收集 task ID，每张表用 `id IN (?)` 一次查询。

#### 1.4 `GetEpisodeDetail` — 场景图片生成状态（N+1）

**文件**: `server/application/services/episode_service.go`，第 342-363 行

与 1.1 相同模式，但作用于剧集内场景。每个场景 1-2 次查询。

#### 1.5 `CopyDrama` — 逐条 INSERT

**文件**: `drama_service.go`，第 4046-4198 行

角色(4053)、场景(4073)、剧集(4118)、分镜(4193) 逐条 `tx.Create`。复制含 100 个分镜的剧目产生 100+ 条 INSERT。

**修复**: 收集到切片后用 `tx.Create(&slice)` 批量插入。

#### 1.6 `ListDramas` — 每个剧目查询封面图

**文件**: `drama_service.go`，第 1229-1231 行、第 1284-1298 行

`resolveCoverImageByStage` → `firstStoryboardImageForDrama`，每个剧目一次 DB 查询。20 个剧目 = 20 次额外查询。

**修复**: 用窗口函数或子查询批量查询所有剧目的封面图。

### 前端：低效循环与冗余计算

#### 1.7 `storyboardRealtimeSyncSnapshot` — computed 中 JSON.stringify

**文件**: `web/src/views/drama/components/ProductionWorkshopStage.vue`，第 1875-1882 行

```js
const storyboardRealtimeSyncSnapshot = computed(() => {
  return JSON.stringify({
    storyboardId,
    payload: buildRealtimeUpdatePayload(storyboardId)
  })
})
```

每次响应式变化触发完整序列化 + `buildRealtimeUpdatePayload` 调用。每次按键操作都会触发 O(n) 序列化。

**修复**: 用手动脏标记或细粒度 watch 替代。

#### 1.8 Deep watcher 遍历大型响应式数组

**文件**: `web/src/stores/flow.ts`，第 1245-1253 行

```js
watch(generationHistory, () => { scheduleSave(false) }, { deep: true })
watch([nodes, edges, nodeCounter], () => { scheduleSave(false) }, { deep: true })
```

`nodes` 数组包含深层嵌套对象（每个 `FlowNode` 有 `videoOptions`、`imageOptions`、`metadata` 等）。100+ 节点时，任何单属性变更触发数千次属性比较。

**同样问题**:
- `ProductionWorkshopStage.vue` 第 4644-4650 行：deep watch `shotDialogueRecordsList`
- `ProductionWorkshopStage.vue` 第 4657 行：deep watch `currentEpisode`（触发 `loadWorkshopData`）
- `EpisodeWorkflow.vue` 第 5266-5268 行：deep watch `currentEpisode`（仅更新保存时间显示）

**修复**: 用细粒度 watch 特定属性替代 deep watch。

#### 1.9 O(n²) 边合并算法

**文件**: `flow.ts`，第 1381-1386 行

```js
const mergedEdges = [...userEdges]
for (const edge of autoEdges) {
  if (!mergedEdges.find(e => e.id === edge.id)) {
    mergedEdges.push(edge)
  }
}
```

每条 auto-edge 做 `find()` 线性扫描，复杂度 O(E × (U+E))。用 `Set` 可降至 O(E+U)。

#### 1.10 O(n²) `rebuildReferencedImageIdsFromEdges`

**文件**: `flow.ts`，第 628-643 行

每个视频节点过滤全部边，O(V × E)。先构建 `Map<targetNodeId, Edge[]>` 索引可降至 O(E+V)。

#### 1.11 `characterEpisodeNumberMap` 三次遍历剧集

**文件**: `DramaManagement.vue`，第 1462-1520 行

三个近乎相同的 computed 属性分别遍历 `drama.value.episodes`，可合并为单次遍历。

---

## 二、数据库查询是否合理

### 2.1 SELECT * 无列投影

**文件**: `drama_service.go`

| 行号 | 表 | 问题 |
|------|------|------|
| 1453 | `episodes` | `Find(&episodes)` 无 Select，加载 longtext `script_content` |
| 1463 | `characters` | 同上 |
| 1468 | `scenes` | 同上 |
| 1505 | `drama_dynamics` | 同上 |
| 1510 | `image_generations` | 同上 |
| 1515 | `video_generations` | 同上 |
| 2224 | `characters` (SaveCharacters) | 加载全部列仅做 diff |
| 2585 | `props` (SaveProps) | 同上 |
| 2813 | `scenes` (SaveScenes) | 同上 |

`episodes` 表的 `script_content`（longtext）尤其浪费 — 仅需 id、episode_number、title、status 时加载了全部数据。

### 2.2 缺少复合索引

**文件**: `server/domain/models/image_generation.go`

全代码库最常见的查询模式：
```sql
WHERE image_type = ? AND resource_id = ? AND status IN (?, ?) ORDER BY created_at DESC LIMIT 1
```
出现于 `drama_service.go:1023,1035`、`episode_service.go:344,352` 等多处。但模型仅定义了 `image_type` 和 `resource_id` 的独立索引，缺少复合索引。

**修复**: 添加 `idx_image_gen_type_resource_status` 复合索引 `(image_type, resource_id, status, created_at)`。

### 2.3 无 LIMIT 的无界查询

| 文件 | 行号 | 查询 | 风险 |
|------|------|------|------|
| `drama_service.go` | 1453-1515 | `buildDramaTrashData` 加载全部关联数据 | 10万+ image_generations 会 OOM |
| `drama_service.go` | 3757-3841 | `ListImageTasksForCharacter/Scene/Prop` 无 LIMIT | 500 次重新生成 = 500 行 |
| `agent_handler.go` | 1463 | `HandleListSessions` 无 LIMIT | 数千会话全部加载 |

### 2.4 冗余 COUNT 查询

**文件**: `drama_service.go`，第 1087-1125 行

`GetDramaDetail` 调用 `getDrama` 后（已加载全部角色、剧集、分镜到内存），又执行 4 次独立 COUNT 查询。这些计数可从已加载数据中直接计算。

### 2.5 LIKE '%keyword%' 无全文索引

**文件**: `drama_service.go`，第 1202 行

```go
db = db.Where("dramas.title LIKE ?", "%"+query.Keyword+"%")
```

前导通配符阻止 B-tree 索引使用。10000+ 剧目时为全表扫描。

**修复**: 使用 MySQL 全文索引或 Elasticsearch。

### 2.6 `ListDramas` Preload 爆炸

**文件**: `drama_service.go`，第 1214-1220 行

```go
Preload("Characters", ...).Preload("Episodes.Storyboards", ...).Find(&dramas)
```

分页列表的 20 个剧目，加载全部角色和每集全部分镜。20 剧目 × 10 集 × 50 分镜 = 10,000 行分镜数据，但列表页只需剧集计数和时长。

**修复**: 用 SQL 聚合计算时长，移除 `Preload("Episodes.Storyboards")`。

### 2.7 `resource_appearances` 同一剧集 3 次查询

**文件**: `server/application/services/image_generation_service.go`，第 1666-1728 行

同一 `drama_id` + `episode_number` 下，角色/场景/道具分别查询 `resource_appearances`。可合并为 `resource_type IN ('character', 'scene', 'prop')` 一次查询。

---

## 三、缓存使用是否正确

### 后端：Redis 几乎未使用

**严重程度: 关键**

Redis 已配置和初始化（`server/infrastructure/cache/redis.go`），但 **仅用于邮箱验证码**（`email_code_store.go`）。以下热数据路径完全无缓存：

| 热路径 | 每次请求数据库查询数 | 现状 |
|--------|----------------------|------|
| `GetDramaDetail` | 7-10+ 次查询 | 无缓存 |
| `ListDramas` | 2+ 次查询 + N 次封面图查询 | 无缓存 |
| `GetCharacters/Scenes/Props` | 多次查询 | 无缓存 |
| `GetEpisodeDetail` | 多次含 N+1 | 无缓存 |
| `resolveImageURLFromTaskID` | 1-3 次查询/调用 | 无缓存 |

**建议的缓存策略**:

| 数据 | TTL | 失效触发 |
|------|-----|----------|
| 剧目详情 | 30-60s | 剧目更新/删除 |
| 图片生成状态 | 10-15s | 状态变更 |
| `resolveImageURLFromTaskID` 结果 | 5min | URL 极少变化 |

### 前端：缓存问题

#### 3.1 `rememberPreview` 用展开替换整个缓存对象

**文件**: `web/src/stores/mediaPreview.ts`，第 30-37 行

```js
cache.value = { ...cache.value, [record.uri]: record }
```

每次调用创建新对象引用，触发所有依赖 `cache` 的 watcher/computed。`flushQueuedRefreshes` 处理 N 个 URI 时调用 N 次 `rememberPreview`，产生 N 次完整展开。

**修复**: 直接赋值 `cache.value[record.uri] = record`。

#### 3.2 `mediaPreview` 缓存被绕过

**文件**: `ProductionWorkshopStage.vue`，第 4072-4109 行

`refreshShotVideoPreviewURLs` 直接调用 `imageAPI.refreshCOSPreviewURLs` 而非使用 `useMediaPreviewStore().refreshPreviewURLs()`，绕过了缓存的去重逻辑，导致相同 URI 被重复刷新。

#### 3.3 Episode Store 无有效缓存失效

**文件**: `web/src/stores/episode.ts`，第 182-219 行

仅靠 3 秒节流防止重复获取，无显式缓存失效。变更操作（`set`/`del`）会触发 `fetchEpisode(episodeId, true)` 强制刷新，但变更和刷新之间的时间窗口可能提供过时数据。

#### 3.4 TaskCenter 每次变更写 localStorage

**文件**: `web/src/stores/taskCenter.ts`，第 60 行

`persist()` 在 `updateTask`、`addTask`、`dismissTask` 中均被调用。活跃轮询期间（每 2 秒/任务），这意味着每 2 秒一次 `JSON.stringify` + localStorage 写入，同步阻塞主线程。

---

## 四、大数据量场景下是否会退化

### 前端

#### 4.1 无虚拟滚动

**文件**: `ProductionWorkshopStage.vue`

- 第 65 行：`v-for="(shot, index) in workshopStoryboards"` — 全量渲染
- 第 161 行：`v-for="rec in shotDialogueRecordsReversed"` — 全量渲染

全项目零使用虚拟滚动（搜索 `virtual`、`VirtualList`、`VirtualScroller`、`RecycleScroller` 均无结果）。100+ 分镜或长生成历史 = 数百个含视频/图片/按钮的 DOM 节点。

#### 4.2 剧集分镜无分页

**文件**: `ProductionWorkshopStage.vue`，第 4403 行

```js
const res = await storyboardAPI.listByEpisode(episodeId, { flat: true })
```

一次获取全部分镜，无分页参数。100+ 分镜全部渲染到 DOM。

#### 4.3 `JSON.parse(JSON.stringify(...))` 克隆大型数据

**文件**: `flow.ts`，第 769 行

`cloneValue` 用 JSON 序列化/反序列化深度克隆。100+ 节点（每个含 `videoOptions`、`imageOptions`、`metadata`）时，这是主线程上的重同步操作。

#### 4.4 `JSON.stringify` 用作哈希

**文件**: `flow.ts`，第 803-805 行

`buildSnapshotHash` 序列化整个快照仅用于脏检测。大型画布时同步阻塞严重。

**修复**: 使用 MurmurHash 或 xxHash 等快速哈希函数。

#### 4.5 `pruneCanvasCache` 每次自动保存遍历 IndexedDB

**文件**: `web/src/utils/agentCanvasCache.ts`，第 104-132 行

每次 `writeEpisodeCache`（即每次自动保存）触发完整 cursor 遍历。

#### 4.6 近零使用 `shallowRef` / `markRaw`

全项目几乎未使用 `shallowRef` 或 `shallowReactive`。所有分镜、剧集、生成记录数据均为深度响应式，Vue 必须追踪每个嵌套属性。对只读 API 响应数据，`shallowRef` 可显著降低响应式开销。

### 后端

#### 4.7 `buildDramaTrashData` 全量加载到内存

**文件**: `drama_service.go`，第 1446-1531 行

一次性加载全部剧集、角色、场景、道具、九宫格、分镜、动态、图片生成、视频生成。生产剧目场景：
- 100 集 × 50 分镜 = 5,000 个分镜
- 100,000 条 image_generations
- 50,000 条 video_generations

可能导致 OOM。

#### 4.8 `cascadeDeleteDrama` 串行 10+ 次 DELETE

**文件**: `drama_service.go`，第 1533-1582 行

未使用 `ON DELETE CASCADE`（除 `resource_appearances`），必须手动有序删除。

#### 4.9 `ListImageTasksForScene` 无 LIMIT + N+1

**文件**: `drama_service.go`，第 3757-3841 行

加载全部 image_generations（无 LIMIT），且每条结果调用 `resolveImageURLFromTaskID`（1-3 次额外查询）。

---

## 第二部分：稳定性与异常处理审计

### 总体评分

| 维度 | 前端 | 后端 | 综合 |
|------|------|------|------|
| 错误捕获 | 6/10 | 4/10 | **5/10** |
| 日志可排查性 | 3/10 | 5/10 | **4/10** |
| 外部服务降级/兜底 | 3/10 | 2/10 | **2.5/10** |
| 空指针/竞态风险 | 6/10 | 4/10 | **5/10** |
| 资源泄漏 | 5/10 | 3/10 | **4/10** |
| **综合** | **4.6/10** | **3.6/10** | **4.1/10** |

---

## 五、错误是否被正确捕获

### 后端

#### 5.1 大量吞没错误 — `_ = h.taskService.UpdateTaskError(...)`

**严重程度: 高**

| 文件 | 行号 | 调用次数 |
|------|------|----------|
| `storyboard.go` | 241, 248, 254, 267, 1693 | 5 |
| `nine_grid.go` | 256, 264, 275, 700, 712, 727 | 6 |
| `drama.go` | 1265, 1274, 1279, 1290, 1297, 1502, 1506, 1520, 1526, 1532, 1541, 1548, 1558 | 13 |

`UpdateTaskError` 失败时，任务将永远处于 "processing" 状态，无错误指示。这在 goroutine 内执行，原始请求上下文已消失。

#### 5.2 DB 更新错误静默丢弃

| 文件 | 行号 | 操作 |
|------|------|------|
| `image_generation_service.go` | 1207, 1211 | 封面图更新丢弃 |
| `agent_canvas_service.go` | 83, 870 | 画布元数据更新丢弃 |
| `scene.go` | 438 | 删除前 DB 读取丢弃 |
| `storyboard.go` | 1878 | 封面图更新丢弃 |

#### 5.3 SSE 流中断未检测

**文件**: `server/api/handlers/ai_chat.go`，第 154-159 行

```go
scanner := bufio.NewScanner(resp.Body)
for scanner.Scan() {
    line := scanner.Text()
    fmt.Fprintf(c.Writer, "%s\n", line)
    c.Writer.(http.Flusher).Flush()
}
```

- `scanner.Err()` 未检查 — 上游断开时无法区分完整流和截断流
- `fmt.Fprintf` 失败（客户端断开）时静默忽略，循环继续浪费资源

### 前端

#### 5.4 SSE 解析错误静默吞没

**文件**: `web/src/api/agent.ts`，第 126-139 行

```typescript
try {
    const parsed = JSON.parse(dataPayload)
} catch {
    // Ignore parse errors for malformed SSE data
}
```

格式错误的 SSE 数据被静默吞没，服务器非标准格式的错误事件永远不会暴露。

#### 5.5 TaskCenter 空 catch 块

**文件**: `web/src/stores/taskCenter.ts`，第 62、102、129 行

`persist()` 和 `loadPersistedTasks()` 中的 `catch {}` 静默吞没 localStorage 错误，可能隐藏配额问题。

---

## 六、日志是否足够排查问题

### 后端

#### 6.1 无请求关联 ID

**严重程度: 高**

无 request ID 或 correlation ID 贯穿请求生命周期。日志中间件仅记录 method、path、status、duration。并发请求的日志条目交错，无法追踪单次请求的完整路径。

#### 6.2 Logger 未强制注入

`response.go` 第 116-122 行通过 `c.Get("logger")` 获取 logger。若 handler 未设置此 key，错误日志静默空操作，无编译时或运行时保障。

#### 6.3 容器管理使用标准 `log` 包

| 文件 | 问题 |
|------|------|
| `pkg/container/manager.go` | 使用 `log.Printf`，绕过结构化 logger 和 Sentry |
| `pkg/container/cleaner.go` | 同上 |
| `pkg/container/heartbeat.go` | 同上 |
| `pkg/claude/executor.go` | 同上 |

### 前端

#### 6.4 无集中错误监控

前端仅使用 `console.error` / `console.warn`。未配置 Sentry 或等价错误监控服务。`main.ts` 第 120-141 行的 `window.addEventListener('error', ...)` 仅处理图片/视频元素的权限回退，不处理一般 JS 错误。

---

## 七、外部服务失败时是否有降级或兜底

### 7.1 无断路器

**严重程度: 高**

全代码库无断路器模式（搜索 "circuit"、"breaker"、"backoff" 零命中）。外部 AI 服务故障时，系统继续全速率发送请求，可能加剧问题并耗尽资源。

### 7.2 HTTP 客户端无超时

**严重程度: 高**

| 文件 | 行号 | 用途 | 问题 |
|------|------|------|------|
| `ai_chat.go` | 129 | OpenClaw 转发 | `http.Client{}` 无超时 |
| `ai_chat.go` | 862 | 标题生成 | `http.Client{}` 无超时 |
| `drama.go` | 1007 | Seedance 请求 | `http.Client{}` 无超时 |
| `pkg/utils/http_client.go` | 13 | 通用 HTTP | timeout=0 时无超时 |

上游 AI 服务挂起时，请求将无限阻塞，消耗 goroutine 和连接池。

### 7.3 `context.Background()` 用于外部调用

**严重程度: 中**

多个外部服务调用使用 `context.Background()`，客户端断开时无法取消：

| 文件 | 行号 | 服务 |
|------|------|------|
| `tencent_cos_storage.go` | 83, 102, 117, 155 | 腾讯 COS Put/Delete/GetPresignedURL |
| `scene.go` | 295 | 场景操作 |
| `props.go` | 195 | 道具操作 |
| `drama.go` | 1262 | 分镜 V1 调用 |
| `drama_service.go` | 733, 3600, 3608, 3649, 3708 | Ark Asset Service |

### 7.4 AI 服务降级有限

**文件**: `video_generation_service.go`，第 1264 行

`resubmitVideoGenerationWithNextProvider` 实现了 provider 回退（最多 3 个替代），但：
- 无指数退避（立即重试）
- 无抖动（集群所有实例同时重试）

### 7.5 Redis 不可用无优雅降级

**文件**: `infrastructure/cache/redis.go`，第 19-24 行

Redis 未配置时邮件验证码不可用。但 Redis 运行时宕机后，操作直接返回连接错误传播为 500。

### 7.6 Elasticsearch — 良好降级（亮点）

ES 禁用时返回 noop 实现。记录失败仅 warn 级别不阻塞请求。中间件在后台 goroutine 中以 10 秒超时记录。

### 7.7 腾讯 COS 阻塞请求路径且无超时

**文件**: `tencent_cos_storage.go`

所有 COS 操作使用 `context.Background()` + 无超时。COS 慢或宕机时请求无限阻塞。

### 7.8 阻塞式 HTTP 下载

**文件**: `dify_service.go`

| 行号 | 操作 | 问题 |
|------|------|------|
| 1675 | `http.Get(imageURL)` + `io.ReadAll` | 阻塞 goroutine，无超时 |
| 2008 | `http.Get(fileURL)` + `io.ReadAll` | 同上 |
| 2767 | `http.Get(videoURL)` + `io.Copy` | 阻塞整个视频下载期间 |

### 7.9 `io.ReadAll` 无大小限制

| 文件 | 行号 | 风险 |
|------|------|------|
| `dify_service.go` | 1685, 2018 | 图片/文件响应无大小上限 |
| `document_parse_service.go` | 97, 157, 236 | 同上 |

恶意或配置错误的服务器可返回 GB 级数据导致 OOM。

### 前端

#### 7.10 SSE 流无 AbortController

**文件**: `web/src/api/agent.ts`，第 76-83 行

`fetch` 调用未使用 `AbortController`。用户导航离开 agent 页面时，流继续在后台消费带宽。

---

## 八、是否可能出现空指针、竞态条件、资源泄漏

### 空指针风险

#### 8.1 `generateTitleAsync` 中 `orgId` 始终为 nil

**文件**: `ai_chat.go`，第 787 行

```go
var orgId *int64  // 声明但从未赋值
```

传给 `GetChatConfigWithOrg`(811) 和 `ListChatModelsWithOrg`(818)，导致标题生成始终使用全局配置（非组织级），是正确性 bug。

#### 8.2 `creationLocks` 类型断言无安全检查

**文件**: `pkg/container/manager.go`，第 220 行

`lockValue.(*sync.Mutex)` — 若 `LoadOrStore` 返回非 `*sync.Mutex` 值会 panic。

#### 8.3 JSON unmarshaling 到 `map[string]interface{}` 无键存在检查

**文件**: `ai_chat.go`，第 457-519, 522-566, 609-747 行

多处 map 访问未检查键是否存在，AI 模型响应格式变更时可能导致静默数据丢失。

### 竞态条件

#### 8.4 `sync.Map` 永不清理 — 内存泄漏 + 潜在陈旧锁

**文件**: `pkg/container/manager.go`，第 51 行

`creationLocks sync.Map` — 条目通过 `LoadOrStore`(219) 添加但从不删除。随时间无限增长。

#### 8.5 限流器 map 无界增长

**文件**: `api/middlewares/ratelimit.go`，第 11-22 行

`requests` map 按 IP 存储时间切片。过期时间戳虽被过滤，但 key 本身永不删除。DDoS 攻击下大量唯一 IP 导致无限增长。

#### 8.6 `GetOrCreateContainer` 双重检查锁 — 读锁持有时间过长

**文件**: `pkg/container/manager.go`，第 199-258 行

读锁内调用 Docker API（`isContainerRunning`），可能阻塞其他读请求。

#### 8.7 前端并发 `sendMessage`

**文件**: `web/src/stores/agent.ts`，第 808 行

`sendMessage` 不检查 `isStreaming` 是否已为 true。双击发送按钮可能启动两个并发 SSE 流，导致消息交错和 `currentStreamingMessage` 损坏。

### 资源泄漏

#### 8.8 每请求新建 `http.Client`

**文件**: `ai_chat.go`，第 129、862 行

每次请求创建新 `http.Client{}`，各自拥有独立 transport 和连接池。连接不重用，高负载下可能耗尽文件描述符。

#### 8.9 Goroutine 泄漏 — 无生命周期追踪

以下 goroutine 通过 `go func()` 启动但无追踪或取消机制：

| 文件 | 行号 | 操作 |
|------|------|------|
| `ai_chat.go` | 325 | `generateTitleAsync` |
| `scene.go` | 237 | 场景图片生成 |
| `storyboard.go` | 219, 341, 1675 | 分镜操作 |
| `nine_grid.go` | 219, 373, 509, 663 | 九宫格操作 |
| `drama.go` | 1127, 1477 | 剧目操作 |
| `props.go` | 137 | 道具操作 |
| `script_generation.go` | 105, 237, 329, 433, 543 | 脚本生成 |
| `image_generation.go` | 692 | 图片生成 |
| `video_merge_service.go` | 102, 141 | 视频合并 |
| `image_generation_service.go` | 855 | 图片生成 |
| `storyboard_composition_service.go` | 367 | 分镜合成 |
| `dify_service.go` | 2732 | Dify 回调 |

`main.go` 关闭序列（第 151-166 行）不等待这些 goroutine 完成。`srv.Shutdown(ctx)` 5 秒超时关闭空闲连接，活跃 goroutine 继续运行。

#### 8.10 前端 SSE 流组件卸载后继续

**文件**: `web/src/api/agent.ts`，第 76-83 行

无 `AbortController`，用户导航离开后流继续运行。

#### 8.11 `loadWorkshopData` 挂载时双重触发

**文件**: `ProductionWorkshopStage.vue`，第 4653-4663 行

watch `immediate: true` + `onMounted` 同时调用 `loadWorkshopData`，数据加载两次。

---

## 九、优先改进建议

### P0 — 必须立即修复（影响生产稳定性）

| # | 问题 | 影响 | 文件 |
|---|------|------|------|
| 1 | **批量处理 N+1 查询** — `getDrama` 中角色图片状态（1.1） | 每次剧目详情页 200-400 次 DB 查询 | `drama_service.go:1020-1048` |
| 2 | **添加 Redis 缓存** — 剧目详情、图片状态、URL 解析 | 热路径零缓存，每次请求全量查询 | 全服务层 |
| 3 | **HTTP 客户端添加超时** — `ai_chat.go`、`drama.go` | 请求可能无限阻塞 | `ai_chat.go:129,862; drama.go:1007` |
| 4 | **`buildDramaTrashData` 添加 LIMIT 和列投影** | 大剧目可能 OOM | `drama_service.go:1446-1531` |
| 5 | **添加复合索引** `(image_type, resource_id, status, created_at)` | 全项目最高频查询缺少覆盖索引 | `image_generation.go` |

### P1 — 高影响改进

| # | 问题 | 影响 | 文件 |
|---|------|------|------|
| 6 | **批量处理 `resolveImageURLFromTaskID` N+1** | 30 角色+15 场景+20 道具 = 195 次查询 | `drama_service.go:2016-2051` |
| 7 | **前端添加虚拟滚动** | 100+ 分镜时 DOM 节点爆炸 | `ProductionWorkshopStage.vue` |
| 8 | **前端 deep watch 替换为细粒度 watch** | 每次属性变更触发 O(n) 深度遍历 | `flow.ts:1245-1253` |
| 9 | **SSE 流中断检测** — 检查 `scanner.Err()` 和客户端断开 | 流截断无法区分 | `ai_chat.go:154-159` |
| 10 | **Goroutine 生命周期管理** — 使用 context + WaitGroup | 服务器关闭时 goroutine 泄漏 | 多个 handler 文件 |
| 11 | **限流器 map 定期清理** | DDoS 下内存无限增长 | `ratelimit.go:11-22` |
| 12 | **`creationLocks` sync.Map 定期清理** | 无界内存泄漏 | `container/manager.go:51` |

### P2 — 中等影响改进

| # | 问题 | 影响 | 文件 |
|---|------|------|------|
| 13 | 前端使用 `shallowRef` 替代深层响应式 | 大幅降低 Vue 响应式追踪开销 | 全 stores |
| 14 | `rememberPreview` 用直接赋值替代展开替换 | 批量刷新时避免 N 次完整克隆 | `mediaPreview.ts:30-37` |
| 15 | 替换 `JSON.stringify` 哈希为快速哈希函数 | 主线程同步阻塞 | `flow.ts:803-805` |
| 16 | 批量 `loadAllShotDialogueRecordsForTable` API 调用 | 100+ 并发 HTTP 请求 | `ProductionWorkshopStage.vue:2634-2678` |
| 17 | 添加断路器模式 | 外部服务故障时系统持续受压 | 全项目 |
| 18 | 添加请求关联 ID | 无法追踪单请求完整日志 | 日志中间件 |
| 19 | `ListDramas` 移除 Storyboard Preload | 列表页加载 10000 行不需要的分镜 | `drama_service.go:1214-1220` |
| 20 | `sendMessage` 添加并发保护 | 双击可能启动两个 SSE 流 | `agent.ts:808` |
| 21 | SSE fetch 添加 AbortController | 组件卸载后流继续 | `agent.ts:76-83` |
| 22 | `_ = UpdateTaskError(...)` 改为检查错误 | 任务可能永远卡在 processing | 多个 handler |
| 23 | `generateTitleAsync` 修复 nil orgId | 标题生成始终用全局配置 | `ai_chat.go:787` |
| 24 | 容器管理使用结构化 logger | 容器问题难以排查 | `pkg/container/*.go` |
| 25 | 前端添加 Sentry 错误监控 | 前端错误无监控 | 全前端 |
