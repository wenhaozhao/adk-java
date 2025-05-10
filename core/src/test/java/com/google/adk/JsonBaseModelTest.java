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

package com.google.adk;

import static com.google.common.truth.Truth.assertThat;

import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// TODO: once the Angular UI is expecting camelCase, switch serialization to that.
/** Tests for JSON serialization/deserialization of classes inheriting from JsonBaseModel. */
@RunWith(JUnit4.class)
public class JsonBaseModelTest {

  @Test
  public void eventSerialization_usesSnakeCase() {
    Event event =
        Event.builder()
            .id(Event.generateEventId())
            .invocationId("test-invocation-id")
            .author("user")
            .content(
                Content.builder().parts(ImmutableList.of(Part.fromText("Hello, world!"))).build())
            .actions(EventActions.builder().stateDelta(ImmutableMap.of("key", "value")).build())
            .partial(true)
            .turnComplete(false)
            .errorCode("TEST_ERROR")
            .errorMessage("This is a test error")
            .interrupted(true)
            .longRunningToolIds(ImmutableSet.of("tool_id_1", "tool_id_2"))
            .build();

    String json = event.toJson();

    // Basic checks for snake_case keys
    assertThat(json).contains("\"invocation_id\":");
    assertThat(json).contains("\"error_code\":");
    assertThat(json).contains("\"error_message\":");
    assertThat(json).contains("\"turn_complete\":");
    assertThat(json).contains("\"long_running_tool_ids\":");
    assertThat(json).contains("\"partial\":");
    assertThat(json).contains("\"interrupted\":");
    assertThat(json).contains("\"state_delta\":");
  }

  @Test
  public void eventDeserialization_handlesSnakeCase() {
    String json =
        "{"
            + "\"id\":\"test-id\","
            + "\"invocation_id\":\"test-invocation\","
            + "\"author\":\"agent\","
            + "\"content\":{\"parts\":[{\"text\":\"Response text\"}]},"
            + "\"partial\":false,"
            + "\"turn_complete\":true,"
            + "\"error_code\":null," // Test null handling
            + "\"error_message\":null," // same as above
            + "\"interrupted\":false,\"long_running_tool_ids\":[\"tool_id_3\"],"
            + "\"actions\":{\"state_delta\":{\"key\":\"value\"},\"artifact_delta\":{},\"requested_auth_configs\":{}},"
            + "\"timestamp\":1234567890}";

    Event event = Event.fromJson(json);

    assertThat(event).isNotNull();
    assertThat(event.id()).isEqualTo("test-id");
    assertThat(event.invocationId()).isEqualTo("test-invocation");
    assertThat(event.author()).isEqualTo("agent");
    assertThat(event.content()).isPresent();
    assertThat(event.content().get().parts()).isPresent();
    assertThat(event.content().get().parts().get()).hasSize(1);
    assertThat(event.content().get().parts().get().get(0).text()).hasValue("Response text");
    assertThat(event.actions()).isNotNull();
    assertThat(event.actions().stateDelta()).containsExactly("key", "value");
    assertThat(event.partial()).hasValue(false);
    assertThat(event.turnComplete()).hasValue(true);
    assertThat(event.errorCode()).isEqualTo(Optional.empty());
    assertThat(event.errorMessage()).isEqualTo(Optional.empty());
    assertThat(event.interrupted()).hasValue(false);
    assertThat(event.longRunningToolIds()).hasValue(ImmutableSet.of("tool_id_3"));
    assertThat(event.timestamp()).isEqualTo(1234567890L);
  }
}
