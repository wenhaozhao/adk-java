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

package com.google.adk.sessions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.JsonBaseModel;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GroundingMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.http.util.EntityUtils;

/** Connects to the managed Vertex AI Session Service. */
/** TODO: Use the genai HttpApiClient and ApiResponse methods once they are public. */
public final class VertexAiSessionService implements BaseSessionService {
  private final String project;
  private final String location;
  private final HttpApiClient apiClient;
  private String reasoningEngineId;
  private int maxRetryAttempts = 5;
  private Map<String, Object> sessionJsonMap;
  private final ObjectMapper objectMapper = JsonBaseModel.getMapper();
  private static final Logger logger = Logger.getLogger(VertexAiSessionService.class.getName());

  /**
   * Creates a new instance of the Vertex AI Session Service with a custom ApiClient for testing.
   */
  public VertexAiSessionService(String project, String location, HttpApiClient apiClient) {
    this.project = project;
    this.location = location;
    this.apiClient = apiClient;
  }

  public VertexAiSessionService(
      String project,
      String location,
      Optional<GoogleCredentials> credentials,
      Optional<HttpOptions> httpOptions) {
    this.project = project;
    this.location = location;
    this.apiClient =
        new HttpApiClient(
            Optional.of(this.project), Optional.of(this.location), credentials, httpOptions);
  }

