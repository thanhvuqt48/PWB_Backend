package com.fpt.producerworkbench.utils;

import java.security.SecureRandom;

public final class OrderCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OrderCodeGenerator() {}

    public static long generate() {
        // 13 digits from epoch millis + 3 digits random => 16 digits within PayOS constraints
        long millis = System.currentTimeMillis();
        int suffix = RANDOM.nextInt(900) + 100; // 100..999
        // combine ensuring it fits into long and remains positive
        return millis * 1000L + suffix; // up to ~16 digits
    }
}


