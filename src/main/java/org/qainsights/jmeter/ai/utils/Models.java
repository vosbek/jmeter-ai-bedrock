package org.qainsights.jmeter.ai.utils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import com.anthropic.models.ModelInfo;
import com.anthropic.models.ModelListPage;
import com.anthropic.models.ModelListParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.Model;

import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.FoundationModelSummary;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsRequest;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsResponse;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Models {
    private static final Logger log = LoggerFactory.getLogger(Models.class);

    /**
     * Get a combined list of model IDs from Anthropic, OpenAI, and Bedrock
     * @param anthropicClient Anthropic client
     * @param openAiClient OpenAI client
     * @param includeBedrockModels Whether to include Bedrock models
     * @return List of model IDs
     */
    public static List<String> getModelIds(AnthropicClient anthropicClient, OpenAIClient openAiClient, boolean includeBedrockModels) {
        List<String> modelIds = new ArrayList<>();
        
        try {
            // Get Anthropic models
            List<String> anthropicModels = getAnthropicModelIds(anthropicClient);
            if (anthropicModels != null) {
                modelIds.addAll(anthropicModels);
            }
            
            // Get OpenAI models
            List<String> openAiModels = getOpenAiModelIds(openAiClient);
            if (openAiModels != null) {
                modelIds.addAll(openAiModels);
            }
            
            // Get Bedrock models if requested
            if (includeBedrockModels) {
                List<String> bedrockModels = getBedrockModelIds();
                if (bedrockModels != null) {
                    modelIds.addAll(bedrockModels);
                }
            }
            
            log.info("Combined {} models from Anthropic, OpenAI{}", modelIds.size(), includeBedrockModels ? ", and Bedrock" : "");
            return modelIds;
        } catch (Exception e) {
            log.error("Error combining models: {}", e.getMessage(), e);
            return modelIds; // Return whatever we have, even if empty
        }
    }

    /**
     * Get a combined list of model IDs from both Anthropic and OpenAI (backward compatibility)
     * @param anthropicClient Anthropic client
     * @param openAiClient OpenAI client
     * @return List of model IDs
     */
    public static List<String> getModelIds(AnthropicClient anthropicClient, OpenAIClient openAiClient) {
        return getModelIds(anthropicClient, openAiClient, false);
    }
    
    /**
     * Get Anthropic models as ModelListPage
     * @param client Anthropic client
     * @return ModelListPage containing Anthropic models
     */
    public static ModelListPage getAnthropicModels(AnthropicClient client) {
        try {
            log.info("Fetching available models from Anthropic API");
            client = AnthropicOkHttpClient.builder()
                    .apiKey(AiConfig.getProperty("anthropic.api.key", "YOUR_API_KEY"))
                    .build();

            ModelListParams modelListParams = ModelListParams.builder().build();
            ModelListPage models = client.models().list(modelListParams);
            
            log.info("Successfully retrieved {} models from Anthropic API", models.data().size());
            for (ModelInfo model : models.data()) {
                log.debug("Available Anthropic model: {}", model.id());
            }
            return models;
        } catch (Exception e) {
            log.error("Error fetching models from Anthropic API: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get Anthropic model IDs as a List of Strings
     * @param client Anthropic client
     * @return List of model IDs
     */
    public static List<String> getAnthropicModelIds(AnthropicClient client) {
        ModelListPage models = getAnthropicModels(client);
        if (models != null && models.data() != null) {
            return models.data().stream()
                    .map(ModelInfo::id)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
    
    /**
     * Get OpenAI models as ModelListPage
     * @param client OpenAI client
     * @return OpenAI ModelListPage
     */
    public static com.openai.models.ModelListPage getOpenAiModels(OpenAIClient client) {
        try {
            log.info("Fetching available models from OpenAI API");
            client = OpenAIOkHttpClient.builder()
                    .apiKey(AiConfig.getProperty("openai.api.key", "YOUR_API_KEY"))
                    .build();            

            com.openai.models.ModelListPage models = client.models().list();
            
            log.info("Successfully retrieved {} models from OpenAI API", models.data().size());
            for (Model model : models.data()) {
                log.debug("Available OpenAI model: {}", model.id());
            }
            return models;
        } catch (Exception e) {
            log.error("Error fetching models from OpenAI API: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get OpenAI model IDs as a List of Strings
     * @param client OpenAI client
     * @return List of model IDs
     */
    public static List<String> getOpenAiModelIds(OpenAIClient client) {
        com.openai.models.ModelListPage models = getOpenAiModels(client);
        if (models != null && models.data() != null) {
            // Return the list of GPT models only, excluding audio and TTS models
            return models.data().stream()
                    .filter(model -> model.id().startsWith("gpt")) // Include only GPT models
                    .filter(model -> !model.id().contains("audio")) // Exclude audio models
                    .filter(model -> !model.id().contains("tts")) // Exclude text-to-speech models
                    .filter(model -> !model.id().contains("whisper")) // Exclude whisper models
                    .filter(model -> !model.id().contains("davinci")) // Exclude Davinci models
                    .filter(model -> !model.id().contains("search")) // Exclude search models
                    .filter(model -> !model.id().contains("transcribe")) // Exclude transcribe models
                    .filter(model -> !model.id().contains("realtime")) // Exclude realtime models
                    .filter(model -> !model.id().contains("instruct")) // Exclude instruct models
                    .map(com.openai.models.Model::id)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    /**
     * Get AWS Bedrock models
     * @return List of Bedrock model IDs with bedrock: prefix
     */
    public static List<String> getBedrockModelIds() {
        List<String> modelIds = new ArrayList<>();
        
        try {
            log.info("Fetching available models from AWS Bedrock");
            
            String region = AiConfig.getProperty("bedrock.region", "us-east-1");
            
            BedrockClient bedrockClient = BedrockClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

            // List foundation models
            ListFoundationModelsRequest request = ListFoundationModelsRequest.builder()
                    .byOutputModality("TEXT") // Only get text models
                    .build();
            
            ListFoundationModelsResponse response = bedrockClient.listFoundationModels(request);
            
            for (FoundationModelSummary model : response.modelSummaries()) {
                String modelId = model.modelId();
                
                // Filter for Claude models and useful text models
                if (modelId.contains("anthropic.claude") || 
                    modelId.contains("us.anthropic.claude") ||
                    modelId.contains("amazon.titan-text") ||
                    modelId.contains("meta.llama")) {
                    
                    // Add bedrock: prefix to distinguish from direct Anthropic models
                    modelIds.add("bedrock:" + modelId);
                    log.debug("Added Bedrock model: {}", modelId);
                }
            }
            
            log.info("Successfully retrieved {} Bedrock models", modelIds.size());
            return modelIds;
            
        } catch (Exception e) {
            log.error("Error fetching models from AWS Bedrock: {}", e.getMessage(), e);
            
            // If we can't fetch models, provide some known common ones
            log.info("Falling back to predefined Bedrock models");
            modelIds.add("bedrock:us.anthropic.claude-3-5-sonnet-20241022-v2:0");
            modelIds.add("bedrock:us.anthropic.claude-3-sonnet-20240229-v1:0");
            modelIds.add("bedrock:anthropic.claude-3-5-sonnet-20241022-v2:0");
            modelIds.add("bedrock:anthropic.claude-3-sonnet-20240229-v1:0");
            
            return modelIds;
        }
    }

    /**
     * Get detailed Bedrock model information
     * @return List of FoundationModelSummary objects
     */
    public static List<FoundationModelSummary> getBedrockModels() {
        try {
            log.info("Fetching detailed model information from AWS Bedrock");
            
            String region = AiConfig.getProperty("bedrock.region", "us-east-1");
            
            BedrockClient bedrockClient = BedrockClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

            ListFoundationModelsRequest request = ListFoundationModelsRequest.builder()
                    .byOutputModality("TEXT") // Only get text models
                    .build();
            
            ListFoundationModelsResponse response = bedrockClient.listFoundationModels(request);
            
            // Filter for useful models
            List<FoundationModelSummary> filteredModels = response.modelSummaries().stream()
                    .filter(model -> {
                        String modelId = model.modelId();
                        return modelId.contains("anthropic.claude") || 
                               modelId.contains("us.anthropic.claude") ||
                               modelId.contains("amazon.titan-text") ||
                               modelId.contains("meta.llama");
                    })
                    .collect(Collectors.toList());
            
            log.info("Successfully retrieved {} filtered Bedrock models", filteredModels.size());
            return filteredModels;
            
        } catch (Exception e) {
            log.error("Error fetching detailed models from AWS Bedrock: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}
