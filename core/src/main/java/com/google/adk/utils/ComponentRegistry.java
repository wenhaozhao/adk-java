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

package com.google.adk.utils;

import com.google.adk.tools.BuiltInCodeExecutionTool;
import com.google.adk.tools.GoogleSearchTool;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A registry for storing and retrieving ADK instances by name.
 *
 * <p>This class provides a base registry with common ADK components and is designed to be extended
 * by users who want to add their own pre-wired entries. The registry is fully thread-safe and
 * supports storing any type of object.
 *
 * <p><strong>Thread Safety:</strong>
 *
 * <ul>
 *   <li>All instance methods are thread-safe due to the underlying ConcurrentHashMap
 *   <li>The singleton instance access is thread-safe using volatile semantics
 *   <li>The setInstance() method is synchronized to ensure atomic singleton replacement
 * </ul>
 *
 * <p>Base pre-wired entries include:
 *
 * <ul>
 *   <li>"google_search" - GoogleSearchTool instance
 *   <li>"code_execution" - BuiltInCodeExecutionTool instance
 *   <li>"exit_loop" - ExitLoopTool instance
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Use the singleton instance
 * ComponentRegistry registry = ComponentRegistry.getInstance();
 * Optional<GoogleSearchTool> searchTool = registry.get("google_search", GoogleSearchTool.class);
 *
 * // Extend ComponentRegistry to add custom pre-wired entries
 * public class MyComponentRegistry extends ComponentRegistry {
 *   public MyComponentRegistry() {
 *     super(); // Initialize base pre-wired entries
 *     register("my_custom_tool", new MyCustomTool());
 *     register("my_agent", new MyCustomAgent());
 *   }
 * }
 *
 * // Replace the singleton with custom registry when server starts
 * ComponentRegistry.setInstance(new MyComponentRegistry());
 * }</pre>
 */
public class ComponentRegistry {

  private static final Logger logger = LoggerFactory.getLogger(ComponentRegistry.class);
  private static volatile ComponentRegistry instance = new ComponentRegistry();

  private final Map<String, Object> registry = new ConcurrentHashMap<>();

  public ComponentRegistry() {
    initializePreWiredEntries();
  }

  /** Initializes the registry with base pre-wired ADK instances. */
  private void initializePreWiredEntries() {
    registry.put("google_search", new GoogleSearchTool());
    registry.put("code_execution", new BuiltInCodeExecutionTool());

    logger.debug("Initialized base pre-wired entries in ComponentRegistry");
  }

  /**
   * Registers an object with the given name. This can override pre-wired entries.
   *
   * <p>This method is thread-safe due to the underlying ConcurrentHashMap.
   *
   * @param name the name to associate with the object
   * @param value the object to register (can be an instance, class, function, etc.)
   * @throws IllegalArgumentException if name is null or empty, or if value is null
   */
  public void register(String name, Object value) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Name cannot be null or empty");
    }
    if (value == null) {
      throw new IllegalArgumentException("Value cannot be null");
    }

    Object previous = registry.put(name, value);
    if (previous != null) {
      logger.info(
          "Overriding existing registration for name: {} (was: {}, now: {})",
          name,
          previous.getClass().getSimpleName(),
          value.getClass().getSimpleName());
    } else {
      logger.debug(
          "Registered new object of type {} with name: {}", value.getClass().getSimpleName(), name);
    }
  }

  /**
   * Retrieves an object by name and attempts to cast it to the specified type.
   *
   * @param name the name of the object to retrieve
   * @param type the expected type of the object
   * @param <T> the type parameter
   * @return an Optional containing the object if found and castable to the specified type, or an
   *     empty Optional otherwise
   */
  public <T> Optional<T> get(String name, Class<T> type) {
    return get(name)
        .filter(
            value -> {
              if (type.isInstance(value)) {
                return true;
              } else {
                logger.warn(
                    "Object with name '{}' is of type {} but expected type {}",
                    name,
                    value.getClass().getSimpleName(),
                    type.getSimpleName());
                return false;
              }
            })
        .map(type::cast);
  }

  /**
   * Retrieves an object by name without type checking.
   *
   * @param name the name of the object to retrieve
   * @return an Optional containing the object if found, or an empty Optional otherwise
   */
  public Optional<Object> get(String name) {
    if (name == null || name.trim().isEmpty()) {
      return Optional.empty();
    }

    return Optional.ofNullable(registry.get(name));
  }

  /**
   * Returns the global singleton instance of ComponentRegistry.
   *
   * @return the singleton ComponentRegistry instance
   */
  public static ComponentRegistry getInstance() {
    return instance;
  }

  /**
   * Updates the global singleton instance with a new ComponentRegistry. This is useful for
   * replacing the default registry with a custom one when the server starts.
   *
   * <p>This method is thread-safe and ensures that all threads see the updated instance atomically.
   *
   * @param newInstance the new ComponentRegistry instance to use as the singleton
   * @throws IllegalArgumentException if newInstance is null
   */
  public static synchronized void setInstance(ComponentRegistry newInstance) {
    if (newInstance == null) {
      throw new IllegalArgumentException("ComponentRegistry instance cannot be null");
    }
    instance = newInstance;
    logger.info("ComponentRegistry singleton instance updated");
  }
}
