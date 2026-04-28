package com.trustplatform.auth.common.api;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaginationMetadata {
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean hasNext;
    private final boolean hasPrevious;
}
