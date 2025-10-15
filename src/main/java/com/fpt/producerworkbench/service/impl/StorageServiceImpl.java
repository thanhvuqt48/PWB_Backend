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
public class StorageServiceImpl implements StorageService {

    @Value("${storage.base-dir:storage}")
    private String baseDir;

    private Path resolve(String key) {
        return Path.of(baseDir).resolve(key).normalize().toAbsolutePath();
    }

    @Override
    public String save(byte[] bytes, String key) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Cannot save file: " + key, e);
        }
    }

    @Override
    public byte[] load(String url) {
        // alias cho read để tương thích interface cũ
        return read(url);
    }

    @Override
    public byte[] read(String key) {
        try {
            Path path = resolve(key);     // dùng baseDir, không phải 'root'
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read file: " + key, e);
        }
    }
}
