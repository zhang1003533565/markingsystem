package com.markingsystem.controller;

import com.markingsystem.service.ProofFileStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.Locale;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final ProofFileStorageService proofFileStorageService;

    public UploadController(ProofFileStorageService proofFileStorageService) {
        this.proofFileStorageService = proofFileStorageService;
    }

    @GetMapping("/**")
    public ResponseEntity<Resource> download(HttpServletRequest request) throws Exception {
        String path = request.getRequestURI();
        if (!isSafeMediaPath(path)) {
            return ResponseEntity.notFound().build();
        }
        InputStream inputStream;
        try {
            inputStream = proofFileStorageService.open(path);
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }
        if (inputStream == null) {
            return ResponseEntity.notFound().build();
        }

        String contentType = resolveContentType(path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setContentDisposition(ContentDisposition.inline().build());
        headers.setCacheControl(CacheControl.noStore());
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(inputStream));
    }

    private boolean isSafeMediaPath(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp|mp4|webm|mov|m4v|avi)$");
    }

    private String resolveContentType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        return "image/jpeg";
    }
}
