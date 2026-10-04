package com.markingsystem.controller.admin;

import com.markingsystem.dto.DocumentImportResult;
import com.markingsystem.dto.KnowledgeBaseDTO;
import com.markingsystem.dto.KnowledgeDocumentDTO;
import com.markingsystem.dto.KnowledgeSearchHit;
import com.markingsystem.dto.KnowledgeSegmentDTO;
import com.markingsystem.service.EmbeddingConfigService;
import com.markingsystem.service.EmbeddingConfigService.EmbeddingConfig;
import com.markingsystem.service.EmbeddingConfigService.RetrievalConfig;
import com.markingsystem.service.KnowledgeBaseService;
import com.markingsystem.service.llm.EmbeddingClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin AI 知识库管理接口（仅管理员，MaxKB 式三级结构：知识库 → 文档 → 分段）。
 *
 * <p>提供知识库 / 文档 / 分段管理、文档上传切块导入、命中测试、
 * embedding 与检索参数配置。检索接口仅供内部（学生对话 / 应用调试）使用。
 */
@RestController
@RequestMapping("/api/admin/knowledge")
public class AdminKnowledgeController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private EmbeddingConfigService embeddingConfigService;

    @Autowired
    private EmbeddingClient embeddingClient;

    // ---------------- embedding / 检索配置（全局） ----------------

    /**
     * 读取当前 embedding 配置（API Key 仅返回掩码）。
     */
    @GetMapping("/embedding-config")
    public ResponseEntity<Map<String, Object>> getEmbeddingConfig(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        EmbeddingConfig config = embeddingConfigService.getConfig();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", Map.of(
                "baseUrl", config.baseUrl(),
                "model", config.model(),
                "enabled", config.enabled(),
                "hasApiKey", config.apiKey() != null && !config.apiKey().isBlank(),
                "apiKeyMasked", embeddingConfigService.maskApiKey(config.apiKey())
        ));
        return ResponseEntity.ok(result);
    }

    /**
     * 保存 embedding 配置。apiKey 为空表示不修改已保存的 Key。
     */
    @PutMapping("/embedding-config")
    public ResponseEntity<Map<String, Object>> saveEmbeddingConfig(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        String baseUrl = asString(body.get("baseUrl"));
        String model = asString(body.get("model"));
        String apiKey = asString(body.get("apiKey"));
        Boolean enabled = body.get("enabled") == null ? null : Boolean.parseBoolean(String.valueOf(body.get("enabled")));

        EmbeddingConfig config = embeddingConfigService.saveConfig(baseUrl, model, apiKey, enabled);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", Map.of(
                "baseUrl", config.baseUrl(),
                "model", config.model(),
                "enabled", config.enabled(),
                "hasApiKey", config.apiKey() != null && !config.apiKey().isBlank(),
                "apiKeyMasked", embeddingConfigService.maskApiKey(config.apiKey())
        ));
        return ResponseEntity.ok(result);
    }

    /**
     * 测试 embedding 连接（用当前配置调用一次向量化）。
     *
     * <p>可选的 body 可携带 baseUrl / model / apiKey 临时覆盖已保存配置，仅用于本次测试、不落库。
     */
    @PostMapping("/embedding-config/test")
    public ResponseEntity<Map<String, Object>> testEmbeddingConfig(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        String baseUrl = body == null ? null : asString(body.get("baseUrl"));
        String model = body == null ? null : asString(body.get("model"));
        String apiKey = body == null ? null : asString(body.get("apiKey"));
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", embeddingClient.testConnection(baseUrl, model, apiKey));
        return ResponseEntity.ok(result);
    }

    /**
     * 读取检索与分段参数（Top-K / 相似度阈值 / 文档切块长度）。
     */
    @GetMapping("/retrieval-config")
    public ResponseEntity<Map<String, Object>> getRetrievalConfig(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        RetrievalConfig config = embeddingConfigService.getRetrievalConfig();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", Map.of(
                "topK", config.topK(),
                "similarityThreshold", config.similarityThreshold(),
                "chunkLength", config.chunkLength()
        ));
        return ResponseEntity.ok(result);
    }

    /**
     * 保存检索与分段参数。
     */
    @PutMapping("/retrieval-config")
    public ResponseEntity<Map<String, Object>> saveRetrievalConfig(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Integer topK = body.get("topK") == null ? null : Integer.parseInt(String.valueOf(body.get("topK")));
        Double similarityThreshold = body.get("similarityThreshold") == null
                ? null : Double.parseDouble(String.valueOf(body.get("similarityThreshold")));
        Integer chunkLength = body.get("chunkLength") == null ? null : Integer.parseInt(String.valueOf(body.get("chunkLength")));
        RetrievalConfig config = embeddingConfigService.saveRetrievalConfig(topK, similarityThreshold, chunkLength);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", Map.of(
                "topK", config.topK(),
                "similarityThreshold", config.similarityThreshold(),
                "chunkLength", config.chunkLength()
        ));
        return ResponseEntity.ok(result);
    }

    // ---------------- 知识库 ----------------

    /**
     * 知识库列表（卡片式，含文档数与分段数统计）。
     */
    @GetMapping("/kbs")
    public ResponseEntity<Map<String, Object>> listKbs(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", knowledgeBaseService.kbList());
        result.put("stats", knowledgeBaseService.kbStats());
        return ResponseEntity.ok(result);
    }

    /**
     * 知识库详情。
     */
    @GetMapping("/kbs/{id}")
    public ResponseEntity<Map<String, Object>> getKb(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", knowledgeBaseService.getKb(id).orElse(null));
        return ResponseEntity.ok(result);
    }

    /**
     * 新建知识库。
     */
    @PostMapping("/kbs")
    public ResponseEntity<Map<String, Object>> createKb(
            @RequestBody KnowledgeBaseDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("知识库名称不能为空");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", knowledgeBaseService.createKb(dto));
        return ResponseEntity.ok(result);
    }

    /**
     * 编辑知识库。
     */
    @PutMapping("/kbs/{id}")
    public ResponseEntity<Map<String, Object>> updateKb(
            @PathVariable Long id,
            @RequestBody KnowledgeBaseDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", knowledgeBaseService.updateKb(id, dto).orElse(null));
        return ResponseEntity.ok(result);
    }

    /**
     * 删除知识库（连同文档与分段）。
     */
    @DeleteMapping("/kbs/{id}")
    public ResponseEntity<Map<String, Object>> deleteKb(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("success", knowledgeBaseService.deleteKb(id));
        return ResponseEntity.ok(result);
    }

    /**
     * 启停知识库。
     */
    @PutMapping("/kbs/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleKb(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("success", knowledgeBaseService.toggleKb(id));
        return ResponseEntity.ok(result);
    }

    // ---------------- 文档 ----------------

    /**
     * 知识库下的文档列表（按创建时间倒序）。
     */
    @GetMapping("/kbs/{kbId}/documents")
    public ResponseEntity<Map<String, Object>> listDocuments(
            @PathVariable Long kbId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        List<KnowledgeDocumentDTO> documents = knowledgeBaseService.documentsOf(kbId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", documents);
        return ResponseEntity.ok(result);
    }

    /**
     * 文档详情（文档信息 + 分段列表）。
     */
    @GetMapping("/documents/{docId}")
    public ResponseEntity<Map<String, Object>> getDocument(
            @PathVariable Long docId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", knowledgeBaseService.documentDetail(docId));
        return ResponseEntity.ok(result);
    }

    /**
     * 上传文档（PDF / Word .docx / TXT / Markdown）批量导入知识库：
     * 解析纯文本、按段落切块入库，并逐条尝试向量化。
     */
    @PostMapping("/kbs/{kbId}/documents/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @PathVariable Long kbId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "source", required = false) String source,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文档");
        }
        DocumentImportResult importResult = knowledgeBaseService.importDocument(kbId, file, category, source);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", importResult);
        return ResponseEntity.ok(result);
    }

    /**
     * 删除文档（连同其分段）。
     */
    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @PathVariable Long docId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("success", knowledgeBaseService.deleteDocument(docId));
        return ResponseEntity.ok(result);
    }

    /**
     * 重新向量化某文档内的全部分段。
     */
    @PostMapping("/documents/{docId}/re-embed")
    public ResponseEntity<Map<String, Object>> reembedDocument(
            @PathVariable Long docId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", Map.of("reembedded", knowledgeBaseService.reembedDoc(docId)));
        return ResponseEntity.ok(result);
    }

    // ---------------- 分段 ----------------

    /**
     * 知识库内分段列表（支持标题 / 正文 / 关键词过滤）。
     */
    @GetMapping("/kbs/{kbId}/segments")
    public ResponseEntity<Map<String, Object>> listSegments(
            @PathVariable Long kbId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", knowledgeBaseService.segmentsOfKb(kbId, keyword));
        return ResponseEntity.ok(result);
    }

    /**
     * 手动新增分段（不挂文档，显示在知识库「全部分段」视图）。
     */
    @PostMapping("/kbs/{kbId}/segments")
    public ResponseEntity<Map<String, Object>> createSegment(
            @PathVariable Long kbId,
            @RequestBody KnowledgeSegmentDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("分段标题不能为空");
        }
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw new IllegalArgumentException("分段内容不能为空");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", knowledgeBaseService.createSegment(kbId, dto));
        return ResponseEntity.ok(result);
    }

    /**
     * 编辑分段（保存后自动重新向量化）。
     */
    @PutMapping("/segments/{id}")
    public ResponseEntity<Map<String, Object>> updateSegment(
            @PathVariable Long id,
            @RequestBody KnowledgeSegmentDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", knowledgeBaseService.updateSegment(id, dto).orElse(null));
        return ResponseEntity.ok(result);
    }

    /**
     * 启停分段。
     */
    @PutMapping("/segments/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleSegment(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", knowledgeBaseService.toggleSegment(id).orElse(null));
        return ResponseEntity.ok(result);
    }

    /**
     * 删除分段。
     */
    @DeleteMapping("/segments/{id}")
    public ResponseEntity<Map<String, Object>> deleteSegment(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("success", knowledgeBaseService.deleteSegment(id));
        return ResponseEntity.ok(result);
    }

    /**
     * 单条分段重新向量化。
     */
    @PostMapping("/segments/{id}/re-embed")
    public ResponseEntity<Map<String, Object>> reembedSegment(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("success", knowledgeBaseService.reembedSegment(id));
        return ResponseEntity.ok(result);
    }

    /**
     * 补齐缺失向量（可限定知识库）。
     */
    @PostMapping("/segments/embed-missing")
    public ResponseEntity<Map<String, Object>> embedMissing(
            @RequestParam(value = "kbId", required = false) Long kbId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", Map.of("embedded", knowledgeBaseService.embedMissing(kbId)));
        return ResponseEntity.ok(result);
    }

    /**
     * 知识库内全部分段重新向量化。
     */
    @PostMapping("/kbs/{kbId}/reembed")
    public ResponseEntity<Map<String, Object>> reembedKb(
            @PathVariable Long kbId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", Map.of("reembedded", knowledgeBaseService.reembedAll(kbId)));
        return ResponseEntity.ok(result);
    }

    /**
     * 全部分段重新向量化（数据维护用）。
     */
    @PostMapping("/reembed")
    public ResponseEntity<Map<String, Object>> reembedAll(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", Map.of("reembedded", knowledgeBaseService.reembedAll(null)));
        return ResponseEntity.ok(result);
    }

    // ---------------- 检索 ----------------

    /**
     * 检索知识库（管理员侧预览检索效果用；kbId 可选，缺省检索全部启用库）。
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int topK,
            @RequestParam(value = "kbId", required = false) Long kbId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Teacher-Id", required = false) String teacherId,
            @RequestHeader(value = "X-Class-Name", required = false) String className,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        AdminAccessSupport.requireAdmin(userId, role, teacherId, className, studentNo);
        List<KnowledgeSearchHit> hits = kbId == null
                ? knowledgeBaseService.search(query, topK)
                : knowledgeBaseService.search(query, topK, List.of(kbId));
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", hits);
        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", exception.getMessage());
        return ResponseEntity.internalServerError().body(result);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
