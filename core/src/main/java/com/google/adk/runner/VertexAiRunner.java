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

package com.google.adk.runner;

import com.google.adk.agents.BaseAgent;
import com.google.adk.artifacts.GcsArtifactService;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.sessions.VertexAiSessionService;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.genai.types.HttpOptions;
import java.util.Optional;

/** The class for the Vertex AI runner, using Vertex AI session services. */
public class VertexAiRunner extends Runner {

  public VertexAiRunner(
      BaseAgent agent,
      String project,
      String location,
      Optional<GoogleCredentials> credentials,
      Optional<HttpOptions> httpOptions) {
    super(
        agent,
        agent.name(),
        new InMemoryArtifactService(),
        new VertexAiSessionService(project, location, credentials, httpOptions));
  }

  public VertexAiRunner(
      BaseAgent agent,
      String project,
      String location,
      Optional<GoogleCredentials> credentials,
      Optional<HttpOptions> httpOptions,
      String bucketName,
      Storage storageClient) {
    super(
        agent,
        agent.name(),
        new GcsArtifactService(bucketName, storageClient),
        new VertexAiSessionService(project, location, credentials, httpOptions));
  }
}
