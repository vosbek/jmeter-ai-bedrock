package org.qainsights.jmeter.ai.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling natural language requests to add or delete JMeter
 * elements.
 */
public class JMeterElementRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(JMeterElementRequestHandler.class);

    // Pattern to match requests to add a JMeter element
    private static final Pattern ADD_ELEMENT_PATTERN = Pattern.compile(
            "(?i)\\b(add|insert|create|include)\\b.*?\\b([a-z\\s-]+?)\\b(?:\\s+(?:called|named)\\s+\"([^\"]+)\")?(?:\\s*$|\\s+(?:and|,|;|then)\\b|\\s+to\\b)");

    // Pattern to match requests to delete a JMeter element
    private static final Pattern DELETE_ELEMENT_PATTERN = Pattern.compile(
            "(?i)\\b(delete|remove|drop|erase)\\b.*?(?:\\b(current|selected|this)\\b.*?)?\\b(element|node|component|item)\\b");

    // Pattern to match requests to delete a JMeter element by name
    private static final Pattern DELETE_ELEMENT_BY_NAME_PATTERN = Pattern.compile(
            "(?i)\\b(delete|remove|drop|erase)\\b.*?(?:the|a|an)?\\s+([a-z\\s-]+?)\\s+(?:called|named|with name|with the name)?\\s+[\"']?([^\"']+?)[\"']?(?:\\s*$|\\s+(?:from|in)\\b)");

    // Pattern to match separators for multiple instructions
    private static final Pattern INSTRUCTION_SEPARATOR = Pattern.compile(
            "(?i)\\b(and|also|then|next|,|;)\\b");

    // Common synonyms and variations for element types
    private static final Map<String, List<String>> ELEMENT_SYNONYMS = new HashMap<>();

    static {
        // Initialize element synonyms
        initializeElementSynonyms();
    }

    /**
     * Initializes the map of element synonyms.
     */
    private static void initializeElementSynonyms() {
        // Samplers (most common)
        addSynonyms("httpsampler", "http sampler", "http request", "web request", "http", "request",
                "http request sampler");

        // Controllers (most common)
        addSynonyms("loopcontroller", "loop controller", "loop", "repeat controller");
        addSynonyms("ifcontroller", "if controller", "conditional controller", "condition");
        addSynonyms("whilecontroller", "while controller", "while loop", "while");
        addSynonyms("transactioncontroller", "transaction controller", "transaction", "tx controller");
        addSynonyms("runtimecontroller", "runtime controller", "runtime", "timed controller");

        // Config Elements (most common)
        addSynonyms("headermanager", "header manager", "http headers", "headers");
        addSynonyms("csvdataset", "csv data set", "csv", "data set", "csv config");

        // Thread Groups (essential)
        addSynonyms("threadgroup", "thread group", "users", "virtual users");

        // Assertions (most common)
        addSynonyms("responseassert", "response assertion", "response validator", "text assertion");
        addSynonyms("jsonassertion", "json assertion", "json path assertion", "json validator");
        addSynonyms("durationassertion", "duration assertion", "response time assertion", "time assertion");
        addSynonyms("sizeassertion", "size assertion", "response size assertion", "byte size assertion");
        addSynonyms("xpathassertion", "xpath assertion", "xml assertion", "xml validator");

        // Timers (most common)
        addSynonyms("constanttimer", "constant timer", "fixed timer", "delay", "wait", "timer");
        addSynonyms("uniformrandomtimer", "uniform random timer", "random timer", "uniform timer");
        addSynonyms("gaussianrandomtimer", "gaussian random timer", "gaussian timer", "normal distribution timer");
        addSynonyms("poissonrandomtimer", "poisson random timer", "poisson timer");

        // Extractors (most common)
        addSynonyms("regexextractor", "regex extractor", "regular expression extractor", "regex", "regexp");
        addSynonyms("xpathextractor", "xpath extractor", "xml extractor", "xpath");
        addSynonyms("jsonpathextractor", "json extractor", "jsonpath extractor", "json path extractor", "jsonpath");
        addSynonyms("boundaryextractor", "boundary extractor", "text extractor", "boundary");

        // Listeners (most common)
        addSynonyms("viewresultstree", "view results tree", "results tree", "view results", "results viewer");
        addSynonyms("aggregatereport", "aggregate report", "summary report", "statistics", "stats");
    }

    /**
     * Adds synonyms for an element type.
     * 
     * @param elementType The normalized element type
     * @param synonyms    The synonyms for the element type
     */
    private static void addSynonyms(String elementType, String... synonyms) {
        List<String> synonymList = new ArrayList<>();
        for (String synonym : synonyms) {
            synonymList.add(synonym.toLowerCase());
        }
        ELEMENT_SYNONYMS.put(elementType, synonymList);
    }

    /**
     * Processes a user message to determine if it's requesting to add or delete a
     * JMeter element.
     * 
     * @param message The user message to process
     * @return A response message if the request was handled, null otherwise
     */
    public static String processElementRequest(String message) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }

        // Check if the message contains multiple instructions
        List<String> instructions = splitIntoInstructions(message);

        if (instructions.size() > 1) {
            return processMultipleInstructions(instructions);
        }

        // Process single instruction
        String response = processAddElementRequest(message);
        if (response != null) {
            return response;
        }

        response = processDeleteElementRequest(message);
        if (response != null) {
            return response;
        }

        // No element request detected
        return null;
    }

    /**
     * Splits a message into multiple instructions based on separators.
     * 
     * @param message The message to split
     * @return A list of instructions
     */
    private static List<String> splitIntoInstructions(String message) {
        List<String> instructions = new ArrayList<>();

        log.info("Original message: {}", message);

        // First, check if the message starts with an action verb (add, create, etc.)
        boolean startsWithAction = message.toLowerCase().matches("^\\s*(?:add|insert|create|include)\\b.*");
        log.info("Message starts with action verb: {}", startsWithAction);

        if (startsWithAction) {
            // If the message starts with an action verb, we need to be careful about
            // splitting
            // For example: "add a thread group, HTTP request, and a timer"
            // We want to split this into: ["add a thread group", "HTTP request", "a timer"]
            // But we need to prepend "add" to the second and third instructions

            // First, extract the action verb
            String actionVerb = message.replaceAll("(?i)^\\s*(add|insert|create|include)\\b.*", "$1").trim();
            log.info("Action verb: {}", actionVerb);

            // Split by common separators
            String[] parts = message.split("(?i)\\s*(?:,|;)\\s+|\\s+and\\s+");
            log.info("Split into {} parts: {}", parts.length, java.util.Arrays.toString(parts));

            // Process each part
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();

                // Skip empty parts
                if (part.isEmpty()) {
                    continue;
                }

                // If this is not the first part and it doesn't start with an action verb,
                // prepend the action verb
                if (i > 0 && !part.toLowerCase().matches("^\\s*(?:add|insert|create|include)\\b.*")) {
                    part = actionVerb + " " + part;
                    log.info("Prepended action verb to part: {}", part);
                }

                instructions.add(part);
            }
        } else {
            // If the message doesn't start with an action verb, just split by common
            // separators
            String[] parts = message.split("(?i)\\s*(?:,|;)\\s+|\\s+and\\s+");

            // Add each part as an instruction
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    instructions.add(trimmed);
                }
            }
        }

        // If no parts were found, treat the whole message as one instruction
        if (instructions.isEmpty()) {
            instructions.add(message.trim());
        }

        log.info("Split message into {} instructions: {}", instructions.size(), instructions);
        return instructions;
    }

    /**
     * Processes multiple instructions and returns a combined response.
     * 
     * @param instructions The list of instructions to process
     * @return A combined response message
     */
    private static String processMultipleInstructions(List<String> instructions) {
        List<String> responses = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String instruction : instructions) {
            log.info("Processing instruction: {}", instruction);

            // Skip instructions that are just separators
            if (instruction.matches("(?i)\\b(and|also|then|next)\\b")) {
                log.info("Skipping separator instruction: {}", instruction);
                continue;
            }

            // Process add element request
            String response = processAddElementRequest(instruction);
            if (response != null) {
                log.info("Processed add element request: {}", response);
                if (response.startsWith("I've added")) {
                    responses.add(response);
                } else {
                    errors.add(response);
                }
                continue;
            }

            // Process delete element request
            response = processDeleteElementRequest(instruction);
            if (response != null) {
                log.info("Processed delete element request: {}", response);
                if (response.startsWith("I've deleted")) {
                    responses.add(response);
                } else {
                    errors.add(response);
                }
                continue;
            }

            // Try to extract just the element type from the instruction
            String elementType = extractElementType(instruction);
            if (elementType != null) {
                log.info("Extracted element type from instruction: {}", elementType);
                boolean success = JMeterElementManager.addElement(elementType, null);
                if (success) {
                    String elementName = JMeterElementManager.getDefaultNameForElement(elementType);
                    String successResponse = "I've added a " + elementName
                            + " to your test plan. You can now configure it as needed.";
                    log.info("Successfully added element: {}", successResponse);
                    responses.add(successResponse);
                } else {
                    String errorResponse = "I tried to add a "
                            + JMeterElementManager.getDefaultNameForElement(elementType) +
                            ", but encountered an error. " +
                            "Please make sure you have a test plan open and a node selected where the element can be added.";
                    log.info("Failed to add element: {}", errorResponse);
                    errors.add(errorResponse);
                }
                continue;
            }

            // Instruction not recognized
            log.info("Instruction not recognized: {}", instruction);
            errors.add("I couldn't understand what to do with: \"" + instruction + "\"");
        }

        // Build the combined response
        StringBuilder result = new StringBuilder();

        if (!responses.isEmpty()) {
            if (responses.size() == 1) {
                result.append(responses.get(0));
            } else {
                result.append("I've made the following changes:\n\n");
                for (int i = 0; i < responses.size(); i++) {
                    result.append(i + 1).append(". ").append(responses.get(i)).append("\n");
                }
            }
        }

        if (!errors.isEmpty()) {
            if (!responses.isEmpty()) {
                result.append("\n\nHowever, I encountered some issues:\n\n");
            }

            for (int i = 0; i < errors.size(); i++) {
                result.append(i + 1).append(". ").append(errors.get(i)).append("\n");
            }
        }

        log.info("Final response: {}", result.toString());
        return result.toString();
    }

    /**
     * Processes a user message to determine if it's requesting to add a JMeter
     * element.
     * 
     * @param message The user message to process
     * @return A response message if the request was handled, null otherwise
     */
    private static String processAddElementRequest(String instruction) {
        log.info("Processing add element request: {}", instruction);

        // Check if the instruction is an add element request
        Matcher matcher = ADD_ELEMENT_PATTERN.matcher(instruction);
        if (!matcher.find()) {
            log.info("Instruction does not match add element pattern: {}", instruction);
            return null;
        }

        // Extract the element type and name
        String requestedElementType = matcher.group(2).trim();
        String elementName = matcher.group(3);

        log.info("Extracted element type: {} and name: {}", requestedElementType, elementName);

        // Map the requested element type to a supported type
        String elementType = mapToSupportedElementType(requestedElementType);

        if (elementType == null) {
            log.info("Could not map requested element type: {} to a supported type", requestedElementType);

            // Try to extract the element type directly from the instruction
            elementType = extractElementType(instruction);

            if (elementType == null) {
                log.info("Could not extract element type from instruction: {}", instruction);
                return "I couldn't find a JMeter element type \"" + requestedElementType + "\".";
            }
        }

        log.info("Mapped element type: {}", elementType);

        // Check if a test plan is open and a node is selected
        JMeterElementManager.TestPlanStatus status = JMeterElementManager.isTestPlanReady();
        if (!status.isReady()) {
            log.error("Test plan is not ready: {}", status.getErrorMessage());

            // If no test plan exists, try to create one
            if (status.getErrorMessage().contains("No test plan is currently open")) {
                boolean created = JMeterElementManager.ensureTestPlanExists();
                if (created) {
                    log.info("Created a new test plan");

                    // Select the test plan node
                    boolean selected = JMeterElementManager.selectTestPlanNode();

                    // Try again after creating a test plan
                    status = JMeterElementManager.isTestPlanReady();
                    if (status.isReady()) {
                        // Now we can add the element
                        return addElementAfterTestPlanCheck(elementType, elementName);
                    } else {
                        if (selected) {
                            // We selected the test plan node but still can't add the element
                            // This is likely because the element can't be added directly to the test plan
                            return "I've created a new test plan for you and selected it. " +
                                    "However, a " + JMeterElementManager.getDefaultNameForElement(elementType) +
                                    " cannot be added directly to the test plan. " +
                                    "Please add a Thread Group first, then try adding the " +
                                    JMeterElementManager.getDefaultNameForElement(elementType) + " again.";
                        } else {
                            return "I've created a new test plan for you. Please select the Test Plan node and try adding the "
                                    +
                                    JMeterElementManager.getDefaultNameForElement(elementType) + " again.";
                        }
                    }
                } else {
                    return "I'd like to add a " + JMeterElementManager.getDefaultNameForElement(elementType) +
                            " for you, but I couldn't create a test plan. Please create a test plan manually first.";
                }
            }

            return "I'd like to add a " + JMeterElementManager.getDefaultNameForElement(elementType) +
                    " for you, but " + status.getErrorMessage();
        }

        return addElementAfterTestPlanCheck(elementType, elementName);
    }

    /**
     * Adds an element after confirming the test plan is ready.
     * 
     * @param elementType The type of element to add
     * @param elementName The name for the element (can be null)
     * @return A response message
     */
    private static String addElementAfterTestPlanCheck(String elementType, String elementName) {
        log.info("Adding element of type: {} with name: {}", elementType, elementName);

        // Try to add the element to the test plan
        boolean success = JMeterElementManager.addElement(elementType, elementName);

        if (success) {
            String response = "I've added a " + JMeterElementManager.getDefaultNameForElement(elementType) +
                    (elementName != null ? " called \"" + elementName + "\"" : "") +
                    " to your test plan. You can now configure it as needed.";
            log.info("Successfully added element: {}", response);
            return response;
        } else {
            String response = "I tried to add a " + JMeterElementManager.getDefaultNameForElement(elementType) +
                    ", but encountered an error. " +
                    "This might be due to compatibility issues with the selected node. Please try selecting a different node in your test plan.";
            log.error("Failed to add element: {}", response);
            return response;
        }
    }

    /**
     * Processes a user message to determine if it's requesting to delete a JMeter
     * element.
     * 
     * @param message The user message to process
     * @return A response message if the request was handled, null otherwise
     */
    private static String processDeleteElementRequest(String message) {
        Matcher matcher = DELETE_ELEMENT_PATTERN.matcher(message);

        if (matcher.find()) {
            log.info("Detected request to delete the currently selected element");
            
            // Check if test plan is ready
            JMeterElementManager.TestPlanStatus status = JMeterElementManager.isTestPlanReady();
            if (!status.isReady()) {
                return "I couldn't delete the element because " + status.getErrorMessage().toLowerCase() + 
                       ". Please make sure you have a test plan open and an element selected.";
            }

            // Delete the element
            boolean success = JMeterElementManager.deleteSelectedElement();

            if (success) {
                return "I've deleted the selected element from your test plan.";
            } else {
                return "I tried to delete the selected element, but encountered an error. " +
                        "Please make sure you have selected a node that can be deleted (you cannot delete the test plan itself).";
            }
        }

        matcher = DELETE_ELEMENT_BY_NAME_PATTERN.matcher(message);
        if (matcher.find()) {
            log.info("Detected request to delete an element by name");

            // Extract the element type and name
            String requestedElementType = matcher.group(2).trim();
            String elementName = matcher.group(3);

            log.info("Extracted element type: {} and name: {}", requestedElementType, elementName);

            // Map the requested element type to a supported type
            String elementType = mapToSupportedElementType(requestedElementType);

            if (elementType == null) {
                log.info("Could not map requested element type: {} to a supported type", requestedElementType);

                // Try to extract the element type directly from the instruction
                elementType = extractElementType(message);

                if (elementType == null) {
                    log.info("Could not extract element type from instruction: {}", message);
                    return "I couldn't find a JMeter element type \"" + requestedElementType + "\".";
                }
            }

            log.info("Mapped element type: {}", elementType);

            // Check if a test plan is open and a node is selected
            JMeterElementManager.TestPlanStatus status = JMeterElementManager.isTestPlanReady();
            if (!status.isReady()) {
                log.error("Test plan is not ready: {}", status.getErrorMessage());

                // If no test plan exists, try to create one
                if (status.getErrorMessage().contains("No test plan is currently open")) {
                    boolean created = JMeterElementManager.ensureTestPlanExists();
                    if (created) {
                        log.info("Created a new test plan");

                        // Select the test plan node
                        boolean selected = JMeterElementManager.selectTestPlanNode();

                        // Try again after creating a test plan
                        status = JMeterElementManager.isTestPlanReady();
                        if (status.isReady()) {
                            // Now we can delete the element
                            return deleteElementAfterTestPlanCheck(elementType, elementName);
                        } else {
                            if (selected) {
                                // We selected the test plan node but still can't delete the element
                                // This is likely because the element can't be deleted directly from the test plan
                                return "I've created a new test plan for you and selected it. " +
                                        "However, a " + JMeterElementManager.getDefaultNameForElement(elementType) +
                                        " cannot be deleted directly from the test plan. " +
                                        "Please select the parent node of the " +
                                        JMeterElementManager.getDefaultNameForElement(elementType) + " and try deleting it again.";
                            } else {
                                return "I've created a new test plan for you. Please select the Test Plan node and try deleting the "
                                        +
                                        JMeterElementManager.getDefaultNameForElement(elementType) + " again.";
                            }
                        }
                    } else {
                        return "I'd like to delete a " + JMeterElementManager.getDefaultNameForElement(elementType) +
                                " for you, but I couldn't create a test plan. Please create a test plan manually first.";
                    }
                }

                return "I'd like to delete a " + JMeterElementManager.getDefaultNameForElement(elementType) +
                        " for you, but " + status.getErrorMessage();
            }

            return deleteElementAfterTestPlanCheck(elementType, elementName);
        }

        return null;
    }

    /**
     * Deletes an element after confirming the test plan is ready.
     * 
     * @param elementType The type of element to delete
     * @param elementName The name of the element to delete
     * @return A response message
     */
    private static String deleteElementAfterTestPlanCheck(String elementType, String elementName) {
        log.info("Deleting element of type: {} with name: {}", elementType, elementName);

        // Try to delete the element from the test plan
        boolean success = JMeterElementManager.deleteElement(elementType, elementName);

        if (success) {
            String response = "I've deleted the " + JMeterElementManager.getDefaultNameForElement(elementType) +
                    " called \"" + elementName + "\" from your test plan.";
            log.info("Successfully deleted element: {}", response);
            return response;
        } else {
            String response = "I tried to delete the " + JMeterElementManager.getDefaultNameForElement(elementType) +
                    " called \"" + elementName + "\", but encountered an error. " +
                    "Please make sure the element exists in your test plan and try again.";
            log.error("Failed to delete element: {}", response);
            return response;
        }
    }

    /**
     * Finds element types that are similar to the given element type.
     * 
     * @param elementType The element type to find similar types for
     * @return A list of similar element types
     */
    private static List<String> findSimilarElementTypes(String elementType) {
        List<String> suggestions = new ArrayList<>();

        if (elementType == null || elementType.trim().isEmpty()) {
            return suggestions;
        }

        String normalized = elementType.toLowerCase().trim();

        // Check against synonyms
        for (Map.Entry<String, List<String>> entry : ELEMENT_SYNONYMS.entrySet()) {
            String type = entry.getKey();
            List<String> synonyms = entry.getValue();

            for (String synonym : synonyms) {
                // Check if there's some overlap between the requested type and the synonym
                if (synonym.contains(normalized) || normalized.contains(synonym)) {
                    String defaultName = JMeterElementManager.getDefaultNameForElement(type);
                    if (!suggestions.contains(defaultName)) {
                        suggestions.add(defaultName);
                    }
                    break;
                }
            }
        }

        return suggestions;
    }

    /**
     * Maps a requested element type to a supported element type using synonyms.
     * 
     * @param requestedType The requested element type
     * @return The mapped element type, or null if no mapping was found
     */
    private static String mapToSupportedElementType(String requestedType) {
        if (requestedType == null || requestedType.trim().isEmpty()) {
            return null;
        }

        // Normalize the requested type
        String normalized = requestedType.toLowerCase().trim();
        log.info("Mapping requested type: '{}' (normalized: '{}')", requestedType, normalized);

        // First, check if the normalized type is directly supported
        String directMatch = JMeterElementManager.normalizeElementType(normalized);
        log.info("Normalized element type: '{}'", directMatch);

        if (JMeterElementManager.isElementTypeSupported(directMatch)) {
            log.info("Direct match found: '{}'", directMatch);
            return directMatch;
        }

        // Check against synonyms
        for (Map.Entry<String, List<String>> entry : ELEMENT_SYNONYMS.entrySet()) {
            String elementType = entry.getKey();
            List<String> synonyms = entry.getValue();

            log.info("Checking element type '{}' with synonyms: {}", elementType, synonyms);

            for (String synonym : synonyms) {
                // Check for exact match
                if (normalized.equals(synonym)) {
                    log.info("Exact synonym match found: '{}' -> '{}'", synonym, elementType);
                    return elementType;
                }

                // Check if the normalized type contains the synonym
                if (normalized.contains(synonym) || synonym.contains(normalized)) {
                    log.info("Partial synonym match found: '{}' contains or is contained in '{}'", normalized, synonym);
                    return elementType;
                }
            }
        }

        // No mapping found
        return null;
    }

    /**
     * Extracts the element type from a given instruction.
     * 
     * @param instruction The instruction to extract the element type from
     * @return The extracted element type, or null if no type was found
     */
    private static String extractElementType(String instruction) {
        log.info("Extracting element type from: {}", instruction);

        // Try to find a word that matches an element type
        String[] words = instruction.split("\\s+");

        // First, check for direct matches with supported element types
        for (String word : words) {
            String normalized = word.toLowerCase().trim();
            if (JMeterElementManager.isElementTypeSupported(normalized)) {
                log.info("Found direct match for element type: {}", normalized);
                return normalized;
            }
        }

        // Next, check for matches with element synonyms
        for (String word : words) {
            String normalized = word.toLowerCase().trim();
            String mappedType = mapToSupportedElementType(normalized);
            if (mappedType != null) {
                log.info("Found mapped element type: {} for word: {}", mappedType, normalized);
                return mappedType;
            }
        }

        // Try common element types that might be in the instruction
        String normalized = instruction.toLowerCase();
        log.info("Checking for common element types in: {}", normalized);

        // Check for thread group
        if (normalized.contains("thread group") ||
                (normalized.contains("thread") && normalized.contains("group")) ||
                normalized.equals("thread group") ||
                normalized.equals("threadgroup")) {
            log.info("Identified as thread group");
            return "threadgroup";
        }

        // Check for timer types
        if (normalized.contains("timer") || normalized.contains("delay") || normalized.contains("wait")) {
            if (normalized.contains("random")) {
                if (normalized.contains("uniform")) {
                    log.info("Identified as uniform random timer");
                    return "uniformrandomtimer";
                } else if (normalized.contains("gaussian") || normalized.contains("normal")) {
                    log.info("Identified as gaussian random timer");
                    return "gaussianrandomtimer";
                } else if (normalized.contains("poisson")) {
                    log.info("Identified as poisson random timer");
                    return "poissonrandomtimer";
                } else {
                    log.info("Identified as uniform random timer (default random timer)");
                    return "uniformrandomtimer";
                }
            } else {
                log.info("Identified as constant timer");
                return "constanttimer";
            }
        }

        // Check for HTTP request
        if (normalized.contains("http") ||
                (normalized.contains("http")) ||
                normalized.equals("http request") ||
                normalized.equals("httprequest") ||
                normalized.equals("http sampler") ||
                normalized.equals("httpsampler")) {
            log.info("Identified as HTTP request");
            return "httpsampler";
        }

        // Check for controller types
        if (normalized.contains("controller")) {
            if (normalized.contains("loop")) {
                log.info("Identified as loop controller");
                return "loopcontroller";
            } else if (normalized.contains("if") || normalized.contains("condition")) {
                log.info("Identified as if controller");
                return "ifcontroller";
            } else if (normalized.contains("while")) {
                log.info("Identified as while controller");
                return "whilecontroller";
            } else if (normalized.contains("transaction") || normalized.contains("tx")) {
                log.info("Identified as transaction controller");
                return "transactioncontroller";
            } else if (normalized.contains("runtime") || normalized.contains("time")) {
                log.info("Identified as runtime controller");
                return "runtimecontroller";
            }
        }

        // Check for assertions
        if (normalized.contains("assertion") || normalized.contains("assert") || normalized.contains("validator")) {
            if (normalized.contains("json") || normalized.contains("jsonpath")) {
                log.info("Identified as JSON assertion");
                return "jsonassertion";
            } else if (normalized.contains("duration") || normalized.contains("time")) {
                log.info("Identified as duration assertion");
                return "durationassertion";
            } else if (normalized.contains("size") || normalized.contains("byte")) {
                log.info("Identified as size assertion");
                return "sizeassertion";
            } else if (normalized.contains("xpath") || normalized.contains("xml")) {
                log.info("Identified as xpath assertion");
                return "xpathassertion";
            } else {
                log.info("Identified as response assertion");
                return "responseassert";
            }
        }

        // Check for extractor types
        if (normalized.contains("extractor") || normalized.contains("extract")) {
            if (normalized.contains("regex") || normalized.contains("regular expression")) {
                log.info("Identified as regex extractor");
                return "regexextractor";
            } else if (normalized.contains("xpath") || normalized.contains("xml")) {
                log.info("Identified as xpath extractor");
                return "xpathextractor";
            } else if (normalized.contains("json") || normalized.contains("jsonpath")) {
                log.info("Identified as json path extractor");
                return "jsonpathextractor";
            } else if (normalized.contains("boundary") || normalized.contains("text")) {
                log.info("Identified as boundary extractor");
                return "boundaryextractor";
            } else {
                log.info("Identified as regex extractor (default)");
                return "regexextractor";
            }
        }

        // Check for listeners
        if (normalized.contains("listener") || normalized.contains("result") || normalized.contains("view") ||
                normalized.contains("report") || normalized.contains("graph") || normalized.contains("statistics") ||
                normalized.contains("stats")) {

            if (normalized.contains("tree") || normalized.contains("view")) {
                log.info("Identified as view results tree");
                return "viewresultstree";
            } else if (normalized.contains("aggregate") || normalized.contains("statistics")
                    || normalized.contains("stats")) {
                log.info("Identified as aggregate report");
                return "aggregatereport";
            } else {
                // Default to view results tree if no specific type is mentioned
                log.info("Defaulting to view results tree");
                return "viewresultstree";
            }
        }

        // Special case handling for common phrases
        if (normalized.equals("a thread group") || normalized.equals("thread group")) {
            log.info("Special case: identified as thread group");
            return "threadgroup";
        } else if (normalized.equals("a timer") || normalized.equals("timer")) {
            log.info("Special case: identified as constant timer");
            return "constanttimer";
        } else if (normalized.equals("a http request") || normalized.equals("http request") ||
                normalized.equals("a request") || normalized.equals("request")) {
            log.info("Special case: identified as HTTP request");
            return "httpsampler";
        }

        // No element type found
        log.info("No element type found for: {}", instruction);
        return null;
    }
}
