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

import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.LiveConnectConfig;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the Gemini Generative AI model.
 *
 * <p>This class provides methods for interacting with the Gemini model, including standard
 * request-response generation and establishing persistent bidirectional connections.
 */
public class Gemini extends BaseLlm {

  private static final Logger logger = Logger.getLogger(Gemini.class.getName());

  private final Client apiClient;

  private static final String CONTINUE_OUTPUT_MESSAGE =
      "Continue output. DO NOT look at this line. ONLY look at the content before this line and"
          + " system instruction.";

  /**
   * Constructs a new Gemini instance.
   *
   * @param modelName The name of the Gemini model to use (e.g., "gemini-2.0-flash").
   * @param apiClient The genai {@link com.google.genai.Client} instance for making API calls.
   */
  public Gemini(String modelName, Client apiClient) {
    super(modelName);
    this.apiClient = Objects.requireNonNull(apiClient, "apiClient cannot be null");
  }

  @Override
  public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {

    List<Content> contents = llmRequest.contents();
    // Last content must be from the user, otherwise the model won't respond.
    if (contents.isEmpty() || !contents.get(contents.size() - 1).role().orElse("").equals("user")) {
      Content userContent =
          Content.builder()
              .role("user")
              .parts(ImmutableList.of(Part.builder().text(CONTINUE_OUTPUT_MESSAGE).build()))
              .build();
      contents =
          Stream.concat(contents.stream(), Stream.of(userContent)).collect(Collectors.toList());
    }

    List<Content> finalContents = stripThoughts(contents);
    GenerateContentConfig config = llmRequest.config().orElse(null);
    String effectiveModelName = llmRequest.model().orElse(model());

    logger.finer(() -> String.format("Request Contents: %s", finalContents));
    logger.finer(() -> String.format("Request Config: %s", config));

    if (stream) {
      logger.fine(
          () ->
              String.format(
                  "Sending streaming generateContent request to model %s", effectiveModelName));
      CompletableFuture<ResponseStream<GenerateContentResponse>> streamFuture =
          apiClient.async.models.generateContentStream(effectiveModelName, finalContents, config);
      return Flowable.fromFuture(streamFuture)
          .flatMapIterable(iterable -> iterable)
          .map(LlmResponse::create);
    } else {
      logger.fine(
          () -> String.format("Sending generateContent request to model %s", effectiveModelName));
      return Flowable.fromFuture(
          apiClient
              .async
              .models
              .generateContent(effectiveModelName, finalContents, config)
              .thenApplyAsync(LlmResponse::create));
    }
  }

  @Override
  public BaseLlmConnection connect(LlmRequest llmRequest) {
    logger.info("Establishing Gemini connection...");
    LiveConnectConfig liveConnectConfig = llmRequest.liveConnectConfig();
    String effectiveModelName = llmRequest.model().orElse(model());

    logger.fine(() -> String.format("Connecting to model %s", effectiveModelName));
    logger.finer(() -> String.format("Connection Config: %s", liveConnectConfig));

    return new GeminiLlmConnection(apiClient, effectiveModelName, liveConnectConfig);
  }

  /** Removes any `Part` that contains only a `thought` from the content list. */
  private List<Content> stripThoughts(List<Content> originalContents) {
    List<Content> updatedContents = new ArrayList<>();
    for (Content content : originalContents) {
      ImmutableList<Part> nonThoughtParts =
          content.parts().get().stream()
              // Keep if thought is not present OR if thought is present but false
              .filter(part -> part.thought().map(isThought -> !isThought).orElse(true))
              .collect(toImmutableList());
      updatedContents.add(content.toBuilder().parts(nonThoughtParts).build());
    }
    return updatedContents;
  }
}
