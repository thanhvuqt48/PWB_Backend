package com.fpt.producerworkbench.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;
import java.util.UUID;

@Slf4j
public class FileUtils {

    private FileUtils() {}

    public static String generateKeyName(MultipartFile file) {
        String originalFilename = Objects.requireNonNull(file.getOriginalFilename());
        return UUID.randomUUID() + "_" + originalFilename
                .substring(originalFilename.lastIndexOf("."));
    }

}