package com.markingsystem.service;

import com.markingsystem.config.CosProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class ProofFileStorageService {

    private static final Logger log = LoggerFactory.getLogger(ProofFileStorageService.class);

    private static final String LOCAL_UPLOAD_MARKER = "/uploads/extra-score-proof-images/";

    private final CosProperties cosProperties;
    private final ObjectProvider<COSClient> cosClientProvider;
    private final Path localUploadRoot = Paths.get("uploads").toAbsolutePath().normalize();

    public ProofFileStorageService(CosProperties cosProperties, ObjectProvider<COSClient> cosClientProvider) {
        this.cosProperties = cosProperties;
        this.cosClientProvider = cosClientProvider;
    }

    public String upload(MultipartFile file, String extension) throws IOException {
        return upload(file, "extra-score-proof-images", extension);
    }

    public String upload(MultipartFile file, String folder, String extension) throws IOException {
        String normalizedFolder = normalizeFolder(folder);
        String normalizedExtension = normalizeExtension(extension);
        if (cosProperties.isEnabled()) {
            String url = uploadToCos(file, normalizedFolder, normalizedExtension);
            if ("avatars".equals(normalizedFolder)) {
                mirrorLocalBestEffort(file.getBytes(), normalizedFolder, url);
            }
            return url;
        }
        return uploadToLocal(file, normalizedFolder, normalizedExtension);
    }

    public String uploadBytes(byte[] data, String extension, String contentType) throws IOException {
        return uploadBytes(data, "extra-score-proof-images", extension, contentType);
    }

    public String uploadBytes(byte[] data, String folder, String extension, String contentType) throws IOException {
        String normalizedFolder = normalizeFolder(folder);
        String normalizedExtension = normalizeExtension(extension);
        if (cosProperties.isEnabled()) {
            String url = uploadBytesToCos(data, normalizedFolder, normalizedExtension, contentType);
            if ("avatars".equals(normalizedFolder)) {
                mirrorLocalBestEffort(data, normalizedFolder, url);
            }
            return url;
        }
        return uploadBytesToLocal(data, normalizedFolder, normalizedExtension);
    }

    public InputStream open(String value) throws IOException {
        String text = defaultString(value);
        if (text.isBlank() || text.startsWith("data:") || text.startsWith("blob:")) {
            return null;
        }

        // COS 优先；未命中或客户端不可用时回退本地，避免「库里有 URL、文件已在本地 / 曾切换存储」时头像 404。
        if (isCosUrl(text) || isCosKey(text) || isCosUploadPath(text)) {
            InputStream cosStream = tryOpenCosObject(text);
            if (cosStream != null) {
                return cosStream;
            }
        }

        Path localPath = resolveLocalPath(text);
        if (localPath != null && Files.exists(localPath) && Files.isRegularFile(localPath)) {
            return Files.newInputStream(localPath);
        }
        return null;
    }

    private String uploadToCos(MultipartFile file, String folder, String extension) throws IOException {
        COSClient cosClient = cosClientProvider.getIfAvailable();
        if (cosClient == null) {
            throw new IllegalStateException("腾讯云 COS 未初始化");
        }
        String key = normalizePrefix(cosProperties.getPrefix())
                + "/"
                + folder
                + "/"
                + UUID.randomUUID()
                + extension;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest request = new PutObjectRequest(cosProperties.getBucket(), key, inputStream, metadata);
            cosClient.putObject(request);
        }
        return "/api/uploads/" + folder + "/" + key.substring(key.lastIndexOf('/') + 1);
    }

    private String uploadToLocal(MultipartFile file, String folder, String extension) throws IOException {
        Path root = Paths.get("uploads", folder).toAbsolutePath().normalize();
        Files.createDirectories(root);
        String filename = UUID.randomUUID() + extension;
        Path target = root.resolve(filename).normalize();
        file.transferTo(target);
        return "/api/uploads/" + folder + "/" + filename;
    }

    private String uploadBytesToCos(byte[] data, String folder, String extension, String contentType) throws IOException {
        COSClient cosClient = cosClientProvider.getIfAvailable();
        if (cosClient == null) {
            throw new IllegalStateException("腾讯云 COS 未初始化");
        }
        String key = normalizePrefix(cosProperties.getPrefix())
                + "/"
                + folder
                + "/"
                + UUID.randomUUID()
                + extension;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(data.length);
        metadata.setContentType(defaultString(contentType).isBlank() ? "application/octet-stream" : contentType);
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            PutObjectRequest request = new PutObjectRequest(cosProperties.getBucket(), key, inputStream, metadata);
            cosClient.putObject(request);
        }
        return "/api/uploads/" + folder + "/" + key.substring(key.lastIndexOf('/') + 1);
    }

    private String uploadBytesToLocal(byte[] data, String folder, String extension) throws IOException {
        Path root = Paths.get("uploads", folder).toAbsolutePath().normalize();
        Files.createDirectories(root);
        String filename = UUID.randomUUID() + extension;
        Path target = root.resolve(filename).normalize();
        Files.write(target, data);
        return "/api/uploads/" + folder + "/" + filename;
    }

    private InputStream tryOpenCosObject(String value) {
        COSClient cosClient = cosClientProvider.getIfAvailable();
        if (cosClient == null) {
            return null;
        }
        String key = resolveCosKey(value);
        if (key.isBlank()) {
            return null;
        }
        try {
            COSObject object = cosClient.getObject(cosProperties.getBucket(), key);
            return object == null ? null : object.getObjectContent();
        } catch (Exception ex) {
            log.warn("COS 读取失败，将尝试本地回退: bucket={}, key={}, reason={}",
                    cosProperties.getBucket(), key, ex.getMessage());
            return null;
        }
    }

    private void mirrorLocalBestEffort(byte[] data, String folder, String uploadUrl) {
        if (data == null || data.length == 0 || defaultString(uploadUrl).isBlank()) {
            return;
        }
        try {
            String filename = uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);
            if (filename.isBlank() || filename.contains("..")) {
                return;
            }
            Path root = Paths.get("uploads", folder).toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path target = root.resolve(filename).normalize();
            if (!target.startsWith(root)) {
                return;
            }
            Files.write(target, data);
        } catch (Exception ex) {
            log.warn("本地镜像上传文件失败（不影响主存储）: folder={}, url={}, reason={}",
                    folder, uploadUrl, ex.getMessage());
        }
    }

    private boolean isCosUrl(String value) {
        return value.startsWith(trimTrailingSlash(cosProperties.getDomain()) + "/")
                || value.contains(".cos.") && value.contains(".myqcloud.com/");
    }

    private boolean isCosKey(String value) {
        String prefix = normalizePrefix(cosProperties.getPrefix());
        return !prefix.isBlank() && value.startsWith(prefix + "/");
    }

    private boolean isCosUploadPath(String value) {
        return cosProperties.isEnabled() && value.startsWith("/api/uploads/");
    }

    private String resolveCosKey(String value) {
        String text = defaultString(value);
        if (text.startsWith("/api/uploads/")) {
            text = normalizePrefix(cosProperties.getPrefix()) + "/" + text.substring("/api/uploads/".length());
        }
        try {
            if (text.startsWith("http://") || text.startsWith("https://")) {
                URL url = URI.create(text).toURL();
                text = url.getPath();
            }
        } catch (Exception ignored) {
            return "";
        }
        text = URLDecoder.decode(text, StandardCharsets.UTF_8);
        while (text.startsWith("/")) {
            text = text.substring(1);
        }
        return text.replace("\\", "/");
    }

    private Path resolveLocalPath(String value) {
        String text = defaultString(value);
        try {
            if (text.startsWith("http://") || text.startsWith("https://")) {
                text = URI.create(text).getPath();
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        text = URLDecoder.decode(text, StandardCharsets.UTF_8);
        int index = text.indexOf("/uploads/");
        if (index < 0) {
            return null;
        }
        String relativePath = text.substring(index + "/uploads/".length())
                .replace("\\", "/");
        Path target = localUploadRoot.resolve(relativePath).normalize();
        return target.startsWith(localUploadRoot) ? target : null;
    }

    private String normalizePrefix(String value) {
        String normalized = defaultString(value).trim().replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeFolder(String value) {
        String normalized = defaultString(value).trim().replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank() || normalized.contains("..")) {
            return "extra-score-proof-images";
        }
        return normalized;
    }

    private String normalizeExtension(String value) {
        String normalized = defaultString(value).trim().toLowerCase();
        if (normalized.isBlank()) {
            return ".bin";
        }
        if (!normalized.startsWith(".")) {
            normalized = "." + normalized;
        }
        if (!normalized.matches("\\.[a-z0-9]{1,8}")) {
            return ".bin";
        }
        return normalized;
    }

    private String trimTrailingSlash(String value) {
        String text = defaultString(value).trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
