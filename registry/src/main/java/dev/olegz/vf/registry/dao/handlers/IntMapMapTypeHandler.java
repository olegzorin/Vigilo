package dev.olegz.vf.registry.dao.handlers;

import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;

public class IntMapMapTypeHandler extends JsonTypeHandler<Map<String, Map<String, String>>> {
    private static final JavaType JAVA_TYPE = mapper.constructType(new TypeReference<Map<Integer, Map<String, String>>>() {});

    @Override
    protected JavaType javaType() {
        return JAVA_TYPE;
    }

    @Override
    protected String typeName() {
        return "Map<Integer,Map<String, String>>";
    }
}
