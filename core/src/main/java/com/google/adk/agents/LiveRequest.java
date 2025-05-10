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

package com.google.adk.agents;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import java.util.Optional;

/** Represents a request to be sent to a live connection to the LLM model. */
@AutoValue
public abstract class LiveRequest {

  /**
   * Returns the content of the request.
   *
   * <p>If set, send the content to the model in turn-by-turn mode.
   *
   * @return An optional {@link Content} object containing the content of the request.
   */
  public abstract Optional<Content> content();

  /**
   * Returns the blob of the request.
   *
   * <p>If set, send the blob to the model in realtime mode.
   *
   * @return An optional {@link Blob} object containing the blob of the request.
   */
  public abstract Optional<Blob> blob();

  /**
   * Returns whether the connection should be closed.
   *
   * <p>If set to true, the connection will be closed after the request is sent.
   *
   * @return A boolean indicating whether the connection should be closed.
   */
  public abstract boolean close();

  /** Builder for constructing {@link LiveRequest} instances. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder content(Content content);

    public abstract Builder content(Optional<Content> content);

    public abstract Builder blob(Blob blob);

    public abstract Builder blob(Optional<Blob> blob);

    public abstract Builder close(boolean close);

    abstract LiveRequest autoBuild();

    public final LiveRequest build() {
      LiveRequest request = autoBuild();
      Preconditions.checkState(
          request.content().isPresent() || request.blob().isPresent() || request.close(),
          "One of content, blob, or close must be set");
      return request;
    }
  }

  public static Builder builder() {
    return new AutoValue_LiveRequest.Builder().close(false);
  }

  public abstract Builder toBuilder();
}
