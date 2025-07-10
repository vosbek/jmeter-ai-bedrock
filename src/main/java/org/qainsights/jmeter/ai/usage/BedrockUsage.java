package org.qainsights.jmeter.ai.usage;

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.qainsights.jmeter.ai.utils.AiConfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Class to track and provide AWS Bedrock token usage information.
 */
public class BedrockUsage {
    private static final Logger log = LoggerFactory.getLogger(BedrockUsage.class);

    // Singleton instance
    private static final BedrockUsage INSTANCE = new BedrockUsage();

    // AWS Bedrock client for API calls
    private BedrockRuntimeClient client;

    // Store usage history
    private final List<UsageRecord> usageHistory = new ArrayList<>();

    // JSON parser for response parsing
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Private constructor for singleton
    private BedrockUsage() {
        initializeClient();
    }

    /**
     * Initialize the AWS Bedrock client
     */
    private void initializeClient() {
        try {
            String region = AiConfig.getProperty("bedrock.region", "us-east-1");
            
            // Initialize the AWS Bedrock client
            client = BedrockRuntimeClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

            log.info("AWS Bedrock client initialized for usage tracking in region: {}", region);
        } catch (Exception e) {
            log.error("Failed to initialize AWS Bedrock client for usage tracking", e);
        }
    }

    /**
     * Get the singleton instance of BedrockUsage.
     *
     * @return The singleton instance
     */
    public static BedrockUsage getInstance() {
        return INSTANCE;
    }

    /**
     * Record usage from an InvokeModelResponse.
     *
     * @param response The InvokeModelResponse from AWS Bedrock
     * @param model    The model used for the completion
     */
    public void recordUsage(InvokeModelResponse response, String model) {
        if (response == null) {
            log.warn("Unable to record usage - response is null");
            return;
        }

        try {
            // Extract usage information from response body
            String responseBody = response.body().asUtf8String();
            JsonNode responseJson = objectMapper.readTree(responseBody);
            
            // Extract usage data (Claude models in Bedrock typically return usage info)
            JsonNode usage = responseJson.get("usage");
            
            long promptTokens = 0;
            long completionTokens = 0;
            
            if (usage != null) {
                JsonNode inputTokens = usage.get("input_tokens");
                JsonNode outputTokens = usage.get("output_tokens");
                
                if (inputTokens != null) {
                    promptTokens = inputTokens.asLong();
                }
                if (outputTokens != null) {
                    completionTokens = outputTokens.asLong();
                }
            } else {
                // Fallback: estimate tokens if usage info not available
                log.warn("No usage information found in response, using token estimation");
                promptTokens = estimateTokens(responseBody);
                completionTokens = estimateTokens(responseBody);
            }
            
            long totalTokens = promptTokens + completionTokens;

            // Clean up model name (remove potential "bedrock:" prefix)
            String cleanModelName = model.startsWith("bedrock:") ? model.substring(8) : model;

            // Record usage
            UsageRecord record = new UsageRecord(
                    new Date(),
                    cleanModelName,
                    promptTokens,
                    completionTokens,
                    totalTokens);

            usageHistory.add(record);
            log.info("Recorded usage: {}", record);
        } catch (JsonProcessingException e) {
            log.error("Error parsing response JSON for usage tracking", e);
        } catch (Exception e) {
            log.error("Error recording usage", e);
        }
    }

    /**
     * Record usage with manual token counts (for cases where response doesn't contain usage info).
     *
     * @param model            The model used for the completion
     * @param promptTokens     The number of prompt tokens (input)
     * @param completionTokens The number of completion tokens (output)
     */
    public void recordUsage(String model, long promptTokens, long completionTokens) {
        try {
            long totalTokens = promptTokens + completionTokens;

            // Clean up model name (remove potential "bedrock:" prefix)
            String cleanModelName = model.startsWith("bedrock:") ? model.substring(8) : model;

            // Record usage
            UsageRecord record = new UsageRecord(
                    new Date(),
                    cleanModelName,
                    promptTokens,
                    completionTokens,
                    totalTokens);

            usageHistory.add(record);
            log.info("Recorded usage: {}", record);
        } catch (Exception e) {
            log.error("Error recording usage", e);
        }
    }

