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

    public ClaudeService() {
        // Default history size of 10, can be configured through jmeter.properties
        this.maxHistorySize = Integer.parseInt(AiConfig.getProperty("claude.max.history.size", "10"));

        // Initialize the client
        String API_KEY = AiConfig.getProperty("anthropic.api.key", "YOUR_API_KEY");
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .build();

        // Get default model from properties or use SONNET if not specified
        this.currentModelId = AiConfig.getProperty("claude.default.model", "claude-3-sonnet-20240229");
        this.temperature = Float.parseFloat(AiConfig.getProperty("claude.temperature", "0.7"));
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

            MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
                    .maxTokens(1024L)
                    .temperature(temperature)
                    .model(currentModelId);

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

            Message message = client.messages().create(paramsBuilder.build());

            log.info(message.content().toString());

            return String.valueOf(message.content().get(0).text().get().text());
        } catch (Exception e) {
            log.error("Error generating response", e);
            return "Error: " + e.getMessage();
        }
    }

    public String getName() {
        return "Anthropic Claude";
    }
}