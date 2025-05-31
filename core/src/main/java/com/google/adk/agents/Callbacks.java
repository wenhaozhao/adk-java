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

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Map;
import java.util.Optional;

public final class Callbacks {

  interface BeforeModelCallbackBase {}

  @FunctionalInterface
  public interface BeforeModelCallback extends BeforeModelCallbackBase {
    Maybe<LlmResponse> call(CallbackContext callbackContext, LlmRequest llmRequest);
  }

  // Helper interface to allow for sync beforeModelCallback. The function is wrapped into an async
  // one before being processed further.
  @FunctionalInterface
  public interface BeforeModelCallbackSync extends BeforeModelCallbackBase {
    Optional<LlmResponse> call(CallbackContext callbackContext, LlmRequest llmRequest);
  }

  interface AfterModelCallbackBase {}

  @FunctionalInterface
  public interface AfterModelCallback extends AfterModelCallbackBase {
    Maybe<LlmResponse> call(CallbackContext callbackContext, LlmResponse llmResponse);
  }

  // Helper interface to allow for sync afterModelCallback. The function is wrapped into an async
  // one before being processed further.
  @FunctionalInterface
  public interface AfterModelCallbackSync extends AfterModelCallbackBase {
    Optional<LlmResponse> call(CallbackContext callbackContext, LlmResponse llmResponse);
  }

  interface BeforeAgentCallbackBase {}

  @FunctionalInterface
  public interface BeforeAgentCallback extends BeforeAgentCallbackBase {
    Maybe<Content> call(CallbackContext callbackContext);
  }

  // Helper interface to allow for sync beforeAgentCallback. The function is wrapped into an async
  // one before being processed further.
  @FunctionalInterface
  public interface BeforeAgentCallbackSync extends BeforeAgentCallbackBase {
    Optional<Content> call(CallbackContext callbackContext);
  }

  interface AfterAgentCallbackBase {}

  @FunctionalInterface
  public interface AfterAgentCallback extends AfterAgentCallbackBase {
    Maybe<Content> call(CallbackContext callbackContext);
  }

  // Helper interface to allow for sync afterAgentCallback. The function is wrapped into an async
  // one before being processed further.
  @FunctionalInterface
  public interface AfterAgentCallbackSync extends AfterAgentCallbackBase {
    Optional<Content> call(CallbackContext callbackContext);
  }

  interface BeforeToolCallbackBase {}

  @FunctionalInterface
  public interface BeforeToolCallback extends BeforeToolCallbackBase {
    Maybe<Map<String, Object>> call(
        InvocationContext invocationContext,
        BaseTool baseTool,
        Map<String, Object> input,
        ToolContext toolContext);
  }

  // Helper interface to allow for sync beforeToolCallback. The function is wrapped into an async
  // one before being processed further.
  @FunctionalInterface
  public interface BeforeToolCallbackSync extends BeforeToolCallbackBase {
    Optional<Map<String, Object>> call(
        InvocationContext invocationContext,
        BaseTool baseTool,
        Map<String, Object> input,
        ToolContext toolContext);
  }

  interface AfterToolCallbackBase {}

  @FunctionalInterface
  public interface AfterToolCallback extends AfterToolCallbackBase {
    Maybe<Map<String, Object>> call(
        InvocationContext invocationContext,
        BaseTool baseTool,
        Map<String, Object> input,
        ToolContext toolContext,
        Object response);
  }

  // Helper interface to allow for sync afterToolCallback. The function is wrapped into an async
  // one before being processed further.
  @FunctionalInterface
  public interface AfterToolCallbackSync extends AfterToolCallbackBase {
    Optional<Map<String, Object>> call(
        InvocationContext invocationContext,
        BaseTool baseTool,
        Map<String, Object> input,
        ToolContext toolContext,
        Object response);
  }

  private Callbacks() {}
}
