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

package com.google.adk.flows.llmflows;

import static com.google.adk.testing.TestUtils.createEvent;
import static com.google.adk.testing.TestUtils.createInvocationContext;
import static com.google.adk.testing.TestUtils.createRootAgent;
import static com.google.adk.testing.TestUtils.createTestAgentBuilder;
import static com.google.adk.testing.TestUtils.createTestLlm;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Functions}. */
@RunWith(JUnit4.class)
public final class FunctionsTest {
  @Test
  public void handleFunctionCalls_noFunctionCalls() {
    InvocationContext invocationContext = createInvocationContext(createRootAgent());
    Event event = createEvent("event");

    Event functionResponseEvent =
        Functions.handleFunctionCalls(invocationContext, event, /* tools= */ ImmutableMap.of())
            .blockingGet();

    assertThat(functionResponseEvent).isNull();
  }

  @Test
  public void handleFunctionCalls_missingTool() {
    InvocationContext invocationContext = createInvocationContext(createRootAgent());
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.fromText("..."), Part.fromFunctionCall("missing_tool", ImmutableMap.of())))
            .build();

    assertThrows(
        RuntimeException.class,
        () ->
            Functions.handleFunctionCalls(
                invocationContext, event, /* tools= */ ImmutableMap.of()));
  }

  @Test
  public void handleFunctionCalls_singleFunctionCall() {
    InvocationContext invocationContext = createInvocationContext(createRootAgent());
    ImmutableMap<String, Object> args = ImmutableMap.<String, Object>of("key", "value");
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.fromText("..."),
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .id("function_call_id")
                                .name("echo_tool")
                                .args(args)
                                .build())
                        .build()))
            .build();

    Event functionResponseEvent =
        Functions.handleFunctionCalls(
                invocationContext, event, ImmutableMap.of("echo_tool", new EchoTool()))
            .blockingGet();

    assertThat(functionResponseEvent).isNotNull();
    assertThat(functionResponseEvent.toBuilder().id("").timestamp(0).build())
        .isEqualTo(
            Event.builder()
                .id("")
                .timestamp(0)
                .invocationId(invocationContext.invocationId())
                .author(invocationContext.agent().name())
                .content(
                    Content.builder()
                        .role("user")
                        .parts(
                            ImmutableList.of(
                                Part.builder()
                                    .functionResponse(
                                        FunctionResponse.builder()
                                            .id("function_call_id")
                                            .name("echo_tool")
                                            .response(ImmutableMap.of("result", args))
                                            .build())
                                    .build()))
                        .build())
                .build());
  }

  @Test
  public void handleFunctionCalls_multipleFunctionCalls() {
    InvocationContext invocationContext = createInvocationContext(createRootAgent());
    ImmutableMap<String, Object> args1 = ImmutableMap.<String, Object>of("key1", "value2");
    ImmutableMap<String, Object> args2 = ImmutableMap.<String, Object>of("key2", "value2");
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.fromText("..."),
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .id("function_call_id1")
                                .name("echo_tool")
                                .args(args1)
                                .build())
                        .build(),
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .id("function_call_id2")
                                .name("echo_tool")
                                .args(args2)
                                .build())
                        .build()))
            .build();

    Event functionResponseEvent =
        Functions.handleFunctionCalls(
                invocationContext, event, ImmutableMap.of("echo_tool", new EchoTool()))
            .blockingGet();

    assertThat(functionResponseEvent).isNotNull();
    assertThat(functionResponseEvent.content().get().parts().get())
        .containsExactly(
            Part.builder()
                .functionResponse(
                    FunctionResponse.builder()
                        .id("function_call_id1")
                        .name("echo_tool")
                        .response(ImmutableMap.of("result", args1))
                        .build())
                .build(),
            Part.builder()
                .functionResponse(
                    FunctionResponse.builder()
                        .id("function_call_id2")
                        .name("echo_tool")
                        .response(ImmutableMap.of("result", args2))
                        .build())
                .build());
  }

  @Test
  public void handleFunctionCalls_withBeforeToolCallback_returnsBeforeToolCallbackResult() {
    ImmutableMap<String, Object> beforeToolCallbackResult =
        ImmutableMap.<String, Object>of("before_tool_callback_result", "value");
    InvocationContext invocationContext =
        createInvocationContext(
            createTestAgentBuilder(createTestLlm(LlmResponse.builder().build()))
                .beforeToolCallback(
                    (invocationContext1, tool, args, toolContext) ->
                        Maybe.just(beforeToolCallbackResult))
                .build());
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.fromText("..."),
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .id("function_call_id")
                                .name("echo_tool")
                                .args(ImmutableMap.of("key", "value"))
                                .build())
                        .build()))
            .build();

    Event functionResponseEvent =
        Functions.handleFunctionCalls(
                invocationContext, event, ImmutableMap.of("echo_tool", new FailingEchoTool()))
            .blockingGet();

    assertThat(functionResponseEvent).isNotNull();
    assertThat(functionResponseEvent.content().get().parts().get())
        .containsExactly(
            Part.builder()
                .functionResponse(
                    FunctionResponse.builder()
                        .id("function_call_id")
                        .name("echo_tool")
                        .response(beforeToolCallbackResult)
                        .build())
                .build());
  }

  @Test
  public void handleFunctionCalls_withBeforeToolCallbackThatReturnsNull_returnsToolResult() {
    InvocationContext invocationContext =
        createInvocationContext(
            createTestAgentBuilder(createTestLlm(LlmResponse.builder().build()))
                .beforeToolCallback((invocationContext1, tool, args, toolContext) -> Maybe.empty())
                .build());
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.fromText("..."),
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .id("function_call_id")
                                .name("echo_tool")
                                .args(ImmutableMap.of("key", "value"))
                                .build())
                        .build()))
            .build();

    Event functionResponseEvent =
        Functions.handleFunctionCalls(
                invocationContext, event, ImmutableMap.of("echo_tool", new EchoTool()))
            .blockingGet();

    assertThat(functionResponseEvent).isNotNull();
    assertThat(functionResponseEvent.content().get().parts().get())
        .containsExactly(
            Part.builder()
                .functionResponse(
                    FunctionResponse.builder()
                        .id("function_call_id")
                        .name("echo_tool")
                        .response(ImmutableMap.of("result", ImmutableMap.of("key", "value")))
                        .build())
                .build());
  }

  @Test
  public void handleFunctionCalls_withBeforeToolCallbackSync_returnsBeforeToolCallbackResult() {
    ImmutableMap<String, Object> beforeToolCallbackResult =
        ImmutableMap.<String, Object>of("before_tool_callback_result", "value");
    InvocationContext invocationContext =
        createInvocationContext(
            createTestAgentBuilder(createTestLlm(LlmResponse.builder().build()))
                .beforeToolCallbackSync(
                    (invocationContext1, tool, args, toolContext) ->
                        Optional.of(beforeToolCallbackResult))
                .build());
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.fromText("..."),
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .id("function_call_id")
                                .name("echo_tool")
                                .args(ImmutableMap.of("key", "value"))
                                .build())
                        .build()))
            .build();

    Event functionResponseEvent =
        Functions.handleFunctionCalls(
                invocationContext, event, ImmutableMap.of("echo_tool", new FailingEchoTool()))
            .blockingGet();

    assertThat(functionResponseEvent).isNotNull();
    assertThat(functionResponseEvent.content().get().parts().get())
        .containsExactly(
            Part.builder()
                .functionResponse(
                    FunctionResponse.builder()
                        .id("function_call_id")
                        .name("echo_tool")
                        .response(beforeToolCallbackResult)
                        .build())
                .build());
  }

  @Test
  public void handleFunctionCalls_withBeforeToolCallbackSyncThatReturnsNull_returnsToolResult() {
    InvocationContext invocationContext =
        createInvocationContext(
            createTestAgentBuilder(createTestLlm(LlmResponse.builder().build()))
                .beforeToolCallbackSync(
                    (invocationContext1, tool, args, toolContext) -> Optional.empty())
                .build());
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.fromText("..."),
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .id("function_call_id")
                                .name("echo_tool")
                                .args(ImmutableMap.of("key", "value"))
                                .build())
                        .build()))
            .build();

    Event functionResponseEvent =
        Functions.handleFunctionCalls(
                invocationContext, event, ImmutableMap.of("echo_tool", new EchoTool()))
            .blockingGet();

    assertThat(functionResponseEvent).isNotNull();
    assertThat(functionResponseEvent.content().get().parts().get())
        .containsExactly(
            Part.builder()
                .functionResponse(
                    FunctionResponse.builder()
                        .id("function_call_id")
                        .name("echo_tool")
                        .response(ImmutableMap.of("result", ImmutableMap.of("key", "value")))
                        .build())
                .build());
  }

  @Test
  public void handleFunctionCalls_withAfterToolCallback_returnsAfterToolCallbackResult() {
    InvocationContext invocationContext =
        createInvocationContext(
            createTestAgentBuilder(createTestLlm(LlmResponse.builder().build()))
                .afterToolCallback(
                    (invocationContext1, tool, args, toolContext, response) ->
                        Maybe.just(
                            ImmutableMap.<String, Object>of(
                                "after_tool_callback_result", response)))
                .build());
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.fromText("..."),
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .id("function_call_id")
                                .name("echo_tool")
                                .args(ImmutableMap.of("key", "value"))
                                .build())
                        .build()))
            .build();

    Event functionResponseEvent =
        Functions.handleFunctionCalls(
                invocationContext, event, ImmutableMap.of("echo_tool", new EchoTool()))
            .blockingGet();

    assertThat(functionResponseEvent).isNotNull();
    assertThat(functionResponseEvent.content().get().parts().get())
        .containsExactly(
            Part.builder()
                .functionResponse(
                    FunctionResponse.builder()
                        .id("function_call_id")
                        .name("echo_tool")
                        .response(
                            ImmutableMap.of(
                                "after_tool_callback_result",
                                ImmutableMap.of("result", ImmutableMap.of("key", "value"))))
                        .build())
                .build());
  }

  @Test
  public void handleFunctionCalls_withAfterToolCallbackThatReturnsNull_returnsToolResult() {
    InvocationContext invocationContext =
        createInvocationContext(
            createTestAgentBuilder(createTestLlm(LlmResponse.builder().build()))
                .afterToolCallback(
                    (invocationContext1, tool, args, toolContext, response) -> Maybe.empty())
                .build());
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.fromText("..."),
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .id("function_call_id")
                                .name("echo_tool")
                                .args(ImmutableMap.of("key", "value"))
                                .build())
                        .build()))
            .build();

    Event functionResponseEvent =
        Functions.handleFunctionCalls(
                invocationContext, event, ImmutableMap.of("echo_tool", new EchoTool()))
            .blockingGet();

    assertThat(functionResponseEvent).isNotNull();
    assertThat(functionResponseEvent.content().get().parts().get())
        .containsExactly(
            Part.builder()
                .functionResponse(
                    FunctionResponse.builder()
                        .id("function_call_id")
                        .name("echo_tool")
                        .response(ImmutableMap.of("result", ImmutableMap.of("key", "value")))
                        .build())
                .build());
  }

  @Test
  public void handleFunctionCalls_withAfterToolCallbackSync_returnsAfterToolCallbackResult() {
    InvocationContext invocationContext =
        createInvocationContext(
            createTestAgentBuilder(createTestLlm(LlmResponse.builder().build()))
                .afterToolCallbackSync(
                    (invocationContext1, tool, args, toolContext, response) ->
                        Optional.of(
                            ImmutableMap.<String, Object>of(
                                "after_tool_callback_result", response)))
                .build());
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.fromText("..."),
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .id("function_call_id")
                                .name("echo_tool")
                                .args(ImmutableMap.of("key", "value"))
                                .build())
                        .build()))
            .build();

    Event functionResponseEvent =
        Functions.handleFunctionCalls(
                invocationContext, event, ImmutableMap.of("echo_tool", new EchoTool()))
            .blockingGet();

    assertThat(functionResponseEvent).isNotNull();
    assertThat(functionResponseEvent.content().get().parts().get())
        .containsExactly(
            Part.builder()
                .functionResponse(
                    FunctionResponse.builder()
                        .id("function_call_id")
                        .name("echo_tool")
                        .response(
                            ImmutableMap.of(
                                "after_tool_callback_result",
                                ImmutableMap.of("result", ImmutableMap.of("key", "value"))))
                        .build())
                .build());
  }

  @Test
  public void handleFunctionCalls_withAfterToolCallbackSyncThatReturnsNull_returnsToolResult() {
    InvocationContext invocationContext =
        createInvocationContext(
            createTestAgentBuilder(createTestLlm(LlmResponse.builder().build()))
                .afterToolCallbackSync(
                    (invocationContext1, tool, args, toolContext, response) -> Optional.empty())
                .build());
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.fromText("..."),
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .id("function_call_id")
                                .name("echo_tool")
                                .args(ImmutableMap.of("key", "value"))
                                .build())
                        .build()))
            .build();

    Event functionResponseEvent =
        Functions.handleFunctionCalls(
                invocationContext, event, ImmutableMap.of("echo_tool", new EchoTool()))
            .blockingGet();

    assertThat(functionResponseEvent).isNotNull();
    assertThat(functionResponseEvent.content().get().parts().get())
        .containsExactly(
            Part.builder()
                .functionResponse(
                    FunctionResponse.builder()
                        .id("function_call_id")
                        .name("echo_tool")
                        .response(ImmutableMap.of("result", ImmutableMap.of("key", "value")))
                        .build())
                .build());
  }

  @Test
  public void
      handleFunctionCalls_withBeforeAndAfterToolCallback_returnsAfterToolCallbackResultAppliedToBeforeToolCallbackResult() {
    InvocationContext invocationContext =
        createInvocationContext(
            createTestAgentBuilder(createTestLlm(LlmResponse.builder().build()))
                .beforeToolCallback(
                    (invocationContext1, tool, args, toolContext) ->
                        Maybe.just(
                            ImmutableMap.<String, Object>of(
                                "before_tool_callback_result", "value")))
                .afterToolCallback(
                    (invocationContext1, tool, args, toolContext, response) ->
                        Maybe.just(
                            ImmutableMap.<String, Object>of(
                                "after_tool_callback_result", response)))
                .build());
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.fromText("..."),
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .id("function_call_id")
                                .name("echo_tool")
                                .args(ImmutableMap.of("key", "value"))
                                .build())
                        .build()))
            .build();

    Event functionResponseEvent =
        Functions.handleFunctionCalls(
                invocationContext, event, ImmutableMap.of("echo_tool", new FailingEchoTool()))
            .blockingGet();

    assertThat(functionResponseEvent).isNotNull();
    assertThat(functionResponseEvent.content().get().parts().get())
        .containsExactly(
            Part.builder()
                .functionResponse(
                    FunctionResponse.builder()
                        .id("function_call_id")
                        .name("echo_tool")
                        .response(
                            ImmutableMap.of(
                                "after_tool_callback_result",
                                ImmutableMap.of("before_tool_callback_result", "value")))
                        .build())
                .build());
  }

  @Test
  public void populateClientFunctionCallId_withMissingId_populatesId() {
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .name("echo_tool")
                                .args(ImmutableMap.of("key", "value"))
                                .build())
                        .build()))
            .build();

    Functions.populateClientFunctionCallId(event);
    FunctionCall functionCall = event.content().get().parts().get().get(0).functionCall().get();
    assertThat(functionCall.id()).isPresent();
    assertThat(functionCall.id().get()).isNotEmpty();
  }

  @Test
  public void populateClientFunctionCallId_withEmptyId_populatesId() {
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .name("echo_tool")
                                .id("")
                                .args(ImmutableMap.of("key", "value"))
                                .build())
                        .build()))
            .build();

    Functions.populateClientFunctionCallId(event);
    FunctionCall functionCall = event.content().get().parts().get().get(0).functionCall().get();
    assertThat(functionCall.id()).isPresent();
    assertThat(functionCall.id().get()).isNotEmpty();
  }

  @Test
  public void populateClientFunctionCallId_withExistingId_noChange() {
    Event event =
        createEvent("event").toBuilder()
            .content(
                Content.fromParts(
                    Part.builder()
                        .functionCall(
                            FunctionCall.builder()
                                .name("echo_tool")
                                .id("some_id")
                                .args(ImmutableMap.of("key", "value"))
                                .build())
                        .build()))
            .build();

    Functions.populateClientFunctionCallId(event);
    assertThat(event).isEqualTo(event);
  }

  private static class EchoTool extends BaseTool {
    EchoTool() {
      super("echo_tool", "description");
    }

    @Override
    public Optional<FunctionDeclaration> declaration() {
      return Optional.of(FunctionDeclaration.builder().name("echo_tool").build());
    }

    @Override
    public Single<Map<String, Object>> runAsync(Map<String, Object> args, ToolContext toolContext) {
      return Single.just(ImmutableMap.<String, Object>builder().put("result", args).buildOrThrow());
    }
  }

  private static class FailingEchoTool extends BaseTool {
    FailingEchoTool() {
      super("echo_tool", "description");
    }

    @Override
    public Optional<FunctionDeclaration> declaration() {
      return Optional.of(FunctionDeclaration.builder().name("echo_tool").build());
    }

    @Override
    public Single<Map<String, Object>> runAsync(Map<String, Object> args, ToolContext toolContext) {
      return Single.error(new RuntimeException("error"));
    }
  }
}
