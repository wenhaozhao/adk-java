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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.adk.JsonBaseModel;
import com.google.adk.events.Event;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;

/** A {@link Session} object that encapsulates the {@link State} and {@link Event}s of a session. */
@JsonDeserialize(builder = Session.Builder.class)
public final class Session extends JsonBaseModel {
  private final String id;

  private final String appName;

  private final String userId;

  private final State state;

  /**
   * 要求必须是可修改的,无锁的ArrayList
   * <li>它是写可见的
   * <li>无锁要求是因为一些实现可能使用了 synchronized, 它对 virtual-thread 不友好
   */
  @NotNull private volatile CopyOnWriteArrayList<Event> events;

  private Instant lastUpdateTime;

  public static Builder builder(String id) {
    return new Builder(id);
  }

  /** Builder for {@link Session}. */
  public static final class Builder {
    private String id;
    private String appName;
    private String userId;
    private State state = new State(new ConcurrentHashMap<>());
    private List<Event> events = new ArrayList<>();
    private Instant lastUpdateTime = Instant.EPOCH;

    public Builder(String id) {
      this.id = id;
    }

    @JsonCreator
    private Builder() {}

    @CanIgnoreReturnValue
    @JsonProperty("id")
    public Builder id(String id) {
      this.id = id;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder state(State state) {
      this.state = state;
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("state")
    public Builder state(ConcurrentMap<String, Object> state) {
      this.state = new State(state);
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("appName")
    public Builder appName(String appName) {
      this.appName = appName;
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("userId")
    public Builder userId(String userId) {
      this.userId = userId;
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("events")
    public Builder events(List<Event> events) {
      this.events = events;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder lastUpdateTime(Instant lastUpdateTime) {
      this.lastUpdateTime = lastUpdateTime;
      return this;
    }

    @CanIgnoreReturnValue
    @JsonProperty("lastUpdateTime")
    public Builder lastUpdateTimeSeconds(double seconds) {
      long secs = (long) seconds;
      // Convert fractional part to nanoseconds
      long nanos = (long) ((seconds - secs) * Duration.ofSeconds(1).toNanos());
      this.lastUpdateTime = Instant.ofEpochSecond(secs, nanos);
      return this;
    }

    public Session build() {
      if (id == null) {
        throw new IllegalStateException("Session id is null");
      }
      return new Session(appName, userId, id, state, events, lastUpdateTime);
    }
  }

  @JsonProperty("id")
  public String id() {
    return id;
  }

  @JsonProperty("state")
  public ConcurrentMap<String, Object> state() {
    return state;
  }

  @JsonProperty("events")
  public List<Event> events() {
    return events;
  }

  public void setEvents(@NotNull List<Event> events) {
    if (events instanceof CopyOnWriteArrayList<Event> cow) {
      this.events = cow;
    } else {
      this.events = new CopyOnWriteArrayList<>(events);
    }
  }

  @JsonProperty("appName")
  public String appName() {
    return appName;
  }

  @JsonProperty("userId")
  public String userId() {
    return userId;
  }

  public void lastUpdateTime(Instant lastUpdateTime) {
    this.lastUpdateTime = lastUpdateTime;
  }

  public Instant lastUpdateTime() {
    return lastUpdateTime;
  }

  @JsonProperty("lastUpdateTime")
  public double getLastUpdateTimeAsDouble() {
    if (lastUpdateTime == null) {
      return 0.0;
    }
    long seconds = lastUpdateTime.getEpochSecond();
    int nanos = lastUpdateTime.getNano();
    return seconds + nanos / (double) Duration.ofSeconds(1).toNanos();
  }

  @Override
  public String toString() {
    return toJson();
  }

  public static Session fromJson(String json) {
    return fromJsonString(json, Session.class);
  }

  private Session(
      String appName,
      String userId,
      String id,
      State state,
      List<Event> events,
      Instant lastUpdateTime) {
    this.id = id;
    this.appName = appName;
    this.userId = userId;
    this.state = state;
    if (events instanceof CopyOnWriteArrayList<Event> cow) {
      this.events = cow;
    } else {
      this.events = new CopyOnWriteArrayList<>(events);
    }
    this.lastUpdateTime = lastUpdateTime;
  }
}
