# Agent Development Kit (ADK) for Java

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![r/agentdevelopmentkit](https://img.shields.io/badge/Reddit-r%2Fagentdevelopmentkit-FF4500?style=flat&logo=reddit&logoColor=white)](https://www.reddit.com/r/agentdevelopmentkit/)

<html>
    <h2 align="center">
      <img src="https://raw.githubusercontent.com/google/adk-python/main/assets/agent-development-kit.png" width="256"/>
    </h2>
    <h3 align="center">
      An open-source, code-first Java toolkit for building, evaluating, and deploying sophisticated AI agents with flexibility and control.
    </h3>
    <h3 align="center">
      Important Links:
      <a href="https://google.github.io/adk-docs/">Docs</a> &
      <a href="https://github.com/google/adk-samples">Samples</a>.
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
    directly in Java for ultimate flexibility, testability, and versioning.

-   **Modular Multi-Agent Systems**: Design scalable applications by composing
    multiple specialized agents into flexible hierarchies.

## 🚀 Installation

If you're using Maven, add the following to your dependencies:

<!-- {x-version-start:google-adk:released} -->

```xml
<dependency>
  <groupId>com.google.adk</groupId>
  <artifactId>google-adk</artifactId>
  <version>0.1.0</version>
</dependency>
```

<!-- {x-version-end} -->

## 📚 Documentation

We're working on the Java version of the ADK docs, but you can but in the
meantime you can familiarize yourself with the concepts and workflows for
building, evaluating, and deploying agents by following the Java
documentation:

*   **[Documentation](https://google.github.io/adk-docs)**

## 🏁 Feature Highlight

### Same Features & Familiar Interface As Python ADK:

```java
import com.google.common.collect.ImmutableList;
import com.google.adk.agents.Agent;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;

public final class TestAgent {

  @Schema(
      description =
          "This function returns the current weather temperature for the specified location.")
  public static int getWeather(
      @Schema(name = "location", description = "The location for which the weather is requested")
          String location) {
    return 25;
  }

  static final Agent rootAgent;

  static {
    FunctionTool weatherFunctionTool = FunctionTool.create(TestAgent.class, "getWeather");

    rootAgent =
        Agent.builder()
            .name("root-agent")
            .description("A helpful AI assistant.")
            .model("gemini-2.0-flash-001")
            .instruction("Be polite and answer all users' questions.")
            .tools(ImmutableList.of(weatherFunctionTool))
            .beforeModelCallback(
                (invocationContext, llmRequest) -> {
                  System.out.printf(
                      "==== Agent %s calling LLM with request: %s%n",
                      invocationContext.agent().name(), llmRequest);
                  return null;
                })
            .afterModelCallback(
                (invocationContext, llmResponse) -> {
                  System.out.printf(
                      "==== Agent %s got response from LLM: %s%n",
                      invocationContext.agent().name(), llmResponse);
                  return null;
                })
            .beforeAgentCallback(
                (invocationContext) -> {
                  System.out.printf("==== Agent %s starting%n", invocationContext.agent().name());
                  return null;
                })
            .beforeToolCallback(
                (invocationContext, tool, args, toolContext) -> {
                  System.out.printf(
                      "==== Agent %s calling tool %s, args: %s%n",
                      invocationContext.agent().name(), tool.name(), args);
                  return null;
                })
            .afterToolCallback(
                (invocationContext, tool, args, toolContext, response) -> {
                  System.out.printf(
                      "==== Agent %s finished calling tool %s, response: %s%n",
                      invocationContext.agent().name(), tool.name(), response);
                  return null;
                })
            .build();
  }

  private TestAgent() {}
}

```

### Development UI
Same as beloved Python's Development UI.

### Evaluate Agents
Coming soon...

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
