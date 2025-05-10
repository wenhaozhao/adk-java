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

package com.google.adk.testing;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.function.Supplier;

/** A test agent that returns events from a supplier. */
public final class TestBaseAgent extends BaseAgent {
  private final Supplier<Flowable<Event>> eventSupplier;
  private int invocationCount = 0;
  private InvocationContext lastInvocationContext;

  TestBaseAgent(
      String name, Supplier<Flowable<Event>> eventSupplier, List<? extends BaseAgent> subAgents) {
    super(name, "description", subAgents, null, null);
    this.eventSupplier = eventSupplier;
  }

  @Override
  public Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
    lastInvocationContext = InvocationContext.copyOf(invocationContext);
    invocationCount++;
    return eventSupplier.get();
  }

  @Override
  public Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
    lastInvocationContext = InvocationContext.copyOf(invocationContext);
    invocationCount++;
    return eventSupplier.get();
  }

  public int getInvocationCount() {
    return invocationCount;
  }

  public InvocationContext getLastInvocationContext() {
    return lastInvocationContext;
  }
}
