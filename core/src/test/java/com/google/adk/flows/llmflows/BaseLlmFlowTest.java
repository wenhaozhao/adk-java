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

import static com.google.adk.testing.TestUtils.createInvocationContext;
import static com.google.adk.testing.TestUtils.createLlmResponse;
import static com.google.adk.testing.TestUtils.createTestAgent;
import static com.google.adk.testing.TestUtils.createTestAgentBuilder;
import static com.google.adk.testing.TestUtils.createTestLlm;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor.RequestProcessingResult;
import com.google.adk.flows.llmflows.ResponseProcessor.ResponseProcessingResult;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.testing.TestLlm;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BaseLlmFlow}. */
@RunWith(JUnit4.class)
public final class BaseLlmFlowTest {

  @Test
  public void run_singleTextResponse_returnsSingleEvent() {
    Content content = Content.fromParts(Part.fromText("LLM response"));
    TestLlm testLlm = createTestLlm(createLlmResponse(content));
    InvocationContext invocationContext = createInvocationContext(createTestAgent(testLlm));
    BaseLlmFlow baseLlmFlow = createBaseLlmFlowWithoutProcessors();

    List<Event> events = baseLlmFlow.run(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(1);
    assertThat(getOnlyElement(events).content()).hasValue(content);
  }

  @Test
  public void run_withFunctionCall_returnsCorrectEvents() {
    Content firstContent =
        Content.fromParts(
            Part.fromText("LLM response with function call"),
            Part.fromFunctionCall("my_function", ImmutableMap.of("arg1", "value1")));
    Content secondContent =
        Content.fromParts(Part.fromText("LLM response after function response"));
    TestLlm testLlm =
        createTestLlm(
            Flowable.just(createLlmResponse(firstContent)),
            Flowable.just(createLlmResponse(secondContent)));
    ImmutableMap<String, Object> testResponse =
        ImmutableMap.<String, Object>of("response", "response for my_function");
    InvocationContext invocationContext =
        createInvocationContext(
            createTestAgentBuilder(testLlm)
                .tools(ImmutableList.of(new TestTool("my_function", testResponse)))
                .build());
    BaseLlmFlow baseLlmFlow = createBaseLlmFlowWithoutProcessors();

    List<Event> events = baseLlmFlow.run(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(3);
    assertContentIgnoringFunctionId(events.get(0).content().get(), firstContent);
    assertContentIgnoringFunctionId(
        events.get(1).content().get(),
        Content.fromParts(
            Part.builder()
                .functionResponse(
                    FunctionResponse.builder()
                        .id("")
                        .name("my_function")
                        .response(testResponse)
                        .build())
                .build()));
    assertThat(events.get(2).content()).hasValue(secondContent);
  }

  @Test
  public void run_withRequestProcessor_doesNotModifyRequest() {
    Content content = Content.fromParts(Part.fromText("LLM response"));
    TestLlm testLlm = createTestLlm(createLlmResponse(content));
    InvocationContext invocationContext = createInvocationContext(createTestAgent(testLlm));
    RequestProcessor requestProcessor = createRequestProcessor();
    BaseLlmFlow baseLlmFlow =
        createBaseLlmFlow(
            ImmutableList.of(requestProcessor), /* responseProcessors= */ ImmutableList.of());

    List<Event> events = baseLlmFlow.run(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(1);
    assertThat(getOnlyElement(events).content()).hasValue(content);
  }

  @Test
  public void run_withRequestProcessor_modifiesRequest() {
    Content content = Content.fromParts(Part.fromText("LLM response"));
    TestLlm testLlm = createTestLlm(createLlmResponse(content));
    InvocationContext invocationContext = createInvocationContext(createTestAgent(testLlm));
    RequestProcessor requestProcessor =
        createRequestProcessor(
            request ->
                request.toBuilder()
                    .appendInstructions(ImmutableList.of("instruction from request processor"))
                    .build());
    BaseLlmFlow baseLlmFlow =
        createBaseLlmFlow(
            ImmutableList.of(requestProcessor), /* responseProcessors= */ ImmutableList.of());

    List<Event> unused = baseLlmFlow.run(invocationContext).toList().blockingGet();

    assertThat(
            testLlm
                .getLastRequest()
                .config()
                .orElseThrow(NoSuchElementException::new)
                .systemInstruction()
                .orElseThrow(NoSuchElementException::new))
        .isEqualTo(Content.fromParts(Part.fromText("instruction from request processor")));
  }

  @Test
  public void run_withResponseProcessor_doesNotModifyResponse() {
    Content content = Content.fromParts(Part.fromText("LLM response"));
    TestLlm testLlm = createTestLlm(createLlmResponse(content));
    InvocationContext invocationContext = createInvocationContext(createTestAgent(testLlm));
    ResponseProcessor responseProcessor = createResponseProcessor();
    BaseLlmFlow baseLlmFlow =
        createBaseLlmFlow(
            /* requestProcessors= */ ImmutableList.of(), ImmutableList.of(responseProcessor));

    List<Event> events = baseLlmFlow.run(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(1);
    assertThat(getOnlyElement(events).content()).hasValue(content);
  }

  @Test
  public void run_withResponseProcessor_modifiesResponse() {
    Content originalContent = Content.fromParts(Part.fromText("Original LLM response"));
    Content newContent = Content.fromParts(Part.fromText("Modified response"));
    TestLlm testLlm = createTestLlm(createLlmResponse(originalContent));
    InvocationContext invocationContext = createInvocationContext(createTestAgent(testLlm));
    ResponseProcessor responseProcessor =
        createResponseProcessor(response -> LlmResponse.builder().content(newContent).build());
    BaseLlmFlow baseLlmFlow =
        createBaseLlmFlow(
            /* requestProcessors= */ ImmutableList.of(), ImmutableList.of(responseProcessor));

    List<Event> events = baseLlmFlow.run(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(1);
    assertThat(getOnlyElement(events).content()).hasValue(newContent);
  }

  @Test
  public void run_withTools_toolsAreAddedToRequest() {
    Content firstContent =
        Content.fromParts(
            Part.fromText("LLM response with function call"),
            Part.fromFunctionCall("my_function", ImmutableMap.of("arg1", "value1")));
    Content secondContent =
        Content.fromParts(Part.fromText("LLM response after function response"));
    TestLlm testLlm =
        createTestLlm(
            Flowable.just(createLlmResponse(firstContent)),
            Flowable.just(createLlmResponse(secondContent)));
    TestTool testTool = new TestTool("my_function", ImmutableMap.<String, Object>of());
    InvocationContext invocationContext =
        createInvocationContext(
            createTestAgentBuilder(testLlm).tools(ImmutableList.of(testTool)).build());
    BaseLlmFlow baseLlmFlow = createBaseLlmFlowWithoutProcessors();

    List<Event> unused = baseLlmFlow.run(invocationContext).toList().blockingGet();

    assertThat(testLlm.getLastRequest().tools()).containsEntry("my_function", testTool);
  }

  @Test
  public void run_withBeforeModelCallback_returnsResponseFromCallback() {
    Content realContent = Content.fromParts(Part.fromText("Real LLM response"));
    Content callbackContent = Content.fromParts(Part.fromText("Callback response"));
    TestLlm testLlm = createTestLlm(createLlmResponse(realContent));
    InvocationContext invocationContext =
        createInvocationContext(
            createTestAgentBuilder(testLlm)
                .beforeModelCallback(
                    (context, request) ->
                        Maybe.just(LlmResponse.builder().content(callbackContent).build()))
                .build());
    BaseLlmFlow baseLlmFlow = createBaseLlmFlowWithoutProcessors();

    List<Event> events = baseLlmFlow.run(invocationContext).toList().blockingGet();

    assertThat(testLlm.getRequests()).isEmpty();
    assertThat(events).hasSize(1);
    assertThat(getOnlyElement(events).content()).hasValue(callbackContent);
  }

  @Test
  public void run_withAfterModelCallback_returnsResponseFromCallback() {
    Part textPartFromModel = Part.fromText("Real LLM response");
    Part textPartFromCallback = Part.fromText("Callback response");
    TestLlm testLlm = createTestLlm(createLlmResponse(Content.fromParts(textPartFromModel)));
    InvocationContext invocationContext =
        createInvocationContext(
            createTestAgentBuilder(testLlm)
                .afterModelCallback(
                    (context, response) ->
                        Maybe.just(addPartToResponse(response, textPartFromCallback)))
                .build());
    BaseLlmFlow baseLlmFlow = createBaseLlmFlowWithoutProcessors();

    List<Event> events = baseLlmFlow.run(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(1);
    assertThat(getOnlyElement(events).content())
        .hasValue(
            Content.builder()
                .parts(ImmutableList.of(textPartFromModel, textPartFromCallback))
                .build());
  }

  @Test
  public void run_withCallbacks_receivesCorrectContext() {
    Content realContent = Content.fromParts(Part.fromText("Real LLM response"));
    TestLlm testLlm = createTestLlm(createLlmResponse(realContent));
    LlmAgent agent =
        createTestAgentBuilder(testLlm)
            .beforeModelCallback(
                (callbackContext, request) -> {
                  assertThat(callbackContext.invocationId()).isNotEmpty();
                  assertThat(callbackContext.agentName()).isEqualTo("test agent");
                  return Maybe.empty();
                })
            .afterModelCallback(
                (callbackContext, response) -> {
                  assertThat(callbackContext.invocationId()).isNotEmpty();
                  assertThat(callbackContext.agentName()).isEqualTo("test agent");
                  assertThat(response.content()).hasValue(realContent);
                  return Maybe.empty();
                })
            .build();

    InvocationContext invocationContext = createInvocationContext(agent);
    BaseLlmFlow baseLlmFlow = createBaseLlmFlowWithoutProcessors();

    List<Event> events = baseLlmFlow.run(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(1);
    assertThat(getOnlyElement(events).content()).hasValue(realContent);
  }

  private static LlmResponse addPartToResponse(LlmResponse response, Part part) {
    return LlmResponse.builder()
        .content(
            Content.builder()
                .parts(
                    ImmutableList.<Part>builder()
                        .addAll(
                            response.content().flatMap(Content::parts).orElse(ImmutableList.of()))
                        .add(part)
                        .build())
                .build())
        .build();
  }

  private static BaseLlmFlow createBaseLlmFlowWithoutProcessors() {
    return createBaseLlmFlow(ImmutableList.of(), ImmutableList.of());
  }

  private static BaseLlmFlow createBaseLlmFlow(
      List<RequestProcessor> requestProcessors, List<ResponseProcessor> responseProcessors) {
    return new BaseLlmFlow(requestProcessors, responseProcessors) {};
  }

  private static RequestProcessor createRequestProcessor() {
    return (context, request) ->
        Single.just(RequestProcessingResult.create(request, ImmutableList.of()));
  }

  private static RequestProcessor createRequestProcessor(
      Function<LlmRequest, LlmRequest> requestUpdater) {
    return (context, request) ->
        Single.just(
            RequestProcessingResult.create(requestUpdater.apply(request), ImmutableList.of()));
  }

  private static ResponseProcessor createResponseProcessor() {
    return (context, response) ->
        Single.just(
            ResponseProcessingResult.create(
                response, ImmutableList.of(), /* transferToAgent= */ Optional.empty()));
  }

  private static ResponseProcessor createResponseProcessor(
      Function<LlmResponse, LlmResponse> responseUpdater) {
    return (context, response) ->
        Single.just(
            ResponseProcessingResult.create(
                responseUpdater.apply(response),
                ImmutableList.of(),
                /* transferToAgent= */ Optional.empty()));
  }

  private static class TestTool extends BaseTool {
    private final Map<String, Object> response;

    TestTool(String name, Map<String, Object> response) {
      super(name, "tool description for " + name);
      this.response = response;
    }

    @Override
    public Optional<FunctionDeclaration> declaration() {
      return Optional.of(FunctionDeclaration.builder().name(name()).build());
    }

    @Override
    public Single<Map<String, Object>> runAsync(Map<String, Object> args, ToolContext toolContext) {
      return Single.just(response);
    }
  }

  private static void assertContentIgnoringFunctionId(
      Content actualContent, Content expectedContent) {

    assertThat(actualContent.role()).isEqualTo(expectedContent.role());

    Optional<List<Part>> actualPartsOpt = actualContent.parts();
    Optional<List<Part>> expectedPartsOpt = expectedContent.parts();
    assertThat(actualPartsOpt.isPresent()).isEqualTo(expectedPartsOpt.isPresent());

    if (expectedPartsOpt.isPresent()) {
      List<Part> actualParts = actualPartsOpt.get();
      List<Part> expectedParts = expectedPartsOpt.get();
      assertThat(actualParts).hasSize(expectedParts.size());

      for (int i = 0; i < expectedParts.size(); i++) {
        Part actualPart = actualParts.get(i);
        Part expectedPart = expectedParts.get(i);

        if (expectedPart.functionCall().isPresent()) {
          assertThat(actualPart.functionCall()).isPresent();
          FunctionCall actualFc = actualPart.functionCall().get();
          FunctionCall expectedFc = expectedPart.functionCall().get();
          assertThat(actualFc.name()).isEqualTo(expectedFc.name());
          assertThat(actualFc.args()).isEqualTo(expectedFc.args());
        } else if (expectedPart.functionResponse().isPresent()) {
          assertThat(actualPart.functionResponse()).isPresent();
          FunctionResponse actualFr = actualPart.functionResponse().get();
          FunctionResponse expectedFr = expectedPart.functionResponse().get();
          assertThat(actualFr.name()).isEqualTo(expectedFr.name());
          assertThat(actualFr.response()).isEqualTo(expectedFr.response());
        } else {
          assertThat(actualPart).isEqualTo(expectedPart);
        }
      }
    }
  }
}
