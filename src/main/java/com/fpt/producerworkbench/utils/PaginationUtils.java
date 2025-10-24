package com.fpt.producerworkbench.utils;

import com.fpt.producerworkbench.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import java.util.function.Function;

public class PaginationUtils {

    public static <T, R> PageResponse<R> paginate(
            int page,
            int size,
            Page<T> pageEntity,
            final Function<T, R> mapper) {

        return PageResponse.<R>builder()
                .page(page)
                .size(size)
                .totalPages(pageEntity.getTotalPages())
                .totalElements(pageEntity.getTotalElements())
                .content(pageEntity.getContent()
                        .stream()
                        .map(mapper)
                        .toList())
                .build();
    }

}
