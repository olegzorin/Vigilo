package dev.olegz.vf.api.web.filter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.ApiResultCodes;
import dev.olegz.vf.common.props.IntProp;
import dev.olegz.vf.common.props.PropertyStore;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
// Customizing the response after the execution of an @ResponseBody or a ResponseEntity controller method but before the body is written with an HttpMessageConverter.
// https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseBodyAdvice.html
public class ActionResponseSortAdvice implements ResponseBodyAdvice<Object> {
    private static final Logger logger = LoggerFactory.getLogger(ActionResponseSortAdvice.class);

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public @Nullable Object beforeBodyWrite(@Nullable Object body, MethodParameter returnType, MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response)
    {
        if (!(body instanceof ActionResponse actionResponse)) return body;

        HttpServletRequest servletRequest = request instanceof ServletServerHttpRequest httpRequest ? httpRequest.getServletRequest() : LocalRequestContext.getRequest();
        if (servletRequest == null) return actionResponse;

        servletRequest.setAttribute("API_RESPONSE", actionResponse);

        if (ApiResultCodes.SUCCESS == actionResponse.resultCode) {
            Map<String, String[]> parameters = servletRequest.getParameterMap();
            if (parameters != null) sortResponseBody(parameters, actionResponse);
        }

        return actionResponse;
    }

    private String getParameter(Map<String, String[]> parameters, String name) {
        String[] value = parameters.get(name);
        return (value == null) || (value.length == 0) ? null : value[0];
    }

    void sortResponseBody(Map<String, String[]> parameters, ActionResponse response) {
        String sortCollection = getParameter(parameters, SortConstants.SORT_COLLECTION);
        if (sortCollection == null) return;

        List<?> collection = null;
        try {
            Object data = getProperty(response, sortCollection);
            if (!(data instanceof List<?> list)) return;
            int size = list.size();
            response.collectionTotalSize = size;
            if (size < 2) return;
            collection = list;
        } catch (Exception e) {
            logger.warn("Exception in get collection: " + e);
        }

        String sortBy = getParameter(parameters, SortConstants.SORT_BY);
        boolean descOrder = SortConstants.SORT_ORDER_DESC.equals(getParameter(parameters, SortConstants.SORT_ORDER));
        String rowCount = getParameter(parameters, SortConstants.ROW_COUNT);
        String firstRow = getParameter(parameters, SortConstants.FIRST_ROW);

        if (logger.isDebugEnabled()) {
            logger.debug("Response: " + response.getClass().getName() + ", collection=" + sortCollection +
                ", size=" + response.collectionTotalSize +
                (response.ordered ? ", ordered" : "") +
                (sortBy != null ? ", sortBy=" + sortBy + (descOrder ? " DESC" : " ASC") : "") +
                (rowCount != null ? ", rowCount=" + rowCount + ", firstRow=" + firstRow : ""));
        }

        if ((sortBy != null) && !response.ordered) {
            sort(collection, sortBy, descOrder);
        }

        if (rowCount != null) {
            limit(collection, firstRow, rowCount);
        }
    }

    private static Field propertyField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getField(fieldName);
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
                return field;
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static Method propertyGetMethod(Class<?> clazz, String fieldName) {
        try {
            Method method;
            if (clazz.isRecord()) {
                method = clazz.getDeclaredMethod(fieldName);
            } else {
                String fieldNameBase = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                try {
                    method = clazz.getDeclaredMethod("get" + fieldNameBase);
                } catch (NoSuchMethodException e) {
                    method = clazz.getDeclaredMethod("is" + fieldNameBase);
                }
            }

            int modifiers = method.getModifiers();
            if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
                return method;
            }
        } catch (Exception ignore) {
        }

