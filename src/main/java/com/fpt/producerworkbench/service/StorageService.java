package com.fpt.producerworkbench.service;

public interface StorageService {

    String save(byte[] bytes, String key);
    byte[] load(String url);
}
