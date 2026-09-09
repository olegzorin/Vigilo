package dev.olegz.vf.registry.dao.handlers;

import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;

public class ObjectMapTypeHandler extends JsonTypeHandler<Map<String, Object>> {
    private static final JavaType JAVA_TYPE = mapper.constructType(new TypeReference<Map<String, Object>>() {});

    @Override
    protected JavaType javaType() {
        return JAVA_TYPE;
    }

    @Override
    protected String typeName() {
        return "Map<String,Object>";
    }
}
