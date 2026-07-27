package com.sitech.prodai.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 智读配置文档暂存：选择文件后立即上传，发送消息时再按 fileId 解析映射。
 */
@Service
public class ConfigDocumentStorage {

    private final Path uploadDir;

    public ConfigDocumentStorage() {
        this.uploadDir = Paths.get("uploads", "ontology").toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create ontology upload dir: " + uploadDir, e);
        }
    }

    public Map<String, Object> store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        String original = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        String safeOriginal = sanitizeOriginalName(original);
        String ext = extensionOf(safeOriginal);
        String fileId = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = uploadDir.resolve(fileId);
        try {
            // 避免 Windows 下 MultipartFile.transferTo(Path) 跨盘符/临时文件问题
            Files.copy(file.getInputStream(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("upload failed: " + e.getMessage(), e);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("file_id", fileId);
        body.put("fileId", fileId);
        body.put("fileName", safeOriginal);
        body.put("filename", safeOriginal);
        body.put("size", file.getSize());
        body.put("url", "/api/v1/product-ontology/config/files/" + fileId);
        return body;
    }

    public byte[] readBytes(String fileId) {
        Path path = resolve(fileId);
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("read uploaded file failed: " + e.getMessage(), e);
        }
    }

    public Path resolve(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("file_id is required");
        }
        String id = fileId.trim();
        if (id.contains("..") || id.contains("/") || id.contains("\\")) {
            throw new IllegalArgumentException("invalid file_id");
        }
        Path path = uploadDir.resolve(id).normalize();
        if (!path.startsWith(uploadDir) || !Files.exists(path)) {
            throw new IllegalArgumentException("uploaded file not found: " + id);
        }
        return path;
    }

    private static String sanitizeOriginalName(String name) {
        String n = name.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        n = n.replaceAll("[\\r\\n\\t]", "_").trim();
        return n.isEmpty() ? "upload.bin" : n;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        String ext = name.substring(dot).toLowerCase(Locale.ROOT);
        if (!ext.matches("\\.[a-z0-9]{1,8}")) {
            return "";
        }
        return ext;
    }
}
