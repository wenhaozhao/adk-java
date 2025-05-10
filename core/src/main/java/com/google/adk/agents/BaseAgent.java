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

import com.google.adk.agents.Callbacks.AfterAgentCallback;
import com.google.adk.agents.Callbacks.BeforeAgentCallback;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Base class for all agents. */
public abstract class BaseAgent {

  /** The agent's name. Must be a unique identifier within the agent tree. */
  private final String name;

  /**
   * One line description about the agent's capability. The system can use this for decision-making
   * when delegating control to different agents.
   */
  private final String description;

  /**
   * The parent agent in the agent tree. Note that one agent cannot be added to two different
   * parents' sub-agents lists.
   */
  private BaseAgent parentAgent;

  private List<? extends BaseAgent> subAgents;

  private final Optional<BeforeAgentCallback> beforeAgentCallback;
  private final Optional<AfterAgentCallback> afterAgentCallback;

  public BaseAgent(
      String name,
      String description,
      List<? extends BaseAgent> subAgents,
      BeforeAgentCallback beforeAgentCallback,
      AfterAgentCallback afterAgentCallback) {
    this.name = name;
    this.description = description;
    this.parentAgent = null;
    this.subAgents = subAgents != null ? subAgents : Collections.emptyList();
    this.beforeAgentCallback = Optional.ofNullable(beforeAgentCallback);
    this.afterAgentCallback = Optional.ofNullable(afterAgentCallback);

    // Establish parent relationships for all sub-agents if needed.
    for (BaseAgent subAgent : this.subAgents) {
      subAgent.parentAgent(this);
    }
  }

  /**
   * Gets the agent's unique name.
   *
   * @return the unique name of the agent.
   */
  public final String name() {
    return name;
  }

  /**
   * Gets the one-line description of the agent's capability.
   *
   * @return the description of the agent.
   */
  public final String description() {
    return description;
  }

  /**
   * Retrieves the parent agent in the agent tree.
   *
   * @return the parent agent, or {@code null} if this agent does not have a parent.
   */
  public BaseAgent parentAgent() {
    return parentAgent;
  }

  /**
   * Sets the parent agent.
   *
   * @param parentAgent The parent agent to set.
   */
  protected void parentAgent(BaseAgent parentAgent) {
    this.parentAgent = parentAgent;
  }

  /**
   * Returns the root agent for this agent by traversing up the parent chain.
   *
   * @return the root agent.
   */
  public BaseAgent rootAgent() {
    BaseAgent agent = this;
    while (agent.parentAgent() != null) {
      agent = agent.parentAgent();
    }
    return agent;
  }

  /**
   * Finds an agent (this or descendant) by name.
   *
   * @return the agent or descendant with the given name, or {@code null} if not found.
   */
  public BaseAgent findAgent(String name) {
    if (this.name().equals(name)) {
      return this;
    }
    return findSubAgent(name);
  }

  /** Recursively search sub agent by name. */
  public @Nullable BaseAgent findSubAgent(String name) {
    for (BaseAgent subAgent : subAgents) {
      if (subAgent.name().equals(name)) {
        return subAgent;
      }
      BaseAgent result = subAgent.findSubAgent(name);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  public List<? extends BaseAgent> subAgents() {
    return subAgents;
  }

  public Optional<BeforeAgentCallback> beforeAgentCallback() {
    return beforeAgentCallback;
  }

  public Optional<AfterAgentCallback> afterAgentCallback() {
    return afterAgentCallback;
  }

  private InvocationContext createInvocationContext(InvocationContext parentContext) {
    InvocationContext invocationContext = InvocationContext.copyOf(parentContext);
    invocationContext.agent(this);
    // Check for branch to be truthy (not None, not empty string),
    if (parentContext.branch().filter(s -> !s.isEmpty()).isPresent()) {
      invocationContext.branch(parentContext.branch().get() + "." + name());
    }
    return invocationContext;
  }

  public Flowable<Event> runAsync(InvocationContext parentContext) {
    InvocationContext invocationContext = createInvocationContext(parentContext);

    return beforeAgentCallback
        .map(callback -> callCallback(callback::call, invocationContext))
        .orElse(Single.just(Optional.empty()))
        .flatMapPublisher(
            beforeEvent -> {
              if (beforeEvent.isPresent()) {
                if (beforeEvent.get().content().isPresent()) {
                  return Flowable.just(beforeEvent.get());
                }
                // TODO (b/413093657): Currently endInvocation cannot be set by tools/callbacks,
                // so this will be a no-op.
                // We should allow it to be settable in the invocation context.
                if (invocationContext.endInvocation()) {
                  return Flowable.just(beforeEvent.get());
                }
              }

              Flowable<Event> beforeEvents = Flowable.fromOptional(beforeEvent);
              Flowable<Event> mainEvents = Flowable.defer(() -> runAsyncImpl(invocationContext));
              Flowable<Event> afterEvents =
                  afterAgentCallback
                      .map(
                          callback ->
                              Flowable.defer(
                                  () ->
                                      callCallback(callback::call, invocationContext)
                                          .flatMapPublisher(Flowable::fromOptional)))
                      .orElse(Flowable.empty());

              return Flowable.concat(beforeEvents, mainEvents, afterEvents);
            });
  }

  private Single<Optional<Event>> callCallback(
      Function<CallbackContext, Maybe<Content>> agentCallback,
      InvocationContext invocationContext) {
    CallbackContext callbackContext =
        new CallbackContext(invocationContext, /* eventActions= */ null);
    return agentCallback
        .apply(callbackContext)
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .map(
            optionalContent -> {
              boolean hasContent = optionalContent.isPresent();
              boolean hasStateDelta = !callbackContext.eventActions().stateDelta().isEmpty();

              if (hasContent || hasStateDelta) {
                Event.Builder eventBuilder =
                    Event.builder()
                        .id(Event.generateEventId())
                        .invocationId(invocationContext.invocationId())
                        .author(name())
                        .branch(invocationContext.branch())
                        .actions(callbackContext.eventActions());
                if (hasContent) {
                  eventBuilder.content(optionalContent);
                  return Optional.of(eventBuilder.build());
                } else {
                  return Optional.of(eventBuilder.build());
                }
              }
              return Optional.<Event>empty();
            });
  }

  public Flowable<Event> runLive(InvocationContext parentContext) {
    InvocationContext invocationContext = createInvocationContext(parentContext);

    return runLiveImpl(invocationContext);
  }

  protected abstract Flowable<Event> runAsyncImpl(InvocationContext invocationContext);

  protected abstract Flowable<Event> runLiveImpl(InvocationContext invocationContext);
}
