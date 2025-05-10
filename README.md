# Agent Development Kit (ADK) for Java

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![r/agentdevelopmentkit](https://img.shields.io/badge/Reddit-r%2Fagentdevelopmentkit-FF4500?style=flat&logo=reddit&logoColor=white)](https://www.reddit.com/r/agentdevelopmentkit/)

<html>
    <h2 align="center">
      <img src="https://raw.githubusercontent.com/google/adk-python/main/assets/agent-development-kit.png" width="256"/>
    </h2>
    <h3 align="center">
      A Java version of our open-source, code-first Python toolkit for building, evaluating, and deploying sophisticated AI agents with flexibility and control.
    </h3>
</html>

Agent Development Kit (ADK) is designed for developers seeking fine-grained
control and flexibility when building advanced AI agents that are tightly
integrated with services in Google Cloud. It allows you to define agent
behavior, orchestration, and tool use directly in code, enabling robust
debugging, versioning, and deployment anywhere – from your laptop to the cloud.

--------------------------------------------------------------------------------

## ✨ Key Features

-   **Rich Tool Ecosystem**: Utilize pre-built tools, custom functions, OpenAPI
    specs, or integrate existing tools to give agents diverse capabilities, all
    for tight integration with the Google ecosystem.

-   **Code-First Development**: Define agent logic, tools, and orchestration
    directly in Python for ultimate flexibility, testability, and versioning.

-   **Modular Multi-Agent Systems**: Design scalable applications by composing
    multiple specialized agents into flexible hierarchies.

## 🚀 Installation

If you're using Maven, add the following to your dependencies:

<!-- {x-version-start:google-adk:released} -->

```xml
<dependencies>
  <dependency>
    <groupId>com.google.adk</groupId>
    <artifactId>google-adk</artifactId>
    <version>0.1.0</version>
  </dependency>
</dependencies>
```

<!-- {x-version-end} -->

## 📚 Documentation

We're working on the Java version of the ADK docs, but you can but in the
meantime you can familiarize yourself with the concepts and workflows for
building, evaluating, and deploying agents by following the python
documentation:

*   **[Documentation](https://google.github.io/adk-docs)**

## 🏁 Feature Highlight

### Define a single agent:

```java

package com.google.adk;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.GoogleLogger;
import com.google.adk.agents.Agent;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;


public final class TestAgent {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  @Schema(
      description =
          "This function returns the current weather temperature for the specified location.")
  public static int getWeather(
      @Schema(name = "location", description = "The location for which the weather is requested")
          String location) {
    return 25;
  }

  static final Agent rootAgent;

  // Static initializer block to handle exceptions
  static {
    FunctionTool weatherFunctionTool = FunctionTool.create(TestAgent.class, "getWeather");

    // Create the Agent
    rootAgent =
        Agent.builder()
            .name("root-agent")
            .description("A helpful AI assistant.")
            .model("gemini-2.0-flash-001")
            .instruction("Be polite and answer all users' questions.")
            .tools(ImmutableList.of(weatherFunctionTool))
            .beforeModelCallback(
                (invocationContext, llmRequest) -> {
                  logger.atInfo().log(
                      "==== Agent %s calling LLM with request: %s",
                      invocationContext.agent().name(), llmRequest);
                  return null;
                })
            .afterModelCallback(
                (invocationContext, llmResponse) -> {
                  logger.atInfo().log(
                      "==== Agent %s got response from LLM: %s",
                      invocationContext.agent().name(), llmResponse);
                  return null;
                })
            .beforeAgentCallback(
                (invocationContext) -> {
                  logger.atInfo().log("==== Agent %s starting", invocationContext.agent().name());
                  return null;
                })
            .beforeToolCallback(
                (invocationContext, tool, args, toolContext) -> {
                  logger.atInfo().log(
                      "==== Agent %s calling tool %s, args: %s",
                      invocationContext.agent().name(), tool.name(), args);
                  return null;
                })
            .afterToolCallback(
                (invocationContext, tool, args, toolContext, response) -> {
                  logger.atInfo().log(
                      "==== Agent %s finished calling tool %s, response: %s",
                      invocationContext.agent().name(), tool.name(), response);
                  return null;
                })
            .build();
  }

  private TestAgent() {}
}

```

### Development UI

TODO(b/410930446): Add development UI

### Evaluate Agents

TODO(b/410912902): Port/reuse Python ADK eval stack

## 🤝 Contributing

We welcome contributions from the community! Whether it's bug reports, feature
requests, documentation improvements, or code contributions, please see our
[**Contributing Guidelines**](./CONTRIBUTING.md) to get started.

## 📄 License

This project is licensed under the Apache 2.0 License - see the
[LICENSE](LICENSE) file for details.

## Preview

This feature is subject to the "Pre-GA Offerings Terms" in the General Service
Terms section of the
[Service Specific Terms](https://cloud.google.com/terms/service-terms#1). Pre-GA
features are available "as is" and might have limited support. For more
information, see the
[launch stage descriptions](https://cloud.google.com/products?hl=en#product-launch-stages).

--------------------------------------------------------------------------------

*Happy Agent Building!*