    /**
     * Estimates the number of tokens for a given text.
     * This is a rough estimate as AWS Bedrock doesn't always provide exact token counts.
     * Uses a heuristic of characters/4 which works reasonably well in practice.
     * 
     * @param text The text to estimate tokens for
     * @return Estimated token count
     */
    private long estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Rough estimation: average token is ~4 characters
        return Math.max(1, text.length() / 4);
    }

    /**
     * Set the AWS Bedrock client for usage tracking
     * 
     * @param client The BedrockRuntimeClient to use
     */
    public void setClient(BedrockRuntimeClient client) {
        this.client = client;
        log.info("AWS Bedrock client set for usage tracking");
    }

    /**
     * Get usage summary as a formatted string.
     *
     * @return The usage summary
     */
    public String getUsageSummary() {
        if (usageHistory.isEmpty()) {
            return "No AWS Bedrock usage data available. Try using the Bedrock service first.";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("# AWS Bedrock Usage Summary\n\n");

        // Summary totals
        long totalPromptTokens = 0;
        long totalCompletionTokens = 0;
        long totalTokens = 0;

        // Calculate totals
        for (UsageRecord record : usageHistory) {
            totalPromptTokens += record.promptTokens;
            totalCompletionTokens += record.completionTokens;
            totalTokens += record.totalTokens;
        }

        // Add summary information
        summary.append("## Overall Summary\n");
        summary.append("- **Total Conversations**: ").append(usageHistory.size()).append("\n");
        summary.append("- **Total Input Tokens**: ").append(totalPromptTokens).append("\n");
        summary.append("- **Total Output Tokens**: ").append(totalCompletionTokens).append("\n");
        summary.append("- **Total Tokens**: ").append(totalTokens).append("\n\n");

        // Add pricing note
        summary.append("## Pricing Information\n");
        summary.append("For up-to-date pricing information, please visit AWS Bedrock's official pricing page:\n");
        summary.append("https://aws.amazon.com/bedrock/pricing/\n\n");
        summary.append("AWS Bedrock pricing varies by model and region and may change over time.\n");
        summary.append("Inference profiles may offer different pricing than on-demand models.\n\n");

        // Add detail for the last 10 conversations using a more readable format
        summary.append("## Recent Conversations\n");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // Get the most recent 10 records or fewer if less than 10 exist
        int startIndex = Math.max(0, usageHistory.size() - 10);
        for (int i = startIndex; i < usageHistory.size(); i++) {
            UsageRecord record = usageHistory.get(i);
            summary.append("### Conversation ").append(i + 1 - startIndex).append("\n");
            summary.append("- **Date**: ").append(dateFormat.format(record.timestamp)).append("\n");
            summary.append("- **Model**: ").append(record.model).append("\n");
            summary.append("- **Input Tokens**: ").append(record.promptTokens).append("\n");
            summary.append("- **Output Tokens**: ").append(record.completionTokens).append("\n");
            summary.append("- **Total Tokens**: ").append(record.totalTokens).append("\n\n");
        }

        return summary.toString();
    }

    /**
     * Class to store a single usage record.
     */
    private static class UsageRecord {
        private final Date timestamp;
        private final String model;
        private final long promptTokens;
        private final long completionTokens;
        private final long totalTokens;

        public UsageRecord(Date timestamp, String model, long promptTokens, long completionTokens,
                long totalTokens) {
            this.timestamp = timestamp;
            this.model = model;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        @Override
        public String toString() {
            return "UsageRecord{" +
                    "timestamp=" + timestamp +
                    ", model='" + model + '\'' +
                    ", promptTokens=" + promptTokens +
                    ", completionTokens=" + completionTokens +
                    ", totalTokens=" + totalTokens +
                    '}';
        }
    }
}