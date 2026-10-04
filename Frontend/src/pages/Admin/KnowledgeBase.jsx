import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Dropdown,
  Drawer,
  Empty,
  InputNumber,
  message,
  Modal,
  Pagination,
  Slider,
  Spin,
  Switch,
  Tag,
  Tabs,
  Tooltip,
} from 'antd'
import './KnowledgeBase.css'
import {
  getKnowledgeBases,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
  toggleKnowledgeBase,
  getKnowledgeDocuments,
  getKnowledgeDocument,
  uploadKnowledgeDocument,
  deleteKnowledgeDocument,
  getKnowledgeSegments,
  toggleKnowledgeSegment,
  deleteKnowledgeSegment,
  reembedKnowledgeSegment,
  embedMissingKnowledgeSegments,
  reembedKnowledgeBase,
  reembedKnowledgeAll,
  searchKnowledge,
  getEmbeddingConfig,
  saveEmbeddingConfig,
  testEmbeddingConfig,
  getRetrievalConfig,
  saveRetrievalConfig,
} from '../../api/admin/knowledge'
import { confirmAction } from '../../components/confirmDialogService'

const EMPTY_KB_FORM = {
  name: '',
  description: '',
  color: '#1677ff',
  enabled: true,
}

// 知识库卡片可选主题色
const KB_COLORS = ['#1677ff', '#52c41a', '#722ed1', '#fa8c16', '#13c2c2', '#eb2f96', '#f5222d', '#faad14']

// 知识库标识：取名称首字符作为简洁标记（避免 emoji 图标）
const kbMark = (name) => {
  const text = String(name || '').trim()
  return text ? text.slice(0, 1).toUpperCase() : '库'
}

// 文档类型标签（替代 emoji 图标）
const DOC_TYPE_LABELS = {
  pdf: 'PDF',
  docx: 'DOCX',
  md: 'MD',
  txt: 'TXT',
  manual: '手册',
}

const docTypeLabel = (type) => DOC_TYPE_LABELS[type] || 'DOC'

// Embedding 预设服务商（OpenAI 兼容 /v1/embeddings 接口），选中后自动填入 base-url 与推荐模型
const EMBEDDING_PROVIDERS = [
  {
    key: 'siliconflow',
    name: '硅基流动 SiliconFlow',
    baseUrl: 'https://api.siliconflow.cn',
    models: [
      'BAAI/bge-m3',
      'BAAI/bge-large-zh-v1.5',
      'BAAI/bge-small-zh-v1.5',
      'netease-youdao/bce-embedding-base_v1',
    ],
  },
  {
    key: 'dashscope',
    name: '阿里云百炼 DashScope',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode',
    models: ['text-embedding-v3', 'text-embedding-v2'],
  },
  {
    key: 'hunyuan',
    name: '腾讯混元 Hunyuan',
    baseUrl: 'https://api.hunyuan.cloud.tencent.com',
    models: ['hunyuan-embedding'],
  },
]

const formatTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '—')

const formatPercent = (score) => `${Math.round(score * 1000) / 10}%`

