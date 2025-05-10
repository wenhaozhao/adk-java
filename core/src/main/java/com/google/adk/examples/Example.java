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

package com.google.adk.examples;

import com.google.auto.value.AutoValue;
import com.google.genai.types.Content;
import java.util.List;

/** Represents an few-shot example. */
@AutoValue
public abstract class Example {
  public abstract Content input();

  public abstract List<Content> output();

  public static Builder builder() {
    return new AutoValue_Example.Builder();
  }

  public abstract Builder toBuilder();

  /** Builder for constructing {@link Example} instances. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder input(Content input);

    public abstract Builder output(List<Content> output);

    public abstract Example build();
  }
}
