package com.google.adk.events;

import static com.google.common.truth.Truth.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class EventTest {

  private static final FunctionCall FUNCTION_CALL =
      FunctionCall.builder().name("function_name").args(ImmutableMap.of("key", "value")).build();
  private static final Content CONTENT =
      Content.builder()
          .parts(ImmutableList.of(Part.builder().functionCall(FUNCTION_CALL).build()))
          .build();
  private static final EventActions EVENT_ACTIONS =
      EventActions.builder()
          .skipSummarization(true)
          .stateDelta(ImmutableMap.of("key", "value"))
          .artifactDelta(
              ImmutableMap.of("artifact_key", Part.builder().text("artifact_value").build()))
          .transferToAgent("agent_id")
          .escalate(true)
          .requestedAuthConfigs(
              ImmutableMap.of("auth_config_key", ImmutableMap.of("auth_key", "auth_value")))
          .build();
  private static final Event EVENT =
      Event.builder()
          .id("event_id")
          .invocationId("invocation_id")
          .author("agent")
          .content(CONTENT)
          .actions(EVENT_ACTIONS)
          .longRunningToolIds(ImmutableSet.of("tool_id"))
          .partial(true)
          .turnComplete(true)
          .errorCode("error_code")
          .errorMessage("error_message")
          .interrupted(true)
          .timestamp(123456789L)
          .build();

  @Test
  public void event_builder_works() {
    assertThat(EVENT.functionCalls()).containsExactly(FUNCTION_CALL);
    assertThat(EVENT.functionResponses()).isEmpty();
    assertThat(EVENT.longRunningToolIds().get()).containsExactly("tool_id");
    assertThat(EVENT.partial().get()).isTrue();
    assertThat(EVENT.turnComplete().get()).isTrue();
    assertThat(EVENT.errorCode()).hasValue("error_code");
    assertThat(EVENT.errorMessage()).hasValue("error_message");
    assertThat(EVENT.interrupted()).hasValue(true);
    assertThat(EVENT.timestamp()).isEqualTo(123456789L);
    assertThat(EVENT.actions()).isEqualTo(EVENT_ACTIONS);
  }

  @Test
  public void event_builder_fills_default_actions() {
    Event event =
        Event.builder().id("event_id").invocationId("invocation_id").author("agent").build();
    assertThat(event.id()).isEqualTo("event_id");
    assertThat(event.invocationId()).isEqualTo("invocation_id");
    assertThat(event.author()).isEqualTo("agent");
    assertThat(event.actions()).isEqualTo(EventActions.builder().build());
  }

  @Test
  public void event_builder_fills_default_timestamp() {
    long before = Instant.now().toEpochMilli();
    Event event =
        Event.builder().id("event_id").invocationId("invocation_id").author("agent").build();
    long after = Instant.now().toEpochMilli();
    assertThat(event.timestamp()).isAtLeast(before);
    assertThat(event.timestamp()).isAtMost(after);
  }

  @Test
  public void event_equals_works() {
    Event event1 =
        Event.builder()
            .id("event_id")
            .invocationId("invocation_id")
            .author("agent")
            .timestamp(123456789L)
            .build();
    Event event2 =
        Event.builder()
            .id("event_id")
            .invocationId("invocation_id")
            .author("agent")
            .timestamp(123456789L)
            .build();

    assertThat(event1).isEqualTo(event2);
  }

  @Test
  public void event_hashcode_works() {
    Event event1 =
        Event.builder()
            .id("event_id")
            .invocationId("invocation_id")
            .author("agent")
            .timestamp(123456789L)
            .build();
    Event event2 =
        Event.builder()
            .id("event_id")
            .invocationId("invocation_id")
            .author("agent")
            .timestamp(123456789L)
            .build();

    assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
  }

  @Test
  public void event_hashcode_works_with_map() {
    Event event1 =
        Event.builder()
            .id("event_id")
            .invocationId("invocation_id")
            .author("agent")
            .timestamp(123456789L)
            .build();
    Event event2 =
        Event.builder()
            .id("event_id_2")
            .invocationId("invocation_id")
            .author("agent")
            .timestamp(123456789L)
            .build();

    ImmutableMap<Event, String> map = ImmutableMap.of(event1, "e1", event2, "e2");
    assertThat(map).containsEntry(event1, "e1");
    assertThat(map).containsEntry(event2, "e2");
  }

  @Test
  public void event_json_serialization_works() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setSerializationInclusion(Include.NON_NULL);
    String json = EVENT.toJson();
    Event deserializedEvent = Event.fromJson(json);
    assertThat(deserializedEvent).isEqualTo(EVENT);
  }
}
