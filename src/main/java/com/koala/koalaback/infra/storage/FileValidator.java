package com.koala.koalaback.infra.storage;

import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class FileValidator {
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 500L * 1024 * 1024;

    private static final byte[] JPEG_MAGIC  = {(byte)0xFF, (byte)0xD8, (byte)0xFF};
    private static final byte[] PNG_MAGIC   = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF87_MAGIC = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61};
    private static final byte[] GIF89_MAGIC = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
    private static final byte[] WEBP_RIFF   = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MARKER = {0x57, 0x45, 0x42, 0x50};

    private static final byte[] FTYP_MARKER = {0x66, 0x74, 0x79, 0x70};

    private static final byte[] AVI_MARKER  = {0x41, 0x56, 0x49, 0x20};

    private static final byte[] WEBM_MAGIC  = {0x1A, 0x45, (byte)0xDF, (byte)0xA3};

    private static final int SIGNATURE_READ_BYTES = 16;

    private FileValidator() {}

    public static void validateImage(MultipartFile file) {
        validateNotEmpty(file);
        validateContentType(file, "image/");
        validateSize(file, MAX_IMAGE_SIZE, "이미지");
        validateImageSignature(readSignatureBytes(file));
    }

    public static void validateVideo(MultipartFile file) {
        validateNotEmpty(file);
        validateContentType(file, "video/");
        validateSize(file, MAX_VIDEO_SIZE, "동영상");
        validateVideoSignature(readSignatureBytes(file));
    }

    public static void validateImageOrVideo(MultipartFile file) {
        validateNotEmpty(file);
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        if (contentType.startsWith("image/")) {
            validateImage(file);
        } else if (contentType.startsWith("video/")) {
            validateVideo(file);
        } else {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private static void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일이 비어있습니다.");
        }
    }

    private static void validateContentType(MultipartFile file, String expectedPrefix) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private static void validateSize(MultipartFile file, long maxSize, String typeName) {
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    typeName + " 파일은 최대 " + (maxSize / 1024 / 1024) + "MB까지 허용됩니다.");
        }
    }

    private static byte[] readSignatureBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] bytes = new byte[SIGNATURE_READ_BYTES];
            int read = is.read(bytes);
            if (read < 4) {
                throw new BusinessException(ErrorCode.INVALID_FILE_SIGNATURE);
            }
            return bytes;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_FILE_SIGNATURE);
        }
    }

    private static void validateImageSignature(byte[] sig) {
        if (startsWith(sig, JPEG_MAGIC))   return;
        if (startsWith(sig, PNG_MAGIC))    return;
        if (startsWith(sig, GIF87_MAGIC))  return;
        if (startsWith(sig, GIF89_MAGIC))  return;

        if (startsWith(sig, WEBP_RIFF) && sig.length >= 12 &&
            sig[8] == WEBP_MARKER[0] && sig[9] == WEBP_MARKER[1] &&
            sig[10] == WEBP_MARKER[2] && sig[11] == WEBP_MARKER[3]) return;

        if (sig.length >= 8 && sig[4] == FTYP_MARKER[0] && sig[5] == FTYP_MARKER[1] &&
            sig[6] == FTYP_MARKER[2] && sig[7] == FTYP_MARKER[3]) return;

        throw new BusinessException(ErrorCode.INVALID_FILE_SIGNATURE);
    }

    private static void validateVideoSignature(byte[] sig) {
        if (sig.length >= 8 &&
            sig[4] == FTYP_MARKER[0] && sig[5] == FTYP_MARKER[1] &&
            sig[6] == FTYP_MARKER[2] && sig[7] == FTYP_MARKER[3]) return;

        if (startsWith(sig, WEBP_RIFF) && sig.length >= 12 &&
            sig[8] == AVI_MARKER[0] && sig[9] == AVI_MARKER[1] &&
            sig[10] == AVI_MARKER[2] && sig[11] == AVI_MARKER[3]) return;

        if (startsWith(sig, WEBM_MAGIC)) return;

        throw new BusinessException(ErrorCode.INVALID_FILE_SIGNATURE);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        return Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }
}
