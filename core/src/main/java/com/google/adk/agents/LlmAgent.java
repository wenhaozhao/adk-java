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
import com.google.adk.agents.Callbacks.AfterAgentCallbackSync;
import com.google.adk.agents.Callbacks.AfterModelCallback;
import com.google.adk.agents.Callbacks.AfterModelCallbackSync;
import com.google.adk.agents.Callbacks.AfterToolCallback;
import com.google.adk.agents.Callbacks.AfterToolCallbackSync;
import com.google.adk.agents.Callbacks.BeforeAgentCallback;
import com.google.adk.agents.Callbacks.BeforeAgentCallbackSync;
import com.google.adk.agents.Callbacks.BeforeModelCallback;
import com.google.adk.agents.Callbacks.BeforeModelCallbackSync;
import com.google.adk.agents.Callbacks.BeforeToolCallback;
import com.google.adk.agents.Callbacks.BeforeToolCallbackSync;
import com.google.adk.events.Event;
import com.google.adk.examples.BaseExampleProvider;
import com.google.adk.examples.Example;
import com.google.adk.flows.llmflows.AutoFlow;
import com.google.adk.flows.llmflows.BaseLlmFlow;
import com.google.adk.flows.llmflows.SingleFlow;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.Model;
import com.google.adk.tools.BaseTool;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Schema;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/** The LLM-based agent. */
public class LlmAgent extends BaseAgent {

  /**
   * Enum to define if contents of previous events should be included in requests to the underlying
   * LLM.
   */
  public enum IncludeContents {
    DEFAULT,
    NONE
  }

  private final Optional<Model> model;
  private final Optional<String> instruction;
  private final Optional<String> globalInstruction;
  private final List<BaseTool> tools;
  private final Optional<GenerateContentConfig> generateContentConfig;
  private final Optional<BaseExampleProvider> exampleProvider;
  private final IncludeContents includeContents;

  private final boolean planning;
  private final boolean disallowTransferToParent;
  private final boolean disallowTransferToPeers;
  private final Optional<BeforeModelCallback> beforeModelCallback;
  private final Optional<AfterModelCallback> afterModelCallback;
  private final Optional<BeforeToolCallback> beforeToolCallback;
  private final Optional<AfterToolCallback> afterToolCallback;
  private final Optional<Schema> inputSchema;
  private final Optional<Schema> outputSchema;
  private final Optional<Executor> executor;
  private final Optional<String> outputKey;

  private volatile Model resolvedModel;
  private final BaseLlmFlow llmFlow;

  private LlmAgent(Builder builder) {
    super(
        builder.name,
        builder.description,
        builder.subAgents,
        builder.beforeAgentCallback,
        builder.afterAgentCallback);
    this.model = Optional.ofNullable(builder.model);
    this.instruction = Optional.ofNullable(builder.instruction);
    this.globalInstruction = Optional.ofNullable(builder.globalInstruction);

    this.generateContentConfig = Optional.ofNullable(builder.generateContentConfig);
    this.exampleProvider = Optional.ofNullable(builder.exampleProvider);
    this.includeContents =
        builder.includeContents != null ? builder.includeContents : IncludeContents.DEFAULT;
    this.planning = builder.planning != null ? builder.planning : false;
    this.disallowTransferToParent =
        builder.disallowTransferToParent != null ? builder.disallowTransferToParent : false;
    this.disallowTransferToPeers =
        builder.disallowTransferToPeers != null ? builder.disallowTransferToPeers : false;
    this.beforeModelCallback = Optional.ofNullable(builder.beforeModelCallback);
    this.afterModelCallback = Optional.ofNullable(builder.afterModelCallback);
    this.beforeToolCallback = Optional.ofNullable(builder.beforeToolCallback);
    this.afterToolCallback = Optional.ofNullable(builder.afterToolCallback);
    this.inputSchema = Optional.ofNullable(builder.inputSchema);
    this.outputSchema = Optional.ofNullable(builder.outputSchema);
    this.executor = Optional.ofNullable(builder.executor);
    this.outputKey = Optional.ofNullable(builder.outputKey);
    this.tools = builder.tools != null ? builder.tools : Collections.emptyList();

    this.llmFlow = determineLlmFlow();

    // Validate name not empty.
    Preconditions.checkArgument(!this.name().isEmpty(), "Agent name cannot be empty.");
  }

  /** Returns a {@link Builder} for {@link LlmAgent}. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link LlmAgent}. */
  public static class Builder {
    private String name;
    private String description;

