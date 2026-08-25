package com.koala.koalaback.domain.maintenance;

import com.koala.koalaback.infra.storage.ImageDerivatives;
import com.koala.koalaback.infra.storage.ImageOptimizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.HashSet;
import java.util.Set;

/**
 * 이미 올라가 있는 이미지에 축소본과 캐시 헤더를 채워 넣는다.
 *
 * 신규 업로드는 S3StorageUploader 가 처음부터 둘 다 처리한다. 이 작업은
 * 그 전에 올라간 것들을 따라잡기 위한 일회성 보정이다.
 *
 * 한 번에 다 돌리지 않고 호출당 limit 만큼만 처리하고 다음 토큰을 돌려준다.
 * 원본이 2000px 이라 디코딩·인코딩이 무거워, 한 번에 전부 돌리면 운영 서버의
 * 힙(768MB)과 CPU 를 오래 붙잡기 때문이다.
 */
@Slf4j
@Service
@Profile("!local")
@RequiredArgsConstructor
public class ImageBackfillService {
    private final S3Client s3Client;
    private final ImageOptimizer imageOptimizer;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public record Result(
            int scanned,
            int thumbsCreated,
            int headersFixed,
            int skipped,
            int failed,
            String nextToken,
            boolean done
    ) {}

    public Result run(String prefix, int limit, String continuationToken) {
        ListObjectsV2Request.Builder req = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .maxKeys(Math.min(Math.max(limit, 1), 1000));
        if (continuationToken != null && !continuationToken.isBlank()) {
            req.continuationToken(continuationToken);
        }

        ListObjectsV2Response listing = s3Client.listObjectsV2(req.build());

        int scanned = 0, thumbs = 0, headers = 0, skipped = 0, failed = 0;
        Set<String> keys = new HashSet<>();
        for (S3Object o : listing.contents()) keys.add(o.key());

        for (S3Object obj : listing.contents()) {
            String key = obj.key();
            scanned++;

            if (ImageDerivatives.isThumbKey(key) || !isImage(key)) {
                skipped++;
                continue;
            }

            try {
                boolean needThumb = !keys.contains(ImageDerivatives.thumbKey(key))
                        && !exists(ImageDerivatives.thumbKey(key));
                boolean needHeader = !hasCacheControl(key);

                if (!needThumb && !needHeader) {
                    skipped++;
                    continue;
                }

                if (needThumb) {
                    if (createThumb(key)) thumbs++;
                    else skipped++;
                }
                if (needHeader) {
                    fixCacheControl(key);
                    headers++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("[ImageBackfill] 처리 실패 — 건너뛴다: key={}, error={}", key, e.getMessage());
            }
        }

        String next = Boolean.TRUE.equals(listing.isTruncated()) ? listing.nextContinuationToken() : null;
        log.info("[ImageBackfill] prefix={} scanned={} thumbs={} headers={} skipped={} failed={} more={}",
                prefix, scanned, thumbs, headers, skipped, failed, next != null);

        return new Result(scanned, thumbs, headers, skipped, failed, next, next == null);
    }

    private boolean isImage(String key) {
        String k = key.toLowerCase();
        return k.endsWith(".jpg") || k.endsWith(".jpeg") || k.endsWith(".png");
    }

    private boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private boolean hasCacheControl(String key) {
        try {
            String cc = s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(key).build()).cacheControl();
            return cc != null && !cc.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean createThumb(String key) {
        ResponseBytes<GetObjectResponse> got = s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build());

        String contentType = got.response().contentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = key.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
        }

        final String ct = contentType;
        return imageOptimizer.derive(got.asByteArray(), ct, ImageDerivatives.THUMB_EDGE)
                .map(thumb -> {
                    s3Client.putObject(
                            PutObjectRequest.builder()
                                    .bucket(bucket)
                                    .key(ImageDerivatives.thumbKey(key))
                                    .contentType(ct)
                                    .contentLength((long) thumb.bytes().length)
                                    .cacheControl(ImageDerivatives.CACHE_CONTROL)
                                    .build(),
                            RequestBody.fromBytes(thumb.bytes()));
                    return true;
                })
                .orElse(false);
    }

    /** 메타데이터만 바꾸려면 자기 자신으로 복사하면서 REPLACE 해야 한다. */
    private void fixCacheControl(String key) {
        String contentType = key.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket).sourceKey(key)
                .destinationBucket(bucket).destinationKey(key)
                .cacheControl(ImageDerivatives.CACHE_CONTROL)
                .contentType(contentType)
                .metadataDirective(MetadataDirective.REPLACE)
                .build());
    }
}
