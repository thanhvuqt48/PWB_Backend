package com.fpt.producerworkbench.storage;

public interface StorageService {
    /** Lưu bytes vào kho, trả về url/path lưu (relative). */
    String save(byte[] bytes, String key);
    /** Đọc lại bytes từ url/path đã lưu. */
    byte[] load(String url);
}
