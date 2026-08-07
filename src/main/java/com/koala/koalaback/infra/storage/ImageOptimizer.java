package com.koala.koalaback.infra.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * 업로드 이미지 축소·재인코딩.
 *
 * <p>상품 이미지가 원본 그대로 올라가 1장에 800KB를 넘는다. CDN 을 붙여 전송은 빨라졌지만
 * 첫 방문자(캐시 미스)와 모바일 데이터 사용량은 그대로다. 업로드 시점에 한 번 줄이는 게
 * 가장 싸고 확실하다.
 *
 * <h3>설계 원칙</h3>
 * <ul>
 *   <li><b>포맷을 바꾸지 않는다</b> — JPEG→JPEG, PNG→PNG. 확장자·Content-Type 이 그대로라
 *       기존 URL 생성 로직과 저장된 데이터에 영향이 없다. PNG 투명도도 보존된다.</li>
 *   <li><b>실패하면 원본을 그대로 올린다</b> — 최적화가 안 됐다고 상품 등록이 막히면 안 된다.</li>
 *   <li><b>이미 작은 이미지는 건드리지 않는다</b> — 재인코딩은 화질 손실만 남긴다.</li>
 *   <li>GIF(애니메이션 손상), WebP/동영상(ImageIO 미지원)은 건너뛴다.</li>
 * </ul>
 */
@Slf4j
@Component
public class ImageOptimizer {

    /** 긴 변 기준 최대 픽셀 — 상세 화면 확대까지 감안한 값 */
    @Value("${koala.image.max-dimension:1600}")
    private int maxDimension;

    /** JPEG 재인코딩 품질 (0~1). 0.82 는 육안 차이가 거의 없으면서 용량이 크게 준다 */
    @Value("${koala.image.jpeg-quality:0.82}")
    private float jpegQuality;

    public record OptimizedImage(byte[] bytes, String contentType, boolean changed) {
        public long size() { return bytes.length; }
    }

    public OptimizedImage optimize(MultipartFile file) {
        String contentType = file.getContentType();
        try {
            byte[] original = file.getBytes();

            if (!isOptimizable(contentType)) {
                return new OptimizedImage(original, contentType, false);
            }

            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null) {
                log.debug("이미지 디코딩 불가 — 원본 업로드: contentType={}", contentType);
                return new OptimizedImage(original, contentType, false);
            }

            int longEdge = Math.max(source.getWidth(), source.getHeight());
            if (longEdge <= maxDimension) {
                // 이미 충분히 작다 — 재인코딩하면 화질만 손해다
                return new OptimizedImage(original, contentType, false);
            }

            BufferedImage resized = resize(source, longEdge);
            byte[] encoded = encode(resized, contentType);

            if (encoded.length >= original.length) {
                // 줄이려다 오히려 커지는 경우(이미 고압축된 원본 등)는 원본을 쓴다
                log.debug("최적화 결과가 더 큼 — 원본 사용: {} → {} bytes", original.length, encoded.length);
                return new OptimizedImage(original, contentType, false);
            }

            log.info("이미지 최적화: {}x{} → {}x{}, {}KB → {}KB",
                    source.getWidth(), source.getHeight(),
                    resized.getWidth(), resized.getHeight(),
                    original.length / 1024, encoded.length / 1024);

            return new OptimizedImage(encoded, contentType, true);

        } catch (Exception e) {
            // 최적화 실패가 업로드 실패로 이어지면 안 된다
            log.warn("이미지 최적화 실패 — 원본 업로드: filename={}, error={}",
                    file.getOriginalFilename(), e.getMessage());
            try {
                return new OptimizedImage(file.getBytes(), contentType, false);
            } catch (Exception inner) {
                throw new IllegalStateException("업로드 파일을 읽을 수 없습니다", inner);
            }
        }
    }

    // ── Private helpers ───────────────────────────────────

    private boolean isOptimizable(String contentType) {
        // GIF 는 애니메이션이 깨지고, WebP·동영상은 ImageIO 가 못 읽는다
        return "image/jpeg".equals(contentType)
                || "image/jpg".equals(contentType)
                || "image/png".equals(contentType);
    }

    private BufferedImage resize(BufferedImage source, int longEdge) {
        double ratio = (double) maxDimension / longEdge;
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));

        // JPEG 는 알파를 못 담으므로 알파 유무에 따라 타입을 나눈다
        int type = source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;

        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private byte[] encode(BufferedImage image, String contentType) throws Exception {
        boolean png = "image/png".equals(contentType);
        String format = png ? "png" : "jpeg";

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        if (png) {
            // PNG 는 무손실이라 품질 파라미터가 없다 — 축소만으로 용량이 준다
            ImageIO.write(image, format, out);
            return out.toByteArray();
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        }

        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(jpegQuality);
            }
            // 재인코딩 과정에서 EXIF(촬영 위치 등)도 함께 제거된다
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
