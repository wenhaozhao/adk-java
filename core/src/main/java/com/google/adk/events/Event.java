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

package com.google.adk.events;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.adk.JsonBaseModel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.genai.types.*;
import java.time.Instant;
import java.util.*;
import javax.annotation.Nullable;

// TODO - b/413761119 update Agent.java when resolved.
/** Represents an event in a session. */
@JsonDeserialize(builder = Event.Builder.class)
public class Event extends JsonBaseModel {

  private String id;
  private String invocationId;
  private String author;
  private Optional<Content> content = Optional.empty();
  private EventActions actions;
  private Optional<Set<String>> longRunningToolIds = Optional.empty();
  private Optional<Boolean> partial = Optional.empty();
  private Optional<Boolean> turnComplete = Optional.empty();
  private Optional<FinishReason> errorCode = Optional.empty();
  private Optional<String> errorMessage = Optional.empty();
  private Optional<Boolean> interrupted = Optional.empty();
  private Optional<String> branch = Optional.empty();
  private Optional<GroundingMetadata> groundingMetadata = Optional.empty();
  private long timestamp;
  private HashMap<String, Object> metadata = new HashMap<>();
  private HashSet<String> tags = new HashSet<>();

  private Event() {}

  public static String generateEventId() {
    return UUID.randomUUID().toString();
  }

  /** The event id. */
  @JsonProperty("id")
  public String id() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  /** Id of the invocation that this event belongs to. */
  @JsonProperty("invocationId")
  public String invocationId() {
    return invocationId;
  }

  public void setInvocationId(String invocationId) {
    this.invocationId = invocationId;
  }

  /** The author of the event, it could be the name of the agent or "user" literal. */
  @JsonProperty("author")
  public String author() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  @JsonProperty("content")
  public Optional<Content> content() {
    return content;
  }

  public void setContent(Optional<Content> content) {
    this.content = content;
  }

  @JsonProperty("actions")
  public EventActions actions() {
    return actions;
  }

  public void setActions(EventActions actions) {
    this.actions = actions;
  }

  /**
   * Set of ids of the long running function calls. Agent client will know from this field about
   * which function call is long running.
   */
  @JsonProperty("longRunningToolIds")
  public Optional<Set<String>> longRunningToolIds() {
    return longRunningToolIds;
  }

  public void setLongRunningToolIds(Optional<Set<String>> longRunningToolIds) {
    this.longRunningToolIds = longRunningToolIds;
  }

  /**
   * partial is true for incomplete chunks from the LLM streaming response. The last chunk's partial
   * is False.
   */
  @JsonProperty("partial")
  public Optional<Boolean> partial() {
    return partial;
  }

  public void setPartial(Optional<Boolean> partial) {
    this.partial = partial;
  }

  @JsonProperty("turnComplete")
  public Optional<Boolean> turnComplete() {
    return turnComplete;
  }

  public void setTurnComplete(Optional<Boolean> turnComplete) {
    this.turnComplete = turnComplete;
  }

  @JsonProperty("errorCode")
  public Optional<FinishReason> errorCode() {
    return errorCode;
  }

  public void setErrorCode(Optional<FinishReason> errorCode) {
    this.errorCode = errorCode;
  }

  @JsonProperty("errorMessage")
  public Optional<String> errorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(Optional<String> errorMessage) {
    this.errorMessage = errorMessage;
  }

  @JsonProperty("interrupted")
  public Optional<Boolean> interrupted() {
    return interrupted;
  }

  public void setInterrupted(Optional<Boolean> interrupted) {
    this.interrupted = interrupted;
  }

  /**
   * The branch of the event. The format is like agent_1.agent_2.agent_3, where agent_1 is the
   * parent of agent_2, and agent_2 is the parent of agent_3. Branch is used when multiple sub-agent
   * shouldn't see their peer agents' conversation history.
   */
  @JsonProperty("branch")
  public Optional<String> branch() {
    return branch;
  }

