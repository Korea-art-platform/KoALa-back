package com.koala.koalaback.infra.storage;

import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Profile("!local")
@RequiredArgsConstructor
public class S3StorageUploader implements StorageUploader {
    private final S3Client s3Client;
    private final ImageOptimizer imageOptimizer;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${koala.cdn-base-url}")
    private String cdnBaseUrl;

    public String upload(MultipartFile file, String directory) {
        validateFile(file);

        ImageOptimizer.OptimizedImage optimized = imageOptimizer.optimize(file);

        String key = buildKey(directory, file.getOriginalFilename());
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(optimized.contentType())
                            .contentLength(optimized.size())
                            .build(),
                    RequestBody.fromBytes(optimized.bytes())
            );
            log.info("S3 upload success: key={}, size={}KB, optimized={}",
                    key, optimized.size() / 1024, optimized.changed());
            return cdnBaseUrl + "/" + key;
        } catch (RuntimeException e) {
            log.error("S3 upload failed: key={}", key, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public String uploadBytes(byte[] bytes, String directory,
                              String filename, String contentType) {
        String key = buildKey(directory, filename);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) bytes.length)
                            .build(),
                    RequestBody.fromBytes(bytes)
            );
            log.info("S3 upload bytes success: key={}", key);
            return cdnBaseUrl + "/" + key;
        } catch (Exception e) {
            log.error("S3 upload bytes failed: key={}", key, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public void delete(String fileUrl) {
        String key = fileUrl.replace(cdnBaseUrl + "/", "");
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            log.info("S3 delete success: key={}", key);
        } catch (Exception e) {
            log.warn("S3 delete failed: key={}, error={}", key, e.getMessage());
        }
    }

    private String buildKey(String directory, String originalFilename) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String ext = extractExtension(originalFilename);
        return directory + "/" + uuid + ext;
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }

    private void validateFile(MultipartFile file) {
        FileValidator.validateImageOrVideo(file);
    }
}
