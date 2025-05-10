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

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.GroundingMetadata;
import java.util.List;
import java.util.Optional;

/** Represents a response received from the LLM. */
@AutoValue
public abstract class LlmResponse {

  /**
   * Returns the content of the first candidate in the response, if available.
   *
   * @return An {@link Content} of the first {@link Candidate} in the {@link
   *     GenerateContentResponse} if the response contains at least one candidate., or an empty
   *     optional if no candidates are present in the response.
   */
  public abstract Optional<Content> content();

  /**
   * Returns the grounding metadata of the first candidate in the response, if available.
   *
   * @return An {@link Optional} containing {@link GroundingMetadata} or empty.
   */
  public abstract Optional<GroundingMetadata> groundingMetadata();

  /**
   * Indicates whether the text content is part of a unfinished text stream.
   *
   * <p>Only used for streaming mode and when the content is plain text.
   */
  public abstract Optional<Boolean> partial();

  /**
   * Indicates whether the response from the model is complete.
   *
   * <p>Only used for streaming mode.
   */
  public abstract Optional<Boolean> turnComplete();

  /** Error code if the response is an error. Code varies by model. */
  public abstract Optional<String> errorCode();

  /** Error message if the response is an error. */
  public abstract Optional<String> errorMessage();

  /**
   * Indicates that LLM was interrupted when generating the content. Usually it's due to user
   * interruption during a bidi streaming.
   */
  public abstract Optional<Boolean> interrupted();

  /** Builder for constructing {@link LlmResponse} instances. */
  @AutoValue.Builder
  public abstract static class Builder {
    @CanIgnoreReturnValue
    public final Builder response(GenerateContentResponse response) {
      Optional<List<Candidate>> candidatesOpt = response.candidates();
      if (candidatesOpt.isPresent() && !candidatesOpt.get().isEmpty()) {
        Candidate candidate = candidatesOpt.get().get(0);
        if (candidate.content().isPresent()) {
          this.content(candidate.content().get());
          this.groundingMetadata(candidate.groundingMetadata());
        } else {
          candidate.finishReason().ifPresent(this::errorCode);
          candidate.finishMessage().ifPresent(this::errorMessage);
        }
      } else {
        Optional<GenerateContentResponsePromptFeedback> promptFeedbackOpt =
            response.promptFeedback();
        if (promptFeedbackOpt.isPresent()) {
          GenerateContentResponsePromptFeedback promptFeedback = promptFeedbackOpt.get();
          promptFeedback.blockReason().ifPresent(this::errorCode);
          promptFeedback.blockReasonMessage().ifPresent(this::errorMessage);
        } else {
          this.errorCode("UNKNOWN_ERROR");
          this.errorMessage("Unknown error.");
        }
      }
      return this;
    }

    public abstract Builder content(Content content);

    public abstract Builder interrupted(boolean interrupted);

    public abstract Builder groundingMetadata(Optional<GroundingMetadata> groundingMetadata);

    public abstract Builder partial(Boolean partial);

    public abstract Builder turnComplete(boolean turnComplete);

    public abstract Builder errorCode(String errorCode);

    public abstract Builder errorCode(Optional<String> errorCode);

    public abstract Builder errorMessage(String errorMessage);

    public abstract Builder errorMessage(Optional<String> errorMessage);

    public abstract LlmResponse build();
  }

  public static Builder builder() {
    return new AutoValue_LlmResponse.Builder();
  }

  public static LlmResponse create(List<Candidate> candidates) {
    GenerateContentResponse response =
        GenerateContentResponse.builder().candidates(candidates).build();
    return builder().response(response).build();
  }

  public static LlmResponse create(GenerateContentResponse response) {
    return builder().response(response).build();
  }
}
