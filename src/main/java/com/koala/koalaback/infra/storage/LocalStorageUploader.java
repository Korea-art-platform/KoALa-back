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

/**
 * 로컬 개발용 파일 저장소.
 * 파일을 로컬 디스크에 저장하고 Spring Boot 정적 리소스로 제공합니다.
 * 업로드된 파일은 {koala.storage.upload-dir}/{directory}/ 에 저장됩니다.
 * 접근 URL: {koala.cdn-base-url}/uploads/{directory}/{uuid}{ext}
 */
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

    // 허용된 mediaRole 목록 — 이 외의 값은 경로 구성에 사용 불가
    private static final Set<String> ALLOWED_ROLES = Set.of(
            // 공통
            "hero", "gallery", "spine_360", "profile", "thumbnail", "detail", "cover",
            // 배너
            "banner", "banners",
            // 아티스트
            "interview_video", "interview_image", "studio", "hands",
            // SKU/상품
            "main", "material", "packaging"
    );

    @Override
    public String upload(MultipartFile file, String directory) {
        FileValidator.validateImageOrVideo(file);
        validateDirectory(directory);

        // 운영(S3)과 동일하게 로컬에서도 축소해서 저장한다 — 환경별 결과가 달라지면 안 된다
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
        // /uploads/ 이후 경로를 추출해 파일 삭제
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

    /**
     * directory가 허용된 mediaRole 세그먼트만 포함하는지 검증합니다.
     * "skus/SKU-001/gallery" 형태를 기대 — 마지막 세그먼트가 허용 목록에 있어야 합니다.
     */
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

    /**
     * 최종 경로가 uploadDir 하위에 위치하는지 검증합니다 (Path Traversal 방어 2중 확인).
     */
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

    /** 확장자를 안전하게 추출합니다 (이중 확장자 공격 방지 — 마지막 점 이후만 사용). */
    private String extractSafeExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        String ext = filename.substring(filename.lastIndexOf(".")).toLowerCase();
        // 허용 확장자만 통과
        return Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".mp4", ".mov").contains(ext)
                ? ext : "";
    }
}
