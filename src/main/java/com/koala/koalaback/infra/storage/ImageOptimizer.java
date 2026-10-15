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
import java.util.Optional;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

@Slf4j
@Component
public class ImageOptimizer {
    @Value("${koala.image.max-dimension:1600}")
    private int maxDimension;

    @Value("${koala.image.jpeg-quality:0.82}")
    private float jpegQuality;

    @Value("${koala.image.webp-quality:0.85}")
    private float webpQuality = 0.85f;

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
                return new OptimizedImage(original, contentType, false);
            }

            BufferedImage resized = resize(source, longEdge);
            byte[] encoded = encode(resized, contentType);

            if (encoded.length >= original.length) {
                log.debug("최적화 결과가 더 큼 — 원본 사용: {} → {} bytes", original.length, encoded.length);
                return new OptimizedImage(original, contentType, false);
            }

            log.info("이미지 최적화: {}x{} → {}x{}, {}KB → {}KB",
                    source.getWidth(), source.getHeight(),
                    resized.getWidth(), resized.getHeight(),
                    original.length / 1024, encoded.length / 1024);

            return new OptimizedImage(encoded, contentType, true);
        } catch (Exception e) {
            log.warn("이미지 최적화 실패 — 원본 업로드: filename={}, error={}",
                    file.getOriginalFilename(), e.getMessage());
            try {
                return new OptimizedImage(file.getBytes(), contentType, false);
            } catch (Exception inner) {
                throw new IllegalStateException("업로드 파일을 읽을 수 없습니다", inner);
            }
        }
    }

    /**
     * 카드·목록용 축소본을 만든다. 원본이 이미 그보다 작으면 만들지 않는다.
     *
     * 상품 이미지는 원본이 2000px 인데 목록에서는 88px 로 그려진다. 그대로
     * 내려보내면 화면 한 번에 8MB 가 넘어가므로 별도 파일로 줄여 둔다.
     */
    public Optional<OptimizedImage> derive(byte[] original, String contentType, int longEdge) {
        if (!isOptimizable(contentType)) return Optional.empty();
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null) return Optional.empty();

            int edge = Math.max(source.getWidth(), source.getHeight());
            if (edge <= longEdge) return Optional.empty();

            BufferedImage resized = scale(source, (double) longEdge / edge);
            byte[] encoded = encode(resized, contentType);
            if (encoded.length >= original.length) return Optional.empty();

            return Optional.of(new OptimizedImage(encoded, contentType, true));
        } catch (Exception e) {
            log.warn("축소본 생성 실패 — 건너뛴다: longEdge={}, error={}", longEdge, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<OptimizedImage> deriveForScreen(byte[] original, String contentType, int maxWidth) {
        if (!isOptimizable(contentType)) return Optional.empty();
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null) return Optional.empty();

            BufferedImage sized = source.getWidth() > maxWidth
                    ? scale(source, (double) maxWidth / source.getWidth())
                    : source;
            boolean transparent = hasTransparency(sized);
            String outType;
            byte[] encoded;
            if (!transparent) {
                outType = "image/jpeg";
                encoded = encode(toRgb(sized), outType);
            } else {
                byte[] webp = encodeWebp(sized);
                outType = webp != null ? "image/webp" : "image/png";
                encoded = webp != null ? webp : encode(sized, outType);
            }
            if (encoded.length >= original.length) return Optional.empty();

            return Optional.of(new OptimizedImage(encoded, outType, true));
        } catch (Exception e) {
            log.warn("화면용 사본 생성 실패 — 건너뛴다: maxWidth={}, error={}", maxWidth, e.getMessage());
            return Optional.empty();
        }
    }

    private byte[] encodeWebp(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) return null;
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                for (String type : param.getCompressionTypes()) {
                    if (type.toLowerCase().contains("lossy")) {
                        param.setCompressionType(type);
                        break;
                    }
                }
                param.setCompressionQuality(webpQuality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
            ios.flush();
            return out.toByteArray();
        } catch (Throwable e) {
            log.warn("WebP 변환 실패 — PNG 로 둔다: {}", e.getMessage());
            return null;
        } finally {
            writer.dispose();
        }
    }

    private boolean hasTransparency(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) return false;
        int w = image.getWidth();
        int h = image.getHeight();
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int argb : row) {
                if ((argb >>> 24) != 0xFF) return true;
            }
        }
        return false;
    }

    private BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) return image;
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private boolean isOptimizable(String contentType) {
        return "image/jpeg".equals(contentType)
                || "image/jpg".equals(contentType)
                || "image/png".equals(contentType);
    }

    private BufferedImage resize(BufferedImage source, int longEdge) {
        return scale(source, (double) maxDimension / longEdge);
    }

    private BufferedImage scale(BufferedImage source, double ratio) {
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));

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

            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
