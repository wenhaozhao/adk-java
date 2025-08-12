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

package com.example;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.maven.AgentProvider;
import com.google.adk.tools.GoogleSearchTool;
import java.util.Map;

/** Example AgentProvider that creates simple agents for demonstration. */
public class SimpleAgentProvider implements AgentProvider {

  // Static instance for easy access
  public static final SimpleAgentProvider INSTANCE = new SimpleAgentProvider();

  @Override
  public Map<String, BaseAgent> getAgents() {
    return Map.of(
        "chat_assistant", createChatAssistant(),
        "search_agent", createSearchAgent(),
        "code_helper", createCodeHelper());
  }

  private BaseAgent createChatAssistant() {
    return LlmAgent.builder()
        .name("chat_assistant")
        .description("A friendly chat assistant")
        .model("gemini-2.0-flash")
        .instruction(
            "You are a helpful and friendly assistant. "
                + "Answer questions clearly and concisely. "
                + "Be encouraging and positive in your responses.")
        .build();
  }

  private BaseAgent createSearchAgent() {
    return LlmAgent.builder()
        .name("search_agent")
        .description("An agent that can search the web")
        .model("gemini-2.0-flash")
        .instruction(
            "You are a search assistant. "
                + "Use Google Search to find current information when users ask questions. "
                + "Provide accurate and up-to-date responses based on your search results.")
        .tools(new GoogleSearchTool())
        .build();
  }

  private BaseAgent createCodeHelper() {
    return LlmAgent.builder()
        .name("code_helper")
        .description("A coding assistant")
        .model("gemini-2.0-flash")
        .instruction(
            "You are a coding assistant. "
                + "Help users with programming questions, code reviews, and debugging. "
                + "Provide clear explanations and well-formatted code examples. "
                + "Support multiple programming languages including Java, Python, JavaScript, etc.")
        .build();
  }
}
