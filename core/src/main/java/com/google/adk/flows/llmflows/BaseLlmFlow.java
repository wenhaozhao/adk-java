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

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LiveRequest;
import com.google.adk.agents.RunConfig.StreamingMode;
import com.google.adk.events.Event;
import com.google.adk.exceptions.LlmCallsLimitExceededException;
import com.google.adk.flows.BaseFlow;
import com.google.adk.flows.llmflows.RequestProcessor.RequestProcessingResult;
import com.google.adk.flows.llmflows.ResponseProcessor.ResponseProcessingResult;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRegistry;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.genai.types.FunctionCall;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableObserver;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A basic flow that calls the LLM in a loop until a final response is generated. */
public abstract class BaseLlmFlow implements BaseFlow {
  // TODO: b/414451154 - Trace runLive and callLlm methods.

  protected final List<RequestProcessor> requestProcessors;
  protected final List<ResponseProcessor> responseProcessors;

  public BaseLlmFlow(
      List<RequestProcessor> requestProcessors, List<ResponseProcessor> responseProcessors) {
    this.requestProcessors = requestProcessors;
    this.responseProcessors = responseProcessors;
  }

  /**
   * Pre-processes the LLM request before sending it to the LLM. Executes all registered {@link
   * RequestProcessor}.
   */
  protected RequestProcessingResult preprocess(InvocationContext context, LlmRequest llmRequest) {

    LlmRequest updatedRequest = llmRequest;
    List<Iterable<Event>> eventIterables = new ArrayList<>();

    // Execute all registered request processors.
    for (RequestProcessor processor : requestProcessors) {
      RequestProcessingResult result =
          processor.processRequest(context, updatedRequest).blockingGet();
      if (result.events() != null) {
        eventIterables.add(result.events());
      }
      updatedRequest = result.updatedRequest();
    }

    LlmAgent agent = (LlmAgent) context.agent();
    LlmRequest.Builder updatedRequestBuilder = updatedRequest.toBuilder();
    for (BaseTool tool : agent.tools()) {
      tool.processLlmRequest(updatedRequestBuilder, ToolContext.builder(context).build());
    }

    // Return a single on-demand (lazy) Iterable by concatenating each processor's iterable.
    Iterable<Event> combinedEvents = Iterables.concat(eventIterables);
    return RequestProcessingResult.create(updatedRequestBuilder.build(), combinedEvents);
  }

  /**
   * Post-processes the LLM response after receiving it from the LLM. Executes all registered {@link
   * ResponseProcessor} instances. Handles function calls if present in the response.
   */
  protected Single<ResponseProcessingResult> postprocess(
      InvocationContext context, String eventId, LlmRequest llmRequest, LlmResponse llmResponse) {

    List<Iterable<Event>> eventIterables = new ArrayList<>();

    // Execute all registered response processors.
    for (ResponseProcessor processor : responseProcessors) {
      ResponseProcessingResult result =
          processor.processResponse(context, llmResponse).blockingGet();
      if (result.events() != null) {
        eventIterables.add(result.events());
      }
      llmResponse = result.updatedResponse();
    }
    LlmResponse updatedResponse = llmResponse;

    // If there is no response, return whatever events we have so far.
    if (!updatedResponse.content().isPresent()
        && !updatedResponse.errorCode().isPresent()
        && !updatedResponse.turnComplete().orElse(false)) {
      return Single.just(
          ResponseProcessingResult.create(
              updatedResponse, Iterables.concat(eventIterables), Optional.empty()));
    }

    // Build the model response event and add it to the iterables list.
    Event modelResponseEvent =
        buildModelResponseEvent(eventId, context, llmRequest, updatedResponse);
    eventIterables.add(Collections.singleton(modelResponseEvent));

    // If there are function calls, handle them and add that event as well.
    Maybe<Event> maybeFunctionCallEvent =
        modelResponseEvent.functionCalls().isEmpty()
            ? Maybe.empty()
            : Functions.handleFunctionCalls(context, modelResponseEvent, llmRequest.tools());

    return maybeFunctionCallEvent
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .map(
            functionCallEvent -> {
              Optional<String> transferToAgent = Optional.empty();
              if (functionCallEvent.isPresent()) {
                eventIterables.add(Collections.singleton(functionCallEvent.get()));
                transferToAgent = functionCallEvent.get().actions().transferToAgent();
              }

              // Return a single on-demand (lazy) Iterable by concatenating each processor's
              // iterable.
              Iterable<Event> combinedEvents = Iterables.concat(eventIterables);
              return ResponseProcessingResult.create(
                  updatedResponse, combinedEvents, transferToAgent);
            });
  }

