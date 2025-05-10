/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.adk.tools.mcp;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connects to a MCP Server, and retrieves MCP Tools into ADK Tools.
 *
 * <p>Attributes:
 *
 * <ul>
 *   <li>{@code connectionParams}: The connection parameters to the MCP server. Can be either {@code
 *       ServerParameters} or {@code SseServerParams}.
 *   <li>{@code exit_stack}: (Python concept) The async exit stack to manage the connection to the
 *       MCP server. In Java, this is implicitly handled by {@code McpToolset} implementing {@code
 *       AutoCloseable}.
 *   <li>{@code session}: The MCP session being initialized with the connection.
 * </ul>
 */
public class McpToolset implements AutoCloseable {
  private static final Logger logger = Logger.getLogger(McpToolset.class.getName());
  private final Object connectionParams;
  private final McpSessionManager mcpSessionManager;
  private McpSyncClient mcpSession;
  private final ObjectMapper objectMapper;

  /**
   * Initializes the McpToolset.
   *
   * @param connectionParams The connection parameters to the MCP server. Can be: {@code
   *     ServerParameters} for using a local mcp server (e.g., using `npx` or `python3`); or {@code
   *     SseServerParams} for a local/remote SSE server.
   * @param objectMapper An ObjectMapper instance to be used for parsing schemas when creating
   *     {@code McpTool} instances.
   */
  public McpToolset(Object connectionParams, ObjectMapper objectMapper) {
    if (connectionParams == null) {
      throw new IllegalArgumentException("Missing connection params in McpToolset.");
    }
    this.connectionParams = connectionParams;
    this.objectMapper = objectMapper;
    this.mcpSessionManager = new McpSessionManager(this.connectionParams);
  }

  /** Holds the result of loading tools, containing both the tools and the toolset instance. */
  public static class McpToolsAndToolsetResult {
    private final List<McpTool> tools;
    private final McpToolset toolset;

    public McpToolsAndToolsetResult(List<McpTool> tools, McpToolset toolset) {
      this.tools = tools;
      this.toolset = toolset;
    }

    public List<McpTool> getTools() {
      return tools;
    }

    public McpToolset getToolset() {
      return toolset;
    }
  }

  /**
   * Retrieve all tools from the MCP connection. This is a convenience static method that
   * initializes an {@code McpToolset} and loads its tools.
   *
   * @param connectionParams The connection parameters to the MCP server.
   * @param objectMapper An ObjectMapper instance to be used for parsing schemas.
   * @return A {@code CompletableFuture} that completes with a {@code Pair} of the list of {@code
   *     McpToolsAndToolsetResult}. The {@code McpToolset} instance within the result should be
   *     closed using {@code .close()} when no longer needed to release resources.
   */
  public static CompletableFuture<McpToolsAndToolsetResult> fromServer(
      Object connectionParams, ObjectMapper objectMapper) {
    McpToolset toolset = new McpToolset(connectionParams, objectMapper);
    return toolset
        .initializeSession() // Initialize the session (this can throw exceptions)
        .thenCompose(
            session ->
                toolset.loadTools()) // Load tools using the initialized session (this can throw
        // exceptions)
        .thenApply(
            tools ->
                new McpToolsAndToolsetResult(
                    tools,
                    toolset)) // If successful, return the tools and the toolset itself for future
        // closing
        .exceptionallyCompose(
            e -> {
              CompletableFuture<McpToolsAndToolsetResult> failedFuture = new CompletableFuture<>();
              // Log the original exception before attempting to close for better context.
              logger.log(Level.SEVERE, "Error during McpToolset operation, attempting cleanup.", e);
              try {
                toolset.close(); // Attempt to close the toolset if an error occurred
              } catch (RuntimeException closeException) {
                logger.log(Level.WARNING, "Failed to close McpToolset after error", closeException);
                // Add the close exception as a suppressed exception to the original error
                e.addSuppressed(closeException);
              }
              // Wrap the original exception in a more specific custom exception
              failedFuture.completeExceptionally(
                  new McpToolsetException(
                      "Failed to load tools from MCP server during fromServer call. See suppressed"
                          + " exceptions for details.",
                      e));
              return failedFuture;
            });
  }

