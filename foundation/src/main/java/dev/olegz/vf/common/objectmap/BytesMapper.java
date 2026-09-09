package dev.olegz.vf.common.objectmap;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.olegz.vf.common.ApplicationFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.smile.SmileMapper;

/**
 * Object to byte[] and byte[] to object mapping wrapper class.
 * This utility is used in sending and receiving data objects to and from message queue.
 */
public class BytesMapper {
    private static final Logger logger = LoggerFactory.getLogger(BytesMapper.class);

    private static final SmileMapper mapper = SmileMapper.builder()
        .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL).withContentInclusion(JsonInclude.Include.NON_NULL))
        .changeDefaultVisibility(vc -> vc.withCreatorVisibility(JsonAutoDetect.Visibility.NONE))
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();
    private static final JsonMapper jsonMapper = buildDefaultMapper();
    private static final byte smile0 = (byte)':';
    private static final byte smile1 = (byte)')';

    public static byte[] writeValue(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new ApplicationFailureException(e);
        }
    }

    public static <T> T readValue(byte[] bytes, Class<T> clazz) {
        try {
            return (bytes.length >= 2) && (bytes[0] == smile0) && (bytes[1] == smile1) ? mapper.readValue(bytes, clazz) : jsonMapper.readValue(bytes, clazz);
        } catch (Exception e) {
            logger.error("Exception in reading " + clazz + '\n' + e);
            throw new ApplicationFailureException("Cannot read " + clazz, e);
        }
    }

    public static <T> T readValue(byte[] bytes, int len, Class<T> clazz) {
        try {
            return (bytes.length >= 2) && (bytes[0] == smile0) && (bytes[1] == smile1) ? mapper.readValue(bytes, 0, len, clazz) : jsonMapper.readValue(bytes, 0, len, clazz);
        } catch (Exception e) {
            logger.error("Exception in reading " + clazz + '\n' + e);
            throw new ApplicationFailureException("Cannot read " + clazz, e);
        }
    }

    public static JsonMapper buildDefaultMapper() {
        return JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL).withContentInclusion(JsonInclude.Include.NON_NULL))
            .changeDefaultVisibility(vc -> vc.withCreatorVisibility(JsonAutoDetect.Visibility.NONE))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    }
}
