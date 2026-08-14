package com.koala.koalaback.infra.storage;

import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalStorageUploader implements StorageUploader {
    private final ImageOptimizer imageOptimizer;

    @Value("${koala.storage.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${koala.cdn-base-url:http://localhost:8080}")
    private String cdnBaseUrl;

    private static final Set<String> ALLOWED_ROLES = Set.of(

            "hero", "gallery", "spine_360", "profile", "thumbnail", "detail", "cover",

            "banner", "banners",

            "interview_video", "interview_image", "studio", "hands",

            "main", "material", "packaging"
    );

    @Override
    public String upload(MultipartFile file, String directory) {
        FileValidator.validateImageOrVideo(file);
        validateDirectory(directory);

        ImageOptimizer.OptimizedImage optimized = imageOptimizer.optimize(file);

        String key = buildKey(directory, file.getOriginalFilename());
        Path target = resolveAndValidatePath(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, optimized.bytes());
            log.info("Local upload success: path={}, size={}KB, optimized={}",
                    target, optimized.size() / 1024, optimized.changed());
            return cdnBaseUrl + "/uploads/" + key;
        } catch (IOException e) {
            log.error("Local upload failed: path={}", target, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public String uploadBytes(byte[] bytes, String directory, String filename, String contentType) {
        validateDirectory(directory);
        String key = buildKey(directory, filename);
        Path target = resolveAndValidatePath(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            log.info("Local upload bytes success: path={}", target);
            return cdnBaseUrl + "/uploads/" + key;
        } catch (IOException e) {
            log.error("Local upload bytes failed: path={}", target, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public void delete(String fileUrl) {
        String prefix = cdnBaseUrl + "/uploads/";
        if (!fileUrl.startsWith(prefix)) {
            log.warn("Local delete skipped — URL does not match prefix: {}", fileUrl);
            return;
        }
        String relativePath = fileUrl.substring(prefix.length());
        Path target = Paths.get(uploadDir, relativePath);
        try {
            Files.deleteIfExists(target);
            log.info("Local delete success: path={}", target);
        } catch (IOException e) {
            log.warn("Local delete failed: path={}, error={}", target, e.getMessage());
        }
    }

    private void validateDirectory(String directory) {
        if (directory == null || directory.contains("..")) {
            log.warn("Path traversal attempt detected: directory={}", directory);
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String[] parts = directory.split("/");
        String role = parts[parts.length - 1].toLowerCase();
        if (!ALLOWED_ROLES.contains(role)) {
            log.warn("Disallowed media role in upload directory: role={}", role);
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private Path resolveAndValidatePath(String key) {
        Path uploadBase = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = Paths.get(uploadDir, key).toAbsolutePath().normalize();
        if (!target.startsWith(uploadBase)) {
            log.warn("Path traversal blocked: key={}", key);
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return target;
    }

    private String buildKey(String directory, String originalFilename) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String ext  = extractSafeExtension(originalFilename);
        return directory + "/" + uuid + ext;
    }

    private String extractSafeExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        String ext = filename.substring(filename.lastIndexOf(".")).toLowerCase();

        return Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".mp4", ".mov").contains(ext)
                ? ext : "";
    }
}
