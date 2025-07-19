package com.google.adk.tools.mcp;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;

public class McpAsyncSession {
    private final McpAsyncClient client;
    private final McpSchema.InitializeResult initResult;

    public McpAsyncSession(McpAsyncClient client, McpSchema.InitializeResult initResult) {
        this.client = client;
        this.initResult = initResult;
    }

    public McpAsyncClient client() {
        return client;
    }

    public McpSchema.InitializeResult result() {
        return initResult;
    }
}
