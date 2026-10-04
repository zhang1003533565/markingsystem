package com.markingsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markingsystem.dto.DocumentImportResult;
import com.markingsystem.dto.KnowledgeBaseDTO;
import com.markingsystem.dto.KnowledgeDocumentDTO;
import com.markingsystem.dto.KnowledgeSearchHit;
import com.markingsystem.dto.KnowledgeSegmentDTO;
import com.markingsystem.entity.KnowledgeBase;
import com.markingsystem.entity.KnowledgeDocument;
import com.markingsystem.entity.KnowledgeSegment;
import com.markingsystem.repository.KnowledgeBaseRepository;
import com.markingsystem.repository.KnowledgeDocumentRepository;
import com.markingsystem.repository.KnowledgeSegmentRepository;
import com.markingsystem.service.llm.EmbeddingClient;
import com.markingsystem.util.DocumentTextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * AI 知识库服务（MaxKB 式三级结构：知识库 → 文档 → 分段，RAG 检索）。
 *
 * <p>能力：
 * <ul>
 *   <li>知识库 CRUD（名称 / 描述 / 颜色 / 图标，含文档数与分段数统计）</li>
 *   <li>文档上传（PDF / Word / TXT / Markdown 解析切块入库）与文档 CRUD</li>
 *   <li>分段查看 / 编辑 / 启停 / 删除 / 向量化与检索</li>
 *   <li>向量余弦检索（OpenAI 兼容 embedding API，向量存 knowledge_segment.embedding_json），
 *       embedding 不可用时自动降级为关键词检索；停用知识库/分段不参与检索</li>
 * </ul>
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    /** 检索文本最长字符数（控制 embedding token 消耗） */
    private static final int EMBED_TEXT_MAX_CHARS = 500;

    /** 关键词降级检索时参与匹配的字段 */
    private static final List<String> KEYWORD_FIELDS = List.of("title", "content", "keywords");

    /** 文档切块单块最大字符数（对齐 EMBED_TEXT_MAX_CHARS 的向量预算） */
    private static final int DOC_CHUNK_MAX_CHARS = 450;

    /** 低于该长度的碎块不单独成条 */
    private static final int DOC_CHUNK_MIN_CHARS = 10;

    /** 分段标题展示长度上限：超过则按句末/弱边界截断，避免标题=整段内容 */
    private static final int TITLE_PREVIEW_CHARS = 30;

    /** 强句末标点（含成对闭合符），优先作为标题/切块边界 */
    private static final String SENTENCE_END = "。！？!?；;…\"”』）】》」";

    /** 弱边界标点：找不到强句末标点时，用作标题截断的次优边界 */
    private static final String SOFT_END = "，,、：:";

    /** 单文档最大导入条数，防止超大文档拖垮入库与向量化 */
    private static final int DOC_MAX_CHUNKS = 500;

    /** 后台向量化每批调用 embedding API 的条数 */
    private static final int EMBED_BATCH_SIZE = 16;

    /** 文档向量化单并发，避免批量上传打爆 embedding API */
    private final Semaphore docEmbedSemaphore = new Semaphore(1);

    private final ExecutorService docEmbedExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "kb-doc-embed");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    private KnowledgeBaseRepository kbRepository;

    @Autowired
    private KnowledgeDocumentRepository documentRepository;

    @Autowired
    private KnowledgeSegmentRepository segmentRepository;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private EmbeddingConfigService embeddingConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentTextExtractor documentTextExtractor;

    // ---------------- 知识库 ----------------

    public List<KnowledgeBaseDTO> kbList() {
        List<KnowledgeBase> kbs = kbRepository.findAll();
        List<KnowledgeDocument> docs = documentRepository.findAll();
        List<KnowledgeSegment> segs = segmentRepository.findAll();
        Map<Long, Long> docCounts = docs.stream()
                .collect(Collectors.groupingBy(KnowledgeDocument::getKbId, Collectors.counting()));
        Map<Long, Long> segCounts = segs.stream()
                .collect(Collectors.groupingBy(KnowledgeSegment::getKbId, Collectors.counting()));
        return kbs.stream()
                .sorted(Comparator.comparing(KnowledgeBase::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(kb -> toKbDto(kb, docCounts.getOrDefault(kb.getId(), 0L),
                        segCounts.getOrDefault(kb.getId(), 0L)))
                .collect(Collectors.toList());
    }

    /**
     * 知识库总览统计（列表头部卡片用）。
     */
    public Map<String, Object> kbStats() {
        long kbCount = kbRepository.count();
        long docCount = documentRepository.count();
        long segmentCount = segmentRepository.count();
        long enabledCount = segmentRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
                .count();
        long embeddedCount = segmentRepository.findAll().stream()
                .filter(this::hasEmbedding)
                .count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kbCount", kbCount);
        result.put("docCount", docCount);
        result.put("segmentCount", segmentCount);
        result.put("enabledCount", enabledCount);
        result.put("embeddedCount", embeddedCount);
        result.put("embeddingEnabled", embeddingClient.isEnabled());
        result.put("embeddingModel", embeddingClient.modelName());
        return result;
    }

    public Optional<KnowledgeBaseDTO> getKb(Long id) {
        return kbRepository.findById(id).map(kb -> {
            long docCount = documentRepository.countByKbId(id);
            long segCount = segmentRepository.countByKbId(id);
            return toKbDto(kb, docCount, segCount);
        });
    }

    public KnowledgeBaseDTO createKb(KnowledgeBaseDTO dto) {
        KnowledgeBase kb = new KnowledgeBase();
        applyKbDto(kb, dto);
        kb = kbRepository.save(kb);
        return toKbDto(kb, 0L, 0L);
    }

    public Optional<KnowledgeBaseDTO> updateKb(Long id, KnowledgeBaseDTO dto) {
        return kbRepository.findById(id).map(kb -> {
            applyKbDto(kb, dto);
            kb = kbRepository.save(kb);
            long docCount = documentRepository.countByKbId(id);
            long segCount = segmentRepository.countByKbId(id);
            return toKbDto(kb, docCount, segCount);
        });
    }

    public boolean toggleKb(Long id) {
        Optional<KnowledgeBase> found = kbRepository.findById(id);
        if (found.isEmpty()) {
            return false;
        }
        KnowledgeBase kb = found.get();
        kb.setEnabled(!Boolean.TRUE.equals(kb.getEnabled()));
        kbRepository.save(kb);
        return true;
    }

    /** 删除知识库（连同其文档与分段，不可恢复） */
    public boolean deleteKb(Long id) {
        Optional<KnowledgeBase> found = kbRepository.findById(id);
        if (found.isEmpty()) {
            return false;
        }
        List<KnowledgeDocument> docs = documentRepository.findByKbIdOrderByCreatedAtDesc(id);
        List<KnowledgeSegment> segs = segmentRepository.findByKbIdOrderByUpdatedAtDesc(id);
        segmentRepository.deleteAll(segs);
        documentRepository.deleteAll(docs);
        kbRepository.deleteById(id);
        return true;
    }

    // ---------------- 文档 ----------------

    public List<KnowledgeDocumentDTO> documentsOf(Long kbId) {
        return documentRepository.findByKbIdOrderByCreatedAtDesc(kbId).stream()
                .map(this::toDocDto)
                .collect(Collectors.toList());
    }

    /** 文档详情：文档信息 + 分段列表（MaxKB 式：左侧文档，右侧分段）。 */
    public Map<String, Object> documentDetail(Long docId) {
        KnowledgeDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));
        List<KnowledgeSegmentDTO> segments = segmentRepository.findByDocumentIdOrderByIdAsc(docId).stream()
                .map(this::toSegDto)
                .collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("document", toDocDto(doc));
        result.put("segments", segments);
        return result;
    }

    /**
     * 上传文档并解析切块入库：同步阶段只切块落库；向量化后台异步执行。
     *
     * @return 导入结果（chunks 为空列表以精简响应；document.status 可能为 PROCESSING）
     */
    public DocumentImportResult importDocument(Long kbId, MultipartFile file, String category, String source) {
        if (!kbRepository.existsById(kbId)) {
            throw new IllegalArgumentException("知识库不存在");
        }
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "未命名文档" : file.getOriginalFilename();
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setKbId(kbId);
        doc.setName(fileName);
        doc.setFileName(fileName);
        doc.setFileType(detectFileType(fileName));
        doc.setStatus("PROCESSING");
        doc = documentRepository.save(doc);

        String text;
        try {
            text = documentTextExtractor.extract(file);
        } catch (IOException e) {
            doc.setStatus("FAILED");
            doc.setErrorMessage("文档读取失败：" + e.getMessage());
            documentRepository.save(doc);
            throw new IllegalStateException("文档读取失败：" + e.getMessage(), e);
        }

        List<String> chunks = chunkDocument(text, embeddingConfigService.getRetrievalConfig().chunkLength());
        boolean truncated = chunks.size() > DOC_MAX_CHUNKS;
        if (truncated) {
            chunks = chunks.subList(0, DOC_MAX_CHUNKS);
        }
        String resolvedSource = source == null || source.isBlank() ? "上传文档：" + fileName : source.trim();
        int imported = 0;
        int chunkFailed = 0;
        for (String chunk : chunks) {
            try {
                KnowledgeSegment seg = new KnowledgeSegment();
                seg.setKbId(kbId);
                seg.setDocumentId(doc.getId());
                seg.setTitle(deriveQuestion(chunk));
                seg.setContent(chunk);
                seg.setCategory(category == null || category.isBlank() ? null : category.trim());
                seg.setSource(resolvedSource);
                seg.setEnabled(Boolean.TRUE);
                seg.setEmbeddingJson(null);
                segmentRepository.save(seg);
                imported++;
            } catch (Exception e) {
                chunkFailed++;
                log.warn("Document chunk import failed: {}", e.getMessage());
            }
        }
        doc.setCharCount(text.length());
        doc.setSegmentCount(imported);
        if (chunkFailed > 0) {
            doc.setErrorMessage("部分切块入库失败：" + chunkFailed + " 条");
        }

        boolean embedEnabled = embeddingClient.isEnabled();
        if (!embedEnabled || imported == 0) {
            doc.setStatus("SUCCESS");
            doc = documentRepository.save(doc);
            log.info("Document imported (no async embed): kb={}, file={}, chunks={}, truncated={}",
                    kbId, fileName, imported, truncated);
        } else {
            doc.setStatus("PROCESSING");
            doc = documentRepository.save(doc);
            enqueueDocumentEmbed(doc.getId());
            log.info("Document imported, embed queued: kb={}, file={}, chunks={}, truncated={}",
                    kbId, fileName, imported, truncated);
        }

        DocumentImportResult result = new DocumentImportResult();
        result.setFileName(fileName);
        result.setImported(imported);
        result.setEmbedded(0);
        result.setTruncated(truncated);
        result.setChunks(List.of());
        result.setDocument(toDocDto(doc));
        return result;
    }

    /** 将文档向量化任务入队（单并发）。 */
    private void enqueueDocumentEmbed(Long docId) {
        if (docId == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                docEmbedSemaphore.acquire();
                try {
                    embedDocumentBatch(docId);
                } finally {
                    docEmbedSemaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Document embed interrupted: docId={}", docId);
            } catch (Exception e) {
                log.warn("Document embed async failed: docId={}, err={}", docId, e.getMessage());
                markDocumentEmbedFinished(docId, 0, true);
            }
        }, docEmbedExecutor);
    }

    /**
     * 按批调用 embedBatch，写回分段向量，并将文档标为 SUCCESS。
     * 文档已删除时直接退出。
     */
    private void embedDocumentBatch(Long docId) {
        Optional<KnowledgeDocument> found = documentRepository.findById(docId);
        if (found.isEmpty()) {
            return;
        }
        if (!embeddingClient.isEnabled()) {
            markDocumentEmbedFinished(docId, 0, false);
            return;
        }
        List<KnowledgeSegment> segments = segmentRepository.findByDocumentIdOrderByIdAsc(docId);
        int embedded = 0;
        for (int i = 0; i < segments.size(); i += EMBED_BATCH_SIZE) {
            if (!documentRepository.existsById(docId)) {
                return;
            }
            int end = Math.min(i + EMBED_BATCH_SIZE, segments.size());
            List<KnowledgeSegment> batch = segments.subList(i, end);
            List<String> texts = new ArrayList<>(batch.size());
            for (KnowledgeSegment seg : batch) {
                texts.add(buildEmbedText(seg.getTitle(), seg));
            }
            List<float[]> vectors = embeddingClient.embedBatch(texts);
            if (vectors == null || vectors.size() != batch.size()) {
                log.warn("Embed batch failed or size mismatch: docId={}, expected={}, got={}",
                        docId, batch.size(), vectors == null ? -1 : vectors.size());
                continue;
            }
            for (int j = 0; j < batch.size(); j++) {
                KnowledgeSegment seg = batch.get(j);
                float[] vector = vectors.get(j);
                if (vector == null || vector.length == 0) {
                    continue;
                }
                try {
                    seg.setEmbeddingJson(objectMapper.writeValueAsString(vector));
                    segmentRepository.save(seg);
                    embedded++;
                } catch (Exception e) {
                    log.warn("Persist embedding failed: segId={}, err={}", seg.getId(), e.getMessage());
                }
            }
        }
        markDocumentEmbedFinished(docId, embedded, embeddingClient.isEnabled() && embedded == 0 && !segments.isEmpty());
        log.info("Document embed finished: docId={}, segments={}, embedded={}", docId, segments.size(), embedded);
    }

    /** 异步向量化结束后更新文档状态（文档不存在则忽略）。 */
    private void markDocumentEmbedFinished(Long docId, int embedded, boolean allFailed) {
        documentRepository.findById(docId).ifPresent(doc -> {
            doc.setStatus("SUCCESS");
            if (allFailed) {
                doc.setErrorMessage("分段已入库，向量化失败，可稍后补向量");
            } else if (doc.getErrorMessage() != null
                    && doc.getErrorMessage().startsWith("分段已入库，向量化失败")) {
                doc.setErrorMessage(null);
            }
            documentRepository.save(doc);
            log.debug("Document status SUCCESS after embed: docId={}, embedded={}", docId, embedded);
        });
    }

    /** 删除文档（连同其分段，不可恢复） */
    public boolean deleteDocument(Long docId) {
        if (!documentRepository.existsById(docId)) {
            return false;
        }
        List<KnowledgeSegment> segments = segmentRepository.findByDocumentIdOrderByIdAsc(docId);
        segmentRepository.deleteAll(segments);
        documentRepository.deleteById(docId);
        return true;
    }

    // ---------------- 分段 ----------------

    /** 知识库内分段列表（支持标题 / 正文 / 关键词过滤）。 */
    public List<KnowledgeSegmentDTO> segmentsOfKb(Long kbId, String keyword) {
        List<KnowledgeSegment> segments = new ArrayList<>(segmentRepository.findByKbIdOrderByUpdatedAtDesc(kbId));
        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase(Locale.ROOT);
            segments.removeIf(seg -> !segMatchesKeyword(seg, lower));
        }
        return segments.stream().map(this::toSegDto).collect(Collectors.toList());
    }

    /** 手动新增分段（不挂文档，显示在知识库「全部分段」视图）。 */
    public KnowledgeSegmentDTO createSegment(Long kbId, KnowledgeSegmentDTO dto) {
        KnowledgeSegment seg = new KnowledgeSegment();
        seg.setKbId(kbId);
        applySegmentDto(seg, dto);
        seg.setEmbeddingJson(null);
        seg = segmentRepository.save(seg);
        embedSegment(seg);
        return toSegDto(seg);
    }

    public Optional<KnowledgeSegmentDTO> updateSegment(Long id, KnowledgeSegmentDTO dto) {
        return segmentRepository.findById(id).map(seg -> {
            applySegmentDto(seg, dto);
            seg.setEmbeddingJson(null); // 内容变化后强制重新向量化
            seg = segmentRepository.save(seg);
            embedSegment(seg);
            return toSegDto(seg);
        });
    }

    public Optional<KnowledgeSegmentDTO> toggleSegment(Long id) {
        return segmentRepository.findById(id).map(seg -> {
            seg.setEnabled(!Boolean.TRUE.equals(seg.getEnabled()));
            seg = segmentRepository.save(seg);
            return toSegDto(seg);
        });
    }

    public boolean deleteSegment(Long id) {
        if (!segmentRepository.existsById(id)) {
            return false;
        }
        segmentRepository.deleteById(id);
        return true;
    }

    /** 对单条分段重新向量化。 */
    public boolean reembedSegment(Long id) {
        Optional<KnowledgeSegment> found = segmentRepository.findById(id);
        return found.map(this::embedSegment).orElse(false);
    }

    /** 补齐缺失向量（可限定知识库；kbId 为 null 表示全部）。 */
    public int embedMissing(Long kbId) {
        List<KnowledgeSegment> list = kbId == null
                ? segmentRepository.findAll()
                : segmentRepository.findByKbIdOrderByUpdatedAtDesc(kbId);
        int done = 0;
        for (KnowledgeSegment seg : list) {
            if (!hasEmbedding(seg) && embedSegment(seg)) {
                done++;
            }
        }
        return done;
    }

    /** 全部重新向量化（可限定知识库）。 */
    public int reembedAll(Long kbId) {
        List<KnowledgeSegment> list = kbId == null
                ? segmentRepository.findAll()
                : segmentRepository.findByKbIdOrderByUpdatedAtDesc(kbId);
        int done = 0;
        for (KnowledgeSegment seg : list) {
            if (embedSegment(seg)) {
                done++;
            }
        }
        return done;
    }

    /** 重新向量化某文档内的全部分段。 */
    public int reembedDoc(Long docId) {
        List<KnowledgeSegment> segments = segmentRepository.findByDocumentIdOrderByIdAsc(docId);
        int done = 0;
        for (KnowledgeSegment seg : segments) {
            if (embedSegment(seg)) {
                done++;
            }
        }
        return done;
    }

    // ---------------- 检索（RAG） ----------------

    /**
     * 全局检索与 query 最相关的分段（仅启用知识库中的启用分段）。
     *
     * <p>优先向量余弦检索；embedding 服务不可用或分段未向量化时降级为关键词检索。
     *
     * @return topK 条命中，按相关度降序
     */
    public List<KnowledgeSearchHit> search(String query, int topK) {
        return search(query, topK, null);
    }

    /**
     * 在指定知识库范围内检索（kbIds 为 null 或空时检索全部启用知识库）。
     */
    public List<KnowledgeSearchHit> search(String query, int topK, List<Long> kbIds) {
        int top = Math.max(1, Math.min(topK, 10));

        // 启用中的知识库 id（停用库的分段不参与检索）
        Set<Long> enabledKbIds = kbRepository.findAll().stream()
                .filter(kb -> Boolean.TRUE.equals(kb.getEnabled()))
                .map(KnowledgeBase::getId)
                .collect(Collectors.toSet());
        if (enabledKbIds.isEmpty()) {
            return List.of();
        }

        List<KnowledgeSegment> enabled;
        if (kbIds == null || kbIds.isEmpty()) {
            enabled = segmentRepository.findByEnabledTrueOrderByUpdatedAtDesc().stream()
                    .filter(seg -> enabledKbIds.contains(seg.getKbId()))
                    .collect(Collectors.toList());
        } else {
            Set<Long> requested = new HashSet<>(kbIds);
            requested.retainAll(enabledKbIds);
            if (requested.isEmpty()) {
                return List.of();
            }
            enabled = segmentRepository.findByKbIdInAndEnabledTrue(requested);
        }
        if (enabled.isEmpty()) {
            return List.of();
        }

        // 相似度阈值（管理员在「知识库设置」中配置，默认 0 表示不过滤）
        double threshold = embeddingConfigService.getRetrievalConfig().similarityThreshold();
        List<ScoredHit> scored = new ArrayList<>();
        float[] queryVector = embeddingClient.isEnabled() ? embeddingClient.embed(buildEmbedText(query, null)) : null;

        if (queryVector != null) {
            for (KnowledgeSegment seg : enabled) {
                float[] segVector = parseEmbedding(seg.getEmbeddingJson());
                if (segVector == null) {
                    continue;
                }
                double score = cosineSimilarity(queryVector, segVector);
                if (score >= threshold && score > 0) {
                    scored.add(new ScoredHit(seg, score));
                }
            }
            scored.sort(Comparator.comparingDouble(ScoredHit::score).reversed());
        }

        // 向量结果不足时用关键词补充
        if (scored.size() < top) {
            String lower = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            if (!lower.isEmpty() && 0.5D >= threshold) {
                for (KnowledgeSegment seg : enabled) {
                    if (scored.stream().anyMatch(h -> h.segment().getId().equals(seg.getId()))) {
                        continue;
                    }
                    if (segMatchesKeyword(seg, lower)) {
                        scored.add(new ScoredHit(seg, 0.5D));
                    }
                }
            }
        }

        return scored.stream()
                .limit(top)
                .map(h -> toHit(h.segment(), h.score()))
                .collect(Collectors.toList());
    }

    /**
     * 学生对话 RAG 检索：应用管理员配置的 Top-K 与相似度阈值（全局启用库）。
     */
    public List<KnowledgeSearchHit> searchForChat(String query) {
        return search(query, embeddingConfigService.getRetrievalConfig().topK(), null);
    }

    // ---------------- 切块（契约保留：供单元测试使用） ----------------

    /**
     * 按段落聚合切块：单块目标 ≤ {@code maxChars} 字符，
     * 超长段落按句末标点硬切（无标点处回退为字符硬切），低于最小长度的碎块丢弃。
     * 包级可见便于单元测试。
     */
    static List<String> chunkDocument(String text) {
        return chunkDocument(text, DOC_CHUNK_MAX_CHARS);
    }

    /**
     * 按段落聚合切块（切块长度可配置，见 {@link EmbeddingConfigService#getRetrievalConfig()}）。
     * 超长段落在句子边界处切断，避免块首出现"目\n标、"这类残句。
     */
    static List<String> chunkDocument(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : text.split("\n+")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.length() > maxChars) {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                int start = 0;
                while (start < line.length()) {
                    int end = Math.min(line.length(), start + maxChars);
                    int cut = lastSentenceEnd(line, start, end);
                    chunks.add(line.substring(start, cut));
                    start = cut;
                }
                continue;
            }
            if (current.length() > 0 && current.length() + line.length() + 1 > maxChars) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks.stream()
                .filter(chunk -> chunk.length() >= DOC_CHUNK_MIN_CHARS)
                .collect(Collectors.toList());
    }

    /** 在 [start, end) 内找最后一个句末标点并切在其后；找不到则按 end 硬切，保证不丢字 */
    private static int lastSentenceEnd(String line, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            if (SENTENCE_END.indexOf(line.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return end;
    }

    /**
     * 生成分段标题：取块首个完整句子或章节标题；当内容超过 {@link #TITLE_PREVIEW_CHARS}
     * 时按强句末→弱边界→空白优先级截断，避免出现残句或"标题=整段内容"。
     */
    static String deriveQuestion(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return "未命名片段";
        }
        String[] lines = chunk.split("\n", -1);
        StringBuilder title = new StringBuilder();
        boolean settled = false;
        for (String raw : lines) {
            String line = raw.strip()
                    .replaceFirst("^#{1,6}\\s*", "")
                    .replaceFirst("^[-*•]\\s*", "");
            if (line.isEmpty()) {
                continue;
            }
            if (title.length() > 0) {
                title.append(joinLines(title.toString(), line));
            }
            title.append(line);
            if (isHeadingLike(line) || endsSentence(line) || title.length() >= TITLE_PREVIEW_CHARS * 2) {
                settled = true;
                break;
            }
        }
        String result = title.toString().strip();
        if (result.isEmpty()) {
            return "未命名片段";
        }
        if (result.length() > TITLE_PREVIEW_CHARS) {
            String cut = cutAtSoftBoundary(result, TITLE_PREVIEW_CHARS);
            if (!cut.isEmpty()) {
                result = cut;
            }
        }
        return result;
    }

    /**
     * 智能截断：优先在句末标点截断，其次在弱边界（逗号/冒号），最后在空白；
     * 都找不到时按 maxChars 硬切（不带省略号，避免残句歧义）。
     */
    private static String cutAtSoftBoundary(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        int cut = lastIndexOfAny(text, maxChars - 1, SENTENCE_END);
        if (cut > 0) {
            return text.substring(0, cut);
        }
        cut = lastIndexOfAny(text, maxChars - 1, SOFT_END);
        if (cut > 0) {
            return text.substring(0, cut);
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\s+").matcher(text.substring(0, maxChars));
        if (m.find()) {
            cut = m.start();
            if (cut > 0) {
                return text.substring(0, cut);
            }
        }
        return text.substring(0, maxChars);
    }

    private static int lastIndexOfAny(String text, int fromInclusive, String chars) {
        for (int i = Math.min(fromInclusive, text.length() - 1); i >= 0; i--) {
            if (chars.indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return -1;
    }

    /** 标题到此处即可结束：章节/编号行（如"第五章 结束语""一、背景""1、首先"） */
    private static boolean isHeadingLike(String line) {
        return line.matches("^第[一二三四五六七八九十百千\\d]+[章节篇部卷段]\\s*.*")
                || line.matches("^[一二三四五六七八九十]+[、．.]\\s*.*")
                || line.matches("^\\d+[、.)）]\\s*.*");
    }

    private static boolean endsSentence(String s) {
        return !s.isEmpty() && SENTENCE_END.indexOf(s.charAt(s.length() - 1)) >= 0;
    }

    /** 拼接标题续行：任一侧含 CJK 时不加空格，否则补一个空格 */
    private static String joinLines(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return "";
        }
        char last = a.charAt(a.length() - 1);
        char first = b.charAt(0);
        return (isCjk(last) || isCjk(first)) ? "" : " ";
    }

    private static boolean isCjk(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    /** 根据文件名推断文档类型（仅用于展示）。 */
    private static String detectFileType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "pdf";
        }
        if (lower.endsWith(".docx")) {
            return "docx";
        }
        if (lower.endsWith(".md")) {
            return "md";
        }
        return "txt";
    }

    // ---------------- 内部工具 ----------------

    private void applyKbDto(KnowledgeBase kb, KnowledgeBaseDTO dto) {
        if (dto.getName() != null && !dto.getName().isBlank()) {
            kb.setName(dto.getName().trim());
        }
        kb.setDescription(dto.getDescription());
        kb.setColor(dto.getColor());
        kb.setIcon(dto.getIcon());
        if (dto.getEnabled() != null) {
            kb.setEnabled(dto.getEnabled());
        }
    }

    private void applySegmentDto(KnowledgeSegment seg, KnowledgeSegmentDTO dto) {
        if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
            seg.setTitle(dto.getTitle().trim());
        }
        if (dto.getContent() != null) {
            seg.setContent(dto.getContent());
        }
        seg.setCategory(dto.getCategory());
        seg.setKeywords(dto.getKeywords());
        seg.setSource(dto.getSource());
        if (dto.getEnabled() != null) {
            seg.setEnabled(dto.getEnabled());
        }
    }

    private KnowledgeBaseDTO toKbDto(KnowledgeBase kb, long docCount, long segCount) {
        KnowledgeBaseDTO dto = new KnowledgeBaseDTO();
        dto.setId(kb.getId());
        dto.setName(kb.getName());
        dto.setDescription(kb.getDescription());
        dto.setColor(kb.getColor());
        dto.setIcon(kb.getIcon());
        dto.setEnabled(kb.getEnabled());
        dto.setDocCount(docCount);
        dto.setSegmentCount(segCount);
        dto.setCreatedAt(kb.getCreatedAt());
        dto.setUpdatedAt(kb.getUpdatedAt());
        return dto;
    }

    private KnowledgeDocumentDTO toDocDto(KnowledgeDocument doc) {
        KnowledgeDocumentDTO dto = new KnowledgeDocumentDTO();
        dto.setId(doc.getId());
        dto.setKbId(doc.getKbId());
        dto.setName(doc.getName());
        dto.setFileName(doc.getFileName());
        dto.setFileType(doc.getFileType());
        dto.setStatus(doc.getStatus());
        dto.setCharCount(doc.getCharCount());
        dto.setSegmentCount(doc.getSegmentCount());
        dto.setErrorMessage(doc.getErrorMessage());
        dto.setCreatedAt(doc.getCreatedAt());
        dto.setUpdatedAt(doc.getUpdatedAt());
        return dto;
    }

    private KnowledgeSegmentDTO toSegDto(KnowledgeSegment seg) {
        KnowledgeSegmentDTO dto = new KnowledgeSegmentDTO();
        dto.setId(seg.getId());
        dto.setKbId(seg.getKbId());
        dto.setDocumentId(seg.getDocumentId());
        dto.setTitle(seg.getTitle());
        dto.setContent(seg.getContent());
        dto.setCategory(seg.getCategory());
        dto.setKeywords(seg.getKeywords());
        dto.setSource(seg.getSource());
        dto.setEnabled(seg.getEnabled());
        dto.setEmbedded(hasEmbedding(seg));
        dto.setCreatedAt(seg.getCreatedAt());
        dto.setUpdatedAt(seg.getUpdatedAt());
        return dto;
    }

    private KnowledgeSearchHit toHit(KnowledgeSegment seg, double score) {
        KnowledgeSearchHit hit = new KnowledgeSearchHit();
        hit.setId(seg.getId());
        hit.setQuestion(seg.getTitle());
        hit.setCategory(seg.getCategory());
        hit.setContent(seg.getContent());
        hit.setSource(seg.getSource());
        hit.setScore(Math.round(score * 1000D) / 1000D);
        return hit;
    }

    private boolean hasEmbedding(KnowledgeSegment seg) {
        return seg.getEmbeddingJson() != null && !seg.getEmbeddingJson().isBlank();
    }

    /** 向量化单条并落库；失败返回 false（不阻塞主流程） */
    private boolean embedSegment(KnowledgeSegment seg) {
        if (!embeddingClient.isEnabled()) {
            return false;
        }
        try {
            String text = buildEmbedText(seg.getTitle(), seg);
            float[] vector = embeddingClient.embed(text);
            if (vector == null) {
                return false;
            }
            seg.setEmbeddingJson(objectMapper.writeValueAsString(vector));
            segmentRepository.save(seg);
            return true;
        } catch (Exception e) {
            log.warn("Embed knowledge segment failed: id={}, title={}",
                    seg.getId(), seg.getTitle(), e);
            return false;
        }
    }

    /** 向量化文本：标题 + 分类 + 关键词 + 正文（截断控制 token） */
    private String buildEmbedText(String title, KnowledgeSegment seg) {
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append(title);
        }
        if (seg != null) {
            if (seg.getCategory() != null && !seg.getCategory().isBlank()) {
                sb.append(' ').append(seg.getCategory());
            }
            if (seg.getKeywords() != null && !seg.getKeywords().isBlank()) {
                sb.append(' ').append(seg.getKeywords());
            }
            if (seg.getContent() != null && !seg.getContent().isBlank()) {
                sb.append(' ').append(seg.getContent());
            }
        }
        String text = sb.toString().trim();
        return text.length() > EMBED_TEXT_MAX_CHARS ? text.substring(0, EMBED_TEXT_MAX_CHARS) : text;
    }

    private float[] parseEmbedding(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return null;
            }
            float[] vector = new float[node.size()];
            for (int i = 0; i < node.size(); i++) {
                vector[i] = (float) node.get(i).asDouble(0);
            }
            return vector;
        } catch (Exception e) {
            return null;
        }
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0D;
        }
        double dot = 0D, normA = 0D, normB = 0D;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0D || normB == 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private boolean segMatchesKeyword(KnowledgeSegment seg, String lowerKeyword) {
        for (String field : KEYWORD_FIELDS) {
            String value = switch (field) {
                case "title" -> seg.getTitle();
                case "content" -> seg.getContent();
                default -> seg.getKeywords();
            };
            if (value != null && value.toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
                return true;
            }
        }
        return false;
    }

    private static int len(String value) {
        return value == null ? 0 : value.length();
    }

    private record ScoredHit(KnowledgeSegment segment, double score) {
    }
}
