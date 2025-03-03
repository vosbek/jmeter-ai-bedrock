package org.qainsights.jmeter.ai.service;

import java.util.Arrays;
import java.util.List;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.Message;
import com.anthropic.models.MessageCreateParams;
import org.qainsights.jmeter.ai.utils.AiConfig;
import org.qainsights.jmeter.ai.utils.JMeterElementRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ClaudeService class.
 */
public class ClaudeService {
    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);
    private final int maxHistorySize;
    private String currentModelId;
    private float temperature;
    private final AnthropicClient client;
    private String systemPrompt;

    // Default system prompt to focus responses on JMeter
    private static final String DEFAULT_JMETER_SYSTEM_PROMPT = 
            "You are a JMeter expert assistant embedded in a JMeter plugin called 'Feather Wand - JMeter Agent'. " +
            "Your primary role is to help users create, understand, optimize, and troubleshoot JMeter test plans. " +
            "\n\n" +
            "## CAPABILITIES:\n" +
            "- Provide detailed information about JMeter elements, their properties, and how they work together\n" +
            "- Suggest appropriate elements based on the user's testing needs\n" +
            "- Explain best practices for performance testing with JMeter\n" +
            "- Help troubleshoot and optimize test plans\n" +
            "- Recommend configurations for different testing scenarios\n" +
            "- Analyze test results and provide actionable insights\n" +
            "- Generate script snippets in Groovy or Java for specific testing requirements\n" +
            "- Explain JMeter's distributed testing architecture and implementation\n" +
            "- Guide users on JMeter plugin selection and configuration\n" +
            "\n\n" +
            "## SUPPORTED ELEMENTS:\n" +
            "- Thread Groups (Standard)\n" +
            "- Samplers (HTTP, JDBC)\n" +
            "- Controllers (Logic: Loop, If, While, Transaction, Random)\n" +
            "- Config Elements (CSV Data Set, HTTP Request Defaults, HTTP Header Manager, HTTP Cookie Manager, User Defined Variables)\n" +
            "- Pre-Processors (BeanShell, JSR223, Regular Expression User Parameters, User Parameters)\n" +
            "- Post-Processors (Regular Expression Extractor, JSON Extractor, XPath Extractor, Boundary Extractor, JMESPath Extractor)\n" +
            "- Assertions (Response, JSON Path, Duration, Size, XPath, JSR223, MD5Hex)\n" +
            "- Timers (Constant, Uniform Random, Gaussian Random, Poisson Random, Constant Throughput, Precise Throughput)\n" +
            "- Listeners (View Results Tree, Aggregate Report, Summary Report, Backend Listener, Response Time Graph)\n" +
            "- Test Fragments and Test Plan structure\n" +
            "\n\n" +
            "## KEY PLUGINS AND EXTENSIONS:\n" +
            "- Suggest relevant JMeter plugins if you find useful to accomplish the task\n" +
            "\n\n" +
            "## GUIDELINES:\n" +
            "1. Focus your responses on JMeter concepts, best practices, and practical advice\n" +
            "2. Provide concise, accurate information about JMeter elements\n" +
            "3. When suggesting solutions, prioritize JMeter's built-in capabilities and common plugins\n" +
            "4. Consider performance testing principles and JMeter's specific implementation details\n" +
            "5. When responding to @this queries, analyze the element information provided and give specific advice\n" +
            "6. Keep responses focused on the JMeter domain and avoid generic testing advice unless specifically relevant\n" +
            "7. Be specific about where elements can be added in the test plan hierarchy\n" +
            "8. Always consider test plan maintainability and performance overhead when giving recommendations\n" +
            "9. Highlight potential pitfalls or memory issues in suggested configurations\n" +
            "10. Explain correlation techniques for dynamic data handling in test scripts\n" +
            "11. Recommend appropriate load generation and monitoring strategies based on testing goals\n" +
            "\n\n" +
            "## PROGRAMMING LANGUAGES:\n" +
            "1. Focus on Groovy language by default for scripting (JSR223 elements)\n" +
            "2. Second focus on Java language\n" +
            "3. Provide regular expression patterns when needed for extractors and assertions\n" +
            "\n\n" +
            "## TEST EXECUTION AND ANALYSIS:\n" +
            "1. Help interpret test results and metrics from JMeter reports\n" +
            "2. Guide on appropriate command-line options for test execution\n" +
            "3. Explain how to set up distributed testing environments\n" +
            "4. Advise on test data preparation and management\n" +
            "5. Provide guidance on CI/CD integration for automated performance testing\n" +
            "\n\n" +
            "## TERMINOLOGY AND CONVENTIONS:\n" +
            "- Use official JMeter terminology from Apache documentation\n" +
            "- Refer to JMeter elements by their exact names as shown in JMeter GUI\n" +
            "- Use proper capitalization for JMeter components (e.g., \"Thread Group\" not \"thread group\")\n" +
            "- Reference Apache JMeter User Manual when providing detailed explanations\n" +
            "\n\n" +
            "Always provide practical, actionable advice that users can immediately apply to their JMeter test plans. Format your responses with clear sections and code examples when applicable.\n" +
            "\n" +
            "When describing script components or configuration, use proper formatting:\n" +
            "- Code blocks for scripts and commands\n" +
            "- Bullet points for steps and options\n" +
            "- Tables for comparing options when appropriate\n" +
            "- Bold for element names and important concepts\n" +
            "\n" +
            "Version: JMeter 5.6+ (Also support questions about older versions from 3.0+)";

    public ClaudeService() {
        // Default history size of 10, can be configured through jmeter.properties
        this.maxHistorySize = Integer.parseInt(AiConfig.getProperty("claude.max.history.size", "10"));

        // Initialize the client
        String API_KEY = AiConfig.getProperty("anthropic.api.key", "YOUR_API_KEY");
        
        // Check if logging should be enabled
        String loggingLevel = AiConfig.getProperty("anthropic.log.level", "");
        if (!loggingLevel.isEmpty()) {
            // Set the environment variable for the Anthropic client logging
            System.setProperty("ANTHROPIC_LOG", loggingLevel);
            log.info("Enabled Anthropic client logging with level: {}", loggingLevel);
        }
        
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .build();

        // Get default model from properties or use SONNET if not specified
        this.currentModelId = AiConfig.getProperty("claude.default.model", "claude-3-sonnet-20240229");
        this.temperature = Float.parseFloat(AiConfig.getProperty("claude.temperature", "0.7"));
        
        // Load system prompt from properties or use default
        try {
            systemPrompt = AiConfig.getProperty("claude.system.prompt", DEFAULT_JMETER_SYSTEM_PROMPT);
            
            if (systemPrompt == null) {
                log.warn("System prompt is null, using default");
                systemPrompt = DEFAULT_JMETER_SYSTEM_PROMPT;
            }
            
            log.info("Loaded system prompt from properties (length: {})", systemPrompt.length());
            // Only log the first 100 characters of the system prompt to avoid flooding the logs
            log.info("System prompt (first 100 chars): {}", 
                    systemPrompt.substring(0, Math.min(100, systemPrompt.length())));
        } catch (Exception e) {
            log.error("Error loading system prompt, using default", e);
            systemPrompt = DEFAULT_JMETER_SYSTEM_PROMPT;
        }
    }

    public AnthropicClient getClient() {
        return client;
    }

    public void setModel(String modelId) {
        this.currentModelId = modelId;
        log.info("Model set to: {}", modelId);
    }

    public String getCurrentModel() {
        return currentModelId;
    }

    public void setTemperature(float temperature) {
        if (temperature < 0 || temperature >= 1) {
            log.warn("Temperature must be between 0 and 1. Provided value: {}. Setting to default 0.7", temperature);
            this.temperature = 0.7f;
        } else {
            this.temperature = temperature;
            log.info("Temperature set to: {}", temperature);
        }
    }

    public float getTemperature() {
        return temperature;
    }

    public String sendMessage(String message) {
        log.info("Sending message to Claude: {}", message);
        return generateResponse(java.util.Collections.singletonList(message));
    }

    public String generateResponse(List<String> conversation) {
        try {
            // Check if the last message is asking to add or delete a JMeter element
            if (!conversation.isEmpty()) {
                String lastMessage = conversation.get(conversation.size() - 1);
                String response = JMeterElementRequestHandler.processElementRequest(lastMessage);
                if (response != null) {
                    return response;
                }
            }

            // Limit conversation history to configured size
            int startIndex = Math.max(0, conversation.size() - maxHistorySize);
            List<String> limitedConversation = conversation.subList(startIndex, conversation.size());

            // Ensure a model is set
            if (currentModelId == null || currentModelId.isEmpty()) {
                currentModelId = "claude-3-sonnet-20240229";
                log.warn("No model was set, defaulting to: {}", currentModelId);
            }

            // Ensure a temperature is set
            if (temperature < 0 || temperature > 1) {
                temperature = 0.7f;
                log.warn("Invalid temperature value ({}), defaulting to: {}", temperature, 0.7f);
            }

            // Log which model is being used for this message
            log.info("Generating response using model: {} and temperature: {}", currentModelId, temperature);
            log.info("Using system prompt (first 100 chars): {}", systemPrompt.substring(0, Math.min(100, systemPrompt.length())));

            MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
                    .maxTokens(1024L)
                    .temperature(temperature)
                    .model(currentModelId)
                    .system(systemPrompt);

            // Add messages alternating between user and assistant
            for (int i = 0; i < limitedConversation.size(); i++) {
                String msg = limitedConversation.get(i);
                if (i % 2 == 0) {
                    // User messages
                    paramsBuilder.addUserMessage(msg);
                } else {
                    // Assistant (Claude) messages
                    paramsBuilder.addAssistantMessage(msg);
                }
            }

            MessageCreateParams params = paramsBuilder.build();
            log.info("Request parameters: maxTokens={}, temperature={}, model={}, systemPromptLength={}, messagesCount={}",
                    params.maxTokens(), params.temperature(), params.model(), 
                    systemPrompt.length(), limitedConversation.size());

            Message message = client.messages().create(params);

            log.info(message.content().toString());

            return String.valueOf(message.content().get(0).text().get().text());
        } catch (Exception e) {
            log.error("Error generating response", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Generates a response from Claude for a single message without conversation
     * history
     * This is useful for context-specific queries like the @this command
     * 
     * @param message The message to send to Claude
     * @return The response from Claude
     */
    public String generateDirectResponse(String message) {
        try {
            log.info("Generating direct response for message: {}",
                    message.substring(0, Math.min(100, message.length())));

            // Ensure a model is set
            if (currentModelId == null || currentModelId.isEmpty()) {
                currentModelId = "claude-3-sonnet-20240229";
                log.warn("No model was set, defaulting to: {}", currentModelId);
            }

            // Ensure a temperature is set
            if (temperature < 0 || temperature > 1) {
                temperature = 0.7f;
                log.warn("Invalid temperature value ({}), defaulting to: {}", temperature, 0.7f);
            }

            // Log which model is being used for this message
            log.info("Generating direct response using model: {} and temperature: {}", currentModelId, temperature);
            log.info("Using system prompt (first 100 chars): {}", systemPrompt.substring(0, Math.min(100, systemPrompt.length())));

            MessageCreateParams params = MessageCreateParams.builder()
                    .maxTokens(1024L)
                    .temperature(temperature)
                    .model(currentModelId)
                    .system(systemPrompt)
                    .addUserMessage(message)
                    .build();
            
            log.info("Request parameters: maxTokens={}, temperature={}, model={}, systemPromptLength={}, messageLength={}",
                    params.maxTokens(), params.temperature(), params.model(), 
                    systemPrompt.length(), message.length());

            Message response = client.messages().create(params);

            log.info(response.content().toString());

            return String.valueOf(response.content().get(0).text().get().text());
        } catch (Exception e) {
            log.error("Error generating direct response", e);
            return "Error: " + e.getMessage();
        }
    }

    public String getName() {
        return "Anthropic Claude";
    }
}