  /**
   * Sets the branch for this event.
   *
   * <p>Format: agentA.agentB.agentC — shows hierarchy of nested agents.
   *
   * @param branch Branch identifier.
   */
  public void branch(@Nullable String branch) {
    this.branch = Optional.ofNullable(branch);
  }

  public void branch(Optional<String> branch) {
    this.branch = branch;
  }

  /** The grounding metadata of the event. */
  @JsonProperty("groundingMetadata")
  public Optional<GroundingMetadata> groundingMetadata() {
    return groundingMetadata;
  }

  public void setGroundingMetadata(Optional<GroundingMetadata> groundingMetadata) {
    this.groundingMetadata = groundingMetadata;
  }

  /** The timestamp of the event. */
  @JsonProperty("timestamp")
  public long timestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  @JsonProperty("metadata")
  public Map<String, Object> metadata() {
    return this.metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = new HashMap<>(metadata == null ? new HashMap<>() : metadata);
  }

  public Event add_metadata(String key, Object value) {
    this.metadata.put(key, value);
    return this;
  }

  public Event add_metadata(Map<String, Object> metadata) {
    this.metadata.putAll(metadata);
    return this;
  }

  @JsonProperty("tags")
  public Set<String> tags() {
    return this.tags;
  }

  public void setTags(Set<String> tags) {
    if (tags instanceof HashSet<String> new_tags) {
      this.tags = new_tags;
    } else {
      this.tags = new HashSet<>();
    }
  }

  public Event add_tags(String... tags) {
    Event.inner_add_tags(this.tags, Arrays.asList(tags));
    return this;
  }

  static void inner_add_tags(HashSet<String> tags, Collection<String> new_tags) {
    tags.addAll(new_tags.stream().map(String::toLowerCase).toList());
  }

  public boolean is_tagged(String tag) {
    return this.tags.contains(tag.toLowerCase());
  }

  /** Returns all function calls from this event. */
  @JsonIgnore
  public final ImmutableList<FunctionCall> functionCalls() {
    return content().flatMap(Content::parts).stream()
        .flatMap(List::stream)
        .flatMap(part -> part.functionCall().stream())
        .collect(toImmutableList());
  }

  /** Returns all function responses from this event. */
  @JsonIgnore
  public final ImmutableList<FunctionResponse> functionResponses() {
    return content().flatMap(Content::parts).stream()
        .flatMap(List::stream)
        .flatMap(part -> part.functionResponse().stream())
        .collect(toImmutableList());
  }

  /** Returns whether the event has a trailing code execution result. */
  @JsonIgnore
  public final boolean hasTrailingCodeExecutionResult() {
    return content()
        .flatMap(Content::parts)
        .filter(parts -> !parts.isEmpty())
        .map(parts -> Iterables.getLast(parts))
        .flatMap(part -> part.codeExecutionResult())
        .isPresent();
  }

  /** Returns true if this is a final response. */
  @JsonIgnore
  public final boolean finalResponse() {
    if (actions().skipSummarization().orElse(false)
        || (longRunningToolIds().isPresent() && !longRunningToolIds().get().isEmpty())) {
      return true;
    }
    return functionCalls().isEmpty()
        && functionResponses().isEmpty()
        && !partial().orElse(false)
        && !hasTrailingCodeExecutionResult();
  }

  /**
   * Converts the event content into a readable string.
   *
   * <p>Includes text, function calls, and responses.
   *
   * @return Stringified content.
   */
  public final String stringifyContent() {
    StringBuilder sb = new StringBuilder();
    content().flatMap(Content::parts).stream()
        .flatMap(List::stream)
        .forEach(
            part -> {
              part.text().ifPresent(sb::append);
              part.functionCall()
                  .ifPresent(functionCall -> sb.append("Function Call: ").append(functionCall));
              part.functionResponse()
                  .ifPresent(
                      functionResponse ->
                          sb.append("Function Response: ").append(functionResponse));
            });
    return sb.toString();
  }

