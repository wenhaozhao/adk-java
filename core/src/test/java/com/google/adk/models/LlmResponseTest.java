/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.adk.models;

import static com.google.common.truth.Truth.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.JsonBaseModel;
import com.google.common.collect.ImmutableList;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LlmResponseTest {

  private ObjectMapper objectMapper;

  @Before
  public void setUp() {
    objectMapper = JsonBaseModel.getMapper();
  }

  private Content createSampleContent(String text) {
    return Content.builder().parts(ImmutableList.of(Part.fromText(text))).build();
  }

  private Content createSampleFunctionCallContent(String functionName) {
    return Content.builder()
        .parts(
            ImmutableList.of(
                Part.builder()
                    .functionCall(FunctionCall.builder().name(functionName).build())
                    .build()))
        .build();
  }

  @Test
  public void testSerializationAndDeserialization_allFieldsPresent()
      throws JsonProcessingException {
    Content sampleContent = createSampleContent("Hello, world!");
    LlmResponse originalResponse =
        LlmResponse.builder()
            .content(sampleContent)
            .partial(true)
            .turnComplete(false)
            .errorCode(Optional.of("ERR_123"))
            .errorMessage(Optional.of("An error occurred."))
            .interrupted(Optional.of(true))
            .build();

    String json = originalResponse.toJson();
    assertThat(json).isNotNull();

    JsonNode jsonNode = objectMapper.readTree(json);
    assertThat(jsonNode.has("content")).isTrue();
    assertThat(jsonNode.get("content").get("parts").get(0).get("text").asText())
        .isEqualTo("Hello, world!");
    assertThat(jsonNode.get("partial").asBoolean()).isTrue();
    assertThat(jsonNode.get("turn_complete").asBoolean()).isFalse();
    assertThat(jsonNode.get("error_code").asText()).isEqualTo("ERR_123");
    assertThat(jsonNode.get("error_message").asText()).isEqualTo("An error occurred.");
    assertThat(jsonNode.get("interrupted").asBoolean()).isTrue();

    LlmResponse deserializedResponse = LlmResponse.fromJsonString(json, LlmResponse.class);

    assertThat(deserializedResponse).isEqualTo(originalResponse);
    assertThat(deserializedResponse.content()).hasValue(sampleContent);
    assertThat(deserializedResponse.partial()).hasValue(true);
    assertThat(deserializedResponse.turnComplete()).hasValue(false);
    assertThat(deserializedResponse.errorCode()).hasValue("ERR_123");
    assertThat(deserializedResponse.errorMessage()).hasValue("An error occurred.");
    assertThat(deserializedResponse.interrupted()).hasValue(true);
  }

  @Test
  public void testSerializationAndDeserialization_optionalFieldsEmpty()
      throws JsonProcessingException {
    Content sampleContent = createSampleFunctionCallContent("tool_abc");
    LlmResponse originalResponse =
        LlmResponse.builder()
            .content(sampleContent)
            .groundingMetadata(Optional.empty())
            .partial(Optional.empty())
            .turnComplete(false)
            .errorCode(Optional.empty())
            .errorMessage(Optional.empty())
            .interrupted(Optional.empty())
            .build();

    String json = originalResponse.toJson();
    assertThat(json).isNotNull();

    JsonNode jsonNode = objectMapper.readTree(json);
    assertThat(jsonNode.has("content")).isTrue();
    assertThat(jsonNode.has("grounding_metadata")).isFalse();
    assertThat(jsonNode.has("partial")).isFalse();
    assertThat(jsonNode.has("turn_complete")).isTrue();
    assertThat(jsonNode.get("turn_complete").asBoolean()).isFalse();
    assertThat(jsonNode.has("error_code")).isFalse();
    assertThat(jsonNode.has("error_message")).isFalse();
    assertThat(jsonNode.has("interrupted")).isFalse();

    LlmResponse deserializedResponse = LlmResponse.fromJsonString(json, LlmResponse.class);

    assertThat(deserializedResponse).isEqualTo(originalResponse);
    assertThat(deserializedResponse.content()).hasValue(sampleContent);
    assertThat(deserializedResponse.groundingMetadata()).isEmpty();
    assertThat(deserializedResponse.partial()).isEmpty();
    assertThat(deserializedResponse.turnComplete()).hasValue(false);
    assertThat(deserializedResponse.errorCode()).isEmpty();
    assertThat(deserializedResponse.errorMessage()).isEmpty();
    assertThat(deserializedResponse.interrupted()).isEmpty();
  }

  @Test
  public void testDeserialization_optionalFieldsNullInJson() throws JsonProcessingException {

    String jsonWithNulls =
        "{"
            + "\"content\": {\"parts\": [{\"text\": \"Test content\"}]},"
            + "\"grounding_metadata\": null,"
            + "\"partial\": null,"
            + "\"turn_complete\": true,"
            + "\"error_code\": null,"
            + "\"error_message\": null,"
            + "\"interrupted\": null"
            + "}";

    LlmResponse deserializedResponse = LlmResponse.fromJsonString(jsonWithNulls, LlmResponse.class);

    assertThat(deserializedResponse.content()).isPresent();
    assertThat(deserializedResponse.content().get().parts().get().get(0).text().get())
        .isEqualTo("Test content");
    assertThat(deserializedResponse.groundingMetadata()).isEmpty();
    assertThat(deserializedResponse.partial()).isEmpty();
    assertThat(deserializedResponse.turnComplete()).hasValue(true);
    assertThat(deserializedResponse.errorCode()).isEmpty();
    assertThat(deserializedResponse.errorMessage()).isEmpty();
    assertThat(deserializedResponse.interrupted()).isEmpty();
  }

  @Test
  public void testDeserialization_someOptionalFieldsMissingSomePresent()
      throws JsonProcessingException {
    Content sampleContent = createSampleContent("Partial data");

    LlmResponse originalResponse =
        LlmResponse.builder()
            .content(sampleContent)
            .turnComplete(true)
            .errorCode("FATAL_ERROR")
            .build();

    String json = originalResponse.toJson();
    JsonNode jsonNode = objectMapper.readTree(json);

    assertThat(jsonNode.has("content")).isTrue();
    assertThat(jsonNode.has("partial")).isFalse();
    assertThat(jsonNode.has("turn_complete")).isTrue();
    assertThat(jsonNode.get("turn_complete").asBoolean()).isTrue();
    assertThat(jsonNode.has("error_code")).isTrue();
    assertThat(jsonNode.get("error_code").asText()).isEqualTo("FATAL_ERROR");
    assertThat(jsonNode.has("error_message")).isFalse();
    assertThat(jsonNode.has("interrupted")).isFalse();

    LlmResponse deserializedResponse = LlmResponse.fromJsonString(json, LlmResponse.class);
    assertThat(deserializedResponse).isEqualTo(originalResponse);

    assertThat(deserializedResponse.content()).isPresent();
    assertThat(deserializedResponse.content().get().parts().get().get(0).text().get())
        .isEqualTo("Partial data");
    assertThat(deserializedResponse.partial()).isEmpty();
    assertThat(deserializedResponse.turnComplete()).hasValue(true);
    assertThat(deserializedResponse.errorCode()).hasValue("FATAL_ERROR");
    assertThat(deserializedResponse.errorMessage()).isEmpty();
    assertThat(deserializedResponse.interrupted()).isEmpty();
  }

}
