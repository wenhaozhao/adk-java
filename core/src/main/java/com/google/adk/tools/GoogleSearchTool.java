package com.google.adk.tools;

import com.google.adk.models.LlmRequest;
import com.google.common.collect.ImmutableList;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.GoogleSearchRetrieval;
import com.google.genai.types.Tool;
import io.reactivex.rxjava3.core.Completable;
import java.util.List;

/**
 * A built-in tool that is automatically invoked by Gemini 2 models to retrieve search results from
 * Google Search.
 *
 * <p>This tool operates internally within the model and does not require or perform local code
 * execution.
 */
public final class GoogleSearchTool extends BaseTool {

  public GoogleSearchTool() {
    super("google_search", "google_search");
  }

  @Override
  public Completable processLlmRequest(
      LlmRequest.Builder llmRequestBuilder, ToolContext toolContext) {

    GenerateContentConfig.Builder configBuilder =
        llmRequestBuilder
            .build()
            .config()
            .map(GenerateContentConfig::toBuilder)
            .orElse(GenerateContentConfig.builder());

    List<Tool> existingTools = configBuilder.build().tools().orElse(ImmutableList.of());
    ImmutableList.Builder<Tool> updatedToolsBuilder = ImmutableList.builder();
    updatedToolsBuilder.addAll(existingTools);

    String model = llmRequestBuilder.build().model().get();
    if (model != null && model.startsWith("gemini-1")) {
      if (!updatedToolsBuilder.build().isEmpty()) {
        System.out.println(configBuilder.build().tools().get());
        return Completable.error(
            new IllegalArgumentException(
                "Google search tool cannot be used with other tools in Gemini 1.x."));
      }
      updatedToolsBuilder.add(
          Tool.builder().googleSearchRetrieval(GoogleSearchRetrieval.builder().build()).build());
      configBuilder.tools(updatedToolsBuilder.build());
    } else if (model != null && model.startsWith("gemini-2")) {

      updatedToolsBuilder.add(Tool.builder().googleSearch(GoogleSearch.builder().build()).build());
      configBuilder.tools(updatedToolsBuilder.build());
    } else {
      return Completable.error(
          new IllegalArgumentException("Google search tool is not supported for model " + model));
    }

    llmRequestBuilder.config(configBuilder.build());
    return Completable.complete();
  }
}
