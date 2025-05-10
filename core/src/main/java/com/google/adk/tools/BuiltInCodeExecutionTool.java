package com.google.adk.tools;

import com.google.adk.models.LlmRequest;
import com.google.common.collect.ImmutableList;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Tool;
import com.google.genai.types.ToolCodeExecution;
import io.reactivex.rxjava3.core.Completable;
import java.util.List;

/**
 * A built-in code execution tool that is automatically invoked by Gemini 2 models.
 *
 * <p>This tool operates internally within the model and does not require or perform local code
 * execution.
 */
public final class BuiltInCodeExecutionTool extends BaseTool {

  public BuiltInCodeExecutionTool() {
    super("code_execution", "code_execution");
  }

  @Override
  public Completable processLlmRequest(
      LlmRequest.Builder llmRequestBuilder, ToolContext toolContext) {

    String model = llmRequestBuilder.build().model().get();
    if (model.isEmpty() || !model.startsWith("gemini-2")) {
      return Completable.error(
          new IllegalArgumentException("Code execution tool is not supported for model " + model));
    }
    GenerateContentConfig.Builder configBuilder =
        llmRequestBuilder
            .build()
            .config()
            .map(GenerateContentConfig::toBuilder)
            .orElse(GenerateContentConfig.builder());

    List<Tool> existingTools = configBuilder.build().tools().orElse(ImmutableList.of());
    ImmutableList.Builder<Tool> updatedToolsBuilder = ImmutableList.builder();
    updatedToolsBuilder.addAll(existingTools);
    updatedToolsBuilder.add(
        Tool.builder().codeExecution(ToolCodeExecution.builder().build()).build());
    configBuilder.tools(updatedToolsBuilder.build());
    llmRequestBuilder.config(configBuilder.build());
    return Completable.complete();
  }
}