  /** Builder for {@link Event}. */
  public static class Builder {

    private String id;
    private String invocationId;
    private String author;
    private Optional<Content> content = Optional.empty();
    private EventActions actions;
    private Optional<Set<String>> longRunningToolIds = Optional.empty();
    private Optional<Boolean> partial = Optional.empty();
    private Optional<Boolean> turnComplete = Optional.empty();
    private Optional<FinishReason> errorCode = Optional.empty();
    private Optional<String> errorMessage = Optional.empty();
    private Optional<Boolean> interrupted = Optional.empty();
    private Optional<String> branch = Optional.empty();
    private Optional<GroundingMetadata> groundingMetadata = Optional.empty();
    private Optional<Long> timestamp = Optional.empty();
    private HashSet<String> tags = new HashSet<>();
    private Map<String, Object> metadata = new HashMap<>();

    @JsonCreator
    private static Builder create() {
      return new Builder();
    }

    @CanIgnoreReturnValue
    @JsonProperty("id")
    public Builder id(String value) {
      this.id = value;
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("invocationId")
    public Builder invocationId(String value) {
      this.invocationId = value;
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("author")
    public Builder author(String value) {
      this.author = value;
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("content")
    public Builder content(@Nullable Content value) {
      this.content = Optional.ofNullable(value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder content(Optional<Content> value) {
      this.content = value;
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("actions")
    public Builder actions(EventActions value) {
      this.actions = value;
      return this;
    }

    Optional<EventActions> actions() {
      return Optional.ofNullable(actions);
    }

    @CanIgnoreReturnValue
    @JsonProperty("longRunningToolIds")
    public Builder longRunningToolIds(@Nullable Set<String> value) {
      this.longRunningToolIds = Optional.ofNullable(value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder longRunningToolIds(Optional<Set<String>> value) {
      this.longRunningToolIds = value;
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("partial")
    public Builder partial(@Nullable Boolean value) {
      this.partial = Optional.ofNullable(value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder partial(Optional<Boolean> value) {
      this.partial = value;
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("turnComplete")
    public Builder turnComplete(@Nullable Boolean value) {
      this.turnComplete = Optional.ofNullable(value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder turnComplete(Optional<Boolean> value) {
      this.turnComplete = value;
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("errorCode")
    public Builder errorCode(@Nullable FinishReason value) {
      this.errorCode = Optional.ofNullable(value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder errorCode(Optional<FinishReason> value) {
      this.errorCode = value;
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("errorMessage")
    public Builder errorMessage(@Nullable String value) {
      this.errorMessage = Optional.ofNullable(value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder errorMessage(Optional<String> value) {
      this.errorMessage = value;
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("interrupted")
    public Builder interrupted(@Nullable Boolean value) {
      this.interrupted = Optional.ofNullable(value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder interrupted(Optional<Boolean> value) {
      this.interrupted = value;
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("timestamp")
    public Builder timestamp(long value) {
      this.timestamp = Optional.of(value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder timestamp(Optional<Long> value) {
      this.timestamp = value;
      return this;
    }

    // Getter for builder's timestamp, used in build()
    Optional<Long> timestamp() {
      return timestamp;
    }

    @CanIgnoreReturnValue
    @JsonProperty("branch")
    public Builder branch(@Nullable String value) {
      this.branch = Optional.ofNullable(value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder branch(Optional<String> value) {
      this.branch = value;
      return this;
    }

    // Getter for builder's branch, used in build()
    Optional<String> branch() {
      return branch;
    }

    @CanIgnoreReturnValue
    @JsonProperty("groundingMetadata")
    public Builder groundingMetadata(@Nullable GroundingMetadata value) {
      this.groundingMetadata = Optional.ofNullable(value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder groundingMetadata(Optional<GroundingMetadata> value) {
      this.groundingMetadata = value;
      return this;
    }

    Optional<GroundingMetadata> groundingMetadata() {
      return groundingMetadata;
    }

    @CanIgnoreReturnValue
    public Builder tags(Set<String> tags) {
      if (tags instanceof HashSet<String> new_tags) {
        this.tags = new_tags;
      } else {
        this.tags = new HashSet<>();
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder add_tags(Set<String> tags) {
      if (tags != null) {
        Event.inner_add_tags(this.tags, tags);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder add_tags(Set<String> tags, boolean overwrite) {
      this.tags = new HashSet<>();
      if (tags != null) {
        Event.inner_add_tags(this.tags, tags);
      }
      return this;
    }

    Set<String> tags() {
      return tags;
    }

    @CanIgnoreReturnValue
    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = metadata == null ? new HashMap<>() : metadata;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder add_metadata(Map<String, Object> metadata) {
      if (metadata != null) {
        this.metadata.putAll(metadata);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder add_metadata(String key, Object value) {
      if (value == null) {
        this.metadata.remove(key);
      } else {
        this.metadata.put(key, value);
      }
      return this;
    }

    Map<String, Object> metadata() {
      return metadata;
    }

    public Event build() {
      Event event = new Event();
      event.setId(id);
      event.setInvocationId(invocationId);
      event.setAuthor(author);
      event.setContent(content);
      event.setLongRunningToolIds(longRunningToolIds);
      event.setPartial(partial);
      event.setTurnComplete(turnComplete);
      event.setErrorCode(errorCode);
      event.setErrorMessage(errorMessage);
      event.setInterrupted(interrupted);
      event.branch(branch);
      event.setGroundingMetadata(groundingMetadata);
      event.setTags(tags);
      event.setMetadata(metadata);

      event.setActions(actions().orElse(EventActions.builder().build()));
      event.setTimestamp(timestamp().orElse(Instant.now().toEpochMilli()));
      return event;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Parses an event from a JSON string. */
  public static Event fromJson(String json) {
    return fromJsonString(json, Event.class);
  }

  /** Creates a builder pre-filled with this event's values. */
  public Builder toBuilder() {
    Builder builder =
        new Builder()
            .id(this.id)
            .invocationId(this.invocationId)
            .author(this.author)
            .content(this.content)
            .actions(this.actions)
            .longRunningToolIds(this.longRunningToolIds)
            .partial(this.partial)
            .turnComplete(this.turnComplete)
            .errorCode(this.errorCode)
            .errorMessage(this.errorMessage)
            .interrupted(this.interrupted)
            .branch(this.branch)
            .groundingMetadata(this.groundingMetadata)
            .tags(new HashSet<>(this.tags))
            .metadata(new HashMap<>(this.metadata));
    if (this.timestamp != 0) {
      builder.timestamp(this.timestamp);
    }
    return builder;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Event other)) {
      return false;
    }
    return timestamp == other.timestamp
        && Objects.equals(id, other.id)
        && Objects.equals(invocationId, other.invocationId)
        && Objects.equals(author, other.author)
        && Objects.equals(content, other.content)
        && Objects.equals(actions, other.actions)
        && Objects.equals(longRunningToolIds, other.longRunningToolIds)
        && Objects.equals(partial, other.partial)
        && Objects.equals(turnComplete, other.turnComplete)
        && Objects.equals(errorCode, other.errorCode)
        && Objects.equals(errorMessage, other.errorMessage)
        && Objects.equals(interrupted, other.interrupted)
        && Objects.equals(branch, other.branch)
        && Objects.equals(groundingMetadata, other.groundingMetadata);
  }

  @Override
  public String toString() {
    return toJson();
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        invocationId,
        author,
        content,
        actions,
        longRunningToolIds,
        partial,
        turnComplete,
        errorCode,
        errorMessage,
        interrupted,
        branch,
        groundingMetadata,
        timestamp);
  }
}
