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

package com.google.adk.agents;

import static com.google.adk.testing.TestUtils.createInvocationContext;
import static com.google.adk.testing.TestUtils.createLlmResponse;
import static com.google.adk.testing.TestUtils.createTestAgent;
import static com.google.adk.testing.TestUtils.createTestAgentBuilder;
import static com.google.adk.testing.TestUtils.createTestLlm;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.adk.events.Event;
import com.google.adk.testing.TestLlm;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Agent}. */
@RunWith(JUnit4.class)
public final class AgentTest {

  @Test
  public void testRun_withNoCallbacks() {
    Content modelContent = Content.fromParts(Part.fromText("Real LLM response"));
    TestLlm testLlm = createTestLlm(createLlmResponse(modelContent));
    Agent agent = createTestAgent(testLlm);
    InvocationContext invocationContext = createInvocationContext(agent);

    List<Event> events = agent.runAsync(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(1);
    assertThat(getOnlyElement(events).content()).hasValue(modelContent);
  }

  @Test
  public void testRun_withBeforeAgentCallback() {
    Content modelContent = Content.fromParts(Part.fromText("Real LLM response"));
    Content beforeAgentContent = Content.fromParts(Part.fromText("before agent content"));
    TestLlm testLlm = createTestLlm(createLlmResponse(modelContent));
    Agent agent =
        createTestAgentBuilder(testLlm)
            .beforeAgentCallback(context -> Maybe.just(beforeAgentContent))
            .build();
    InvocationContext invocationContext = createInvocationContext(agent);

    List<Event> events = agent.runAsync(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(1);
    assertThat(getOnlyElement(events).content()).hasValue(beforeAgentContent);
  }

  @Test
  public void testRun_withAfterAgentCallback() {
    Content modelContent = Content.fromParts(Part.fromText("Real LLM response"));
    Content afterAgentContent = Content.fromParts(Part.fromText("after agent content"));
    TestLlm testLlm = createTestLlm(createLlmResponse(modelContent));
    Agent agent =
        createTestAgentBuilder(testLlm)
            .afterAgentCallback(context -> Maybe.just(afterAgentContent))
            .build();
    InvocationContext invocationContext = createInvocationContext(agent);

    List<Event> events = agent.runAsync(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(2);
    assertThat(events.get(0).content()).hasValue(modelContent);
    assertThat(events.get(1).content()).hasValue(afterAgentContent);
  }

  @Test
  public void testRun_agentCallback_modifyStateAndOverrideResponse() {
    Content modelContent = Content.fromParts(Part.fromText("Real LLM response"));
    TestLlm testLlm = createTestLlm(createLlmResponse(modelContent));

    Agent agent =
        createTestAgentBuilder(testLlm)
            .beforeAgentCallback(
                callbackContext -> {
                  Object unused = callbackContext.state().put("before_key", "before_value");
                  return Maybe.empty();
                })
            .afterAgentCallback(
                callbackContext -> {
                  Object unused = callbackContext.state().put("after_key", "after_value");
                  return Maybe.just(Content.fromParts(Part.fromText("after agent content")));
                })
            .build();
    InvocationContext invocationContext = createInvocationContext(agent);

    List<Event> events = agent.runAsync(invocationContext).toList().blockingGet();
    Map<String, Object> finalState = invocationContext.session().state();

    assertThat(events).hasSize(3);
    assertThat(events.get(0).content().flatMap(Content::parts)).isEmpty();
    assertThat(events.get(0).actions().stateDelta()).containsExactly("before_key", "before_value");
    assertThat(events.get(1).content()).hasValue(modelContent);
    assertThat(events.get(1).actions().stateDelta()).isEmpty();
    assertThat(events.get(2).content().get().parts().get().get(0).text())
        .hasValue("after agent content");
    assertThat(events.get(2).actions().stateDelta()).containsExactly("after_key", "after_value");
    assertThat(finalState).containsEntry("before_key", "before_value");
    assertThat(finalState).containsEntry("after_key", "after_value");
  }

  @Test
  public void testRun_withAsyncCallbacks() {
    Content modelContent = Content.fromParts(Part.fromText("Real LLM response"));
    TestLlm testLlm = createTestLlm(createLlmResponse(modelContent));

    Agent agent =
        createTestAgentBuilder(testLlm)
            .beforeAgentCallback(
                callbackContext ->
                    Maybe.<Content>empty().delay(10, MILLISECONDS, Schedulers.computation()))
            .afterAgentCallback(
                callbackContext ->
                    Maybe.just(Content.fromParts(Part.fromText("async after agent content")))
                        .delay(10, MILLISECONDS, Schedulers.computation()))
            .build();
    InvocationContext invocationContext = createInvocationContext(agent);

    List<Event> events = agent.runAsync(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(2);
    assertThat(events.get(0).content()).hasValue(modelContent);
    assertThat(events.get(0).actions().stateDelta()).isEmpty();
    assertThat(events.get(1).content().get().parts().get().get(0).text())
        .hasValue("async after agent content");
    assertThat(events.get(1).actions().stateDelta()).isEmpty();
  }

  @Test
  public void testRun_withSyncCallbacks() {
    Content modelContent = Content.fromParts(Part.fromText("Real LLM response"));
    TestLlm testLlm = createTestLlm(createLlmResponse(modelContent));

    Agent agent =
        createTestAgentBuilder(testLlm)
            .beforeAgentCallbackSync(callbackContext -> Optional.empty())
            .afterAgentCallbackSync(
                callbackContext ->
                    Optional.of(Content.fromParts(Part.fromText("sync after agent content"))))
            .build();
    InvocationContext invocationContext = createInvocationContext(agent);

    List<Event> events = agent.runAsync(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(2);
    assertThat(events.get(0).content()).hasValue(modelContent);
    assertThat(events.get(0).actions().stateDelta()).isEmpty();
    assertThat(events.get(1).content().get().parts().get().get(0).text())
        .hasValue("sync after agent content");
    assertThat(events.get(1).actions().stateDelta()).isEmpty();
  }

  @Test
  public void testRun_withBeforeAgentCallback_returnsContent() {
    Content modelContent = Content.fromParts(Part.fromText("Real LLM response"));
    TestLlm testLlm = createTestLlm(createLlmResponse(modelContent));
    Content beforeAgentContent = Content.fromParts(Part.fromText("before agent content"));
    Agent agent =
        createTestAgentBuilder(testLlm)
            .beforeAgentCallback(
                callbackContext -> {
                  Object unused = callbackContext.state().put("before_key", "before_value");
                  return Maybe.just(beforeAgentContent);
                })
            .build();
    InvocationContext invocationContext = createInvocationContext(agent);

    List<Event> events = agent.runAsync(invocationContext).toList().blockingGet();
    Map<String, Object> finalState = invocationContext.session().state();

    // Verify only one event is returned (content from beforeAgentCallback)
    assertThat(events).hasSize(1);
    assertThat(events.get(0).content()).hasValue(beforeAgentContent);
    assertThat(events.get(0).actions().stateDelta()).containsExactly("before_key", "before_value");
    assertThat(finalState).containsEntry("before_key", "before_value");
  }

  @Test
  public void testRun_withBeforeAgentCallback_modifiesStateOnly() {
    Content modelContent = Content.fromParts(Part.fromText("Real LLM response"));
    TestLlm testLlm = createTestLlm(createLlmResponse(modelContent));
    Agent agent =
        createTestAgentBuilder(testLlm)
            .beforeAgentCallback(
                callbackContext -> {
                  Object unused = callbackContext.state().put("before_key", "before_value");
                  // Return empty to signal no immediate content response
                  return Maybe.empty();
                })
            .build();
    InvocationContext invocationContext = createInvocationContext(agent);

    List<Event> events = agent.runAsync(invocationContext).toList().blockingGet();
    Map<String, Object> finalState = invocationContext.session().state();

    // Verify two events are returned (state delta + model response)
    assertThat(events).hasSize(2);
    // Verify the first event (state delta)
    assertThat(events.get(0).content().flatMap(Content::parts)).isEmpty(); // No content
    assertThat(events.get(0).actions().stateDelta()).containsExactly("before_key", "before_value");
    // Verify the second event (model response)
    assertThat(events.get(1).content()).hasValue(modelContent);
    assertThat(events.get(1).actions().stateDelta()).isEmpty();
    assertThat(finalState).containsEntry("before_key", "before_value");
  }

  @Test
  public void testRun_withBeforeAgentCallback_returnsNothing() {
    Content modelContent = Content.fromParts(Part.fromText("Real LLM response"));
    TestLlm testLlm = createTestLlm(createLlmResponse(modelContent));
    Agent agent =
        createTestAgentBuilder(testLlm)
            .beforeAgentCallback(
                callbackContext -> {
                  // No state modification, no content returned
                  return Maybe.empty();
                })
            .build();
    InvocationContext invocationContext = createInvocationContext(agent);

    List<Event> events = agent.runAsync(invocationContext).toList().blockingGet();
    Map<String, Object> finalState = invocationContext.session().state();

    // Verify only one event is returned (model response)
    assertThat(events).hasSize(1);
    assertThat(events.get(0).content()).hasValue(modelContent);
    assertThat(events.get(0).actions().stateDelta()).isEmpty();
    assertThat(finalState).isEmpty();
  }

  @Test
  public void testRun_withOutputKey_savesState() {
    Content modelContent = Content.fromParts(Part.fromText("Saved output"));
    TestLlm testLlm = createTestLlm(createLlmResponse(modelContent));
    Agent agent = createTestAgentBuilder(testLlm).outputKey("myOutput").build();
    InvocationContext invocationContext = createInvocationContext(agent);

    List<Event> events = agent.runAsync(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(1);
    assertThat(events.get(0).content()).hasValue(modelContent);
    assertThat(events.get(0).finalResponse()).isTrue();

    assertThat(invocationContext.session().state()).containsEntry("myOutput", "Saved output");
  }

  @Test
  public void testRun_withOutputKey_savesMultiPartState() {
    Content modelContent = Content.fromParts(Part.fromText("Part 1."), Part.fromText(" Part 2."));
    TestLlm testLlm = createTestLlm(createLlmResponse(modelContent));
    Agent agent = createTestAgentBuilder(testLlm).outputKey("myMultiPartOutput").build();
    InvocationContext invocationContext = createInvocationContext(agent);

    List<Event> events = agent.runAsync(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(1);
    assertThat(events.get(0).content()).hasValue(modelContent);
    assertThat(events.get(0).finalResponse()).isTrue();

    assertThat(invocationContext.session().state())
        .containsEntry("myMultiPartOutput", "Part 1. Part 2.");
  }

  @Test
  public void testRun_withoutOutputKey_doesNotSaveState() {
    Content modelContent = Content.fromParts(Part.fromText("Some output"));
    TestLlm testLlm = createTestLlm(createLlmResponse(modelContent));
    Agent agent = createTestAgentBuilder(testLlm).build();
    InvocationContext invocationContext = createInvocationContext(agent);

    List<Event> events = agent.runAsync(invocationContext).toList().blockingGet();

    assertThat(events).hasSize(1);
    assertThat(events.get(0).content()).hasValue(modelContent);
    assertThat(events.get(0).finalResponse()).isTrue();

    assertThat(invocationContext.session().state()).isEmpty();
  }
}
