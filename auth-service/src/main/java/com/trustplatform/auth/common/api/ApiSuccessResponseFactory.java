package com.trustplatform.auth.common.api;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ApiSuccessResponseFactory {

    public <T> ApiSuccessResponse<T> build(T data) {
        return ApiSuccessResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public <T> ApiSuccessResponse<List<T>> buildPage(Page<T> page) {
        return ApiSuccessResponse.<List<T>>builder()
                .success(true)
                .data(page.getContent())
                .timestamp(Instant.now())
                .pagination(PaginationMetadata.builder()
                        .page(page.getNumber())
                        .size(page.getSize())
                        .totalElements(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .hasNext(page.hasNext())
                        .hasPrevious(page.hasPrevious())
                        .build())
                .build();
    }
}