    private Model model;
    private String instruction;
    private String globalInstruction;
    private List<BaseAgent> subAgents;
    private List<BaseTool> tools;
    private GenerateContentConfig generateContentConfig;
    private BaseExampleProvider exampleProvider;
    private IncludeContents includeContents;
    private Boolean planning;
    private Boolean disallowTransferToParent;
    private Boolean disallowTransferToPeers;
    private BeforeModelCallback beforeModelCallback;
    private AfterModelCallback afterModelCallback;
    private BeforeAgentCallback beforeAgentCallback;
    private AfterAgentCallback afterAgentCallback;
    private BeforeToolCallback beforeToolCallback;
    private AfterToolCallback afterToolCallback;
    private Schema inputSchema;
    private Schema outputSchema;
    private Executor executor;
    private String outputKey;

    @CanIgnoreReturnValue
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder description(String description) {
      this.description = description;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder model(String model) {
      this.model = Model.builder().modelName(model).build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder model(BaseLlm model) {
      this.model = Model.builder().model(model).build();
      return this;
    }

    @CanIgnoreReturnValue
    public Builder instruction(String instruction) {
      this.instruction = instruction;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder globalInstruction(String globalInstruction) {
      this.globalInstruction = globalInstruction;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder subAgents(List<? extends BaseAgent> subAgents) {
      this.subAgents = ImmutableList.copyOf(subAgents);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder subAgents(BaseAgent... subAgents) {
      this.subAgents = ImmutableList.copyOf(subAgents);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder tools(List<? extends BaseTool> tools) {
      this.tools = ImmutableList.copyOf(tools);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder tools(BaseTool... tools) {
      this.tools = ImmutableList.copyOf(tools);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder generateContentConfig(GenerateContentConfig generateContentConfig) {
      this.generateContentConfig = generateContentConfig;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder exampleProvider(BaseExampleProvider exampleProvider) {
      this.exampleProvider = exampleProvider;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder exampleProvider(List<Example> examples) {
      this.exampleProvider =
          new BaseExampleProvider() {
            @Override
            public List<Example> getExamples(String query) {
              return examples;
            }
          };
      return this;
    }

    @CanIgnoreReturnValue
    public Builder exampleProvider(Example... examples) {
      this.exampleProvider =
          new BaseExampleProvider() {
            @Override
            public ImmutableList<Example> getExamples(String query) {
              return ImmutableList.copyOf(examples);
            }
          };
      return this;
    }

    @CanIgnoreReturnValue
    public Builder includeContents(IncludeContents includeContents) {
      this.includeContents = includeContents;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder planning(boolean planning) {
      this.planning = planning;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder disallowTransferToParent(boolean disallowTransferToParent) {
      this.disallowTransferToParent = disallowTransferToParent;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder disallowTransferToPeers(boolean disallowTransferToPeers) {
      this.disallowTransferToPeers = disallowTransferToPeers;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder beforeModelCallback(BeforeModelCallback beforeModelCallback) {
      this.beforeModelCallback = beforeModelCallback;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder beforeModelCallbackSync(BeforeModelCallbackSync beforeModelCallbackSync) {
      this.beforeModelCallback =
          (callbackContext, llmRequest) ->
              Maybe.fromOptional(beforeModelCallbackSync.call(callbackContext, llmRequest));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder afterModelCallback(AfterModelCallback afterModelCallback) {
      this.afterModelCallback = afterModelCallback;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder afterModelCallbackSync(AfterModelCallbackSync afterModelCallbackSync) {
      this.afterModelCallback =
          (callbackContext, llmResponse) ->
              Maybe.fromOptional(afterModelCallbackSync.call(callbackContext, llmResponse));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder beforeAgentCallback(BeforeAgentCallback beforeAgentCallback) {
      this.beforeAgentCallback = beforeAgentCallback;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder beforeAgentCallbackSync(BeforeAgentCallbackSync beforeAgentCallbackSync) {
      this.beforeAgentCallback =
          (callbackContext) -> Maybe.fromOptional(beforeAgentCallbackSync.call(callbackContext));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder afterAgentCallback(AfterAgentCallback afterAgentCallback) {
      this.afterAgentCallback = afterAgentCallback;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder afterAgentCallbackSync(AfterAgentCallbackSync afterAgentCallbackSync) {
      this.afterAgentCallback =
          (callbackContext) -> Maybe.fromOptional(afterAgentCallbackSync.call(callbackContext));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder beforeToolCallback(BeforeToolCallback beforeToolCallback) {
      this.beforeToolCallback = beforeToolCallback;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder beforeToolCallbackSync(BeforeToolCallbackSync beforeToolCallbackSync) {
      this.beforeToolCallback =
          (invocationContext, baseTool, input, toolContext) ->
              Maybe.fromOptional(
                  beforeToolCallbackSync.call(invocationContext, baseTool, input, toolContext));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder afterToolCallback(AfterToolCallback afterToolCallback) {
      this.afterToolCallback = afterToolCallback;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder afterToolCallbackSync(AfterToolCallbackSync afterToolCallbackSync) {
      this.afterToolCallback =
          (invocationContext, baseTool, input, toolContext, response) ->
              Maybe.fromOptional(
                  afterToolCallbackSync.call(
                      invocationContext, baseTool, input, toolContext, response));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder inputSchema(Schema inputSchema) {
      this.inputSchema = inputSchema;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder outputSchema(Schema outputSchema) {
      this.outputSchema = outputSchema;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder executor(Executor executor) {
      this.executor = executor;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder outputKey(String outputKey) {
      this.outputKey = outputKey;
      return this;
    }

    public LlmAgent build() {
      return new LlmAgent(this);
    }
  }

  private BaseLlmFlow determineLlmFlow() {
    if (disallowTransferToParent() && disallowTransferToPeers() && subAgents().isEmpty()) {
      return new SingleFlow();
    } else {
      return new AutoFlow();
    }
  }

  private void maybeSaveOutputToState(Event event, InvocationContext context) {
    if (outputKey().isPresent() && event.finalResponse() && event.content().isPresent()) {
      // Concatenate text from all parts.
      String result =
          event.content().flatMap(Content::parts).orElse(Collections.emptyList()).stream()
              .map(part -> part.text().orElse(""))
              .collect(Collectors.joining());

      // Update the session state directly. Won't be reflected in the event's EventActions object
      // itself, because events are immutable once created.
      context.session().state().put(outputKey().get(), result);
    }
  }

  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
    return llmFlow
        .run(invocationContext)
        .doOnNext(event -> maybeSaveOutputToState(event, invocationContext));
  }

  @Override
  protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
    return llmFlow
        .runLive(invocationContext)
        .doOnNext(event -> maybeSaveOutputToState(event, invocationContext));
  }

  public Optional<String> instruction() {
    return instruction;
  }

  public Optional<String> globalInstruction() {
    return globalInstruction;
  }

  public Optional<Model> model() {
    return model;
  }

  public boolean planning() {
    return planning;
  }

  public Optional<GenerateContentConfig> generateContentConfig() {
    return generateContentConfig;
  }

  public Optional<BaseExampleProvider> exampleProvider() {
    return exampleProvider;
  }

  public IncludeContents includeContents() {
    return includeContents;
  }

  public List<BaseTool> tools() {
    return tools;
  }

  public boolean disallowTransferToParent() {
    return disallowTransferToParent;
  }

  public boolean disallowTransferToPeers() {
    return disallowTransferToPeers;
  }

  public Optional<BeforeModelCallback> beforeModelCallback() {
    return beforeModelCallback;
  }

  public Optional<AfterModelCallback> afterModelCallback() {
    return afterModelCallback;
  }

  public Optional<BeforeToolCallback> beforeToolCallback() {
    return beforeToolCallback;
  }

  public Optional<AfterToolCallback> afterToolCallback() {
    return afterToolCallback;
  }

  public Optional<Schema> inputSchema() {
    return inputSchema;
  }

  public Optional<Schema> outputSchema() {
    return outputSchema;
  }

  public Optional<Executor> executor() {
    return executor;
  }

  public Optional<String> outputKey() {
    return outputKey;
  }

  public Model resolvedModel() {
    if (resolvedModel == null) {
      synchronized (this) {
        if (resolvedModel == null) {
          resolvedModel = resolveModelInternal();
        }
      }
    }
    return resolvedModel;
  }

  private Model resolveModelInternal() {
    // 1. Check if the model is defined locally for this agent.
    if (this.model.isPresent()) {
      if (this.model().isPresent()) {
        return this.model.get();
      }
    }
    // 2. If not defined locally, search ancestors.
    BaseAgent current = this.parentAgent();
    while (current != null) {
      if (current instanceof LlmAgent) {
        return ((LlmAgent) current).resolvedModel();
      }
      current = current.parentAgent();
    }
    throw new IllegalStateException("No model found for agent " + name() + " or its ancestors.");
  }
}