  /** Calls the LLM to generate content. This method handles optional before and after callbacks. */
  private Flowable<LlmResponse> callLlm(
      InvocationContext context, LlmRequest llmRequest, Event modelResponseEvent) {
    LlmAgent agent = (LlmAgent) context.agent();

    return handleBeforeModelCallback(context, llmRequest, modelResponseEvent)
        .flatMapPublisher(
            beforeResponse -> {
              if (beforeResponse.isPresent()) {
                return Flowable.just(beforeResponse.get());
              }

              BaseLlm llm =
                  agent.resolvedModel().model().isPresent()
                      ? agent.resolvedModel().model().get()
                      : LlmRegistry.getLlm(agent.resolvedModel().modelName().get());

              Flowable<LlmResponse> llmResponseFlowable =
                  llm.generateContent(
                      llmRequest, context.runConfig().streamingMode() == StreamingMode.SSE);
              return llmResponseFlowable.concatMap(
                  llmResponse ->
                      handleAfterModelCallback(context, llmResponse, modelResponseEvent)
                          .toFlowable());
            });
  }

  private Single<Optional<LlmResponse>> handleBeforeModelCallback(
      InvocationContext context, LlmRequest llmRequest, Event modelResponseEvent) {
    LlmAgent agent = (LlmAgent) context.agent();
    return agent
        .beforeModelCallback()
        .map(
            callback -> {
              CallbackContext callbackContext =
                  new CallbackContext(context, modelResponseEvent.actions());
              return callback
                  .call(callbackContext, llmRequest)
                  .map(Optional::of)
                  .defaultIfEmpty(Optional.empty());
            })
        .orElse(Single.just(Optional.empty()));
  }

  private Single<LlmResponse> handleAfterModelCallback(
      InvocationContext context, LlmResponse llmResponse, Event modelResponseEvent) {
    LlmAgent agent = (LlmAgent) context.agent();
    return agent
        .afterModelCallback()
        .map(
            callback -> {
              CallbackContext callbackContext =
                  new CallbackContext(context, modelResponseEvent.actions());
              return callback.call(callbackContext, llmResponse).defaultIfEmpty(llmResponse);
            })
        .orElse(Single.just(llmResponse));
  }

  /**
   * Executes a single step of the LLM flow, including pre-processing, LLM call, and
   * post-processing.
   */
  private Flowable<Event> runOneStep(InvocationContext context) {
    LlmRequest llmRequest = LlmRequest.builder().build();
    String eventId = Event.generateEventId();

    // Preprocess before calling the LLM.
    RequestProcessingResult preResult = preprocess(context, llmRequest);

    // If the end invocation is set to true by any callbacks, stop the invocation.
    if (context.endInvocation()) {
      return Flowable.fromIterable(preResult.events());
    }

    // Increment the LLM call count. If the current call exceeds the maximum allowed,
    // the execution is stopped, and an exception is thrown.
    try {
      context.incrementLlmCallsCount();
    } catch (LlmCallsLimitExceededException e) {
      return Flowable.error(e);
    }
    Event modelResponseEvent =
        Event.builder()
            .id(eventId)
            .invocationId(context.invocationId())
            .author(context.agent().name())
            .branch(context.branch())
            .build();

    return callLlm(context, preResult.updatedRequest(), modelResponseEvent)
        .concatMap(
            llmResponse ->
                postprocess(context, eventId, preResult.updatedRequest(), llmResponse).toFlowable())
        .concatMap(
            postResult -> {
              Flowable<Event> combinedEvents =
                  Flowable.fromIterable(Iterables.concat(preResult.events(), postResult.events()));
              if (postResult.transferToAgent().isPresent()) {
                BaseAgent rootAgent = context.agent().rootAgent();
                BaseAgent nextAgent = rootAgent.findAgent(postResult.transferToAgent().get());
                if (nextAgent == null) {
                  throw new IllegalStateException(
                      "Agent not found: " + postResult.transferToAgent().get());
                }
                combinedEvents =
                    combinedEvents.concatWith(Flowable.defer(() -> nextAgent.runAsync(context)));
              }
              return combinedEvents;
            });
  }

