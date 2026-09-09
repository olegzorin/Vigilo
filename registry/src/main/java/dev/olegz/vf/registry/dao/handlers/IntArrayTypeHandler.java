package dev.olegz.vf.registry.dao.handlers;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;

public class IntArrayTypeHandler extends JsonTypeHandler<int[]> {
    private static final JavaType JAVA_TYPE = mapper.constructType(new TypeReference<int[]>() {});

    @Override
    protected JavaType javaType() {
        return JAVA_TYPE;
    }

    @Override
    protected String typeName() {
        return "IntArray";
    }
}
