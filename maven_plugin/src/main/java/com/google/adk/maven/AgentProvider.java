/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.adk.maven;

import com.google.adk.agents.BaseAgent;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Interface for providing agents to the ADK Web Server.
 *
 * <p>Users implement this interface to register their custom agents with the web server. The
 * implementation should return a map where keys are agent names (used as app names in the UI) and
 * values are the corresponding BaseAgent instances.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public class MyAgentProvider implements AgentProvider {
 *   public static final MyAgentProvider INSTANCE = new MyAgentProvider(); @Override
 *   public Map<String, BaseAgent> getAgents() {
 *     return Map.of("chat_bot", createChatBot(), "code_assistant", createCodeAssistant());
 *   }
 * }
 * }</pre>
 *
 * <p>Then use with Maven plugin:
 *
 * <pre>{@code
 * mvn google-adk:web -Dagents=com.acme.MyAgentProvider.INSTANCE
 * }</pre>
 *
 * TODO: Add config-based agent registration in the future.
 */
public interface AgentProvider {

  /**
   * Returns a map of agent names to BaseAgent instances.
   *
   * @return Map where keys are agent names (app names) and values are BaseAgent instances. Must not
   *     return null - return an empty map if no agents are available.
   */
  @Nonnull
  Map<String, BaseAgent> getAgents();
}
