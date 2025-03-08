package org.qainsights.jmeter.ai.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.qainsights.jmeter.ai.optimizer.OptimizeRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for handling natural language requests to add JMeter
 * elements to the test plan.
 */
public class JMeterElementRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(JMeterElementRequestHandler.class);

    // Pattern to match requests to add a JMeter element
    private static final Pattern ADD_ELEMENT_PATTERN = Pattern.compile(
            "(?i)\\b(add|create|insert|include)\\b.*?(?:a|an)?\\s+([a-z0-9\\s-]+?)(?:\\s+(?:called|named|with name|with the name)?\\s+[\"']?([^\"']+?)[\"']?)?(?:\\s*$|\\s+(?:to|in)\\b)");

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

        // JSR223 Elements
        addSynonyms("jsr223sampler", "jsr223 sampler", "jsr223", "jsr 223", "jsr 223 sampler", "javascript sampler",
                "groovy sampler", "java sampler");
        addSynonyms("jsr223preprocessor", "jsr223 preprocessor", "jsr223 pre processor", "jsr223 pre",
                "jsr 223 pre processor", "javascript preprocessor", "groovy preprocessor");
        addSynonyms("jsr223postprocessor", "jsr223 postprocessor", "jsr223 post processor", "jsr223 post",
                "jsr 223 post processor", "javascript postprocessor", "groovy postprocessor");

        // Additional elements from ELEMENT_CLASS_MAP
        addSynonyms("ftprequest", "ftp request", "ftp sampler");
        addSynonyms("httprequest", "http request", "web request", "http", "request");
        addSynonyms("jdbcrequest", "jdbc request", "database request", "sql request");
        addSynonyms("javarequest", "java request", "java sampler");
        addSynonyms("ldaprequest", "ldap request", "ldap sampler");
        addSynonyms("ldapeextendedrequest", "ldap extended request", "ldap ext sampler");
        addSynonyms("accesslogsampler", "access log sampler", "log sampler");
        addSynonyms("beanshellsampler", "beanshell sampler", "bsh sampler");
        addSynonyms("tcpsampler", "tcp sampler", "tcp request");
        addSynonyms("jmspublisher", "jms publisher", "jms publish");
        addSynonyms("jmssubscriber", "jms subscriber", "jms subscribe");
        addSynonyms("jmspointtopoint", "jms point to point", "jms p2p");
        addSynonyms("junitrequest", "junit request", "junit sampler");
        addSynonyms("mailreadersampler", "mail reader sampler", "email sampler");
        addSynonyms("flowcontrolaction", "flow control action", "test action");
        addSynonyms("smtpsampler", "smtp sampler", "smtp request");
        addSynonyms("osprocesssampler", "os process sampler", "system sampler");
        addSynonyms("mongodbscript", "mongodb script", "mongo script");
        addSynonyms("boltrequest", "bolt request", "bolt sampler");
        addSynonyms("simplecontroller", "simple controller", "generic controller");
        addSynonyms("onceonlycontroller", "once only controller", "once controller");
        addSynonyms("interleavecontroller", "interleave controller", "interleave");
        addSynonyms("randomcontroller", "random controller", "random");
        addSynonyms("randomordercontroller", "random order controller", "random order");
        addSynonyms("throughputcontroller", "throughput controller", "throughput");
        addSynonyms("switchcontroller", "switch controller", "switch");
        addSynonyms("foreachcontroller", "foreach controller", "for each controller");
        addSynonyms("modulecontroller", "module controller", "module");
        addSynonyms("includecontroller", "include controller", "include");
        addSynonyms("recordingcontroller", "recording controller", "recording");
        addSynonyms("criticalsectioncontroller", "critical section controller", "critical section");
        addSynonyms("sampleresultsaveconfiguration", "sample result save configuration", "save configuration");
        addSynonyms("graphresults", "graph results", "graph visualizer");
        addSynonyms("assertionresults", "assertion results", "assertion visualizer");
        addSynonyms("viewresultsintable", "view results in table", "table visualizer");
        addSynonyms("simpledatawriter", "simple data writer", "data writer");
        addSynonyms("aggregategraph", "aggregate graph", "stat graph visualizer");
        addSynonyms("responsetimegraph", "response time graph", "response time visualizer");
        addSynonyms("mailervisualizer", "mailer visualizer", "mailer");
        addSynonyms("beanshelllistener", "beanshell listener", "bsh listener");
        addSynonyms("summaryreport", "summary report", "summary");
        addSynonyms("saveresponsestoafile", "save responses to a file", "result saver");
        addSynonyms("jsr223listener", "jsr223 listener", "jsr223");
        addSynonyms("generatesummaryresults", "generate summary results", "summariser");
        addSynonyms("comparisonassertionvisualizer", "comparison assertion visualizer", "comparison visualizer");
        addSynonyms("backendlistener", "backend listener", "backend");
        addSynonyms("csvdatasetconfig", "csv data set config", "csv config");
        addSynonyms("ftprequestdefaults", "ftp request defaults", "ftp defaults");
        addSynonyms("dnscachemanager", "dns cache manager", "dns manager");
        addSynonyms("httpauthorizationmanager", "http authorization manager", "auth manager");
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
     * Processes a user message to determine if it's requesting to add a JMeter
     * element.
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

        response = processOptimizeTestPlanRequest(message);
        if (response != null) {
            return response;
        }

        // If the message doesn't match any of the patterns, return null
        // This will cause the message to be sent to the AI for processing
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
        boolean startsWithAction = message.toLowerCase()
                .matches("^\\s*(?:add|insert|create|include)\\b.*");
        log.info("Message starts with action verb: {}", startsWithAction);

        if (startsWithAction) {
            // If the message starts with an action verb, we need to be careful about
            // splitting
            // For example: "add a thread group, HTTP request, and a timer"
            // We want to split this into: ["add a thread group", "HTTP request", "a timer"]
            // But we need to prepend "add" to the second and third instructions

            // First, extract the action verb
            String actionVerb = message.replaceAll("(?i)^\\s*(add|insert|create|include)\\b.*", "$1")
                    .trim();
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

            // Process optimize test plan request
            response = processOptimizeTestPlanRequest(instruction);
            if (response != null) {
                log.info("Processed optimize test plan request: {}", response);
                responses.add(response);
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
    private static String processAddElementRequest(String message) {
        log.info("Processing add element request: {}", message);

        // Check if the message matches the add element pattern
        Matcher matcher = ADD_ELEMENT_PATTERN.matcher(message);
        if (!matcher.find()) {
            log.info("Message does not match add element pattern: {}", message);
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
                    " for you, but " + status.getErrorMessage().toLowerCase() +
                    ". Please make sure you have a test plan open.";
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

        // Get the currently selected node before adding the element
        GuiPackage guiPackage = GuiPackage.getInstance();
        JMeterTreeNode currentNode = null;
        if (guiPackage != null) {
            currentNode = guiPackage.getTreeListener().getCurrentNode();
        }

        // Try to add the element to the test plan
        boolean success = JMeterElementManager.addElement(elementType, elementName);

        if (success) {
            // Expand the node after adding the element
            if (currentNode != null) {
                try {
                    // Expand the current node to show the newly added element
                    guiPackage.getMainFrame().getTree()
                            .expandPath(new javax.swing.tree.TreePath(currentNode.getPath()));
                    log.info("Successfully expanded node: {}", currentNode.getName());

                    // Select the newly added element (last child of current node)
                    if (currentNode.getChildCount() > 0) {
                        JMeterTreeNode lastChild = (JMeterTreeNode) currentNode
                                .getChildAt(currentNode.getChildCount() - 1);
                        guiPackage.getTreeListener().getJTree()
                                .setSelectionPath(new javax.swing.tree.TreePath(lastChild.getPath()));
                        log.info("Selected newly added element: {}", lastChild.getName());
                    }
                } catch (Exception e) {
                    log.error("Failed to expand node or select element", e);
                    // Not returning an error here as the element was still added successfully
                }
            }

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
     * Processes a user message to determine if it's requesting to optimize the test
     * plan.
     * 
     * @param userMessage The user's message
     * @return A response message, or null if the message is not a request to
     *         optimize the test plan
     */
    public static String processOptimizeTestPlanRequest(String userMessage) {
        return OptimizeRequestHandler.processOptimizeTestPlanRequest(userMessage);
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

        // Check for JSR223 elements
        if (normalized.contains("jsr223") || normalized.contains("jsr 223") ||
                normalized.contains("javascript") || normalized.contains("groovy")) {
            if (normalized.contains("sampler")) {
                log.info("Identified as JSR223 sampler");
                return "jsr223sampler";
            } else if (normalized.contains("pre") && normalized.contains("processor")) {
                log.info("Identified as JSR223 preprocessor");
                return "jsr223preprocessor";
            } else if (normalized.contains("post") && normalized.contains("processor")) {
                log.info("Identified as JSR223 postprocessor");
                return "jsr223postprocessor";
            } else {
                // Default to JSR223 sampler if no specific type is mentioned
                log.info("Defaulting to JSR223 sampler");
                return "jsr223sampler";
            }
        }

        // Check HTTP request
        if (normalized.contains("http") &&
                !normalized.contains("jsr") && !normalized.contains("jsr223") && !normalized.contains("javascript")
                && !normalized.contains("groovy") &&
                (normalized.equals("http request") ||
                        normalized.equals("httprequest") ||
                        normalized.equals("http sampler") ||
                        normalized.equals("httpsampler") ||
                        normalized.equals("http req") ||
                        normalized.contains("http") && normalized.contains("request") ||
                        normalized.contains("http") && normalized.contains("req") ||
                        normalized.contains("http") && normalized.contains("sampler"))) {
            log.info("Identified as HTTP request");
            return "httpsampler";
        }

        // Check for controllers
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

        // Check for extractors
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
     * Gets a map of element display names to their normalized types
     * 
     * @return Map of element display names to normalized types
     */
    public static Map<String, String> getElementTypeMap() {
        Map<String, String> elementTypeMap = new HashMap<>();

        // Add all supported element types
        elementTypeMap.put("Thread Group", "threadgroup");
        elementTypeMap.put("HTTP Request", "httprequest");
        elementTypeMap.put("Loop Controller", "loopcontroller");
        elementTypeMap.put("If Controller", "ifcontroller");
        elementTypeMap.put("While Controller", "whilecontroller");
        elementTypeMap.put("Transaction Controller", "transactioncontroller");
        elementTypeMap.put("Runtime Controller", "runtimecontroller");
        elementTypeMap.put("CSV Data Set Config", "csvdataset");
        elementTypeMap.put("HTTP Header Manager", "headerManager");
        elementTypeMap.put("Response Assertion", "responseassert");
        elementTypeMap.put("JSON Path Assertion", "jsonpathassert");
        elementTypeMap.put("Duration Assertion", "durationassert");
        elementTypeMap.put("Size Assertion", "sizeassert");
        elementTypeMap.put("XPath Assertion", "xpathassert");
        elementTypeMap.put("Constant Timer", "constanttimer");
        elementTypeMap.put("Uniform Random Timer", "uniformrandomtimer");
        elementTypeMap.put("Gaussian Random Timer", "gaussianrandomtimer");
        elementTypeMap.put("Poisson Random Timer", "poissonrandomtimer");
        elementTypeMap.put("Regex Extractor", "regexextractor");
        elementTypeMap.put("XPath Extractor", "xpathextractor");
        elementTypeMap.put("JSON Path Extractor", "jsonpathextractor");
        elementTypeMap.put("Boundary Extractor", "boundaryextractor");
        elementTypeMap.put("View Results Tree", "viewresultstree");
        elementTypeMap.put("Aggregate Report", "aggregatereport");

        return elementTypeMap;
    }

    

    /**
     * Checks if a node has a parent of the specified type.
     * 
     * @param node The node to check
     * @param type The type to look for
     * @return True if the node has a parent of the specified type, false otherwise
     */
    private static boolean hasParentOfType(JMeterTreeNode node, String type) {
        if (node == null) {
            return false;
        }

        JMeterTreeNode parent = (JMeterTreeNode) node.getParent();
        while (parent != null) {
            if (parent.getTestElement().getClass().getSimpleName().equals(type)) {
                return true;
            }
            parent = (JMeterTreeNode) parent.getParent();
        }

        return false;
    }
}