// 文件大小展示（批量上传文件列表用）
const formatFileSize = (bytes) => {
  if (!bytes && bytes !== 0) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

// 从上传异常中提取可读错误信息（批量逐份失败时展示）
const importErrorText = (err) => {
  if (err?.code === 'ERR_CANCELED' || err?.name === 'CanceledError' || err?.name === 'AbortError') {
    return '已取消'
  }
  const raw = err?.response?.data?.message || err?.message || '导入失败'
  return String(raw).replace(/^Error:\s*/i, '').trim() || '导入失败'
}

/** 单批上传文件数上限 */
const BATCH_UPLOAD_MAX = 20

/** 向量化状态轮询间隔 / 最长等待 */
const EMBED_POLL_INTERVAL_MS = 2000
const EMBED_POLL_MAX_MS = 5 * 60 * 1000

const isProcessingDoc = (doc) => doc?.status === 'PROCESSING'

const embedStatusLabel = (r) => {
  if (!r?.ok) return null
  if (r.document?.status === 'PROCESSING' || r.embeddingPending) return '向量化中'
  if (typeof r.embedded === 'number' && r.embedded > 0) return `${r.embedded} 个已向量化`
  return '切块已入库'
}

// 标题展示兜底：超过 36 字则按最后一个标点/空格截断，避免"残句"+"标题=内容"
const TITLE_DISPLAY_MAX = 36
const truncateTitle = (title) => {
  const text = String(title || '').trim()
  if (text.length <= TITLE_DISPLAY_MAX) return text
  const slice = text.slice(0, TITLE_DISPLAY_MAX)
  const soft = /[。，；：！？…、,;:!?. ]/.exec(slice)
  if (soft && soft.index >= TITLE_DISPLAY_MAX * 0.5) {
    return slice.slice(0, soft.index)
  }
  return slice
}

const snippetOf = (text, max = 110) =>
  String(text || '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, max) +
  (String(text || '')
    .replace(/\s+/g, ' ')
    .trim().length > max
    ? '…'
    : '')

// 命中关键词高亮（按原文本切片渲染，React 自动转义，无 XSS 风险）
function HighlightText({ text = '', query = '' }) {
  const q = String(query || '').trim()
  const content = String(text || '')
  if (!q) return content
  const lower = content.toLowerCase()
  const ql = q.toLowerCase()
  const parts = []
  let i = 0
  let idx = lower.indexOf(ql)
  while (idx !== -1) {
    if (idx > i) parts.push(content.slice(i, idx))
    parts.push(
      <mark key={`${idx}-${i}`} className="kb-mark">
        {content.slice(idx, idx + q.length)}
      </mark>,
    )
    i = idx + q.length
    idx = lower.indexOf(ql, i)
  }
  if (i < content.length) parts.push(content.slice(i))
  return parts
}

// 导入结果汇总卡片：单份成功时展示分段/向量化/字符数；批量或多份时展示成功份数与合计
function BatchImportSummary({ results }) {
  if (!Array.isArray(results) || results.length === 0) return null
  const anyProcessing = results.some(
    (r) => r.ok && (r.document?.status === 'PROCESSING' || r.embeddingPending),
  )
  if (results.length === 1 && results[0].ok) {
    const r = results[0]
    const items = [
      { value: r.imported ?? 0, label: '成功导入分段' },
      {
        value: anyProcessing ? '进行中' : r.embedded ?? 0,
        label: anyProcessing ? '向量化中' : '已向量化',
      },
      { value: r.document?.charCount ?? 0, label: '总字符数' },
    ]
    return (
      <div className="kb-import-summary">
        {items.map((item) => (
          <div key={item.label} className="kb-import-summary-item">
            <b>{item.value}</b>
            <span>{item.label}</span>
          </div>
        ))}
        {r.truncated ? (
          <div className="kb-batch-result-error" style={{ marginTop: 8 }}>
            已截断至 500 段（超出部分未导入）
          </div>
        ) : null}
      </div>
    )
  }
  const okCount = results.filter((r) => r.ok).length
  const imported = results.reduce((sum, r) => sum + (r.imported || 0), 0)
  const embedded = results.reduce((sum, r) => sum + (r.embedded || 0), 0)
  const truncatedCount = results.filter((r) => r.truncated).length
  const items = [
    { value: `${okCount}/${results.length}`, label: '成功文档' },
    { value: imported, label: '成功导入分段' },
    {
      value: anyProcessing ? '进行中' : embedded,
      label: anyProcessing ? '向量化中' : '已向量化',
    },
  ]
  return (
    <div className="kb-import-summary">
      {items.map((item) => (
        <div key={item.label} className="kb-import-summary-item">
          <b>{item.value}</b>
          <span>{item.label}</span>
        </div>
      ))}
      {truncatedCount > 0 ? (
        <div className="kb-batch-result-error" style={{ marginTop: 8 }}>
          {truncatedCount} 份文档已截断至 500 段
        </div>
      ) : null}
    </div>
  )
}

function KnowledgeBase() {
  // ---------------- 列表状态 ----------------
  const [view, setView] = useState('list')
  const [kbs, setKbs] = useState([])
  const [stats, setStats] = useState({})
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)

  // 知识库弹窗
  const [kbModalOpen, setKbModalOpen] = useState(false)
  const [editingKbId, setEditingKbId] = useState(null)
  const [kbForm, setKbForm] = useState(EMPTY_KB_FORM)

  // ---------------- 详情状态 ----------------
  const [activeKb, setActiveKb] = useState(null)
  const [documents, setDocuments] = useState([])
  const [documentLoading, setDocumentLoading] = useState(false)
  const [selectedDocId, setSelectedDocId] = useState('all')
  const [allSegments, setAllSegments] = useState([])
  const [docSegments, setDocSegments] = useState([])
  const [segmentsLoading, setSegmentsLoading] = useState(false)
  const [segKeyword, setSegKeyword] = useState('')
  const [segPage, setSegPage] = useState(1)
  const [segPageSize, setSegPageSize] = useState(10)
  const [togglingSegId, setTogglingSegId] = useState(null)

  // 上传文档（支持多选批量）
  const docInputRef = useRef(null)
  const uploadAbortRef = useRef(null)
  const embedPollRef = useRef(null)
  const [uploadModalOpen, setUploadModalOpen] = useState(false)
  const [pendingFiles, setPendingFiles] = useState([])
  const [uploadingDoc, setUploadingDoc] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(null)
  const [uploadCategory, setUploadCategory] = useState('')
  const [uploadSource, setUploadSource] = useState('')
  const [importResults, setImportResults] = useState([])
  const [importResultOpen, setImportResultOpen] = useState(false)

  // 命中测试抽屉
  const [hitOpen, setHitOpen] = useState(false)
  const [hitQuery, setHitQuery] = useState('')
  const [hitHits, setHitHits] = useState([])
  const [hitLoading, setHitLoading] = useState(false)
  const [hitSearched, setHitSearched] = useState(false)
  const [hitExpandedIds, setHitExpandedIds] = useState([])

  // 知识库设置抽屉
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [settingsTab, setSettingsTab] = useState('embedding')
  const [embeddingConfig, setEmbeddingConfig] = useState(null)
  const [configForm, setConfigForm] = useState({
    baseUrl: '',
    model: '',
    apiKey: '',
    enabled: true,
  })
  const [configProviderKey, setConfigProviderKey] = useState('')
  const [configSaving, setConfigSaving] = useState(false)
  const [configTesting, setConfigTesting] = useState(false)
  const [configTest, setConfigTest] = useState(null)
  const [retrievalForm, setRetrievalForm] = useState({
    topK: 3,
    similarityThreshold: 0.6,
    chunkLength: 450,
  })

  // ---------------- 数据加载 ----------------

  const fetchKbs = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getKnowledgeBases()
      setKbs(Array.isArray(res?.data) ? res.data : [])
      if (res?.stats) setStats(res.stats)
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchKbs()
  }, [fetchKbs])

  const fetchDocuments = useCallback(async (kbId) => {
    setDocumentLoading(true)
    try {
      const res = await getKnowledgeDocuments(kbId)
      const list = Array.isArray(res?.data) ? res.data : []
      setDocuments(list)
      return list
    } catch (_e) {
      // 拦截器已提示
      return []
    } finally {
      setDocumentLoading(false)
    }
  }, [])

  const stopEmbedPoll = useCallback(() => {
    if (embedPollRef.current) {
      clearInterval(embedPollRef.current)
      embedPollRef.current = null
    }
  }, [])

  /** 上传后轮询文档列表，直到无 PROCESSING 或超时 */
  const startEmbedPoll = useCallback(
    (kbId) => {
      stopEmbedPoll()
      const startedAt = Date.now()
      embedPollRef.current = setInterval(async () => {
        if (!kbId || Date.now() - startedAt > EMBED_POLL_MAX_MS) {
          stopEmbedPoll()
          return
        }
        try {
          const res = await getKnowledgeDocuments(kbId)
          const list = Array.isArray(res?.data) ? res.data : []
          setDocuments(list)
          setImportResults((prev) => {
            if (!prev.length) return prev
            return prev.map((r) => {
              if (!r.ok || !r.document?.id) return r
              const latest = list.find((d) => d.id === r.document.id)
              if (!latest) return r
              return {
                ...r,
                document: { ...r.document, ...latest },
                embeddingPending: latest.status === 'PROCESSING',
              }
            })
          })
          if (!list.some(isProcessingDoc)) {
            stopEmbedPoll()
          }
        } catch (_e) {
          // 轮询失败不打断，下次再试
        }
      }, EMBED_POLL_INTERVAL_MS)
    },
    [stopEmbedPoll],
  )

  useEffect(() => () => stopEmbedPoll(), [stopEmbedPoll])

  const fetchAllSegments = useCallback(async (kbId) => {
    setSegmentsLoading(true)
    try {
      const res = await getKnowledgeSegments(kbId, '')
      setAllSegments(Array.isArray(res?.data) ? res.data : [])
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setSegmentsLoading(false)
    }
  }, [])

  // 初始加载 embedding / 检索配置
  useEffect(() => {
    ;(async () => {
      try {
        const [embRes, retRes] = await Promise.all([getEmbeddingConfig(), getRetrievalConfig()])
        if (embRes?.data) setEmbeddingConfig(embRes.data)
        if (retRes?.data) {
          // 页面最低限度：Top-K ≥ 3、相似度阈值 ≥ 0.6（旧配置低于下限时钳制到下限）
          const { topK, similarityThreshold, chunkLength } = retRes.data
          setRetrievalForm({
            topK: Math.max(3, Number(topK) || 3),
            similarityThreshold: Math.max(0.6, Number(similarityThreshold) || 0.6),
            chunkLength: Number(chunkLength) || 450,
          })
        }
      } catch (_e) {
        // 拦截器已提示
      }
    })()
  }, [])

  // ---------------- 知识库操作 ----------------

  const openKbDetail = async (kb) => {
    setActiveKb(kb)
    setView('detail')
    setSelectedDocId('all')
    setSegKeyword('')
    setSegPage(1)
    await Promise.all([fetchDocuments(kb.id), fetchAllSegments(kb.id)])
  }

  const backToList = async () => {
    setView('list')
    setActiveKb(null)
    setSelectedDocId('all')
    setAllSegments([])
    setDocSegments([])
    fetchKbs()
  }

  const openCreateKb = () => {
    setEditingKbId(null)
    setKbForm({ ...EMPTY_KB_FORM })
    setKbModalOpen(true)
  }

  const openEditKb = (kb) => {
    setEditingKbId(kb.id)
    setKbForm({
      name: kb.name || '',
      description: kb.description || '',
      color: kb.color || '#1677ff',
      enabled: kb.enabled !== false,
    })
    setKbModalOpen(true)
  }

  const handleKbFormChange = (key, value) => {
    setKbForm((prev) => ({ ...prev, [key]: value }))
  }

  const handleSaveKb = async () => {
    if (busy) return
    if (!kbForm.name.trim()) {
      message.warning('请填写知识库名称')
      return
    }
    setBusy(true)
    try {
      const payload = {
        name: kbForm.name.trim(),
        description: kbForm.description.trim(),
        color: kbForm.color,
        enabled: kbForm.enabled,
      }
      if (editingKbId) {
        const res = await updateKnowledgeBase(editingKbId, payload)
        message.success('知识库已更新')
        if (activeKb?.id === editingKbId && res?.data) setActiveKb(res.data)
      } else {
        await createKnowledgeBase(payload)
        message.success('知识库已创建')
      }
      setKbModalOpen(false)
      fetchKbs()
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setBusy(false)
    }
  }

  const handleDeleteKb = async (kb) => {
    const ok = await confirmAction({
      title: '删除知识库？',
      message: `「${kb.name}」下的 ${kb.segmentCount ?? 0} 个分段与 ${kb.docCount ?? 0} 份文档将一并删除，不可恢复。`,
      confirmText: '删除知识库',
      tone: 'danger',
    })
    if (!ok) return
    setBusy(true)
    try {
      await deleteKnowledgeBase(kb.id)
      message.success('知识库已删除')
      if (activeKb?.id === kb.id) {
        backToList()
      } else {
        fetchKbs()
      }
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setBusy(false)
    }
  }

  const handleToggleKb = async (kb, next) => {
    setBusy(true)
    try {
      await toggleKnowledgeBase(kb.id)
      if (activeKb?.id === kb.id) {
        setActiveKb((prev) => ({ ...prev, enabled: next }))
      } else {
        setKbs((prev) => prev.map((item) => (item.id === kb.id ? { ...item, enabled: next } : item)))
      }
      message.success(next ? '知识库已启用，其分段参与检索' : '知识库已停用，其分段不再参与检索')
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setBusy(false)
    }
  }

  // ---------------- 文档操作 ----------------

  const selectDoc = async (docId) => {
    setSelectedDocId(docId)
    setSegKeyword('')
    setSegPage(1)
    if (docId === 'all') {
      await fetchAllSegments(activeKb.id)
    } else {
      setSegmentsLoading(true)
      try {
        const res = await getKnowledgeDocument(docId)
        const data = res?.data
        setDocSegments(Array.isArray(data?.segments) ? data.segments : [])
      } catch (_e) {
        // 拦截器已提示
      } finally {
        setSegmentsLoading(false)
      }
    }
  }

  const handleChooseFile = (e) => {
    const picked = Array.from(e.target.files || [])
    e.target.value = ''
    if (!picked.length) return
    let files = picked.filter((f) => /\.(pdf|docx|txt|md)$/i.test(f.name))
    if (files.length !== picked.length) {
      message.warning('已忽略不支持的文件类型（仅支持 PDF、Word(.docx)、TXT/Markdown）')
    }
    if (!files.length) return
    if (files.length > BATCH_UPLOAD_MAX) {
      message.warning(`单批最多上传 ${BATCH_UPLOAD_MAX} 份，已截取前 ${BATCH_UPLOAD_MAX} 份`)
      files = files.slice(0, BATCH_UPLOAD_MAX)
    }
    setPendingFiles(files)
    setUploadCategory('')
    setUploadSource('')
    setUploadModalOpen(true)
  }

  const handleCancelUpload = () => {
    uploadAbortRef.current?.abort()
  }

  const handleConfirmUpload = async () => {
    const files = pendingFiles
    if (!files.length || !activeKb) return
    setUploadModalOpen(false)
    setUploadingDoc(true)
    setImportResults([])
    setUploadProgress({ current: 0, total: files.length, fileName: '' })
    const controller = new AbortController()
    uploadAbortRef.current = controller
    const results = []
    let cancelled = false
    try {
      for (let i = 0; i < files.length; i++) {
        if (controller.signal.aborted) {
          cancelled = true
          for (let j = i; j < files.length; j++) {
            results.push({
              fileName: files[j].name,
              ok: false,
              imported: 0,
              embedded: 0,
              document: null,
              truncated: false,
              embeddingPending: false,
              errorMessage: '已取消',
            })
          }
          break
        }
        const file = files[i]
        setUploadProgress({ current: i + 1, total: files.length, fileName: file.name })
        try {
          const res = await uploadKnowledgeDocument(activeKb.id, file, {
            category: uploadCategory.trim() || undefined,
            source: uploadSource.trim() || undefined,
            silentError: true,
            signal: controller.signal,
          })
          const result = res?.data || {}
          const pending = result.document?.status === 'PROCESSING'
          results.push({
            fileName: result.fileName || file.name,
            ok: true,
            imported: result.imported ?? 0,
            embedded: result.embedded ?? 0,
            document: result.document,
            truncated: !!result.truncated,
            embeddingPending: pending,
          })
        } catch (err) {
          const aborted =
            controller.signal.aborted ||
            err?.code === 'ERR_CANCELED' ||
            err?.name === 'CanceledError' ||
            err?.name === 'AbortError'
          if (aborted) {
            cancelled = true
            results.push({
              fileName: file.name,
              ok: false,
              imported: 0,
              embedded: 0,
              document: null,
              truncated: false,
              embeddingPending: false,
              errorMessage: '已取消',
            })
            for (let j = i + 1; j < files.length; j++) {
              results.push({
                fileName: files[j].name,
                ok: false,
                imported: 0,
                embedded: 0,
                document: null,
                truncated: false,
                embeddingPending: false,
                errorMessage: '已取消',
              })
            }
            break
          }
          results.push({
            fileName: file.name,
            ok: false,
            imported: 0,
            embedded: 0,
            document: null,
            truncated: false,
            embeddingPending: false,
            errorMessage: importErrorText(err),
          })
        }
      }
    } finally {
      uploadAbortRef.current = null
      setUploadProgress(null)
      setUploadingDoc(false)
      setImportResults(results)
      setImportResultOpen(true)
      await fetchDocuments(activeKb.id)
    }
    const okCount = results.filter((r) => r.ok).length
    const totalImported = results.reduce((sum, r) => sum + (r.imported || 0), 0)
    const pendingEmbed = results.some((r) => r.ok && r.embeddingPending)
    if (cancelled && okCount === 0) {
      message.info('已取消上传')
    } else if (okCount === files.length) {
      message.success(
        pendingEmbed
          ? `切块导入完成：${okCount} 份文档，共 ${totalImported} 个分段，向量化进行中`
          : `导入完成：${okCount} 份文档，共 ${totalImported} 个分段`,
      )
    } else {
      message.warning(
        `导入结束：成功 ${okCount}/${files.length} 份，失败 ${files.length - okCount} 份${cancelled ? '（含取消）' : ''}，详见导入结果`,
      )
    }
    if (pendingEmbed) {
      startEmbedPoll(activeKb.id)
    }
    if (results.length === 1 && results[0].ok && results[0].document?.id) {
      await selectDoc(results[0].document.id)
    } else if (okCount > 0) {
      await fetchAllSegments(activeKb.id)
    }
  }

  const handleDeleteDocument = async (doc) => {
    const ok = await confirmAction({
      title: '删除文档？',
      message: `「${doc.name}」下的 ${doc.segmentCount ?? 0} 个分段将一并删除，不可恢复。`,
      confirmText: '删除文档',
      tone: 'danger',
    })
    if (!ok) return
    setBusy(true)
    try {
      await deleteKnowledgeDocument(doc.id)
      message.success('文档已删除')
      if (selectedDocId === doc.id) {
        setSelectedDocId('all')
        await fetchAllSegments(activeKb.id)
      }
      await fetchDocuments(activeKb.id)
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setBusy(false)
    }
  }

  // ---------------- 分段操作 ----------------

  const displayedSegments = useMemo(() => {
    const source = selectedDocId === 'all' ? allSegments : docSegments
    const kw = segKeyword.trim().toLowerCase()
    if (!kw) return source
    return source.filter((seg) =>
      [seg.title, seg.content, seg.keywords, seg.source, seg.category].some((v) =>
        (v || '').toLowerCase().includes(kw),
      ),
    )
  }, [selectedDocId, allSegments, docSegments, segKeyword])

  const maxSegPage = Math.max(1, Math.ceil(displayedSegments.length / segPageSize))
  const currentSegPage = Math.min(segPage, maxSegPage)
  const pageSegments = useMemo(() => {
    const start = (currentSegPage - 1) * segPageSize
    return displayedSegments.slice(start, start + segPageSize)
  }, [displayedSegments, currentSegPage, segPageSize])

  const startIdx = (currentSegPage - 1) * segPageSize

  const missingEmbeddedInKb = useMemo(
    () => (selectedDocId === 'all' ? allSegments : docSegments).filter((s) => !s.embedded).length,
    [selectedDocId, allSegments, docSegments],
  )

  const handleToggleSegment = async (seg) => {
    setTogglingSegId(seg.id)
    try {
      await toggleKnowledgeSegment(seg.id)
      const apply = (list) => list.map((s) => (s.id === seg.id ? { ...s, enabled: !s.enabled } : s))
      if (selectedDocId === 'all') setAllSegments((prev) => apply(prev))
      else setDocSegments((prev) => apply(prev))
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setTogglingSegId(null)
    }
  }

  const handleDeleteSegment = async (seg) => {
    const ok = await confirmAction({
      title: '删除分段？',
      message: `「${seg.title}」删除后不可恢复。`,
      confirmText: '删除分段',
      tone: 'danger',
    })
    if (!ok) return
    setBusy(true)
    try {
      await deleteKnowledgeSegment(seg.id)
      message.success('分段已删除')
      const apply = (list) => list.filter((s) => s.id !== seg.id)
      if (selectedDocId === 'all') setAllSegments((prev) => apply(prev))
      else setDocSegments((prev) => apply(prev))
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setBusy(false)
    }
  }

  const handleReembedSegment = async (seg) => {
    setBusy(true)
    try {
      const ok = await reembedKnowledgeSegment(seg.id)
      message.success(ok ? '分段已重新向量化' : '向量化失败（请检查 Embedding 配置）')
      const apply = (list) => list.map((s) => (s.id === seg.id ? { ...s, embedded: ok } : s))
      if (selectedDocId === 'all') setAllSegments((prev) => apply(prev))
      else setDocSegments((prev) => apply(prev))
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setBusy(false)
    }
  }

  const handleEmbedMissing = async () => {
    setBusy(true)
    try {
      const res = await embedMissingKnowledgeSegments(activeKb.id)
      message.success(`已补齐向量：${res?.data?.embedded ?? 0} 个分段`)
      await refreshCurrentSegments()
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setBusy(false)
    }
  }

  const handleReembedKb = async () => {
    const ok = await confirmAction({
      title: '重新向量化本知识库？',
      message: `将对「${activeKb.name}」全部分段重新调用 embedding 服务生成向量，耗时取决于分段数与网络。`,
      confirmText: '开始向量化',
    })
    if (!ok) return
    setBusy(true)
    try {
      const res = await reembedKnowledgeBase(activeKb.id)
      message.success(`重新向量化完成：${res?.data?.reembedded ?? 0} 个分段`)
      await refreshCurrentSegments()
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setBusy(false)
    }
  }

  const refreshCurrentSegments = async () => {
    if (selectedDocId === 'all') {
      await fetchAllSegments(activeKb.id)
    } else {
      await selectDoc(selectedDocId)
    }
  }

  // ---------------- 知识库设置抽屉 ----------------

  const openSettings = async (tab = 'embedding') => {
    setConfigTest(null)
    setSettingsTab(tab)
    setSettingsOpen(true)
    try {
      const [embRes, retRes] = await Promise.all([getEmbeddingConfig(), getRetrievalConfig()])
      const emb = embRes?.data
      if (emb) setEmbeddingConfig(emb)
      const baseUrl = (emb?.baseUrl || '').trim()
      const matched = EMBEDDING_PROVIDERS.find((p) => p.baseUrl === baseUrl) || null
      setConfigProviderKey(matched ? matched.key : '')
      setConfigForm({
        baseUrl,
        model: emb?.model || (matched ? matched.models[0] : ''),
        apiKey: '',
        enabled: emb?.enabled !== false,
      })
      if (retRes?.data) setRetrievalForm(retRes.data)
    } catch (_e) {
      // 拦截器已提示
    }
  }

  const handleConfigChange = (key, value) => {
    setConfigForm((prev) => ({ ...prev, [key]: value }))
    if (key === 'baseUrl') {
      const provider = EMBEDDING_PROVIDERS.find((p) => p.key === configProviderKey)
      if (provider && value.trim() !== provider.baseUrl) {
        setConfigProviderKey('')
      }
    }
  }

  const handleConfigProviderChange = (key) => {
    const provider = EMBEDDING_PROVIDERS.find((p) => p.key === key) || null
    setConfigProviderKey(key)
    setConfigForm((prev) => ({
      ...prev,
      baseUrl: provider ? provider.baseUrl : prev.baseUrl,
      model: provider ? provider.models[0] : prev.model,
    }))
  }

  const handleTestConfig = async () => {
    if (!configForm.baseUrl.trim() || !configForm.model.trim()) {
      message.warning('请填写服务地址和模型')
      return
    }
    if (!configForm.apiKey.trim() && !embeddingConfig?.hasApiKey) {
      message.warning('请输入 API Key')
      return
    }
    setConfigTesting(true)
    setConfigTest(null)
    try {
      const res = await testEmbeddingConfig({
        baseUrl: configForm.baseUrl.trim(),
        model: configForm.model.trim(),
        apiKey: configForm.apiKey.trim() || undefined,
      })
      setConfigTest(res?.data || { ok: false, message: '无返回' })
    } catch (_e) {
      setConfigTest({ ok: false, message: '请求失败' })
    } finally {
      setConfigTesting(false)
    }
  }

  const handleSaveSettings = async () => {
    if (!configForm.baseUrl.trim() || !configForm.model.trim()) {
      message.warning('请填写服务地址和模型')
      return
    }
    setConfigSaving(true)
    try {
      const [embRes, retRes] = await Promise.all([
        saveEmbeddingConfig({
          baseUrl: configForm.baseUrl.trim(),
          model: configForm.model.trim(),
          apiKey: configForm.apiKey.trim() || undefined,
          enabled: configForm.enabled,
        }),
        saveRetrievalConfig({
          topK: retrievalForm.topK,
          similarityThreshold: retrievalForm.similarityThreshold,
          chunkLength: retrievalForm.chunkLength,
        }),
      ])
      if (embRes?.data) setEmbeddingConfig(embRes.data)
      if (retRes?.data) setRetrievalForm(retRes.data)
      setConfigForm((prev) => ({ ...prev, apiKey: '' }))
      message.success('知识库配置已保存，检索与向量化将使用新配置')
      fetchKbs()
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setConfigSaving(false)
    }
  }

  // ---------------- 数据维护（全局，设置抽屉内） ----------------

  const handleEmbedMissingAll = async () => {
    setBusy(true)
    try {
      const res = await embedMissingKnowledgeSegments(undefined)
      message.success(`已补齐向量：${res?.data?.embedded ?? 0} 个分段`)
      fetchKbs()
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setBusy(false)
    }
  }

  const handleReembedAll = async () => {
    const ok = await confirmAction({
      title: '全部分段重新向量化？',
      message: '将对所有知识库的全部分段重新调用 embedding 服务生成向量，耗时较长。',
      confirmText: '开始向量化',
    })
    if (!ok) return
    setBusy(true)
    try {
      const res = await reembedKnowledgeAll()
      message.success(`重新向量化完成：${res?.data?.reembedded ?? 0} 个分段`)
      fetchKbs()
    } catch (_e) {
      // 拦截器已提示
    } finally {
      setBusy(false)
    }
  }

  // ---------------- 命中测试 ----------------

  const handleHitSearch = async () => {
    if (!hitQuery.trim()) return
    setHitLoading(true)
    setHitSearched(false)
    try {
      const res = await searchKnowledge(hitQuery.trim(), 5, view === 'detail' ? activeKb?.id : undefined)
      setHitHits(Array.isArray(res?.data) ? res.data : [])
      setHitSearched(true)
    } catch (_e) {
      setHitHits([])
      setHitSearched(true)
    } finally {
      setHitLoading(false)
    }
  }

  const toggleHitExpand = (id) => {
    setHitExpandedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    )
  }

  const similarityThreshold = retrievalForm.similarityThreshold || 0

  const embeddingReady = Boolean(embeddingConfig?.baseUrl && embeddingConfig?.enabled !== false)

  // 知识库详情「维护」菜单
  const maintenanceMenu = {
    items: [
      {
        key: 'embed-missing',
        label: `补齐向量${missingEmbeddedInKb > 0 ? `（缺 ${missingEmbeddedInKb}）` : ''}`,
        disabled: missingEmbeddedInKb === 0,
      },
      { key: 'reembed', label: '本库全部分段重新向量化' },
    ],
    onClick: ({ key }) => {
      if (key === 'embed-missing') handleEmbedMissing()
      else if (key === 'reembed') handleReembedKb()
    },
  }

  return (
    <div className="knowledge-base-page">
      {/* ================= 列表视图 ================= */}
      {view === 'list' && (
        <>
          <header className="knowledge-base-header">
            <div>
              <h1>AI 知识库</h1>
              <p className="knowledge-base-desc">
                面向软件技术专业群的教学实训与岗位技能知识（RAG）。学生 AI
                聊天会先检索知识库再回答，并标注来源。
              </p>
            </div>
            <div className="knowledge-base-actions">
              <button type="button" className="btn btn-default" onClick={() => setHitOpen(true)} disabled={busy}>
                命中测试
              </button>
              <button type="button" className="btn btn-default" onClick={() => openSettings()} disabled={busy}>
                知识库设置
              </button>
              <button type="button" className="btn btn-primary" onClick={openCreateKb} disabled={busy}>
                ＋ 新建知识库
              </button>
            </div>
          </header>

          <section className="knowledge-base-stats">
            <div className="kb-stat-card">
              <span className="kb-stat-value">{stats.kbCount ?? 0}</span>
              <span className="kb-stat-label">知识库</span>
            </div>
            <div className="kb-stat-card">
              <span className="kb-stat-value">{stats.docCount ?? 0}</span>
              <span className="kb-stat-label">文档</span>
            </div>
            <div className="kb-stat-card">
              <span className="kb-stat-value">{stats.segmentCount ?? 0}</span>
              <span className="kb-stat-label">分段</span>
              {stats.enabledCount != null && (
                <span className="kb-stat-sub">已启用 {stats.enabledCount}</span>
              )}
            </div>
            <div className="kb-stat-card">
              <span className="kb-stat-value">{stats.embeddedCount ?? 0}</span>
              <span className="kb-stat-label">已向量化</span>
              <span className="kb-stat-sub">共 {stats.segmentCount ?? 0} 个分段</span>
            </div>
          </section>

          {loading ? (
            <div className="kb-table-loading">
              <Spin tip="加载中…" size="large" />
            </div>
          ) : kbs.length === 0 ? (
            <div className="kb-guide">
              <h3>开始搭建你的知识库</h3>
              <p className="kb-guide-desc">三步即可让学生 AI 聊天基于你的知识回答</p>
              <div className="kb-guide-steps">
                <div className="kb-guide-step">
                  <span className="kb-guide-num">1</span>
                  <b>配置向量模型</b>
                  {embeddingReady ? (
                    <>
                      <p>已配置 {embeddingConfig?.model || '向量模型'}，知识可被语义检索</p>
                      <button type="button" className="btn btn-default" onClick={() => openSettings()}>
                        修改配置
                      </button>
                    </>
                  ) : (
                    <>
                      <p>选择服务商并填入 API Key，知识才能被语义检索</p>
                      <button type="button" className="btn btn-default" onClick={() => openSettings()}>
                        去配置
                      </button>
                    </>
                  )}
                </div>
                <div className="kb-guide-step">
                  <span className="kb-guide-num">2</span>
                  <b>创建知识库并导入知识</b>
                  <p>新建知识库，上传 PDF / Word / TXT / Markdown 文档导入知识</p>
                  <button type="button" className="btn btn-default" onClick={openCreateKb}>
                    新建知识库
                  </button>
                </div>
                <div className="kb-guide-step">
                  <span className="kb-guide-num">3</span>
                  <b>测试检索效果</b>
                  <p>输入真实问题，确认知识库能命中相关内容</p>
                  <button type="button" className="btn btn-default" onClick={() => setHitOpen(true)}>
                    命中测试
                  </button>
                </div>
              </div>
            </div>
          ) : (
            <div className="kb-card-grid">
              {kbs.map((kb) => (
                <div key={kb.id} className="kb-card" onClick={() => openKbDetail(kb)}>
                  <div className="kb-card-top">
                    <span
                      className="kb-card-icon"
                      style={{ background: (kb.color || '#1677ff') + '1a', color: kb.color || '#1677ff' }}
                    >
                      {kbMark(kb.name)}
                    </span>
                    <div className="kb-card-title-wrap">
                      <div className="kb-card-title">{kb.name}</div>
                      <div className="kb-card-meta">
                        {kb.docCount ?? 0} 份文档 · {kb.segmentCount ?? 0} 个分段
                      </div>
                    </div>
                    <Switch
                      size="small"
                      checked={kb.enabled !== false}
                      onClick={(checked, e) => {
                        e.stopPropagation()
                        handleToggleKb(kb, checked)
                      }}
                      checkedChildren="开"
                      unCheckedChildren="关"
                    />
                  </div>
                  <p className="kb-card-desc">{kb.description || '暂无描述'}</p>
                  <div className="kb-card-footer">
                    <span className="kb-card-time">更新于 {formatTime(kb.updatedAt)}</span>
                    <div className="kb-card-actions">
                      <button
                        type="button"
                        className="btn btn-sm btn-text"
                        onClick={(e) => {
                          e.stopPropagation()
                          openEditKb(kb)
                        }}
                      >
                        编辑
                      </button>
                      <button
                        type="button"
                        className="btn btn-sm btn-text btn-text-danger"
                        onClick={(e) => {
                          e.stopPropagation()
                          handleDeleteKb(kb)
                        }}
                      >
                        删除
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {/* ================= 详情视图 ================= */}
      {view === 'detail' && activeKb && (
        <>
          <div className="kb-detail-top">
            <button type="button" className="kb-back-btn" onClick={backToList}>
              ← 返回知识库列表
            </button>
            <div className="kb-detail-title-row">
              <span
                className="kb-detail-icon"
                style={{ background: (activeKb.color || '#1677ff') + '1a', color: activeKb.color || '#1677ff' }}
              >
                {kbMark(activeKb.name)}
              </span>
              <div className="kb-detail-title-text">
                <h1>
                  {activeKb.name}
                  <span className={`kb-badge ${activeKb.enabled !== false ? 'kb-badge-ok' : 'kb-badge-warn'}`}>
                    {activeKb.enabled !== false ? '已启用' : '已停用'}
                  </span>
                </h1>
                <p>{activeKb.description || '暂无描述'}</p>
              </div>
              <div className="kb-detail-actions">
                <input
                  ref={docInputRef}
                  type="file"
                  accept=".pdf,.docx,.txt,.md"
                  multiple
                  style={{ display: 'none' }}
                  onChange={handleChooseFile}
                />
                <button
                  type="button"
                  className="btn btn-default"
                  onClick={() => docInputRef.current?.click()}
                  disabled={uploadingDoc || busy}
                >
                  {uploadingDoc
                    ? `导入中 ${uploadProgress?.current ?? 0}/${uploadProgress?.total ?? 0}`
                    : '上传文档'}
                </button>
                <button type="button" className="btn btn-default" onClick={() => setHitOpen(true)} disabled={busy}>
                  命中测试
                </button>
                <Dropdown menu={maintenanceMenu} placement="bottomRight" disabled={busy}>
                  <button type="button" className="btn btn-default" disabled={busy}>
                    维护 ▾
                  </button>
                </Dropdown>
                <button type="button" className="btn btn-default" onClick={() => openEditKb(activeKb)} disabled={busy}>
                  编辑
                </button>
              </div>
            </div>
          </div>

          {uploadingDoc && uploadProgress && (
            <div className="kb-upload-progress">
              <Spin size="small" />
              <span>
                正在导入 {uploadProgress.current}/{uploadProgress.total}：
                <b title={uploadProgress.fileName}>{uploadProgress.fileName}</b>
              </span>
              <div className="kb-upload-progress-track">
                <div
                  className="kb-upload-progress-bar"
                  style={{ width: `${(uploadProgress.current / Math.max(1, uploadProgress.total)) * 100}%` }}
                />
              </div>
              <button type="button" className="kb-batch-cancel" onClick={handleCancelUpload}>
                取消剩余
              </button>
            </div>
          )}

          <div className="kb-detail-layout">
            {/* 左侧：文档列表 */}
            <aside className="kb-doc-panel">
              <div className="kb-doc-panel-head">
                <span>文档</span>
                <button
                  type="button"
                  className="kb-doc-add"
                  onClick={() => docInputRef.current?.click()}
                  disabled={uploadingDoc}
                  title="上传文档"
                >
                  ＋
                </button>
              </div>
              <div className="kb-doc-list">
                <div
                  className={`kb-doc-item ${selectedDocId === 'all' ? 'active' : ''}`}
                  onClick={() => selectDoc('all')}
                >
                  <span className="kb-doc-type">全部</span>
                  <span className="kb-doc-item-name">全部分段</span>
                  <span className="kb-doc-item-count">{allSegments.length}</span>
                </div>
                {documentLoading ? (
                  <div className="kb-doc-loading">
                    <Spin size="small" />
                  </div>
                ) : (
                  documents.map((doc) => (
                    <div
                      key={doc.id}
                      className={`kb-doc-item ${selectedDocId === doc.id ? 'active' : ''}`}
                      onClick={() => selectDoc(doc.id)}
                    >
                      <span className="kb-doc-type">{docTypeLabel(doc.fileType)}</span>
                      <span className="kb-doc-item-main">
                        <span className="kb-doc-item-name" title={doc.name}>
                          {doc.name}
                        </span>
                        <span className="kb-doc-item-meta">
                          {doc.segmentCount ?? 0} 段 · {doc.charCount ?? 0} 字
                        </span>
                      </span>
                      {doc.status === 'FAILED' ? (
                        <Tooltip title={doc.errorMessage || '处理失败'}>
                          <span className="kb-badge kb-badge-warn">失败</span>
                        </Tooltip>
                      ) : doc.status === 'PROCESSING' ? (
                        <Tooltip title={doc.errorMessage || '切块已入库，正在向量化'}>
                          <span className="kb-badge kb-badge-warn">向量化中</span>
                        </Tooltip>
                      ) : doc.errorMessage ? (
                        <Tooltip title={doc.errorMessage}>
                          <span className="kb-badge kb-badge-warn">有提示</span>
                        </Tooltip>
                      ) : null}
                      <button
                        type="button"
                        className="kb-doc-del"
                        title="删除文档"
                        onClick={(e) => {
                          e.stopPropagation()
                          handleDeleteDocument(doc)
                        }}
                      >
                        ×
                      </button>
                    </div>
                  ))
                )}
              </div>
            </aside>

            {/* 右侧：分段列表 */}
            <section className="kb-seg-panel">
              <div className="kb-seg-toolbar">
                <input
                  type="text"
                  className="kb-input kb-input-search"
                  placeholder="搜索分段标题 / 内容 / 关键词"
                  value={segKeyword}
                  onChange={(e) => {
                    setSegKeyword(e.target.value)
                    setSegPage(1)
                  }}
                />
                <span className="kb-seg-count">{displayedSegments.length} 个分段</span>
                <div className="kb-toolbar-spacer" />
                <button
                  type="button"
                  className="btn btn-sm btn-default"
                  onClick={handleEmbedMissing}
                  disabled={busy || missingEmbeddedInKb === 0}
                >
                  补齐向量{missingEmbeddedInKb > 0 ? `（${missingEmbeddedInKb}）` : ''}
                </button>
              </div>

              {segmentsLoading ? (
                <div className="kb-table-loading">
                  <Spin size="large" />
                </div>
              ) : displayedSegments.length === 0 ? (
                <div className="kb-table-empty">
                  <Empty description={segKeyword ? '没有匹配的分段' : '暂无分段，请上传文档导入'} />
                  {!segKeyword && (
                    <button
                      type="button"
                      className="btn btn-default"
                      onClick={() => docInputRef.current?.click()}
                    >
                      上传文档
                    </button>
                  )}
                </div>
              ) : (
                <>
                  <div className="kb-seg-list">
                    {pageSegments.map((seg, idx) => (
                      <div key={seg.id} className="kb-seg-card">
                        <div className="kb-seg-card-head">
                          <span className="kb-seg-card-title" title={seg.title}>
                            {truncateTitle(seg.title)}
                          </span>
                          <span className="kb-seg-card-index">#{startIdx + idx + 1}</span>
                          <div className="kb-seg-card-badges">
                            <Tooltip
                              title={
                                seg.embedded
                                  ? '已向量化，参与语义检索'
                                  : '未向量化，仅在关键词降级检索中命中'
                              }
                            >
                              <span
                                className={`kb-badge ${seg.embedded ? 'kb-badge-ok' : 'kb-badge-warn'}`}
                              >
                                {seg.embedded ? '已向量化' : '待向量化'}
                              </span>
                            </Tooltip>
                            <Switch
                              size="small"
                              checked={seg.enabled !== false}
                              loading={togglingSegId === seg.id}
                              onChange={() => handleToggleSegment(seg)}
                              checkedChildren="开"
                              unCheckedChildren="关"
                            />
                          </div>
                        </div>
                        <div className="kb-seg-card-meta">
                          {seg.category && <Tag color="blue">{seg.category}</Tag>}
                          {seg.source && (
                            <span className="kb-preview-hit-source" title={seg.source}>
                              {seg.source}
                            </span>
                          )}
                        </div>
                        <div className="kb-seg-card-content">{snippetOf(seg.content, 220)}</div>
                        <div className="kb-seg-card-actions">
                          <button
                            type="button"
                            className="btn btn-sm btn-text"
                            onClick={() => handleReembedSegment(seg)}
                          >
                            重新向量化
                          </button>
                          <button
                            type="button"
                            className="btn btn-sm btn-text btn-text-danger"
                            onClick={() => handleDeleteSegment(seg)}
                          >
                            删除
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                  <div className="kb-pagination">
                    <Pagination
                      current={currentSegPage}
                      pageSize={segPageSize}
                      total={displayedSegments.length}
                      showSizeChanger
                      showQuickJumper
                      pageSizeOptions={[10, 20, 50]}
                      showTotal={(total) => `共 ${total} 个分段`}
                      onChange={(p, ps) => {
                        setSegPage(p)
                        setSegPageSize(ps)
                      }}
                    />
                  </div>
                </>
              )}
            </section>
          </div>
        </>
      )}

      {/* ---------------- 知识库新建 / 编辑弹窗 ---------------- */}
      <Modal
        title={editingKbId ? '编辑知识库' : '新建知识库'}
        open={kbModalOpen}
        onCancel={() => setKbModalOpen(false)}
        onOk={handleSaveKb}
        confirmLoading={busy}
        okText={editingKbId ? '保存' : '创建'}
        cancelText="取消"
      >
        <div className="kb-kb-modal">
          <label className="kb-field">
            <span className="kb-field-label">
              名称 <em>*</em>
            </span>
            <input
              type="text"
              className="kb-input"
              placeholder="如：软件技术专业群知识库"
              value={kbForm.name}
              onChange={(e) => handleKbFormChange('name', e.target.value)}
            />
          </label>
          <label className="kb-field">
            <span className="kb-field-label">描述</span>
            <textarea
              className="kb-input kb-textarea"
              rows={2}
              placeholder="简要说明该知识库覆盖的内容范围"
              value={kbForm.description}
              onChange={(e) => handleKbFormChange('description', e.target.value)}
            />
          </label>
          <div className="kb-field">
            <span className="kb-field-label">主题色</span>
            <div className="kb-color-row">
              {KB_COLORS.map((color) => (
                <button
                  key={color}
                  type="button"
                  className={`kb-color-swatch ${kbForm.color === color ? 'active' : ''}`}
                  style={{ background: color }}
                  onClick={() => handleKbFormChange('color', color)}
                  aria-label={color}
                />
              ))}
            </div>
          </div>
          <div className="kb-field kb-field-switch">
            <span className="kb-field-label">启用（停用后该库全部分段不参与检索）</span>
            <Switch checked={kbForm.enabled} onChange={(v) => handleKbFormChange('enabled', v)} />
          </div>
        </div>
      </Modal>

      {/* ---------------- 上传文档确认弹窗 ---------------- */}
      <Modal
        title={pendingFiles.length > 1 ? `上传文档（${pendingFiles.length} 份）` : '上传文档'}
        open={uploadModalOpen}
        onCancel={() => setUploadModalOpen(false)}
        onOk={handleConfirmUpload}
        confirmLoading={uploadingDoc}
        okText={pendingFiles.length > 1 ? `开始导入（${pendingFiles.length} 份）` : '开始导入'}
        cancelText="取消"
      >
        <div className="kb-import-options">
          <p className="kb-import-options-tip">
            {pendingFiles.length > 1 ? (
              <>
                将批量上传 <b>{pendingFiles.length}</b> 份文档，逐份解析后按段落切块，每个分段写入知识库
                <b>「{activeKb?.name}」</b>。
              </>
            ) : (
              <>
                将上传 <b>{pendingFiles[0]?.name}</b>，解析后按段落切块，每个分段写入知识库
                <b>「{activeKb?.name}」</b>。
              </>
            )}
          </p>
          {pendingFiles.length > 1 && (
            <div className="kb-batch-files">
              {pendingFiles.map((f) => (
                <div key={`${f.name}-${f.size}`} className="kb-batch-file">
                  <span className="kb-batch-file-name" title={f.name}>
                    {f.name}
                  </span>
                  <span className="kb-batch-file-meta">{formatFileSize(f.size)}</span>
                </div>
              ))}
            </div>
          )}
          <label className="kb-field">
            <span className="kb-field-label">分类（可选，将写入每个分段）</span>
            <input
              type="text"
              className="kb-input"
              placeholder="如：实训手册"
              value={uploadCategory}
              onChange={(e) => setUploadCategory(e.target.value)}
            />
          </label>
          <label className="kb-field">
            <span className="kb-field-label">来源标注（可选，缺省为「上传文档：文件名」）</span>
            <input
              type="text"
              className="kb-input"
              placeholder="如：依据《实训手册》第2章"
              value={uploadSource}
              onChange={(e) => setUploadSource(e.target.value)}
            />
          </label>
        </div>
      </Modal>

      {/* ---------------- 导入结果抽屉 ---------------- */}
      <Drawer
        title={importResults.length > 1 ? `文档导入结果（${importResults.length} 份）` : '文档导入结果'}
        placement="right"
        width={560}
        open={importResultOpen}
        onClose={() => setImportResultOpen(false)}
      >
        {importResults.length > 0 && (
          <div className="kb-import-result-body">
            <BatchImportSummary results={importResults} />
            {importResults.length > 1 ? (
              <div className="kb-batch-result-list">
                {importResults.map((r, index) => (
                  <div
                    key={r.document?.id || `${r.fileName}-${index}`}
                    className={`kb-batch-result-item ${r.ok ? 'ok' : 'fail'}`}
                  >
                    <div className="kb-batch-result-head">
                      <span className="kb-batch-file-name" title={r.fileName}>
                        {r.fileName}
                      </span>
                      <span className={`kb-badge ${r.ok ? 'kb-badge-ok' : 'kb-badge-warn'}`}>
                        {r.ok
                          ? r.embeddingPending || r.document?.status === 'PROCESSING'
                            ? '切块成功'
                            : '成功'
                          : '失败'}
                      </span>
                    </div>
                    {r.ok ? (
                      <div className="kb-batch-result-meta">
                        {r.imported ?? 0} 个分段 · {embedStatusLabel(r)}
                        {r.truncated ? ' · 已截断至 500 段' : ''}
                      </div>
                    ) : (
                      <div className="kb-batch-result-error">{r.errorMessage || '导入失败'}</div>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              importResults[0]?.ok && (
                <div className="kb-batch-result-meta" style={{ marginTop: 12 }}>
                  已导入 {importResults[0].imported ?? 0} 个分段
                  {importResults[0].truncated ? '（已截断至 500 段）' : ''}
                  。{embedStatusLabel(importResults[0])}
                  。可在左侧文档列表查看分段；向量化完成后徽章会自动消失。
                </div>
              )
            )}
            {importResults.length === 1 && !importResults[0]?.ok ? (
              <div className="kb-batch-result-error" style={{ marginTop: 12 }}>
                {importResults[0]?.errorMessage || '导入失败'}
              </div>
            ) : null}
          </div>
        )}
      </Drawer>

      {/* ---------------- 命中测试抽屉 ---------------- */}
      <Drawer
        title={view === 'detail' ? `命中测试 · ${activeKb?.name}` : '命中测试'}
        placement="right"
        width={560}
        open={hitOpen}
        onClose={() => setHitOpen(false)}
      >
        <div className="kb-hit-search">
          <input
            type="text"
            className="kb-input kb-input-search"
            placeholder="输入一个问题，查看知识库检索命中了什么（如：Git 冲突怎么解决）"
            value={hitQuery}
            onChange={(e) => {
              setHitQuery(e.target.value)
              if (!e.target.value.trim()) {
                setHitHits([])
                setHitSearched(false)
                setHitExpandedIds([])
              }
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleHitSearch()
            }}
          />
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleHitSearch}
            disabled={hitLoading || !hitQuery.trim()}
          >
            {hitLoading ? '检索中…' : '检索'}
          </button>
        </div>
        <div className="kb-hit-hint">
          <span>
            当前相似度阈值{' '}
            {similarityThreshold > 0 ? formatPercent(similarityThreshold) : '不过滤（0）'}
            ，低于阈值的命中不会出现在学生对话中
          </span>
          <button
            type="button"
            className="kb-link"
            onClick={() => {
              setHitOpen(false)
              openSettings('retrieval')
            }}
          >
            调整检索参数
          </button>
        </div>
        {hitLoading ? (
          <div className="kb-table-loading">
            <Spin size="large" />
          </div>
        ) : !hitQuery.trim() ? (
          <Empty description="输入问题开始测试" />
        ) : hitHits.length === 0 && hitSearched ? (
          <Empty description="未命中知识库条目（检查是否已配置 embedding、知识库是否启用或阈值是否过高）" />
        ) : (
          <div className="kb-preview-hits">
            <div className="kb-preview-hits-summary">
              命中 <b>{hitHits.length}</b> 条
              {similarityThreshold > 0 && (
                <span className="kb-preview-hits-threshold">阈值 {formatPercent(similarityThreshold)}</span>
              )}
            </div>
            {hitHits.map((hit) => {
              const expanded = hitExpandedIds.includes(hit.id)
              return (
                <div key={hit.id} className="kb-preview-hit">
                  <div className="kb-preview-hit-head">
                    <span className="kb-preview-hit-q">{hit.question}</span>
                    <span className="kb-preview-hit-score">{formatPercent(hit.score)}</span>
                  </div>
                  <div className="kb-preview-hit-meta">
                    {hit.category && <Tag color="blue">{hit.category}</Tag>}
                    {hit.source && (
                      <span className="kb-preview-hit-source" title={hit.source}>
                        {hit.source}
                      </span>
                    )}
                    <span
                      className={`kb-preview-hit-pass ${hit.score >= similarityThreshold ? 'ok' : 'fail'}`}
                    >
                      {hit.score >= similarityThreshold ? '达阈值' : '低于阈值'}
                    </span>
                  </div>
                  <div className={`kb-preview-hit-content ${expanded ? 'expanded' : ''}`}>
                    <HighlightText text={hit.content} query={hitQuery} />
                  </div>
                  <div className="kb-hit-expand-row">
                    <button
                      type="button"
                      className="kb-hit-expand-toggle"
                      onClick={() => toggleHitExpand(hit.id)}
                    >
                      {expanded ? '收起' : '展开'}
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </Drawer>

      {/* ---------------- 知识库设置抽屉 ---------------- */}
      <Drawer
        title="知识库设置"
        placement="right"
        width={520}
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
      >
        <div className="kb-settings-body">
          <Tabs
            className="kb-settings-tabs"
            activeKey={settingsTab}
            onChange={setSettingsTab}
            items={[
              {
                key: 'embedding',
                label: '向量模型',
                children: (
                  <div className="kb-settings-panel">
                    <div className="kb-settings-section">
                      <h3 className="kb-settings-title">Embedding 服务</h3>
                      <p className="kb-settings-tip">
                        配置 OpenAI 兼容的 /v1/embeddings 服务。学生对话与命中测试都会用该服务把文本向量化后做余弦相似度检索；未配置时降级为关键词检索。
                      </p>
                      <label className="kb-field">
                        <span className="kb-field-label">服务商预设</span>
                        <select
                          className="kb-input"
                          value={configProviderKey}
                          onChange={(e) => handleConfigProviderChange(e.target.value)}
                        >
                          <option value="">自定义（手动填写）</option>
                          {EMBEDDING_PROVIDERS.map((p) => (
                            <option key={p.key} value={p.key}>
                              {p.name}
                            </option>
                          ))}
                        </select>
                      </label>
                      <label className="kb-field">
                        <span className="kb-field-label">
                          Base URL <em>*</em>
                        </span>
                        <input
                          type="text"
                          className="kb-input"
                          placeholder="https://api.siliconflow.cn"
                          value={configForm.baseUrl}
                          onChange={(e) => handleConfigChange('baseUrl', e.target.value)}
                        />
                      </label>
                      <label className="kb-field">
                        <span className="kb-field-label">
                          模型 <em>*</em>
                        </span>
                        <input
                          type="text"
                          className="kb-input"
                          list="kb-embedding-models"
                          placeholder="如：BAAI/bge-m3"
                          value={configForm.model}
                          onChange={(e) => handleConfigChange('model', e.target.value)}
                        />
                        <datalist id="kb-embedding-models">
                          {(EMBEDDING_PROVIDERS.find((p) => p.key === configProviderKey)?.models || []).map(
                            (m) => (
                              <option key={m} value={m} />
                            ),
                          )}
                        </datalist>
                      </label>
                      <label className="kb-field">
                        <span className="kb-field-label">API Key{embeddingConfig?.hasApiKey ? '（已保存，留空保持不变）' : ''}</span>
                        <input
                          type="password"
                          className="kb-input"
                          placeholder={embeddingConfig?.apiKeyMasked || 'sk-...'}
                          value={configForm.apiKey}
                          onChange={(e) => handleConfigChange('apiKey', e.target.value)}
                        />
                      </label>
                      <div className="kb-field kb-field-switch">
                        <span className="kb-field-label">启用向量检索（关闭后降级为关键词检索）</span>
                        <Switch
                          checked={configForm.enabled}
                          onChange={(v) => handleConfigChange('enabled', v)}
                        />
                      </div>
                      <div className="kb-settings-row">
                        <button
                          type="button"
                          className="btn btn-default"
                          onClick={handleTestConfig}
                          disabled={configTesting}
                        >
                          {configTesting ? '测试中…' : '测试连接'}
                        </button>
                        {configTest && (
                          <div
                            className={`kb-config-test-result ${
                              configTest.ok ? 'kb-config-test-ok' : 'kb-config-test-fail'
                            }`}
                          >
                            {configTest.ok ? '连接成功，向量维度可用' : configTest.message || '连接失败'}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                ),
              },
              {
                key: 'retrieval',
                label: '检索参数',
                children: (
                  <div className="kb-settings-panel">
                    <div className="kb-settings-section">
                      <h3 className="kb-settings-title">检索与分段参数</h3>
                      <div className="kb-settings-item">
                        <div className="kb-settings-item-head">
                          <span>Top-K（每次检索返回的分段数）</span>
                          <span className="kb-settings-value">{retrievalForm.topK}</span>
                        </div>
                        <Slider
                          min={3}
                          max={10}
                          value={retrievalForm.topK}
                          onChange={(v) => setRetrievalForm((prev) => ({ ...prev, topK: v }))}
                        />
                      </div>
                      <div className="kb-settings-item">
                        <div className="kb-settings-item-head">
                          <span>相似度阈值（低于该值的命中不展示）</span>
                          <span className="kb-settings-value">
                            {similarityThreshold > 0 ? formatPercent(similarityThreshold) : '不过滤'}
                          </span>
                        </div>
                        <Slider
                          min={0.6}
                          max={1}
                          step={0.05}
                          value={similarityThreshold}
                          onChange={(v) =>
                            setRetrievalForm((prev) => ({ ...prev, similarityThreshold: v }))
                          }
                        />
                      </div>
                      <div className="kb-settings-item">
                        <div className="kb-settings-item-head">
                          <span>文档切块长度（单段最大字符数）</span>
                          <InputNumber
                            min={100}
                            max={2000}
                            step={50}
                            value={retrievalForm.chunkLength}
                            onChange={(v) => setRetrievalForm((prev) => ({ ...prev, chunkLength: v || 450 }))}
                            style={{ width: 140 }}
                          />
                        </div>
                      </div>
                    </div>
                  </div>
                ),
              },
              {
                key: 'maintenance',
                label: '数据维护',
                children: (
                  <div className="kb-settings-panel">
                    <div className="kb-settings-section">
                      <h3 className="kb-settings-title">全库数据维护</h3>
                      <div className="kb-maintenance-grid">
                        <div className="kb-maintenance-card">
                          <div>
                            <b>补齐缺失向量</b>
                            <p>仅对尚未向量化的分段调用 embedding 服务生成向量。</p>
                          </div>
                          <button type="button" className="btn btn-default" onClick={handleEmbedMissingAll}>
                            补齐向量
                          </button>
                        </div>
                        <div className="kb-maintenance-card">
                          <div>
                            <b>全部重新向量化</b>
                            <p>对全部分段重新调用 embedding 服务，耗时较长，请谨慎操作。</p>
                          </div>
                          <button type="button" className="btn btn-default" onClick={handleReembedAll}>
                            重新向量化
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                ),
              },
            ]}
          />
          {settingsTab !== 'maintenance' && (
            <div className="kb-drawer-footer">
              <button type="button" className="btn btn-default" onClick={() => setSettingsOpen(false)}>
                取消
              </button>
              <button type="button" className="btn btn-primary" onClick={handleSaveSettings} disabled={configSaving}>
                {configSaving ? '保存中…' : '保存配置'}
              </button>
            </div>
          )}
        </div>
      </Drawer>
    </div>
  )
}

export default KnowledgeBase
