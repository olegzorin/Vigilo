package dev.olegz.vf.registry.dao.handlers;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;

public class JsonNodeTypeHandler extends JsonTypeHandler<JsonNode> {
    @Override
    protected JavaType javaType() {
        return null;
    }

    @Override
    protected String typeName() {
        return "JsonNode";
    }

    @Override
    protected JsonNode readStringValue(String value) {
        return mapper.readTree(value);
    }
}
