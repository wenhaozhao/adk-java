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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.adk.tools.BaseTool;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.LiveConnectConfig;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Represents a request to be sent to the LLM. */
@AutoValue
public abstract class LlmRequest {

  /**
   * Returns the name of the LLM model to be used. If not set, the default model of the LLM class
   * will be used.
   *
   * @return An optional string representing the model name.
   */
  public abstract Optional<String> model();

  /**
   * Returns the list of content sent to the LLM.
   *
   * @return A list of {@link Content} objects.
   */
  public abstract List<Content> contents();

  /**
   * Returns the configuration for content generation.
   *
   * @return An optional {@link GenerateContentConfig} object containing the generation settings.
   */
  public abstract Optional<GenerateContentConfig> config();

  /**
   * Returns the configuration for live connections. Populated using the RunConfig in the
   * InvocationContext.
   *
   * @return An optional {@link LiveConnectConfig} object containing the live connection settings.
   */
  public abstract LiveConnectConfig liveConnectConfig();

  /**
   * Returns a map of tools available to the LLM.
   *
   * @return A map where keys are tool names and values are {@link BaseTool} instances.
   */
  public abstract Map<String, BaseTool> tools();

  public static Builder builder() {
    return new AutoValue_LlmRequest.Builder()
        .tools(ImmutableMap.of())
        .contents(ImmutableList.of())
        .liveConnectConfig(LiveConnectConfig.builder().build());
  }

  public abstract Builder toBuilder();

  /** Builder for constructing {@link LlmRequest} instances. */
  @AutoValue.Builder
  public abstract static class Builder {
    @CanIgnoreReturnValue
    public abstract Builder model(String model);

    @CanIgnoreReturnValue
    public abstract Builder contents(List<Content> contents);

    @CanIgnoreReturnValue
    public abstract Builder config(GenerateContentConfig config);

    public abstract Optional<GenerateContentConfig> config();

    @CanIgnoreReturnValue
    public abstract Builder liveConnectConfig(LiveConnectConfig liveConnectConfig);

    abstract LiveConnectConfig liveConnectConfig();

    @CanIgnoreReturnValue
    abstract Builder tools(Map<String, BaseTool> tools);

    abstract Map<String, BaseTool> tools();

    @CanIgnoreReturnValue
    public final Builder appendInstructions(List<String> instructions) {
      if (instructions.isEmpty()) {
        return this;
      }
      GenerateContentConfig config = config().orElse(GenerateContentConfig.builder().build());
      ImmutableList.Builder<Part> parts = ImmutableList.builder();
      if (config.systemInstruction().isPresent()) {
        parts.addAll(config.systemInstruction().get().parts().orElse(ImmutableList.of()));
      }
      parts.addAll(
          instructions.stream()
              .map(instruction -> Part.builder().text(instruction).build())
              .collect(toImmutableList()));
      return config(
          config.toBuilder()
              .systemInstruction(
                  Content.builder()
                      .parts(parts.build())
                      .role(
                          config
                              .systemInstruction()
                              .map(c -> c.role().orElse("user"))
                              .orElse("user"))
                      .build())
              .build());
    }

    @CanIgnoreReturnValue
    public final Builder appendTools(List<BaseTool> tools) {
      if (tools.isEmpty()) {
        return this;
      }
      return tools(
          ImmutableMap.<String, BaseTool>builder()
              .putAll(
                  Stream.concat(tools.stream(), tools().values().stream())
                      .collect(
                          toImmutableMap(
                              BaseTool::name,
                              tool -> tool,
                              (tool1, tool2) -> {
                                throw new IllegalArgumentException(
                                    String.format("Duplicate tool name: %s", tool1.name()));
                              })))
              .buildOrThrow());
    }

    /**
     * Sets the output schema for the LLM response. If set, The output content will always be a JSON
     * string that conforms to the schema.
     */
    @CanIgnoreReturnValue
    public final Builder outputSchema(Schema schema) {
      GenerateContentConfig config = config().orElse(GenerateContentConfig.builder().build());
      return config(
          config.toBuilder().responseSchema(schema).responseMimeType("application/json").build());
    }

    public abstract LlmRequest build();
  }
}
