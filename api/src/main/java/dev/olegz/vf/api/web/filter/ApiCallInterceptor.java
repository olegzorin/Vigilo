package dev.olegz.vf.api.web.filter;

import java.io.IOException;
import java.io.OutputStream;

import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.ApiResultCodes;
import dev.olegz.vf.common.objectmap.StringMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

// https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/HandlerInterceptor.html
class ApiCallInterceptor implements HandlerInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(ApiCallInterceptor.class);
    private static final String START_TIME_ATTR = "startTime";

    @Override
    // Interception point before the execution of a handler.
    // Called after HandlerMapping determined an appropriate handler object, but before HandlerAdapter invokes the handler.
    public boolean preHandle(
        HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull Object handler)
    {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        String httpMethod = request.getMethod();

        if ("OPTIONS".equals(httpMethod)) return true;

        if ((httpMethod == null) || !(handler instanceof HandlerMethod)) {
            // not supported API method
            String uri = request.getRequestURI();
            if (logger.isDebugEnabled()) {
                logger.debug("preHandle() not found " + httpMethod + ' ' + uri + ", " + handler);
            }

            // This interceptor is scoped to the API base path, so any unresolved handler here is a missing JSON API method.
            ActionResponse actionResponse = new ActionResponse();
            actionResponse.resultCodeAndMessage(ApiResultCodes.METHOD_NOT_FOUND, httpMethod + ' ' + uri + " API not found");
            byte[] res = StringMapper.toBytes(actionResponse);

            response.setContentType(MediaType.APPLICATION_JSON.toString());
            response.setContentLength(res.length);

            try (OutputStream outputStream = response.getOutputStream()) {
                outputStream.write(res);
                outputStream.flush();
            } catch (IOException ignore) {
            }

            return false; // stop call here
        }

        LocalRequestContext.setRequest(request);
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());

        return true;
    }

    @Override
    // Callback after completion of request processing, that is, after rendering the view.
    // Will be called on any outcome of handler execution, thus allows for proper resource cleanup.
    public void afterCompletion(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull Object handler,
        Exception ex)
    {
        // Tomcat reuses worker threads, so the request bound in preHandle() must be
        // unbound here to avoid leaking it into the next request served by this thread.
        LocalRequestContext.clear();
    }

}
