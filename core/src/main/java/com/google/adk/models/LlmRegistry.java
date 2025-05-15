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

import com.google.genai.Client;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.google.common.base.VerifyException;

public class LlmRegistry {

  // A thread-safe cache mapping model names to LLM instances.
  private static final Map<String, BaseLlm> instances = new ConcurrentHashMap<>();

  /** The factory interface for creating LLM instances. */
  @FunctionalInterface
  public interface LlmFactory {
    BaseLlm create(String modelName);
  }

  // API clients
  private static Client geminiApiClient = Client.builder().build();
  private static Object anthropicApiClient;

  // Map of model name patterns regex to factories
  private static final Map<String, LlmFactory> llmFactories = new ConcurrentHashMap<>();

  private static Client getGeminiApiClient() {
    return geminiApiClient;
  }

  private static Object getAnthropicApiClient() {
    if (anthropicApiClient == null) {
      try {
        // com.anthropic isn't available in google3/. So loading it dynamically so build still
        // works.
        Class<?> okHttpClass = Class.forName("com.anthropic.client.okhttp.AnthropicOkHttpClient");
        Method builderMethod = okHttpClass.getMethod("builder");
        Object builder = builderMethod.invoke(null);

        Class<?> vertexBackendClass = Class.forName("com.anthropic.vertex.backends.VertexBackend");
        Method fromEnvMethod = vertexBackendClass.getMethod("fromEnv");
        Object vertexBackend = fromEnvMethod.invoke(null);

        Class<?> anthropicBackendInterface = Class.forName("com.anthropic.backends.Backend");

        Method backendMethod = builder.getClass().getMethod("backend", anthropicBackendInterface);
        builder = backendMethod.invoke(builder, vertexBackend);

        Method buildMethod = builder.getClass().getMethod("build");
        anthropicApiClient = buildMethod.invoke(builder);
      } catch (ClassNotFoundException
          | NoSuchMethodException
          | IllegalAccessException
          | InvocationTargetException e) {
        throw new VerifyException("Failed to initialize Anthropic client dynamically", e);
      }
    }
    return anthropicApiClient;
  }

  static {
    registerLlm("gemini-.*", modelName -> new Gemini(modelName, getGeminiApiClient()));
    registerLlm(
        "claude-.*",
        modelName -> {
          try {
            Class<?> claudeClass = Class.forName("com.google.adk.models.Claude");
            Class<?> anthropicClientInterface =
                Class.forName("com.anthropic.client.AnthropicClient");
            Constructor<?> claudeConstructor =
                claudeClass.getConstructor(String.class, anthropicClientInterface);
            return (BaseLlm) claudeConstructor.newInstance(modelName, getAnthropicApiClient());
          } catch (Exception e) {
            throw new VerifyException("Failed to initialize Claude LLM dynamically", e);
          }
        });
  }

  public static void registerLlm(String modelNamePattern, LlmFactory factory) {
    llmFactories.put(modelNamePattern, factory);
  }

  public static BaseLlm getLlm(String modelName) {
    return instances.computeIfAbsent(modelName, LlmRegistry::createLlm);
  }

  private static BaseLlm createLlm(String modelName) {
    for (Map.Entry<String, LlmFactory> entry : llmFactories.entrySet()) {
      if (modelName.matches(entry.getKey())) {
        return entry.getValue().create(modelName);
      }
    }
    throw new IllegalArgumentException("Unsupported model: " + modelName);
  }

  // This is for testing only.
  static void registerTestLlm(String modelNamePattern, LlmFactory factory) {
    llmFactories.put(modelNamePattern, factory);
    // Clear any cached instances that match this pattern to ensure test isolation.
    instances.keySet().removeIf(modelName -> modelName.matches(modelNamePattern));
  }
}
