package org.qainsights.jmeter.ai.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.Message;
import com.anthropic.models.MessageCreateParams;
import com.anthropic.models.Model;
import org.qainsights.jmeter.ai.utils.AiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ClaudeService class.
 */
public class ClaudeService {
    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);
    private final int maxHistorySize;

    public ClaudeService() {
        // Default history size of 10, can be configured through jmeter.properties
        this.maxHistorySize = Integer.parseInt(AiConfig.getProperty("claude.max.history.size", "10"));
    }

    public String sendMessage(String message) {
        log.info("Sending message to Claude: {}", message);
        return generateResponse(java.util.Collections.singletonList(message));
    }

    public String generateResponse(List<String> conversation) {
        try {
            // Reading it from jmeter.properties
            String API_KEY = AiConfig.getProperty("anthropic.api.key", "YOUR_API_KEY");

            AnthropicClient client = AnthropicOkHttpClient.builder()
                    .apiKey(API_KEY)
                    .build();

            // Limit conversation history to configured size
            int startIndex = Math.max(0, conversation.size() - maxHistorySize);
            List<String> limitedConversation = conversation.subList(startIndex, conversation.size());

            MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
                    .maxTokens(1024L)
                    .model(Model.CLAUDE_3_5_SONNET_20240620);

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
            log.info(e.getMessage());
        }
        return "";

    }

    public String getName() {
        return "Anthropic Claude";
    }
}