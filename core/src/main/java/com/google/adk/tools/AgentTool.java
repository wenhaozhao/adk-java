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

package com.google.adk.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.adk.JsonBaseModel;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.runner.Runner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import io.reactivex.rxjava3.core.Single;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/** AgentTool implements a tool that allows an agent to call another agent. */
public class AgentTool extends BaseTool {

  private final BaseAgent agent;
  private final boolean skipSummarization;

  public static AgentTool create(BaseAgent agent, boolean skipSummarization) {
    return new AgentTool(agent, skipSummarization);
  }

  public static AgentTool create(BaseAgent agent) {
    return new AgentTool(agent, false);
  }

  protected AgentTool(BaseAgent agent, boolean skipSummarization) {
    super(agent.name(), agent.description());
    this.agent = agent;
    this.skipSummarization = skipSummarization;
  }

  @SuppressWarnings("unchecked") // For tool parameter type casting.
  private static Boolean matchType(Object value, Schema schema, Boolean isInput)
      throws IllegalArgumentException {
    // Based on types from https://cloud.google.com/vertex-ai/docs/reference/rest/v1/Schema
    Type.Known type = schema.type().get().knownEnum();
    switch (type) {
      case STRING:
        return value instanceof String;
      case INTEGER:
        return value instanceof Integer;
      case BOOLEAN:
        return value instanceof Boolean;
      case NUMBER:
        return value instanceof Number;
      case ARRAY:
        if (value instanceof List) {
          for (Object element : (List<?>) value) {
            if (!matchType(element, schema.items().get(), isInput)) {
              return false;
            }
          }
          return true;
        }
        return false;
      case OBJECT:
        if (value instanceof Map) {
          validateMapOnSchema((Map<String, Object>) value, schema, isInput);
          return true;
        } else {
          return false;
        }
      case TYPE_UNSPECIFIED:
        throw new IllegalArgumentException(
            "Unsupported type: " + type + " is not a Open API data type.");
    }
    return false;
  }

  protected static void validateMapOnSchema(
      Map<String, Object> args, Schema schema, Boolean isInput) throws IllegalArgumentException {
    Map<String, Schema> properties = schema.properties().get();
    for (Entry<String, Object> arg : args.entrySet()) {
      // Check if the argument is in the schema.
      if (!properties.containsKey(arg.getKey())) {
        if (isInput) {
          throw new IllegalArgumentException(
              "Input arg: " + arg.getKey() + " does not match agent input schema: " + schema);
        } else {
          throw new IllegalArgumentException(
              "Output arg: " + arg.getKey() + " does not match agent output schema: " + schema);
        }
      }
      // Check if the argument type matches the schema type.
      if (!matchType(arg.getValue(), properties.get(arg.getKey()), isInput)) {
        if (isInput) {
          throw new IllegalArgumentException(
              "Input arg: " + arg.getKey() + " does not match agent input schema: " + schema);
        } else {
          throw new IllegalArgumentException(
              "Output arg: " + arg.getKey() + " does not match agent output schema: " + schema);
        }
      }
    }
    // Check if all required arguments are present.
    if (schema.required().isPresent()) {
      for (String required : schema.required().get()) {
        if (!args.containsKey(required)) {
          if (isInput) {
            throw new IllegalArgumentException("Input args does not contain required " + required);
          } else {
            throw new IllegalArgumentException("Output args does not contain required " + required);
          }
        }
      }
    }
  }

  @SuppressWarnings("unchecked") // For tool parameter type casting.
  protected static Map<String, Object> validateOutputSchema(String output, Schema schema)
      throws IllegalArgumentException, JsonProcessingException {
    Map<String, Object> outputMap = JsonBaseModel.getMapper().readValue(output, HashMap.class);
    validateMapOnSchema(outputMap, schema, false);
    return outputMap;
  }

  @Override
  public Optional<FunctionDeclaration> declaration() {
    FunctionDeclaration.Builder builder =
        FunctionDeclaration.builder().description(this.description()).name(this.name());

    Optional<Schema> agentInputSchema = Optional.empty();
    if (agent instanceof LlmAgent llmAgent) {
      agentInputSchema = llmAgent.inputSchema();
    }

    if (agentInputSchema.isPresent()) {
      builder.parameters(agentInputSchema.get());
    } else {
      builder.parameters(
          Schema.builder()
              .type("OBJECT")
              .properties(ImmutableMap.of("request", Schema.builder().type("STRING").build()))
              .required(ImmutableList.of("request"))
              .build());
    }
    return Optional.of(builder.build());
  }

  @Override
  public Single<Map<String, Object>> runAsync(
      Map<String, Object> args, ToolContext toolContext) {

    if (this.skipSummarization) {
      toolContext.actions().setSkipSummarization(true);
    }

    Optional<Schema> agentInputSchema = Optional.empty();
    if (agent instanceof LlmAgent llmAgent) {
      agentInputSchema = llmAgent.inputSchema();
    }

    final Content content;
    if (agentInputSchema.isPresent()) {
      validateMapOnSchema(args, agentInputSchema.get(), true);
      try {
        content =
            Content.fromParts(Part.fromText(JsonBaseModel.getMapper().writeValueAsString(args)));
      } catch (JsonProcessingException e) {
        return Single.error(
            new RuntimeException("Error serializing tool arguments to JSON: " + args, e));
      }
    } else {
      Object input = args.get("request");
      content =
          Content.builder()
              .role("user")
              .parts(ImmutableList.of(Part.builder().text(input.toString()).build()))
              .build();
    }

    Runner runner = new InMemoryRunner(this.agent, toolContext.agentName());
    // Session state is final, can't update to toolContext state
    // session.toBuilder().setState(toolContext.getState());
    return runner
        .sessionService()
        .createSession(toolContext.agentName(), "tmp-user", toolContext.state(), null)
        .flatMapPublisher(session -> runner.runAsync(session.userId(), session.id(), content))
        .lastElement()
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .map(
            optionalLastEvent -> {
              if (optionalLastEvent.isEmpty()) {
                return ImmutableMap.of();
              }
              Event lastEvent = optionalLastEvent.get();
              Optional<String> outputText =
                  lastEvent
                      .content()
                      .flatMap(Content::parts)
                      .filter(parts -> !parts.isEmpty())
                      .flatMap(parts -> parts.get(0).text());

              if (outputText.isEmpty()) {
                return ImmutableMap.of();
              }
              String output = outputText.get();

              Optional<Schema> agentOutputSchema = Optional.empty();
              if (agent instanceof LlmAgent llmAgent) {
                agentOutputSchema = llmAgent.outputSchema();
              }

              if (agentOutputSchema.isPresent()) {
                return validateOutputSchema(output, agentOutputSchema.get());
              } else {
                return ImmutableMap.of("result", output);
              }
            });
  }
}
