package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class LocalStorageServiceImpl implements StorageService {

    @Value("${storage.base-dir}")
    private String baseDir;

    @Override
    public String save(byte[] bytes, String key) {
        try {
            Path target = Path.of(baseDir, key).normalize();
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Cannot save file: " + key, e);
        }
    }

    @Override
    public byte[] load(String url) {
        try {
            Path p = Path.of(baseDir, url).normalize();
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read file: " + url, e);
        }
    }
}
