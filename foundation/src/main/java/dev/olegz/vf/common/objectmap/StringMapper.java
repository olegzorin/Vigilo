package dev.olegz.vf.common.objectmap;

import java.util.Iterator;
import java.util.Map;

import dev.olegz.vf.common.ApplicationFailureException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Object to String and String to object mapping wrapper class
 * This implementation utilizes Jackson JsonMapper.
 */
public class StringMapper {
    private static final JsonMapper mapper = BytesMapper.buildDefaultMapper();

    public static JsonNode valueToTree(Object object) {
        return mapper.valueToTree(object);
    }

    public static String toString(Object value) {
        return mapper.writeValueAsString(value);
    }

    public static <T> T readValue(String value, Class<T> valueType) {
        try {
            return mapper.readValue(value, valueType);
        } catch (Exception e) {
            throw new ApplicationFailureException("Cannot read " + valueType + '\n' + value, e);
        }
    }

    public static <T> T readValue(String value, TypeReference<T> valueType) {
        try {
            return mapper.readValue(value, valueType);
        } catch (Exception e) {
            throw new ApplicationFailureException("Cannot read " + valueType + '\n' + value, e);
        }
    }

    public static byte[] toBytes(Object value) {
        return mapper.writeValueAsBytes(value);
    }

    public static <T> T readValue(byte[] value, Class<T> valueType) {
        try {
            return mapper.readValue(value, valueType);
        } catch (Exception e) {
            throw new ApplicationFailureException("Cannot read " + valueType, e);
        }
    }

    public static <T> T readValue(byte[] value, TypeReference<T> valueType) {
        try {
            return mapper.readValue(value, valueType);
        } catch (Exception e) {
            throw new ApplicationFailureException("Cannot read " + valueType, e);
        }
    }

    public static String toString(JsonNode node) {
        removeNulls(node);
        return mapper.writeValueAsString(node);
    }

    // Jackson mapper does not remove null fields from JsonNode in writing
    private static boolean removeNulls(JsonNode node) {
        boolean res = false;
        for (Iterator<JsonNode> it = node.iterator(); it.hasNext(); ) {
            JsonNode child = it.next();
            if (child.isNull()) {
                it.remove();
                res = true;
            } else {
                res = removeNulls(child) || res;
            }
        }
        return res;
    }

    private static final TypeReference<Map<String, String>> STRING_MAP_REF = new TypeReference<>() {};

    public static Map<String, String> readStringMap(String mapStr) {
        return mapper.readValue(mapStr, STRING_MAP_REF);
    }

}
