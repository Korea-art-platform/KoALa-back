package com.koala.koalaback.infra.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageUploader {
    String upload(MultipartFile file, String directory);

    String uploadBytes(byte[] bytes, String directory, String filename, String contentType);

    void delete(String fileUrl);
}
