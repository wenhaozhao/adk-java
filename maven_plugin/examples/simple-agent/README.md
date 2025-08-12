# Simple ADK Agent Example

This example demonstrates how to create and run custom ADK agents using the Maven plugin.

## What's Included

- **SimpleAgentProvider**: An implementation of `AgentProvider` that creates three example agents:
  - `chat_assistant`: A friendly general-purpose assistant
  - `search_agent`: An agent with Google Search capabilities
  - `code_helper`: A coding assistant

## How to Run

1. **Compile the project:**
   ```bash
   mvn compile
   ```

2. **Start the ADK Web Server:**
   ```bash
   mvn google-adk:web -Dagents=com.example.SimpleAgentProvider.INSTANCE
   ```

3. **Open your browser:**
   Navigate to http://localhost:8000

4. **Try the agents:**
   - Select an agent from the dropdown
   - Start a conversation
   - Test different agents to see their capabilities

## Customizing

You can modify `SimpleAgentProvider.java` to:
- Add more agents
- Change agent instructions
- Add different tools
- Use different models
- Configure agent-specific settings

Example modifications:

### Add a New Agent
```java
private BaseAgent createMathTutor() {
    return LlmAgent.builder()
        .name("math_tutor")
        .description("A mathematics tutoring agent")
        .model("gemini-2.0-flash")
        .instruction("You are a patient math tutor. " +
                    "Help students understand mathematical concepts step by step.")
        .build();
}
```

Then add it to the `getAgents()` map:
```java
@Override
public Map<String, BaseAgent> getAgents() {
    return Map.of(
        "chat_assistant", createChatAssistant(),
        "search_agent", createSearchAgent(),
        "code_helper", createCodeHelper(),
        "math_tutor", createMathTutor()  // Add the new agent
    );
}
```

### Configure Different Ports
```bash
mvn google-adk:web -Dagents=com.example.SimpleAgentProvider.INSTANCE -Dport=9090
```

## Next Steps

- Explore the ADK documentation for more advanced features
- Add custom tools to your agents
- Implement agent hierarchies with sub-agents
- Try different models (Claude, custom models, etc.)
- Add memory and state management