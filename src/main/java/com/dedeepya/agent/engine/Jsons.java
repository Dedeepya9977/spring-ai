package com.dedeepya.agent.engine;

import java.util.*;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

public final class Jsons {
  private Jsons() {}

  public static final ObjectMapper MAPPER =
      JsonMapper.builder(
              JsonFactory.builder()
                  .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                  .streamReadConstraints(
                      StreamReadConstraints.builder()
                          .maxNestingDepth(40)
                          .maxStringLength(400000)
                          .maxNumberLength(30)
                          .build())
                  .build())
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
          .build();

  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JacksonException ex) {
      throw new IllegalStateException("JSON serialization failed", ex);
    }
  }

  public static <T> T read(String json, Class<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (JacksonException ex) {
      throw new IllegalArgumentException("Invalid JSON contract", ex);
    }
  }

  public static JsonNode tree(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (JacksonException ex) {
      throw new IllegalArgumentException("Invalid JSON contract", ex);
    }
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> transcript(String json) {
    return read(json, ArrayList.class);
  }
}
