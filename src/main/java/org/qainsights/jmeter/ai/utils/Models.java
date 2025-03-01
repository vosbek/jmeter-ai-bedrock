package org.qainsights.jmeter.ai.utils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import com.anthropic.models.ModelInfo;
import com.anthropic.models.ModelListPage;
import com.anthropic.models.ModelListParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Models {
    private static final Logger log = LoggerFactory.getLogger(Models.class);

    public static ModelListPage getModels(AnthropicClient client) {
        try {
            log.info("Fetching available models from Anthropic API");
            client = AnthropicOkHttpClient.builder()
                    .apiKey(AiConfig.getProperty("anthropic.api.key", "YOUR_API_KEY"))
                    .build();

            ModelListParams modelListParams = ModelListParams.builder().build();
            ModelListPage models = client.models().list(modelListParams);
            
            log.info("Successfully retrieved {} models from Anthropic API", models.data().size());
            for (ModelInfo model : models.data()) {
                log.debug("Available model: {}", model.id());
            }
            return models;
        } catch (Exception e) {
            log.error("Error fetching models from Anthropic API: {}", e.getMessage(), e);
            return null;
        }
    }
}
