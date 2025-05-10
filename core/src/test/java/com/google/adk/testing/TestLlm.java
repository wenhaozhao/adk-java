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

import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A test implementation of {@link BaseLlm}.
 *
 * <p>Supports providing responses via a sequence of {@link LlmResponse} objects or a {@link
 * Supplier} of {@code Flowable<LlmResponse>}.
 */
public final class TestLlm extends BaseLlm {
  private final List<LlmRequest> llmRequests = Collections.synchronizedList(new ArrayList<>());

  private final List<LlmResponse> responseSequence;
  private final AtomicInteger responseIndex = new AtomicInteger(0);

  private final Supplier<Flowable<LlmResponse>> responsesSupplier;

  /**
   * Constructs a TestLlm that serves responses sequentially from the provided list.
   *
   * @param responses A list of LlmResponse objects to be served in order. Can be null or empty.
   */
  public TestLlm(List<LlmResponse> responses) {
    super("test-llm");
    this.responseSequence =
        (responses == null) ? ImmutableList.of() : ImmutableList.copyOf(responses);
    this.responsesSupplier = null;
  }

  /**
   * Constructs a TestLlm that uses the provided supplier to get responses.
   *
   * @param responsesSupplier A supplier that provides a Flowable of LlmResponse.
   */
  public TestLlm(Supplier<Flowable<LlmResponse>> responsesSupplier) {
    super("test-llm");
    this.responsesSupplier = responsesSupplier;
    this.responseSequence = null;
  }

  @Override
  public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
    llmRequests.add(llmRequest);

    if (this.responseSequence != null) {
      // Sequential discrete response mode
      int currentIndex = responseIndex.getAndIncrement();
      if (currentIndex < responseSequence.size()) {
        LlmResponse nextResponse = responseSequence.get(currentIndex);
        return Flowable.just(nextResponse);
      } else {
        return Flowable.error(
            new NoSuchElementException(
                "TestLlm (List mode) out of responses. Requested response for LLM call "
                    + llmRequests.size()
                    + " (index "
                    + currentIndex
                    + ") but only "
                    + responseSequence.size()
                    + " were configured."));
      }
    } else if (this.responsesSupplier != null) {
      // Legacy/streaming supplier mode
      return responsesSupplier.get();
    } else {
      // Should not happen if constructors are used properly
      return Flowable.error(new IllegalStateException("TestLlm not initialized with responses."));
    }
  }

  @Override
  public BaseLlmConnection connect(LlmRequest llmRequest) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public ImmutableList<LlmRequest> getRequests() {
    return ImmutableList.copyOf(llmRequests);
  }

  public LlmRequest getLastRequest() {
    return Iterables.getLast(llmRequests);
  }
}