        return null;
    }

    private static Object getProperty(Object obj, String propertyName) throws Exception {
        Class<?> clazz = obj.getClass();

        Field field = propertyField(clazz, propertyName);
        if (field != null) return field.get(obj);

        Method method = propertyGetMethod(clazz, propertyName);
        return method != null ? method.invoke(obj) : null;
    }

    private void sort(List<?> list, String sortBy, boolean descOrder) {
        try {
            Comparator<Object> comparator = getComparator(list.getFirst().getClass(), sortBy, descOrder);
            if (comparator != null) list.sort(comparator);
        } catch (Exception e) {
            logger.warn("Exception in sort: " + e);
        }
    }

    private Comparator<Object> getComparator(Class<?> clazz, String sortBy, boolean descOrder) {
        // try public field first
        Field field = propertyField(clazz, sortBy);
        if (field != null) {
            Comparator<Object> pc = getComparatorByClass(field.getType());
            if (pc == null) return null;

            return descOrder ?
                (o1, o2) -> {
                    try {
                        return pc.compare(field.get(o2), field.get(o1));
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        return 0;
                    }
                } :
                (o1, o2) -> {
                    try {
                        return pc.compare(field.get(o1), field.get(o2));
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        return 0;
                    }
            };
        }

        // records and private fields with getters
        Method getMethod = propertyGetMethod(clazz, sortBy);
        if (getMethod != null) {
            Comparator<Object> pc = getComparatorByClass(getMethod.getReturnType());
            if (pc == null) return null;

            return descOrder ?
                (o1, o2) -> {
                    try {
                        return pc.compare(getMethod.invoke(o2), getMethod.invoke(o1));
                    } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                        return 0;
                    }
                } :
                (o1, o2) -> {
                    try {
                        return pc.compare(getMethod.invoke(o1), getMethod.invoke(o2));
                    } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                        return 0;
                    }
                };
        }

        return null;
    }

    private static final Comparator<Object> intComparator = Comparator.comparingInt(o -> ((Number) o).intValue());

    private static final Comparator<Object> longComparator = Comparator.comparingLong(o -> (long) o);

    private static final Comparator<Object> floatComparator = Comparator.comparingDouble(o -> (float) o);

    private static final Comparator<Object> doubleComparator = Comparator.comparingDouble(o -> (double) o);

    private static final Comparator<Object> charComparator = Comparator.comparingInt(o -> (char) o);

    private static final Comparator<Object> booleanComparator = Comparator.comparing(o -> (boolean) o);

    private static final Comparator<Object> stringComparator = (Object o1, Object o2) ->
        o1 == o2 ? 0 :
        o1 == null ? -1 :
        o2 == null ? 1 :
        ((String) o1).compareToIgnoreCase((String) o2);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final Comparator<Object> comparableComparator = (Object o1, Object o2) ->
        o1 == o2 ? 0 :
        o1 == null ? -1 :
        o2 == null ? 1 :
        ((Comparable) o1).compareTo(o2);

    private Comparator<Object> getComparatorByClass(Class<?> clazz) {
        return clazz.isPrimitive() ? (
                int.class == clazz || short.class == clazz || byte.class == clazz ? intComparator :
                long.class == clazz ? longComparator :
                double.class == clazz ? doubleComparator :
                float.class == clazz ? floatComparator :
                boolean.class == clazz ? booleanComparator :
                char.class == clazz ? charComparator :
                null
            ) :
            String.class == clazz ? stringComparator :
            Comparable.class.isAssignableFrom(clazz) ? comparableComparator :
            null;
    }

    private void limit(List<?> list, String firstRowStr, String rowCountStr) {
        int firstRow = -1; // default value to get the last rowCount elements
        int rowCount;

        if (firstRowStr != null) {
            try {
                firstRow = Integer.parseInt(firstRowStr);
            } catch (NumberFormatException e) {
                return;
            }
        }

        try {
            rowCount = Math.max(
                Integer.parseInt(rowCountStr),
                PropertyStore.getInt(IntProp.API_MIN_ROW_COUNT)
            );
        } catch (NumberFormatException e) {
            return;
        }
        if (rowCount <= 0) return;

        int size = list.size();

        if ((firstRow >= size) || (firstRow + size < 0)) {
            list.clear();
        } else {
            int to;
            if (firstRow >= 0) {
                to = firstRow + rowCount;
            } else {
                to = size + firstRow + 1;
                firstRow = to - rowCount;
            }

            if (to < size) list.subList(to, size).clear();
            if (firstRow > 0) list.subList(0, firstRow).clear();
        }
    }
}
