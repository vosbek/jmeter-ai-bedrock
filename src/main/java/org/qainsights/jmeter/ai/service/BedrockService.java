package org.qainsights.jmeter.ai.service;

import java.util.List;

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.qainsights.jmeter.ai.utils.AiConfig;
import org.qainsights.jmeter.ai.usage.BedrockUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * BedrockService class for AWS Bedrock integration.
 */
public class BedrockService implements AiService {
    private static final Logger log = LoggerFactory.getLogger(BedrockService.class);
    
    private final int maxHistorySize;
    private String currentModelId;
    private float temperature;
    private final BedrockRuntimeClient client;
    private String systemPrompt;
    private boolean systemPromptInitialized = false;
    private long maxTokens;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Default system prompt - reuse the same JMeter-focused prompt as Claude
    private static final String DEFAULT_JMETER_SYSTEM_PROMPT = "You are a JMeter expert assistant embedded in a JMeter plugin called 'Feather Wand - JMeter Agent'. "
            +
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
            "- Config Elements (CSV Data Set, HTTP Request Defaults, HTTP Header Manager, HTTP Cookie Manager, User Defined Variables)\n"
            +
            "- Pre-Processors (BeanShell, JSR223, Regular Expression User Parameters, User Parameters)\n" +
            "- Post-Processors (Regular Expression Extractor, JSON Extractor, XPath Extractor, Boundary Extractor, JMESPath Extractor)\n"
            +
            "- Assertions (Response, JSON Path, Duration, Size, XPath, JSR223, MD5Hex)\n" +
            "- Timers (Constant, Uniform Random, Gaussian Random, Poisson Random, Constant Throughput, Precise Throughput)\n"
            +
            "- Listeners (View Results Tree, Aggregate Report, Summary Report, Backend Listener, Response Time Graph)\n"
            +
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
            "6. Keep responses focused on the JMeter domain and avoid generic testing advice unless specifically relevant\n"
            +
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
            "Always provide practical, actionable advice that users can immediately apply to their JMeter test plans. Format your responses with clear sections and code examples when applicable.\n"
            +
            "\n" +
            "When describing script components or configuration, use proper formatting:\n" +
            "- Code blocks for scripts and commands\n" +
            "- Bullet points for steps and options\n" +
            "- Tables for comparing options when appropriate\n" +
            "- Bold for element names and important concepts\n" +
            "\n" +
            "Version: JMeter 5.6+ (Also support questions about older versions from 3.0+)";

    public BedrockService() {
        // Default history size of 10, can be configured through jmeter.properties
        this.maxHistorySize = Integer.parseInt(AiConfig.getProperty("bedrock.max.history.size", "10"));

        // Validate configuration before initializing
        validateConfiguration();

        // Initialize the client with AWS credentials
        String region = AiConfig.getProperty("bedrock.region", "us-east-1");
        
        try {
            this.client = BedrockRuntimeClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
            
            log.info("AWS Bedrock client initialized for region: {}", region);
            
            // Test the connection with a simple operation (this will validate credentials)
            testConnection();
            
        } catch (Exception e) {
            log.error("Failed to initialize AWS Bedrock client", e);
            throw new RuntimeException("Failed to initialize AWS Bedrock client: " + extractUserFriendlyErrorMessage(e), e);
        }

        // Get default model from properties or use Claude 3.5 Sonnet inference profile
        this.currentModelId = AiConfig.getProperty("bedrock.default.model", "us.anthropic.claude-3-5-sonnet-20241022-v2:0");
        this.temperature = Float.parseFloat(AiConfig.getProperty("bedrock.temperature", "0.5"));
        this.maxTokens = Long.parseLong(AiConfig.getProperty("bedrock.max.tokens", "1024"));

        // Load system prompt from properties or use default
        try {
            systemPrompt = AiConfig.getProperty("bedrock.system.prompt", DEFAULT_JMETER_SYSTEM_PROMPT);

            if (systemPrompt == null) {
                log.warn("System prompt is null, using default");
                systemPrompt = DEFAULT_JMETER_SYSTEM_PROMPT;
            }

            log.info("Loaded system prompt from properties (length: {})", systemPrompt.length());
            // Only log the first 100 characters of the system prompt to avoid flooding the
            // logs
            log.info("System prompt (first 100 chars): {}",
                    systemPrompt.substring(0, Math.min(100, systemPrompt.length())));
        } catch (Exception e) {
            log.error("Error loading system prompt, using default", e);
            systemPrompt = DEFAULT_JMETER_SYSTEM_PROMPT;
        }
    }

