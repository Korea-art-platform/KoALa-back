package com.koala.koalaback.infra.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("업로드 이미지 최적화")
class ImageOptimizerTest {
    private static final int MAX_DIMENSION = 1600;

    private ImageOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = new ImageOptimizer();
        ReflectionTestUtils.setField(optimizer, "maxDimension", MAX_DIMENSION);
        ReflectionTestUtils.setField(optimizer, "jpegQuality", 0.82f);
    }

    @Test
    @DisplayName("큰 JPEG은 긴 변이 1600px로 줄고 용량도 함께 준다")
    void largeJpeg_isResizedAndShrunk() throws Exception {
        byte[] original = jpeg(3000, 2000);

        ImageOptimizer.OptimizedImage result = optimizer.optimize(
                file("photo.jpg", "image/jpeg", original));

        assertThat(result.changed()).isTrue();
        assertThat(result.contentType()).as("포맷은 유지").isEqualTo("image/jpeg");
        assertThat(result.size()).as("용량 감소").isLessThan(original.length);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isEqualTo(MAX_DIMENSION);
        assertThat(decoded.getWidth()).as("가로세로 비율 유지").isEqualTo(1600);
        assertThat(decoded.getHeight()).isEqualTo(1067);
    }

    @Test
    @DisplayName("이미 작은 이미지는 재인코딩하지 않는다 — 화질만 손해다")
    void smallImage_isLeftUntouched() throws Exception {
        byte[] original = jpeg(800, 600);

        ImageOptimizer.OptimizedImage result = optimizer.optimize(
                file("small.jpg", "image/jpeg", original));

        assertThat(result.changed()).isFalse();
        assertThat(result.bytes()).isEqualTo(original);
    }

    @Test
    @DisplayName("PNG는 PNG로 유지된다 — 확장자·Content-Type이 바뀌면 저장된 URL과 어긋난다")
    void largePng_keepsPngFormat() throws Exception {
        byte[] original = png(2400, 2400);

        ImageOptimizer.OptimizedImage result = optimizer.optimize(
                file("art.png", "image/png", original));

        assertThat(result.contentType()).isEqualTo("image/png");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isEqualTo(MAX_DIMENSION);
    }

    @Test
    @DisplayName("GIF는 건드리지 않는다 — 재인코딩하면 애니메이션이 깨진다")
    void gif_isSkipped() {
        byte[] original = new byte[] {'G', 'I', 'F', '8', '9', 'a', 0, 0};

        ImageOptimizer.OptimizedImage result = optimizer.optimize(
                file("anim.gif", "image/gif", original));

        assertThat(result.changed()).isFalse();
        assertThat(result.bytes()).isEqualTo(original);
    }

    @Test
    @DisplayName("동영상 등 이미지가 아닌 파일은 그대로 통과한다")
    void nonImage_passesThrough() {
        byte[] original = new byte[] {0, 1, 2, 3, 4};

        ImageOptimizer.OptimizedImage result = optimizer.optimize(
                file("clip.mp4", "video/mp4", original));

        assertThat(result.changed()).isFalse();
        assertThat(result.bytes()).isEqualTo(original);
    }

    @Test
    @DisplayName("디코딩할 수 없는 파일이어도 예외 없이 원본을 반환한다 — 업로드가 막히면 안 된다")
    void corruptImage_failsOpen() {
        byte[] garbage = new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};

        ImageOptimizer.OptimizedImage result = optimizer.optimize(
                file("broken.jpg", "image/jpeg", garbage));

        assertThat(result.changed()).isFalse();
        assertThat(result.bytes()).isEqualTo(garbage);
    }

    private MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    private byte[] jpeg(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(photoLike(width, height, BufferedImage.TYPE_INT_RGB), "jpeg", out);
        return out.toByteArray();
    }

    private byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(photoLike(width, height, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    private BufferedImage photoLike(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        try {
            g.setPaint(new GradientPaint(0, 0, new Color(40, 20, 80),
                    width, height, new Color(230, 200, 160)));
            g.fillRect(0, 0, width, height);
            for (int i = 0; i < 40; i++) {
                g.setColor(new Color((i * 37) % 255, (i * 61) % 255, (i * 97) % 255, 140));
                g.fillOval((i * 137) % width, (i * 191) % height,
                        300 + (i * 13) % 500, 250 + (i * 17) % 400);
            }
        } finally {
            g.dispose();
        }
        return image;
    }
}
