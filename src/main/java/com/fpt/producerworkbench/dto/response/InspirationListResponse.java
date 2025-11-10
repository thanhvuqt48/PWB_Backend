package com.fpt.producerworkbench.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Setter @Getter @Builder
@NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InspirationListResponse<T> {
    List<T> items;
    int page;
    int size;
    long totalElements;
    int totalPages;
}