    public BedrockRuntimeClient getClient() {
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
            log.warn("Temperature must be between 0 and 1. Provided value: {}. Setting to default 0.5", temperature);
            this.temperature = 0.5f;
        } else {
            this.temperature = temperature;
            log.info("Temperature set to: {}", temperature);
        }
    }

    public float getTemperature() {
        return temperature;
    }

    public long getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(long maxTokens) {
        this.maxTokens = maxTokens;
        log.info("Max tokens set to: {}", maxTokens);
    }

    /**
     * Resets the system prompt initialization flag.
     * This should be called when starting a new conversation.
     */
    public void resetSystemPromptInitialization() {
        this.systemPromptInitialized = false;
        log.info("Reset system prompt initialization flag");
    }

    public String sendMessage(String message) {
        log.info("Sending message to AWS Bedrock: {}", message);
        return generateResponse(java.util.Collections.singletonList(message));
    }

    public String generateResponse(List<String> conversation) {
        try {
            log.info("Generating response for conversation with {} messages", conversation.size());

            // Ensure a model is set
            if (currentModelId == null || currentModelId.isEmpty()) {
                currentModelId = "us.anthropic.claude-3-5-sonnet-20241022-v2:0";
                log.warn("No model was set, defaulting to: {}", currentModelId);
            }

            // Ensure a temperature is set
            if (temperature < 0 || temperature > 1) {
                temperature = 0.5f;
                log.warn("Invalid temperature value ({}), defaulting to: {}", temperature, 0.5f);
            }

            // Log which model is being used for this conversation
            log.info("Generating response using model: {} and temperature: {}", currentModelId, temperature);

            // Check if this is the first message in a conversation
            boolean isFirstMessage = !systemPromptInitialized;
            if (isFirstMessage) {
                log.info("Using system prompt (first 100 chars): {}",
                        systemPrompt.substring(0, Math.min(100, systemPrompt.length())));
                systemPromptInitialized = true;
            } else {
                log.info("Using previously initialized conversation with system prompt");
            }

            // Limit conversation history to last maxHistorySize messages to avoid token limits
            List<String> limitedConversation = conversation;
            if (conversation.size() > maxHistorySize) {
                limitedConversation = conversation.subList(conversation.size() - maxHistorySize, conversation.size());
                log.info("Limiting conversation to last {} messages", limitedConversation.size());
            }

            // Build the request payload for Claude in Bedrock
            ObjectNode requestPayload = objectMapper.createObjectNode();
            requestPayload.put("anthropic_version", "bedrock-2023-05-31");
            requestPayload.put("max_tokens", maxTokens);
            requestPayload.put("temperature", temperature);
            
            // Add system prompt if this is the first message
            if (isFirstMessage) {
                requestPayload.put("system", systemPrompt);
            }

            // Build messages array
            ObjectNode messagesArray = objectMapper.createObjectNode();
            StringBuilder messageBuilder = new StringBuilder();
            
            // Add conversation history
            for (int i = 0; i < limitedConversation.size(); i++) {
                String msg = limitedConversation.get(i);
                if (i % 2 == 0) {
                    // User messages
                    messageBuilder.append("Human: ").append(msg).append("\n\n");
                } else {
                    // Assistant messages
                    messageBuilder.append("Assistant: ").append(msg).append("\n\n");
                }
            }

            // Add current message if it's not already included
            if (!messageBuilder.toString().endsWith("Human: " + limitedConversation.get(limitedConversation.size() - 1) + "\n\n")) {
                messageBuilder.append("Human: ").append(limitedConversation.get(limitedConversation.size() - 1)).append("\n\n");
            }
            
            messageBuilder.append("Assistant:");
            
            // For Claude models, we need to format the request differently
            ObjectNode messages = objectMapper.createObjectNode();
            messages.put("role", "user");
            messages.put("content", messageBuilder.toString());
            
            requestPayload.set("messages", objectMapper.createArrayNode().add(messages));

            // Convert to JSON string
            String jsonPayload = objectMapper.writeValueAsString(requestPayload);
            log.info("Request payload: {}", jsonPayload);

            // Create the Bedrock request
            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(currentModelId)
                    .body(SdkBytes.fromUtf8String(jsonPayload))
                    .build();

            // Invoke the model
            InvokeModelResponse response = client.invokeModel(request);

            // Parse the response
            String responseBody = response.body().asUtf8String();
            log.info("Response body: {}", responseBody);

            JsonNode responseJson = objectMapper.readTree(responseBody);
            String responseText = "";
            
            // Extract the response text
            if (responseJson.has("content")) {
                JsonNode content = responseJson.get("content");
                if (content.isArray() && content.size() > 0) {
                    JsonNode firstContent = content.get(0);
                    if (firstContent.has("text")) {
                        responseText = firstContent.get("text").asText();
                    }
                }
            }

            // Record usage
            try {
                BedrockUsage.getInstance().recordUsage(response, currentModelId);
                log.info("Recorded usage for model: {}", currentModelId);
            } catch (Exception e) {
                log.error("Failed to record usage", e);
            }

            return responseText;
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON", e);
            return "Error: Failed to process JSON request/response";
        } catch (Exception e) {
            log.error("Error generating response", e);
            
            // Extract and format error message for better readability
            String errorMessage = extractUserFriendlyErrorMessage(e);
            return "Error: " + errorMessage;
        }
    }

    /**
     * Generates a response from the AI using the specified model.
     * 
     * @param conversation The conversation history
     * @param model        The specific model to use for this request
     * @return The AI's response
     */
    public String generateResponse(List<String> conversation, String model) {
        log.info("Generating response with specified model: {}", model);

        // Store current model
        String originalModel = this.currentModelId;

        try {
            // Set the specified model
            this.currentModelId = model;

            // Generate the response using the specified model
            return generateResponse(conversation);
        } finally {
            // Restore the original model
            this.currentModelId = originalModel;
            log.info("Restored original model: {}", originalModel);
        }
    }

    /**
     * Extracts a user-friendly error message from an exception
     * 
     * @param e The exception to extract the error message from
     * @return A user-friendly error message
     */
    private String extractUserFriendlyErrorMessage(Exception e) {
        String errorMessage = e.getMessage();
        String className = e.getClass().getSimpleName();

        // Check for AWS credential-related errors (common on Windows)
        if (errorMessage != null && (errorMessage.contains("Unable to load AWS credentials") ||
                errorMessage.contains("The AWS Access Key Id you provided does not exist") ||
                errorMessage.contains("NoCredentialsProvided") ||
                className.contains("NoCredentialsException"))) {
            return "AWS credentials not found. Please configure your AWS credentials using one of these methods:\n" +
                   "1. Set environment variables: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY\n" +
                   "2. Use AWS CLI: 'aws configure'\n" +
                   "3. Set up AWS SSO profile\n" +
                   "For detailed setup instructions, see the plugin documentation.";
        }

        // Check for region-specific errors
        if (errorMessage != null && (errorMessage.contains("InvalidSignatureException") ||
                errorMessage.contains("SignatureDoesNotMatch"))) {
            return "AWS signature error. This might be due to incorrect credentials or system clock issues. " +
                   "Please verify your AWS credentials and ensure your system time is correct.";
        }

        // Check for common AWS Bedrock errors
        if (errorMessage != null && errorMessage.contains("AccessDenied")) {
            return "Access denied. Please check your AWS credentials and permissions for Bedrock. " +
                   "Ensure your IAM user/role has 'bedrock:InvokeModel' permission.";
        }

        // Check for model not found error
        if (errorMessage != null && errorMessage.contains("ModelNotFound")) {
            return "The selected model was not found. Please check the model ID and try again. " +
                   "Ensure the model is available in your AWS region (" + 
                   AiConfig.getProperty("bedrock.region", "us-east-1") + ").";
        }

        // Check for region availability issues
        if (errorMessage != null && (errorMessage.contains("UnknownEndpoint") ||
                errorMessage.contains("InvalidEndpoint") ||
                errorMessage.contains("EndpointConnectionError"))) {
            return "AWS Bedrock service endpoint error. Please check that AWS Bedrock is available in your region (" +
                   AiConfig.getProperty("bedrock.region", "us-east-1") + "). " +
                   "Consider using 'us-east-1' which has the most model availability.";
        }

        // Check for throttling error
        if (errorMessage != null && errorMessage.contains("ThrottlingException")) {
            return "Request was throttled by AWS Bedrock. Please try again in a few moments.";
        }

        // Check for quota exceeded error
        if (errorMessage != null && errorMessage.contains("ServiceQuotaExceededException")) {
            return "Service quota exceeded. Please check your AWS Bedrock limits in the AWS console " +
                   "or request a quota increase.";
        }

        // Check for validation error
        if (errorMessage != null && errorMessage.contains("ValidationException")) {
            return "Invalid request parameters. Please check your Bedrock configuration in jmeter.properties.";
        }

        // Check for connection/network errors (common on Windows with firewalls)
        if (errorMessage != null && (errorMessage.contains("UnknownHostException") ||
                errorMessage.contains("ConnectException") ||
                errorMessage.contains("SocketTimeoutException") ||
                errorMessage.contains("Connection refused"))) {
            return "Network connection error. Please check your internet connection and firewall settings. " +
                   "If you're behind a corporate firewall, you may need to configure proxy settings.";
        }

        // Check for SSL/TLS errors (can occur on Windows with corporate networks)
        if (errorMessage != null && (errorMessage.contains("SSLException") ||
                errorMessage.contains("CertificateException") ||
                errorMessage.contains("SSLHandshakeException"))) {
            return "SSL/TLS connection error. This may be due to corporate firewall or proxy settings. " +
                   "Contact your IT administrator if you're on a corporate network.";
        }

        // For other errors, provide a cleaner message with troubleshooting hints
        if (errorMessage != null) {
            return errorMessage + "\n\nTroubleshooting tips:\n" +
                   "- Verify AWS credentials are configured\n" +
                   "- Check that Bedrock is available in your region\n" +
                   "- Ensure model access is enabled in AWS console";
        }

        // If we couldn't extract a specific error message, return a generic one with help
        return "An error occurred while communicating with AWS Bedrock. Please check your configuration:\n" +
               "- AWS credentials (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)\n" +
               "- AWS region setting (bedrock.region in jmeter.properties)\n" +
               "- Network connectivity and firewall settings";
    }

    /**
     * Validates the Bedrock service configuration
     */
    private void validateConfiguration() {
        log.info("Validating Bedrock service configuration");
        
        // Check required properties
        String region = AiConfig.getProperty("bedrock.region", "us-east-1");
        String model = AiConfig.getProperty("bedrock.default.model", "");
        
        if (region == null || region.trim().isEmpty()) {
            throw new RuntimeException("Bedrock region not configured. Please set 'bedrock.region' in jmeter.properties");
        }
        
        if (model == null || model.trim().isEmpty()) {
            log.warn("No default Bedrock model configured. Using fallback model.");
        }
        
        // Validate region format
        try {
            Region.of(region);
        } catch (Exception e) {
            throw new RuntimeException("Invalid AWS region: " + region + ". Please check 'bedrock.region' in jmeter.properties");
        }
        
        // Check for common configuration issues on Windows
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            // Check if AWS CLI is available
            try {
                ProcessBuilder pb = new ProcessBuilder("aws", "--version");
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    log.info("AWS CLI detected on Windows system");
                } else {
                    log.warn("AWS CLI not found. Please install AWS CLI for easier credential management on Windows");
                }
            } catch (Exception e) {
                log.warn("AWS CLI not available: {}", e.getMessage());
            }
        }
        
        log.info("Bedrock configuration validation completed");
    }
    
    /**
     * Tests the connection to AWS Bedrock
     */
    private void testConnection() {
        try {
            log.info("Testing AWS Bedrock connection");
            
            // Try to list foundation models as a connection test
            // This validates both credentials and network connectivity
            software.amazon.awssdk.services.bedrock.BedrockClient testClient = 
                software.amazon.awssdk.services.bedrock.BedrockClient.builder()
                    .region(Region.of(AiConfig.getProperty("bedrock.region", "us-east-1")))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
                    
            software.amazon.awssdk.services.bedrock.model.ListFoundationModelsRequest request = software.amazon.awssdk.services.bedrock.model.ListFoundationModelsRequest.builder()
                    .build();
                    
            software.amazon.awssdk.services.bedrock.model.ListFoundationModelsResponse response = testClient.listFoundationModels(request);
            
            if (response.modelSummaries() != null && !response.modelSummaries().isEmpty()) {
                log.info("AWS Bedrock connection test successful. {} models available", response.modelSummaries().size());
            } else {
                log.warn("AWS Bedrock connection successful but no models found. This may indicate a permission issue.");
            }
            
            testClient.close();
            
        } catch (Exception e) {
            log.warn("AWS Bedrock connection test failed: {}", e.getMessage());
            // Don't throw exception here - let the service try to work anyway
            // Some users might have limited permissions that don't allow listing models
        }
    }

    public String getName() {
        return "AWS Bedrock";
    }
}