  /**
   * Connects to the MCP Server and initializes the ClientSession. This method is intended for
   * internal use and is called automatically by {@code fromServer} or when {@code McpToolset}
   * enters an "active" state.
   *
   * @return A {@code CompletableFuture} that completes with the initialized {@code McpSyncClient}.
   */
  private CompletableFuture<McpSyncClient> initializeSession() {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            this.mcpSession = this.mcpSessionManager.createSession();
            return this.mcpSession;
          } catch (IllegalArgumentException e) {
            logger.log(Level.SEVERE, "Invalid connection parameters for MCP session.", e);
            throw new McpInitializationException(
                "Invalid connection parameters for MCP session.", e);
          } catch (RuntimeException e) { // Catch any other unexpected exceptions
            logger.log(Level.SEVERE, "Unexpected error during MCP session initialization.", e);
            throw new McpInitializationException(
                "Unexpected error during MCP session initialization.", e);
          }
        });
  }

  /**
   * Loads all tools from the MCP Server. This method includes retry logic in case of transient
   * session issues.
   *
   * @return A {@code CompletableFuture} that completes with a list of {@code McpTool}s imported
   *     from the MCP Server.
   */
  public CompletableFuture<List<McpTool>> loadTools() {
    final int maxRetries = 3;
    final long retryDelayMillis = 100; // milliseconds

    return CompletableFuture.supplyAsync(
        () -> {
          for (int i = 0; i < maxRetries; i++) {
            try {
              // If session is not initialized or was closed, reinitialize it.
              // The createSession in McpSessionManager will handle creating a new one.
              if (this.mcpSession == null) {
                logger.log(Level.INFO, "MCP session is null, attempting to reinitialize.");
                this.mcpSession = this.mcpSessionManager.createSession();
              }

              ListToolsResult toolsResponse = this.mcpSession.listTools();
              return toolsResponse.tools().stream()
                  .map(
                      tool ->
                          new McpTool(
                              tool, this.mcpSession, this.mcpSessionManager, this.objectMapper))
                  .collect(toImmutableList());
            } catch (IllegalArgumentException e) {
              // This could happen if parameters for tool loading are somehow invalid.
              // This is likely a fatal error and should not be retried.
              logger.log(Level.SEVERE, "Invalid argument encountered during tool loading.", e);
              throw new McpToolLoadingException(
                  "Invalid argument encountered during tool loading.", e);
            } catch (RuntimeException e) { // Catch any other unexpected runtime exceptions
              logger.log(
                  Level.SEVERE,
                  "Unexpected error during tool loading, retry attempt " + (i + 1),
                  e);
              if (i < maxRetries - 1) {
                // For other general exceptions, we might still want to retry if they are
                // potentially transient, or if we don't have more specific handling. But it's
                // better to be specific. For now, we'll treat them as potentially retryable but log
                // them at a higher level.
                try {
                  logger.log(
                      Level.INFO,
                      "Reinitializing MCP session before next retry for unexpected error.");
                  this.mcpSession = this.mcpSessionManager.createSession();
                  Thread.sleep(retryDelayMillis);
                } catch (InterruptedException ie) {
                  Thread.currentThread().interrupt();
                  logger.log(
                      Level.SEVERE,
                      "Interrupted during retry delay for loadTools (unexpected error).",
                      ie);
                  throw new McpToolLoadingException(
                      "Interrupted during retry delay (unexpected error)", ie);
                } catch (RuntimeException reinitE) {
                  logger.log(
                      Level.SEVERE,
                      "Failed to reinitialize session during retry (unexpected error).",
                      reinitE);
                  throw new McpInitializationException(
                      "Failed to reinitialize session during tool loading retry (unexpected"
                          + " error).",
                      reinitE);
                }
              } else {
                logger.log(
                    Level.SEVERE,
                    "Failed to load tools after multiple retries due to unexpected error.",
                    e);
                throw new McpToolLoadingException(
                    "Failed to load tools after multiple retries due to unexpected error.", e);
              }
            }
          }
          // This line should ideally not be reached if retries are handled correctly or an
          // exception is always thrown.
          throw new IllegalStateException(
              "Unexpected state: loadTools retry loop completed without success or throwing an"
                  + " exception.");
        });
  }

  /**
   * Closes the connection to MCP Server. This method is part of the {@code AutoCloseable}
   * interface, allowing {@code McpToolset} to be used in a try-with-resources statement for
   * automatic resource management.
   */
  @Override
  public void close() {
    if (this.mcpSession != null) {
      try {
        this.mcpSession.close();
        logger.log(Level.INFO, "MCP session closed successfully.");
      } catch (RuntimeException e) {
        logger.log(Level.SEVERE, "Failed to close MCP session", e);
        // We don't throw an exception here, as closing is a cleanup operation and
        // failing to close shouldn't prevent the program from continuing (or exiting).
        // However, we log the error for debugging purposes.
      } finally {
        this.mcpSession = null; // Ensure session is marked as null after close attempt
      }
    }
  }

  /** Base exception for all errors originating from {@code McpToolset}. */
  public static class McpToolsetException extends RuntimeException {
    public McpToolsetException(String message) {
      super(message);
    }

    public McpToolsetException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** Exception thrown when there's an error during MCP session initialization. */
  public static class McpInitializationException extends McpToolsetException {
    public McpInitializationException(String message) {
      super(message);
    }

    public McpInitializationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** Exception thrown when there's an error during loading tools from the MCP server. */
  public static class McpToolLoadingException extends McpToolsetException {
    public McpToolLoadingException(String message) {
      super(message);
    }

    public McpToolLoadingException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