  @Override
  public Flowable<Event> run(InvocationContext invocationContext) {
    // Use .cache() to allow events from runOneStep to be emitted as they arrive,
    // while still enabling the collection of these events to decide on recursion
    // after the current step completes.
    Flowable<Event> currentStepEvents = runOneStep(invocationContext).cache();
    return currentStepEvents.concatWith(
        currentStepEvents
            .toList()
            .flatMapPublisher(
                eventList -> {
                  if (eventList.isEmpty() || Iterables.getLast(eventList).finalResponse()) {
                    return Flowable.empty();
                  } else {
                    // If not stopping, recursively call run for the next step.
                    return Flowable.defer(() -> run(invocationContext));
                  }
                }));
  }

  @Override
  public Flowable<Event> runLive(InvocationContext invocationContext) {
    LlmRequest llmRequest = LlmRequest.builder().build();
    String eventId = Event.generateEventId();

    // Preprocess before calling the LLM.
    RequestProcessingResult preResult = preprocess(invocationContext, llmRequest);
    llmRequest = preResult.updatedRequest();
    if (invocationContext.endInvocation()) {
      return Flowable.fromIterable(preResult.events());
    }

    LlmAgent agent = (LlmAgent) invocationContext.agent();
    BaseLlm llm =
        agent.resolvedModel().model().isPresent()
            ? agent.resolvedModel().model().get()
            : LlmRegistry.getLlm(agent.resolvedModel().modelName().get());
    BaseLlmConnection connection = llm.connect(llmRequest);
    Completable historySent =
        llmRequest.contents().isEmpty()
            ? Completable.complete()
            : connection.sendHistory(llmRequest.contents());
    Flowable<LiveRequest> liveRequests = invocationContext.liveRequestQueue().get().get();
    historySent
        .observeOn(
            agent.executor().map(executor -> Schedulers.from(executor)).orElse(Schedulers.io()))
        .andThen(
            liveRequests.concatMapCompletable(
                request -> {
                  if (request.content().isPresent()) {
                    return connection.sendContent(request.content().get());
                  } else if (request.blob().isPresent()) {
                    return connection.sendRealtime(request.blob().get());
                  }
                  return Completable.fromAction(connection::close);
                }))
        .subscribe(
            new CompletableObserver() {
              @Override
              public void onSubscribe(Disposable d) {}

              @Override
              public void onComplete() {
                connection.close();
              }

              @Override
              public void onError(Throwable e) {
                connection.close(e);
              }
            });

    return connection
        .receive()
        .flatMapSingle(
            llmResponse ->
                postprocess(invocationContext, eventId, preResult.updatedRequest(), llmResponse))
        .flatMap(
            postResult -> {
              Flowable<Event> events = Flowable.fromIterable(postResult.events());
              if (postResult.transferToAgent().isPresent()) {
                BaseAgent rootAgent = invocationContext.agent().rootAgent();
                BaseAgent nextAgent = rootAgent.findAgent(postResult.transferToAgent().get());
                if (nextAgent == null) {
                  throw new IllegalStateException(
                      "Agent not found: " + postResult.transferToAgent().get());
                }
                Flowable<Event> nextAgentEvents = nextAgent.runLive(invocationContext);
                events = Flowable.concat(events, nextAgentEvents);
              }
              return events;
            })
        .doOnNext(
            event -> {
              if (!event.functionResponses().isEmpty()) {
                invocationContext.liveRequestQueue().get().content(event.content().get());
              }
            })
        .startWithIterable(preResult.events());
  }

  private Event buildModelResponseEvent(
      String eventId,
      InvocationContext invocationContext,
      LlmRequest llmRequest,
      LlmResponse llmResponse) {
    Event event =
        Event.builder()
            .id(eventId)
            .invocationId(invocationContext.invocationId())
            .author(invocationContext.agent().name())
            .content(llmResponse.content())
            .partial(llmResponse.partial())
            .errorCode(llmResponse.errorCode())
            .errorMessage(llmResponse.errorMessage())
            .interrupted(llmResponse.interrupted())
            .turnComplete(llmResponse.turnComplete())
            .build();
    ImmutableList<FunctionCall> functionCalls = event.functionCalls();
    if (!functionCalls.isEmpty()) {
      Functions.populateClientFunctionCallId(event);
    }
    Set<String> longRunningToolIds =
        Functions.getLongRunningFunctionCalls(functionCalls, llmRequest.tools());
    if (!longRunningToolIds.isEmpty()) {
      event = event.toBuilder().longRunningToolIds(longRunningToolIds).build();
    }
    return event;
  }
}
