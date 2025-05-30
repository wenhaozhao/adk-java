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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LlmRegistry {

  // A thread-safe cache mapping model names to LLM instances.
  private static final Map<String, BaseLlm> instances = new ConcurrentHashMap<>();

  /** The factory interface for creating LLM instances. */
  @FunctionalInterface
  public interface LlmFactory {
    BaseLlm create(String modelName);
  }

  // API clients
  private static final Client geminiApiClient = Client.builder().build();

  // Map of model name patterns regex to factories
  private static final Map<String, LlmFactory> llmFactories = new ConcurrentHashMap<>();

  private static Client getGeminiApiClient() {
    return geminiApiClient;
  }

  static {
    registerLlm("gemini-.*", modelName -> new Gemini(modelName, getGeminiApiClient()));
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

  private LlmRegistry() {}
}
