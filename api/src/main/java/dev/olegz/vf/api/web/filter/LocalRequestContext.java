package dev.olegz.vf.api.web.filter;

import jakarta.servlet.http.HttpServletRequest;

public class LocalRequestContext {
    private static final ThreadLocal<HttpServletRequest> localContext = new ThreadLocal<>();

    public static void setRequest(HttpServletRequest request) {
        localContext.set(request);
    }

    public static HttpServletRequest getRequest() {
        return localContext.get();
    }

    public static void clear() {
        localContext.remove();
    }
}
