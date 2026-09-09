package dev.olegz.vf.api.web.exception;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.DataTruncation;
import java.time.format.DateTimeParseException;
import java.util.Enumeration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.olegz.vf.api.Application;
import dev.olegz.vf.api.web.filter.LocalRequestContext;
import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.ApiResultCodes;
import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.exception.ApiResultException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.text.CaseUtils;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final ResponseEntity<?> INTERNAL_ERROR_RESPONSE = ResponseEntity.internalServerError().build();

    private static final String[] HEADER_NAMES = {HttpHeaders.CONTENT_TYPE, HttpHeaders.CONTENT_LENGTH, HttpHeaders.USER_AGENT};

    private String requestInfo(WebRequest webRequest) {
        HttpServletRequest request = httpServletRequest(webRequest);
        String value;
        StringBuilder buf = new StringBuilder(250);
        Map<String, String[]> parameters;

        if (request != null) {
            buf.append('\n').append(request.getMethod()).append(' ').append(request.getRequestURI());
            for (String header : HEADER_NAMES) {
                value = request.getHeader(header);
                if (value != null) buf.append('\n').append(header).append('=').append(value);
            }
            if ((value = getClientIpAddress(request)) != null) buf.append("\nIP address=").append(value);

            parameters = request.getParameterMap();
        } else {
            buf.append('\n').append(webRequest.getDescription(false));
            for (String header : HEADER_NAMES) {
                value = webRequest.getHeader(header);
                if (value != null) buf.append('\n').append(header).append('=').append(value);
            }

            parameters = webRequest.getParameterMap();
        }

        if ((parameters != null) && !parameters.isEmpty()) {
            buf.append("\nParameters:");
            parameters.forEach((name, values) -> {
                if ((values != null) && (values.length > 0)) {
                    buf.append('\n').append(name).append('=');
                    boolean comma = false;
                    for (int i = 0; (i < values.length) && (i < 10); i++) {
                        if (values[i] != null) {
                            if (comma) {
                                buf.append(',');
                            } else {
                                comma = true;
                            }

                            if (values[i].length() > 30) {
                                buf.append(values[i], 0, 30).append("...");
                            } else {
                                buf.append(values[i]);
                            }
                        }
                    }
                }
            });
        }

        return buf.toString();
    }


    @ExceptionHandler({
        ServletRequestBindingException.class, ServletException.class, BindException.class,
        TypeMismatchException.class, HttpMessageConversionException.class, IllegalArgumentException.class, DateTimeParseException.class,
        IllegalStateException.class, BeanCreationNotAllowedException.class, UncategorizedDataAccessException.class
    })
    public ResponseEntity<?> handleSpringException(Exception ex, WebRequest request) {
        if (logger.isInfoEnabled()) {
            logger.info("SpringException " + ex + requestInfo(request));
        }

        if (checkCause(ex, IOException.class) != null) return null;

        byte resultCode;
        String message = ex.getMessage();
        ApiResultException resultException;
        ApplicationFailureException applicationFailure;

        if (ex instanceof MissingRequestValueException) {
            // Cover all missing required parameter exceptions
            resultCode = ApiResultCodes.MISSED_PARAMETER;
        } else if ((ex instanceof ServletRequestBindingException) ||
            (ex instanceof MissingServletRequestPartException) ||
            (ex instanceof IllegalArgumentException) ||
            (ex instanceof DateTimeParseException) ||
            (ex instanceof TypeMismatchException) ||
            (ex instanceof InvalidParameterException))
        {
            resultCode = ApiResultCodes.WRONG_PARAM_VALUE;
        } else if (ex instanceof HttpMediaTypeException) {
            if (request.getHeader(HttpHeaders.CONTENT_TYPE) == null) {
                resultCode = ApiResultCodes.MISSED_PARAMETER;
                message = "Missing Content-Type";
            } else {
                resultCode = ApiResultCodes.WRONG_PARAM_VALUE;
            }
        } else if (ex instanceof HttpMessageNotReadableException) {
            resultCode = (message != null) && message.startsWith("Required request body is missing") ? ApiResultCodes.MISSED_PARAMETER : ApiResultCodes.PARSING_ERROR;
        } else if (ex instanceof HttpMessageNotWritableException) {
            logger.error("HttpMessageNotWritableException" + requestInfo(request), ex);
            resultCode = ApiResultCodes.FAILURE;
        } else if ((ex instanceof HttpMessageConversionException) && (ex.getCause() instanceof JacksonException)) {
            resultCode = ApiResultCodes.PARSING_ERROR;
        } else if ((ex instanceof HttpRequestMethodNotSupportedException) ||
            (ex instanceof BindException) ||
            (ex instanceof NoHandlerFoundException))
        {
            resultCode = ApiResultCodes.METHOD_NOT_FOUND;
        } else if (ex instanceof NoResourceFoundException nfe) {
            if (HttpMethod.GET.equals(nfe.getHttpMethod())) {
                String path = nfe.getResourcePath();
                if (!path.endsWith(".js") && !path.endsWith(".css") && !path.endsWith(".map")) {
                    try {
                        return redirect("/cloud/error.html");
                    } catch (Exception e) {
                        logger.error("Exception in redirecting to error page\n" + e);
                    }
                }
                resultCode = ApiResultCodes.OBJECT_NOT_FOUND;
            } else {
                resultCode = ApiResultCodes.METHOD_NOT_FOUND;
            }
        } else if (ex instanceof BeanCreationNotAllowedException) {
            logger.warn("BeanCreationNotAllowedException\n" + ex + requestInfo(request));
            resultCode = ApiResultCodes.FAILURE;
            message = "Stopping server application";
        } else if ((resultException = checkCause(ex, ApiResultException.class)) != null) {
            return handleApiResultException(resultException, request);
        } else if ((applicationFailure = checkCause(ex, ApplicationFailureException.class)) != null) {
            return handleApplicationFailureException(applicationFailure, request);
        } else if (Application.isStopped()) {
            logger.debug("Application is stopping");
            resultCode = ApiResultCodes.FAILURE;
            message = "The server is stopping";
        } else {
            logger.error("Spring exception" + requestInfo(request), ex);
            resultCode = ApiResultCodes.FAILURE;
        }

        return makeResponse(resultCode, message, request, null);
    }

    private <E> E checkCause(Throwable throwable, Class<E> checkFor) {
        Throwable cause = throwable;
        Throwable c;
        while (((c = cause.getCause()) != null) && (c != cause)) {
            if (checkFor.isInstance(c)) return checkFor.cast(c);
            cause = c;
        }
        return null;
    }

    private static final Pattern DATA_TOO_LONG = Pattern.compile("Data too long for column '([A-z0-9_]+)'");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        ObjectError error = ex.getBindingResult().getFieldError();
        if (error == null) {
            error = ex.getBindingResult().getGlobalError();
        }

        byte resultCode = isMissingValue(error) ? ApiResultCodes.MISSED_PARAMETER : ApiResultCodes.WRONG_PARAM_VALUE;
        String message = (error != null) ? error.getDefaultMessage() : ex.getMessage();

        if (logger.isInfoEnabled()) {
            logger.info("MethodArgumentNotValidException " + message + requestInfo(request));
        }

        return makeResponse(resultCode, message, request, null);
    }

    private static boolean isMissingValue(ObjectError error) {
        if (error != null) {
            String[] codes = error.getCodes();
            if (codes != null) {
                for (String code : codes) {
                    if ("NotNull".equals(code) || "NotBlank".equals(code) || "NotEmpty".equals(code)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityViolationException(DataIntegrityViolationException ex, WebRequest request) {
        if (ex.getCause() instanceof DataTruncation tx) {
            Matcher m = DATA_TOO_LONG.matcher(tx.getMessage());
            if (m.find()) {
                logger.warn("Data too long: " + ex + requestInfo(request));
                String message = "Too long value for " + CaseUtils.toCamelCase(m.group(1), false, '_');
                return makeResponse(ApiResultCodes.WRONG_PARAM_VALUE, message, request, null);
            }
        }

        // handle general exception
        logger.error("DataIntegrityViolationException" + requestInfo(request), ex);
        return makeResponse(ApiResultCodes.FAILURE, null, request, null);
    }

    @ExceptionHandler(ApiResultException.class)
    public ResponseEntity<?> handleApiResultException(ApiResultException ex, WebRequest request) {
        byte errorCode = ex.getApiErrorCode();
        if (ApiResultCodes.noWarn(errorCode)) {
            if (logger.isInfoEnabled()) {
                logger.info("ApiResultException " + ex + requestInfo(request));
            }
        } else {
            logger.warn("ApiResultException " + ex + requestInfo(request));
        }

        return makeResponse(errorCode, ex.getApiErrorMessage(), request, null);
    }

    @ExceptionHandler(ApplicationFailureException.class)
    public ResponseEntity<?> handleApplicationFailureException(ApplicationFailureException ex, WebRequest request) {
        if (ex.logStackTrace) {
            logger.error("ApplicationFailureException" + requestInfo(request), ex);
        } else {
            logger.error("ApplicationFailureException\n" + ex + requestInfo(request));
        }

        return makeResponse(ApiResultCodes.FAILURE, null, request, null);
    }

    @ExceptionHandler(ApiResponseStatusException.class)
    public ResponseEntity<?> handleApiResponseStatusException(ApiResponseStatusException ex, WebRequest request) {
        if (logger.isInfoEnabled()) {
            logger.info("ApiResponseStatusException " + requestInfo(request));
        }

        return INTERNAL_ERROR_RESPONSE;
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<?> handleIOException(IOException ex, WebRequest request) {
        if (logger.isInfoEnabled()) {
            logger.info("IOException " + ex + requestInfo(request));
        }
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneralException(Exception ex, WebRequest request) {
        logger.error("Exception" + requestInfo(request), ex);
        return makeResponse(ApiResultCodes.FAILURE, null, request, null);
    }

    private ResponseEntity<?> makeResponse(byte resultCode, String message, WebRequest webRequest, Consumer<ActionResponse> setResponse) {
        String uri = null;
        HttpServletRequest request = httpServletRequest(webRequest);

        if (request != null) {
            uri = request.getRequestURI();

            if (uri.startsWith("/vf/")) {
                if (logger.isDebugEnabled()) {
                    logger.debug("| JSON error response for " + request.getMethod() + ' ' + uri + ", resultCode=" + resultCode + (message != null ? "\n" + message : ""));
                }
                ActionResponse response = new ActionResponse();
                response.resultCodeAndMessage(resultCode, message);
                if (setResponse != null) setResponse.accept(response);
                return ResponseEntity.status(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response); // return as an object to be processed by the API call logger
            }
        }

        HttpStatus status = statusFromCode(resultCode);

        if (logger.isDebugEnabled()) {
            logger.debug("| Text error response for " + (uri != null ? "uri=" + uri : webRequest.getDescription(false)) +
                ", resultCode=" + resultCode + ", status=" + status + (message != null ? "\n" + message : ""));
        }

        ResponseEntity.BodyBuilder bodyBuilder = ResponseEntity.status(status);
        return message == null ? bodyBuilder.build() :
            bodyBuilder.contentLength(message.length()).contentType(MediaType.TEXT_PLAIN).body(message);
    }

    private HttpServletRequest httpServletRequest(WebRequest webRequest) {
        return (webRequest instanceof ServletWebRequest servletWebRequest) ? servletWebRequest.getRequest() : LocalRequestContext.getRequest();
    }

    private static ResponseEntity<?> redirect(String uriStr) throws URISyntaxException {
        URI uri = new URI(uriStr);
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(uri).build();
    }

    private static HttpStatus statusFromCode(byte resultCode) {
        return switch (resultCode) {
            case ApiResultCodes.SUCCESS
                -> HttpStatus.OK;
            case ApiResultCodes.WRONG_API_KEY,
                ApiResultCodes.WRONG_USERNAME_PASSWORD,
                ApiResultCodes.NOT_AUTHENTICATED
                -> HttpStatus.UNAUTHORIZED;
            case ApiResultCodes.WRONG_PARAM_VALUE,
                ApiResultCodes.PARSING_ERROR,
                ApiResultCodes.MISSED_PARAMETER,
                ApiResultCodes.DUPLICATE_USERNAME,
                ApiResultCodes.DUPLICATE_ENTITY,
                ApiResultCodes.WEAK_PASSWORD
                -> HttpStatus.BAD_REQUEST;
            case ApiResultCodes.OBJECT_NOT_FOUND
                -> HttpStatus.NOT_FOUND;
            case ApiResultCodes.METHOD_NOT_FOUND
                -> HttpStatus.METHOD_NOT_ALLOWED;
            case ApiResultCodes.ACCESS_DENIED,
                ApiResultCodes.RESOURCE_NOT_AVAILABLE,
                ApiResultCodes.OPERATION_NOT_ALLOWED
                -> HttpStatus.FORBIDDEN;
            default
                -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static final int MAX_IP_ADDRESS_LEN = 45;

    private static String getClientIpAddress(HttpServletRequest request) {
        String userIpAddr = null;
        // get the client's IP address from proxy headers
        Enumeration<String> clientIpHeaders = request.getHeaders("X-Forwarded-For");
        if (clientIpHeaders != null) {
            while (clientIpHeaders.hasMoreElements()) {
                String h = clientIpHeaders.nextElement();
                if (h != null) userIpAddr = h;
            }
        }

        if (userIpAddr == null) {
            userIpAddr = request.getRemoteAddr();
            if (userIpAddr == null) return null;
        }

        int comma = userIpAddr.indexOf(',');
        if (comma > 0) userIpAddr = userIpAddr.substring(0, comma);

        return userIpAddr.length() > MAX_IP_ADDRESS_LEN ? userIpAddr.substring(0, MAX_IP_ADDRESS_LEN) : userIpAddr;
    }

}
