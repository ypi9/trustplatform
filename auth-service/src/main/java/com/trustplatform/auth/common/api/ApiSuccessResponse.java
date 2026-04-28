package com.trustplatform.auth.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiSuccessResponse<T> {
    private final boolean success;
    private final T data;
    private final Instant timestamp;
    private final PaginationMetadata pagination;
}