  public JsonNode getJsonResponse(ApiResponse apiResponse) {
    try {
      return objectMapper.readTree(EntityUtils.toString(apiResponse.getEntity()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public Single<Session> createSession(
      String appName,
      String userId,
      @Nullable Map<String, Object> state,
      @Nullable String sessionId) {

    reasoningEngineId = parseReasoningEngineId(appName);
    sessionJsonMap = new HashMap<>();
    sessionJsonMap.put("userId", userId);
    if (state != null) {
      sessionJsonMap.put("sessionState", state);
    }

    ApiResponse apiResponse;
    try {
      apiResponse =
          apiClient.request(
              "POST",
              "reasoningEngines/" + reasoningEngineId + "/sessions",
              objectMapper.writeValueAsString(sessionJsonMap));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    logger.log(Level.INFO, "Create Session response " + apiResponse.getEntity());
    String sessionName = "";
    String operationId = "";
    String sessId = sessionId == null ? "" : sessionId;
    if (apiResponse.getEntity() != null) {
      JsonNode jsonResponse = getJsonResponse(apiResponse);
      sessionName = (String) jsonResponse.get("name").asText();
      List<String> parts = Splitter.on('/').splitToList(sessionName);
      sessId = parts.get(parts.size() - 3);
      operationId = parts.get(parts.size() - 1);
    }
    while (maxRetryAttempts >= 0) {
      ApiResponse lroResponse = apiClient.request("GET", "operations/" + operationId, "");
      JsonNode jsonResponse = getJsonResponse(lroResponse);
      if (jsonResponse.get("done") != null) {
        break;
      }
      try {
        TimeUnit.SECONDS.sleep(1);
        maxRetryAttempts -= 1;
      } catch (InterruptedException e) {
        logger.log(Level.WARNING, "Error during sleep", e);
      }
    }

    ApiResponse getSessionApiResponse =
        apiClient.request(
            "GET", "reasoningEngines/" + reasoningEngineId + "/sessions/" + sessId, "");
    JsonNode getSessionResponseMap = getJsonResponse(getSessionApiResponse);
    Instant updateTimestamp =
        Instant.parse((String) getSessionResponseMap.get("updateTime").asText());
    Map<String, Object> sessionState;
    try {
      sessionState =
          objectMapper.readValue(
              getSessionResponseMap.get("sessionState").toString(),
              new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException e) {
      sessionState = new HashMap<>();
    }
    return Single.just(
        Session.builder(sessId)
            .appName(appName)
            .userId(userId)
            .lastUpdateTime(updateTimestamp)
            .state(sessionState)
            .build());
  }

  @Override
  public Single<ListSessionsResponse> listSessions(String appName, String userId) {
    reasoningEngineId = parseReasoningEngineId(appName);

    ApiResponse apiResponse =
        apiClient.request(
            "GET",
            "reasoningEngines/" + reasoningEngineId + "/sessions?filter=user_id=" + userId,
            "");

    // Handles empty response case
    if (apiResponse.getEntity() == null) {
      return Single.just(ListSessionsResponse.builder().build());
    }

    JsonNode listSessionsResponseMap = getJsonResponse(apiResponse);
    List<Map<String, Object>> apiSessions;
    try {
      apiSessions =
          objectMapper.readValue(
              listSessionsResponseMap.get("sessions").toString(),
              new TypeReference<List<Map<String, Object>>>() {});
    } catch (JsonProcessingException e) {
      apiSessions = new ArrayList<>();
    }

    List<Session> sessions = new ArrayList<>();
    for (Map<String, Object> apiSession : apiSessions) {
      String name = (String) apiSession.get("name");
      List<String> parts = Splitter.on('/').splitToList(name);
      String sessionId = parts.get(parts.size() - 1);
      Instant updateTimestamp = Instant.parse((String) apiSession.get("updateTime"));
      Session session =
          Session.builder(sessionId)
              .appName(appName)
              .userId(userId)
              .state(new HashMap<>())
              .lastUpdateTime(updateTimestamp)
              .build();
      sessions.add(session);
    }
    return Single.just(ListSessionsResponse.builder().sessions(sessions).build());
  }

  @Override
  public Single<ListEventsResponse> listEvents(String appName, String userId, String sessionId) {
    reasoningEngineId = parseReasoningEngineId(appName);
    ApiResponse apiResponse =
        apiClient.request(
            "GET",
            "reasoningEngines/" + reasoningEngineId + "/sessions/" + sessionId + "/events",
            "");

    logger.log(Level.INFO, "List events response " + apiResponse);

    if (apiResponse.getEntity() == null) {
      return Single.just(ListEventsResponse.builder().build());
    }

    JsonNode getEventsResponseMap = getJsonResponse(apiResponse);
    List<Map<String, Object>> listEventsResponse;
    try {
      listEventsResponse =
          objectMapper.readValue(
              getEventsResponseMap.get("sessionEvents").toString(),
              new TypeReference<List<Map<String, Object>>>() {});
    } catch (JsonProcessingException e) {
      listEventsResponse = new ArrayList<>();
    }

    List<Event> events = new ArrayList<>();
    for (Map<String, Object> event : listEventsResponse) {
      events.add(fromApiEvent(event));
    }
    return Single.just(ListEventsResponse.builder().events(events).build());
  }

  @Override
  public Maybe<Session> getSession(
      String appName, String userId, String sessionId, Optional<GetSessionConfig> config) {
    reasoningEngineId = parseReasoningEngineId(appName);
    ApiResponse apiResponse =
        apiClient.request(
            "GET", "reasoningEngines/" + reasoningEngineId + "/sessions/" + sessionId, "");
    JsonNode getSessionResponseMap = getJsonResponse(apiResponse);

    String name = (String) getSessionResponseMap.get("name").asText();
    List<String> parts = Splitter.on('/').splitToList(name);
    String sessId = parts.get(parts.size() - 1);

    Instant updateTimestamp =
        Instant.parse((String) getSessionResponseMap.get("updateTime").asText());
    Map<String, Object> sessionState;
    try {
      sessionState =
          objectMapper.readValue(
              getSessionResponseMap.get("sessionState").toString(),
              new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException e) {
      sessionState = new HashMap<>();
    }
    Session session =
        Session.builder(sessId)
            .appName(appName)
            .userId(userId)
            .lastUpdateTime(updateTimestamp)
            .state(sessionState)
            .build();

    ApiResponse listEventsApiResponse =
        apiClient.request(
            "GET",
            "reasoningEngines/" + reasoningEngineId + "/sessions/" + sessionId + "/events",
            "");

    if (listEventsApiResponse.getEntity() == null) {
      return Maybe.just(session);
    }

    JsonNode getEventsResponseMap = getJsonResponse(listEventsApiResponse);
    List<Map<String, Object>> listEventsResponse;
    try {
      listEventsResponse =
          objectMapper.readValue(
              getEventsResponseMap.get("sessionEvents").toString(),
              new TypeReference<List<Map<String, Object>>>() {});
    } catch (JsonProcessingException e) {
      listEventsResponse = new ArrayList<>();
    }

    List<Event> events = new ArrayList<>();
    for (Map<String, Object> event : listEventsResponse) {
      events.add(fromApiEvent(event));
    }
    events.removeIf(event -> Instant.ofEpochMilli(event.timestamp()).isAfter(updateTimestamp));
    events.sort(
        (event1, event2) ->
            Instant.ofEpochMilli(event1.timestamp())
                .compareTo(Instant.ofEpochMilli(event2.timestamp())));

    if (config.isPresent()) {
      if (config.get().numRecentEvents().isPresent()) {
        int numRecentEvents = config.get().numRecentEvents().get();
        if (events.size() > numRecentEvents) {
          events = events.subList(events.size() - numRecentEvents, events.size());
        }
      } else if (config.get().afterTimestamp().isPresent()) {
        Instant afterTimestamp = config.get().afterTimestamp().get();
        int i = events.size() - 1;
        while (i >= 0) {
          if (Instant.ofEpochMilli(events.get(i).timestamp()).isBefore(afterTimestamp)) {
            break;
          }
          i -= 1;
        }
        if (i >= 0) {
          events = events.subList(i, events.size());
        }
      }
    }

    session =
        Session.builder(sessId)
            .appName(appName)
            .userId(userId)
            .lastUpdateTime(updateTimestamp)
            .state(sessionState)
            .events(events)
            .build();
    return Maybe.just(session);
  }

  @Override
  public Completable deleteSession(String appName, String userId, String sessionId) {
    reasoningEngineId = parseReasoningEngineId(appName);
    ApiResponse unused =
        apiClient.request(
            "DELETE", "reasoningEngines/" + reasoningEngineId + "/sessions/" + sessionId, "");
    return Completable.complete();
  }

  public static String parseReasoningEngineId(String appName) {
    if (appName.matches("\\d+")) {
      return appName;
    }

    Matcher matcher = APP_NAME_PATTERN.matcher(appName);

    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "App name "
              + appName
              + " is not valid. It should either be the full"
              + " ReasoningEngine resource name, or the reasoning engine id.");
    }

    return matcher.group(matcher.groupCount());
  }

  @SuppressWarnings("unchecked")
  public Event fromApiEvent(Map<String, Object> apiEvent) {
    EventActions eventActions = new EventActions();
    if (apiEvent.get("actions") != null) {
      Map<String, Object> actionsMap = (Map<String, Object>) apiEvent.get("actions");
      eventActions.setSkipSummarization(
          Optional.ofNullable(actionsMap.get("skipSummarization")).map(value -> (Boolean) value));
      eventActions.setStateDelta(
          actionsMap.get("stateDelta") != null
              ? (Map<String, Object>) actionsMap.get("stateDelta")
              : new HashMap<>());
      eventActions.setArtifactDelta(
          actionsMap.get("artifactDelta") != null
              ? (Map<String, Part>) actionsMap.get("artifactDelta")
              : new HashMap<>());
      eventActions.setTransferToAgent(
          actionsMap.get("transferAgent") != null
              ? (String) actionsMap.get("transferAgent")
              : null);
      eventActions.setEscalate(
          Optional.ofNullable(actionsMap.get("escalate")).map(value -> (Boolean) value));
      eventActions.setRequestedAuthConfigs(
          actionsMap.get("requestedAuthConfigs") != null
              ? (Map<String, Map<String, Object>>) actionsMap.get("requestedAuthConfigs")
              : new HashMap<>());
    }

    Event event =
        Event.builder()
            .id(
                (String)
                    Iterables.get(
                        Splitter.on('/').split(apiEvent.get("name").toString()),
                        apiEvent.get("name").toString().split("/").length - 1))
            .invocationId((String) apiEvent.get("invocationId"))
            .author((String) apiEvent.get("author"))
            .actions(eventActions)
            // TODO(b/414263934): Currently works for text parts. Make it work with custom parts.
            .content(
                Optional.ofNullable(apiEvent.get("content"))
                    .map(
                        content -> {
                          List<Part> parts = new ArrayList<>();
                          List<Object> partsList =
                              (List<Object>) ((Map<String, Object>) content).get("parts");
                          for (Object partObj : partsList) {
                            Map<?, ?> partMap = (Map<?, ?>) partObj;
                            String text = (String) partMap.get("text");
                            parts.add(Part.fromText(text));
                          }
                          return Content.builder().parts(parts).build();
                        })
                    .orElse(null))
            .timestamp(Instant.parse((String) apiEvent.get("timestamp")).toEpochMilli())
            .errorCode(
                Optional.ofNullable(apiEvent.get("errorCode"))
                    .map(value -> new FinishReason((String) value)))
            .errorMessage(
                Optional.ofNullable(apiEvent.get("errorMessage")).map(value -> (String) value))
            .branch(Optional.ofNullable(apiEvent.get("branch")).map(value -> (String) value))
            .build();
    // TODO(b/414263934): Add Event branch and grounding metadata for python parity.
    if (apiEvent.get("eventMetadata") != null) {
      Map<String, Object> eventMetadata = (Map<String, Object>) apiEvent.get("eventMetadata");
      List<String> longRunningToolIdsList = (List<String>) eventMetadata.get("longRunningToolIds");

      GroundingMetadata groundingMetadata = null;
      Object rawGroundingMetadata = eventMetadata.get("groundingMetadata");
      if (rawGroundingMetadata != null) {
        groundingMetadata =
            objectMapper.convertValue(rawGroundingMetadata, GroundingMetadata.class);
      }

      event =
          event.toBuilder()
              .partial((Boolean) eventMetadata.get("partial"))
              .turnComplete((Boolean) eventMetadata.get("turnComplete"))
              .interrupted((Boolean) eventMetadata.get("interrupted"))
              .branch((String) eventMetadata.get("branch"))
              .groundingMetadata(groundingMetadata)
              .longRunningToolIds(
                  longRunningToolIdsList != null ? new HashSet<>(longRunningToolIdsList) : null)
              .build();
    }
    return event;
  }

  private static final Pattern APP_NAME_PATTERN =
      Pattern.compile(
          "^projects/([a-zA-Z0-9-_]+)/locations/([a-zA-Z0-9-_]+)/reasoningEngines/(\\d+)$");
}
