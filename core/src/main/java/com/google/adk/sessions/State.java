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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** A {@link State} object that also keeps track of the changes to the state. */
public final class State implements Map<String, Object> {

  public static final String APP_PREFIX = "app:";
  public static final String USER_PREFIX = "user:";
  public static final String TEMP_PREFIX = "temp:";

  private final Map<String, Object> state;
  private final Map<String, Object> delta;

  public State(Map<String, Object> state) {
    this(state, new HashMap<>());
  }

  public State(Map<String, Object> state, Map<String, Object> delta) {
    this.state = state;
    this.delta = delta;
  }

  @Override
  public void clear() {
    state.clear();
  }

  @Override
  public boolean containsKey(Object key) {
    return state.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return state.containsValue(value);
  }

  @Override
  public Set<Entry<String, Object>> entrySet() {
    return state.entrySet();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof State)) {
      return false;
    }
    State other = (State) o;
    return state.equals(other.state);
  }

  @Override
  public Object get(Object key) {
    return state.get(key);
  }

  @Override
  public int hashCode() {
    return state.hashCode();
  }

  @Override
  public boolean isEmpty() {
    return state.isEmpty();
  }

  @Override
  public Set<String> keySet() {
    return state.keySet();
  }

  @Override
  public Object put(String key, Object value) {
    Object oldValue = state.put(key, value);
    delta.put(key, value);
    return oldValue;
  }

  @Override
  public void putAll(Map<? extends String, ? extends Object> m) {
    state.putAll(m);
    delta.putAll(m);
  }

  @Override
  public Object remove(Object key) {
    return state.remove(key);
  }

  @Override
  public int size() {
    return state.size();
  }

  @Override
  public Collection<Object> values() {
    return state.values();
  }
}
