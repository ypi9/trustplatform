package com.trustplatform.auth.common.logging;

import org.slf4j.MDC;

public final class RequestCorrelation {

    public static final String REQUEST_ID_KEY = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private RequestCorrelation() {
    }

    public static void setRequestId(String requestId) {
        MDC.put(REQUEST_ID_KEY, requestId);
    }

    public static String getRequestId() {
        return MDC.get(REQUEST_ID_KEY);
    }

    public static void clear() {
        MDC.remove(REQUEST_ID_KEY);
    }
}
