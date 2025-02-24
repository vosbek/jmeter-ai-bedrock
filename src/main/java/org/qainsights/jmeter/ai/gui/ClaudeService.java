package org.qainsights.jmeter.ai.gui;

import java.util.List;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.Message;
import com.anthropic.models.MessageCreateParams;
import com.anthropic.models.Model;
import org.qainsights.jmeter.ai.utils.AiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ClaudeService {
    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);

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

            MessageCreateParams params = MessageCreateParams.builder()
                    .maxTokens(1024L)
                    .addUserMessage(conversation.toString())
                    .model(Model.CLAUDE_3_5_SONNET_20240620)
                    .build();
            Message message = client.messages().create(params);

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