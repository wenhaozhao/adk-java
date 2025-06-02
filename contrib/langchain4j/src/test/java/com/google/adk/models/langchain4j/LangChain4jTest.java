package com.google.adk.models.langchain4j;

import static org.junit.jupiter.api.Assertions.*;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.AgentTool;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class LangChain4jTest {

    public static final String CLAUDE_3_7_SONNET_20250219 = "claude-3-7-sonnet-20250219";
    public static final String GEMINI_2_0_FLASH = "gemini-2.0-flash";
    public static final String GPT_4_O_MINI = "gpt-4o-mini";

    @BeforeAll
    public static void setUp() {
        assertNotNull(System.getenv("ANTHROPIC_API_KEY"));
        assertNotNull(System.getenv("GOOGLE_API_KEY"));
    }

    @Test
    void testSimpleAgent() {
        // given
        AnthropicChatModel claudeModel = AnthropicChatModel.builder()
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_3_7_SONNET_20250219)
            .build();

        LlmAgent agent = LlmAgent.builder()
            .name("science-app")
            .description("Science teacher agent")
            .model(new LangChain4j(claudeModel, CLAUDE_3_7_SONNET_20250219))
            .instruction("""
                You are a helpful science teacher that explains science concepts
                to kids and teenagers.
                """)
            .build();

        // when
        List<Event> events = askAgent(agent, "What is a qubit?");

        // then
        assertEquals(1, events.size());

        Event firstEvent = events.get(0);
        assertTrue(firstEvent.content().isPresent());

        Content content = firstEvent.content().get();
        System.out.println("Answer: " + content.text());
        assertTrue(content.text().contains("quantum"));
    }

    @Test
    void testSingleAgentWithTools() {
        // given
        AnthropicChatModel claudeModel = AnthropicChatModel.builder()
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_3_7_SONNET_20250219)
            .build();

        BaseAgent agent = LlmAgent.builder()
            .name("friendly-weather-app")
            .description("Friend agent that knows about the weather")
            .model(new LangChain4j(claudeModel, CLAUDE_3_7_SONNET_20250219))
            .instruction("""
                You are a friendly assistant.
                
                If asked about the weather forecast for a city,
                you MUST call the `getWeather` function.
                """)
            .tools(FunctionTool.create(LangChain4jTest.class, "getWeather"))
            .build();

        // when
        List<Event> events = askAgent(agent, "What's the weather like in Paris?");

        // then
        assertEquals(3, events.size());

        events.forEach(event -> {
            assertTrue(event.content().isPresent());
            System.out.printf("%nevent: %s%n", event.stringifyContent());
        });

        Event eventOne = events.get(0);
        Event eventTwo = events.get(1);
        Event eventThree = events.get(2);

        // assert the first event is a function call
        Content contentOne = eventOne.content().get();
        assertTrue(contentOne.parts().isPresent());
        List<Part> partsOne = contentOne.parts().get();
        assertEquals(1, partsOne.size());
        Optional<FunctionCall> functionCall = partsOne.get(0).functionCall();
        assertTrue(functionCall.isPresent());
        assertEquals("getWeather", functionCall.get().name().get());
        assertTrue(functionCall.get().args().get().containsKey("city"));

        // assert the second event is a function response
        Content contentTwo = eventTwo.content().get();
        assertTrue(contentTwo.parts().isPresent());
        List<Part> partsTwo = contentTwo.parts().get();
        assertEquals(1, partsTwo.size());
        Optional<FunctionResponse> functionResponseTwo = partsTwo.get(0).functionResponse();
        assertTrue(functionResponseTwo.isPresent());

        // assert the third event is the final text response
        assertTrue(eventThree.finalResponse());
        Content contentThree = eventThree.content().get();
        assertTrue(contentThree.parts().isPresent());
        List<Part> partsThree = contentThree.parts().get();
        assertEquals(1, partsThree.size());
        assertTrue(partsThree.get(0).text().get().contains("beautiful"));
    }

    @Test
    void testAgentTool() {
        // given
        OpenAiChatModel gptModel = OpenAiChatModel.builder()
            .baseUrl("http://langchain4j.dev/demo/openai/v1")
            .apiKey(Objects.requireNonNullElse(System.getenv("OPENAI_API_KEY"), "demo"))
            .modelName(GPT_4_O_MINI)
            .build();

        LlmAgent weatherAgent = LlmAgent.builder()
            .name("weather-agent")
            .description("Weather agent")
            .model(GEMINI_2_0_FLASH)
            .instruction("""
                Your role is to always answer that the weather is sunny and 20°C.
                """)
            .build();

        BaseAgent agent = LlmAgent.builder()
            .name("friendly-weather-app")
            .description("Friend agent that knows about the weather")
            .model(new LangChain4j(gptModel))
            .instruction("""
                You are a friendly assistant.
                
                If asked about the weather forecast for a city,
                you MUST call the `weather-agent` function.
                """)
            .tools(AgentTool.create(weatherAgent))
            .build();

        // when
        List<Event> events = askAgent(agent, "What's the weather like in Paris?");

        // then
        assertEquals(3, events.size());
        events.forEach(event -> {
            assertTrue(event.content().isPresent());
            System.out.printf("%nevent: %s%n", event.stringifyContent());
        });

        assertEquals(1, events.get(0).functionCalls().size());
        assertEquals("weather-agent", events.get(0).functionCalls().get(0).name().get());

        assertEquals(1, events.get(1).functionResponses().size());
        assertTrue(events.get(1).functionResponses().get(0).response().get().toString().toLowerCase().contains("sunny"));
        assertTrue(events.get(1).functionResponses().get(0).response().get().toString().contains("20"));

        assertTrue(events.get(2).finalResponse());
        assertTrue(events.get(2).content().get().text().contains("sunny"));
        assertTrue(events.get(2).content().get().text().contains("20"));
    }

    @Test
    void testSubAgent() {
        // given
        OpenAiChatModel gptModel = OpenAiChatModel.builder()
            .baseUrl("http://langchain4j.dev/demo/openai/v1")
            .apiKey(Objects.requireNonNullElse(System.getenv("OPENAI_API_KEY"), "demo"))
            .modelName(GPT_4_O_MINI)
            .build();

        LlmAgent greeterAgent = LlmAgent.builder()
            .name("greeterAgent")
            .description("Friendly agent that greets users")
            .model(new LangChain4j(gptModel))
            .instruction("""
                You are a friendly that greets users.
                """)
            .build();

        LlmAgent farewellAgent = LlmAgent.builder()
            .name("farewellAgent")
            .description("Friendly agent that says goodbye to users")
            .model(new LangChain4j(gptModel))
            .instruction("""
                You are a friendly that says goodbye to users.
                """)
            .build();

        LlmAgent coordinatorAgent = LlmAgent.builder()
            .name("coordinator-agent")
            .description("Coordinator agent")
            .model(GEMINI_2_0_FLASH)
            .instruction("""
                Your role is to coordinate 2 agents:
                - `greeterAgent`: should reply to messages saying hello, hi, etc.
                - `farewellAgent`: should reply to messages saying bye, goodbye, etc.
                """)
            .subAgents(greeterAgent, farewellAgent)
            .build();

        // when
        List<Event> hiEvents = askAgent(coordinatorAgent, "Hi");
        List<Event> byeEvents = askAgent(coordinatorAgent, "Goodbye");

        // then
        hiEvents.forEach(event -> { System.out.println(event.stringifyContent()); });
        byeEvents.forEach(event -> { System.out.println(event.stringifyContent()); });

        // Assertions for hiEvents
        assertEquals(3, hiEvents.size());

        Event hiEvent1 = hiEvents.get(0);
        assertTrue(hiEvent1.content().isPresent());
        assertFalse(hiEvent1.functionCalls().isEmpty());
        assertEquals(1, hiEvent1.functionCalls().size());
        FunctionCall hiFunctionCall = hiEvent1.functionCalls().get(0);
        assertTrue(hiFunctionCall.id().isPresent());
        assertEquals(Optional.of("transferToAgent"), hiFunctionCall.name());
        assertEquals(Optional.of(Map.of("agentName", "greeterAgent")), hiFunctionCall.args());

        Event hiEvent2 = hiEvents.get(1);
        assertTrue(hiEvent2.content().isPresent());
        assertFalse(hiEvent2.functionResponses().isEmpty());
        assertEquals(1, hiEvent2.functionResponses().size());
        FunctionResponse hiFunctionResponse = hiEvent2.functionResponses().get(0);
        assertTrue(hiFunctionResponse.id().isPresent());
        assertEquals(Optional.of("transferToAgent"), hiFunctionResponse.name());
        assertEquals(Optional.of(Map.of()), hiFunctionResponse.response()); // Empty map for response

        Event hiEvent3 = hiEvents.get(2);
        assertTrue(hiEvent3.content().isPresent());
        assertTrue(hiEvent3.content().get().text().toLowerCase().contains("hello"));
        assertTrue(hiEvent3.finalResponse());

        // Assertions for byeEvents
        assertEquals(3, byeEvents.size());

        Event byeEvent1 = byeEvents.get(0);
        assertTrue(byeEvent1.content().isPresent());
        assertFalse(byeEvent1.functionCalls().isEmpty());
        assertEquals(1, byeEvent1.functionCalls().size());
        FunctionCall byeFunctionCall = byeEvent1.functionCalls().get(0);
        assertTrue(byeFunctionCall.id().isPresent());
        assertEquals(Optional.of("transferToAgent"), byeFunctionCall.name());
        assertEquals(Optional.of(Map.of("agentName", "farewellAgent")), byeFunctionCall.args());

        Event byeEvent2 = byeEvents.get(1);
        assertTrue(byeEvent2.content().isPresent());
        assertFalse(byeEvent2.functionResponses().isEmpty());
        assertEquals(1, byeEvent2.functionResponses().size());
        FunctionResponse byeFunctionResponse = byeEvent2.functionResponses().get(0);
        assertTrue(byeFunctionResponse.id().isPresent());
        assertEquals(Optional.of("transferToAgent"), byeFunctionResponse.name());
        assertEquals(Optional.of(Map.of()), byeFunctionResponse.response()); // Empty map for response

        Event byeEvent3 = byeEvents.get(2);
        assertTrue(byeEvent3.content().isPresent());
        assertTrue(byeEvent3.content().get().text().toLowerCase().contains("goodbye"));
        assertTrue(byeEvent3.finalResponse());
    }

    private static List<Event> askAgent(BaseAgent agent, String... messages) {
        ArrayList<Event> allEvents = new ArrayList<>();

        Runner runner = new InMemoryRunner(agent, agent.name());
        Session session = runner.sessionService().createSession(agent.name(), "user132").blockingGet();

        for (String message : messages) {
            Content messageContent = Content.fromParts(Part.fromText(message));
            allEvents.addAll(
                runner.runAsync(session, messageContent, RunConfig.builder().build())
                    .blockingStream().toList()
            );
        }

        return allEvents;
    }

    @Schema(description = "Function to get the weather forecast for a given city")
    public static Map<String, String> getWeather(
        @Schema(name = "city", description = "The city to get the weather forecast for")
        String city,
        ToolContext toolContext) {

        System.out.format("""
            Tool context
            - function call ID: %s
            - invocation ID: %s
            - agent name: %s
            - state: %s
            """,
            toolContext.functionCallId(),
            toolContext.invocationId(),
            toolContext.agentName(),
            toolContext.state().entrySet());

        return Map.of(
            "city", city,
            "forecast", "a beautiful and sunny weather",
            "temperature", "from 10°C in the morning up to 24°C in the afternoon"
        );
    }
}
