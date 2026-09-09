package dev.olegz.vf.registry.dao.handlers;

import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;

public class StringListTypeHandler extends JsonTypeHandler<Map<String, String>> {
    private static final JavaType JAVA_TYPE = mapper.constructType(new TypeReference<List<String>>() {});

    @Override
    protected JavaType javaType() {
        return JAVA_TYPE;
    }

    @Override
    protected String typeName() {
        return "List<String>";
    }
}
