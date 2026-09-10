package com.dedeepya.agent.engine;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.*;

public final class Jsons {
  private Jsons() {}

  public static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  static {
    MAPPER.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    MAPPER
        .getFactory()
        .setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxNestingDepth(40)
                .maxStringLength(400000)
                .maxNumberLength(30)
                .build());
  }

  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("JSON serialization failed", ex);
    }
  }

  public static <T> T read(String json, Class<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Invalid JSON contract", ex);
    }
  }

  public static JsonNode tree(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Invalid JSON contract", ex);
    }
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> transcript(String json) {
    return read(json, ArrayList.class);
  }
}
