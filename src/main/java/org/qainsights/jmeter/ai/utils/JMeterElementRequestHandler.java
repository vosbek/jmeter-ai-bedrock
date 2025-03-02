package org.qainsights.jmeter.ai.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;
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
            "(?i)\\b(add|create|insert|include)\\b.*?(?:a|an)?\\s+([a-z\\s-]+?)(?:\\s+(?:called|named|with name|with the name)?\\s+[\"']?([^\"']+?)[\"']?)?(?:\\s*$|\\s+(?:to|in)\\b)");

    // Pattern to match requests to optimize the test plan
    private static final Pattern OPTIMIZE_TEST_PLAN_PATTERN = Pattern.compile(
            "(?i).*\\b(optimize|improve|enhance)\\b.*\\b(test plan)\\b.*");

    // Pattern to match requests for help with JMeter
    private static final Pattern JMETER_HELP_PATTERN = Pattern.compile(
            "(?i).*\\b(help|explain|how to|what is|guide)\\b.*\\b(jmeter)\\b.*");

    // Pattern to match requests to delete or remove elements
    private static final Pattern DELETE_REQUEST_PATTERN = Pattern.compile(
            "(?i)\\b(delete|remove|drop|erase)\\b.*?(?:\\b(element|node|component|item|sampler|controller|listener|timer|assertion|extractor)\\b|\\b(thread group|http request|config)\\b)");

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

        response = processDeleteRequest(message);
        if (response != null) {
            return response;
        }
        
        response = processPerformanceTestPlanRequest(message);
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
        boolean startsWithAction = message.toLowerCase().matches("^\\s*(?:add|insert|create|include|delete|remove)\\b.*");
        log.info("Message starts with action verb: {}", startsWithAction);

        if (startsWithAction) {
            // If the message starts with an action verb, we need to be careful about
            // splitting
            // For example: "add a thread group, HTTP request, and a timer"
            // We want to split this into: ["add a thread group", "HTTP request", "a timer"]
            // But we need to prepend "add" to the second and third instructions

            // First, extract the action verb
            String actionVerb = message.replaceAll("(?i)^\\s*(add|insert|create|include|delete|remove)\\b.*", "$1").trim();
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
                if (i > 0 && !part.toLowerCase().matches("^\\s*(?:add|insert|create|include|delete|remove)\\b.*")) {
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

            // Process delete request
            response = processDeleteRequest(instruction);
            if (response != null) {
                log.info("Processed delete request: {}", response);
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

        // Validate if the element can be added to the currently selected node
        String validationError = validateElementPlacement(elementType);
        if (validationError != null) {
            log.error("Validation failed: {}", validationError);
            return validationError + "\n\nI'll show you some suggested elements that you can add to your test plan.";
        }

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
                    guiPackage.getMainFrame().getTree().expandPath(new javax.swing.tree.TreePath(currentNode.getPath()));
                    log.info("Successfully expanded node: {}", currentNode.getName());
                    
                    // Select the newly added element (last child of current node)
                    if (currentNode.getChildCount() > 0) {
                        JMeterTreeNode lastChild = (JMeterTreeNode) currentNode.getChildAt(currentNode.getChildCount() - 1);
                        guiPackage.getTreeListener().getJTree().setSelectionPath(new javax.swing.tree.TreePath(lastChild.getPath()));
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
     * Validates if an element of the given type can be added to the currently selected node.
     * 
     * @param elementType The type of element to validate
     * @return An error message if validation fails, null if validation passes
     */
    private static String validateElementPlacement(String elementType) {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null) {
            return "I couldn't add the element because the JMeter GUI is not available.";
        }

        // Get the currently selected node
        JMeterTreeNode currentNode = guiPackage.getTreeListener().getCurrentNode();
        if (currentNode == null) {
            return "I couldn't add the element because no node is currently selected in the test plan.";
        }

        // Get information about the current node
        String nodeName = currentNode.getName();
        String nodeType = currentNode.getTestElement().getClass().getSimpleName();
        String nodeGuiClass = currentNode.getTestElement().getPropertyAsString(TestElement.GUI_CLASS);
        
        log.info("Validating placement of {} under node: {} (type: {}, guiClass: {})", 
                elementType, nodeName, nodeType, nodeGuiClass);

        // Normalize element type for validation
        String normalizedType = JMeterElementManager.normalizeElementType(elementType);
        String defaultName = JMeterElementManager.getDefaultNameForElement(normalizedType);

        // Check if the current node is the Test Plan
        boolean isTestPlan = nodeType.equals("TestPlan") || nodeGuiClass.contains("TestPlanGui");
        
        // Check if the current node is a Thread Group
        boolean isThreadGroup = nodeType.equals("ThreadGroup") || nodeGuiClass.contains("ThreadGroupGui");
        
        // Check if the current node is a Controller
        boolean isController = nodeType.contains("Controller") || nodeGuiClass.contains("ControllerPanel");
        
        // Check if the current node is a Sampler
        boolean isSampler = nodeType.contains("Sampler") || nodeGuiClass.contains("SamplerGui");

        // Validate based on element type and current node
        if (normalizedType.equals("threadgroup")) {
            // Thread Groups can only be added directly under Test Plan
            if (!isTestPlan) {
                return "I couldn't add the " + defaultName + " because Thread Groups can only be added directly under the Test Plan. " +
                       "Please select the Test Plan node and try again.";
            }
        } else if (normalizedType.equals("httpsampler") || normalizedType.contains("sampler")) {
            // Samplers can only be added under Thread Groups or Controllers
            if (!isThreadGroup && !isController) {
                return "I couldn't add the " + defaultName + " because samplers need to be added under a Thread Group or Controller. " +
                       "Please select a Thread Group or Controller node and try again.";
            }
        } else if (normalizedType.contains("timer")) {
            // Timers can only be added under Thread Groups, Controllers, or Samplers
            if (!isThreadGroup && !isController && !isSampler) {
                return "I couldn't add the " + defaultName + " because timers need to be added under a Thread Group, Controller, or Sampler. " +
                       "Please select an appropriate node and try again.";
            }
        } else if (normalizedType.contains("assertion")) {
            // Assertions are typically added under Samplers, but can also be added to Thread Groups and Controllers
            if (!isSampler && !isThreadGroup && !isController) {
                return "I couldn't add the " + defaultName + " because assertions are typically added under Samplers. " +
                       "Please select a Sampler node and try again.";
            }
        } else if (normalizedType.contains("extractor") || normalizedType.contains("postprocessor")) {
            // Extractors/Post-processors can only be added under Samplers
            if (!isSampler) {
                return "I couldn't add the " + defaultName + " because extractors need to be added under a Sampler. " +
                       "Please select a Sampler node and try again.";
            }
        } else if (normalizedType.contains("controller")) {
            // Controllers can only be added under Thread Groups or other Controllers
            if (!isThreadGroup && !isController) {
                return "I couldn't add the " + defaultName + " because controllers need to be added under a Thread Group or another Controller. " +
                       "Please select a Thread Group or Controller node and try again.";
            }
        } else if (normalizedType.contains("listener")) {
            // Listeners can be added under Test Plan, Thread Groups, or Samplers
            if (!isTestPlan && !isThreadGroup && !isSampler && !isController) {
                return "I couldn't add the " + defaultName + " because listeners are typically added under the Test Plan, Thread Groups, or Samplers. " +
                       "Please select an appropriate node and try again.";
            }
        } else if (normalizedType.contains("config")) {
            // Config elements can be added under Test Plan, Thread Groups, or Controllers
            if (!isTestPlan && !isThreadGroup && !isController) {
                return "I couldn't add the " + defaultName + " because configuration elements are typically added under the Test Plan, Thread Groups, or Controllers. " +
                       "Please select an appropriate node and try again.";
            }
        }

        // If we get here, the validation passed
        return null;
    }

    /**
     * Processes a user message to determine if it's requesting to optimize the test plan.
     * 
     * @param userMessage The user's message
     * @return A response message, or null if the message is not a request to optimize the test plan
     */
    public static String processOptimizeTestPlanRequest(String userMessage) {
        if (userMessage == null) {
            return null;
        }

        // Define patterns to match requests to optimize the test plan
        Matcher matcher = OPTIMIZE_TEST_PLAN_PATTERN.matcher(userMessage);

        if (!matcher.find()) {
            return null;
        }

        log.info("Detected request to optimize the test plan");

        // Check if test plan is ready
        JMeterElementManager.TestPlanStatus status = JMeterElementManager.isTestPlanReady();
        if (!status.isReady()) {
            return "I couldn't optimize the test plan because " + status.getErrorMessage().toLowerCase() +
                    ". Please make sure you have a test plan open.";
        }

        // Check if GuiPackage is available
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null) {
            log.error("GuiPackage is null, cannot optimize test plan");
            return "I couldn't optimize the test plan because the JMeter GUI is not available.";
        }

        // Check if the tree model is available
        if (guiPackage.getTreeModel() == null) {
            log.error("Tree model is null, cannot optimize test plan");
            return "I couldn't optimize the test plan because the test plan structure is not available.";
        }

        // Analyze and optimize the test plan
        return analyzeAndOptimizeTestPlan();
    }

    /**
     * Analyzes the test plan structure and provides optimization suggestions.
     * 
     * @return A response message with optimization suggestions
     */
    private static String analyzeAndOptimizeTestPlan() {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null) {
            log.error("GuiPackage is null, cannot optimize test plan");
            return "I couldn't optimize the test plan because the JMeter GUI is not available.";
        }

        // Refresh the tree model to ensure we have the latest test plan structure
        refreshTreeModel();

        // Get the root node of the test plan
        JMeterTreeNode root = (JMeterTreeNode) guiPackage.getTreeModel().getRoot();
        if (root == null) {
            log.error("Root node is null, cannot optimize test plan");
            return "I couldn't optimize the test plan because the test plan structure is not available.";
        }

        log.info("Starting test plan optimization analysis");
        
        // Dump the test plan structure for debugging
        if (log.isDebugEnabled()) {
            StringBuilder structureDump = new StringBuilder();
            structureDump.append("Test Plan Structure:\n");
            dumpTestPlanStructure(root, 0, structureDump);
            log.debug(structureDump.toString());
        }
        
        StringBuilder optimizationReport = new StringBuilder();
        optimizationReport.append("# Test Plan Optimization Report\n\n");
        optimizationReport.append("I've analyzed your test plan structure and identified the following optimization opportunities:\n\n");

        // Track if we found any issues to optimize
        boolean foundIssues = false;
        
        // Create a StringBuilder to capture the analysis
        StringBuilder analysisOutput = new StringBuilder();
        
        // Instead of analyzing the root directly, analyze its children
        // This prevents the Test Plan from appearing twice
        boolean childrenHaveIssues = false;
        for (int i = 0; i < root.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) root.getChildAt(i);
            childrenHaveIssues = analyzeNode(child, analysisOutput, 1) || childrenHaveIssues; // Start at level 1 for proper indentation
        }
        
        // Only add the Test Plan header if there are issues to report
        if (childrenHaveIssues) {
            // Insert the Test Plan header at the beginning of the analysis output
            analysisOutput.insert(0, "- **" + root.getName() + "** (" + root.getTestElement().getClass().getSimpleName() + ")\n");
            foundIssues = true;
        }
        
        // If we found issues, add them to the report
        if (foundIssues) {
            optimizationReport.append(analysisOutput);
        } else {
            optimizationReport.append("Your test plan looks well-structured! I didn't find any specific issues that need optimization.\n\n");
            
            // Add some targeted suggestions based on the test plan structure
            optimizationReport.append("However, here are a few test plan best practices to consider:\n\n");
            
            // Check if the test plan has Thread Groups
            boolean hasThreadGroups = false;
            for (int i = 0; i < root.getChildCount(); i++) {
                JMeterTreeNode child = (JMeterTreeNode) root.getChildAt(i);
                if (child.getTestElement().getClass().getSimpleName().contains("ThreadGroup")) {
                    hasThreadGroups = true;
                    break;
                }
            }
            
            if (!hasThreadGroups) {
                optimizationReport.append("- Consider adding a Thread Group to your test plan to define the number of users and test execution behavior.\n");
            }
            
            // Check if the test plan has HTTP Samplers
            boolean hasHttpSamplers = hasElementOfType(root, "HTTPSampler");
            if (!hasHttpSamplers) {
                optimizationReport.append("- Consider adding HTTP Samplers to your test plan to make requests to your application.\n");
            }
            
            // Check if the test plan has Listeners
            boolean hasListeners = hasElementOfType(root, "Listener");
            if (!hasListeners) {
                optimizationReport.append("- Consider adding Listeners like View Results Tree or Aggregate Report to analyze test results.\n");
            }
        }

        log.info("Completed test plan optimization analysis");
        return optimizationReport.toString();
    }

    /**
     * Refreshes the JMeter tree model to ensure we have the latest test plan structure.
     * This is important for detecting newly added elements.
     */
    private static void refreshTreeModel() {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null) {
            log.warn("Cannot refresh tree model: GuiPackage is null");
            return;
        }
        
        try {
            // Force a refresh of the GUI
            guiPackage.getMainFrame().repaint();
            
            // Wait a short time for the repaint to complete
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }
            
            // Get the current tree and force a reload
            if (guiPackage.getTreeModel() != null) {
                // This forces JMeter to update the GUI
                guiPackage.getCurrentGui();
                
                // Refresh the tree structure
                JMeterTreeNode root = (JMeterTreeNode) guiPackage.getTreeModel().getRoot();
                if (root != null) {
                    guiPackage.getTreeModel().nodeStructureChanged(root);
                    log.debug("Tree model refreshed successfully");
                }
            }
        } catch (Exception e) {
            log.warn("Error refreshing tree model", e);
        }
    }

    /**
     * Dumps the test plan structure for debugging purposes.
     * This is useful for understanding what elements are in the test plan.
     * 
     * @param node The node to start from
     * @param level The indentation level
     * @param output The StringBuilder to append to
     */
    private static void dumpTestPlanStructure(JMeterTreeNode node, int level, StringBuilder output) {
        if (node == null) {
            return;
        }
        
        String indent = "  ".repeat(level);
        String nodeName = node.getName();
        String nodeType = node.getTestElement().getClass().getSimpleName();
        
        output.append(indent).append("- ").append(nodeName).append(" (").append(nodeType).append(")\n");
        
        for (int i = 0; i < node.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) node.getChildAt(i);
            dumpTestPlanStructure(child, level + 1, output);
        }
    }

    /**
     * Recursively analyzes a node in the test plan and adds optimization suggestions to the report.
     * 
     * @param node The node to analyze
     * @param report The report to append suggestions to
     * @param level The indentation level for the tree structure
     * @return True if any issues were found, false otherwise
     */
    private static boolean analyzeNode(JMeterTreeNode node, StringBuilder report, int level) {
        if (node == null) {
            return false;
        }

        // Get node information
        String nodeName = node.getName();
        String nodeType = node.getTestElement().getClass().getSimpleName();
        
        // Add debug logging
        log.debug("Analyzing node: {} ({})", nodeName, nodeType);
        
        // Indent based on level
        String indent = "  ".repeat(level);
        
        // Track if this node or its children have issues
        boolean hasIssues = false;
        
        // Create a StringBuilder to capture this node's issues
        StringBuilder nodeIssues = new StringBuilder();
        
        // Check for specific optimization opportunities based on node type
        if (nodeType.contains("ThreadGroup")) {
            hasIssues = analyzeThreadGroup(node, nodeIssues, level + 1) || hasIssues;
        } else if (nodeType.contains("HTTPSampler")) {
            hasIssues = analyzeHttpSampler(node, nodeIssues, level + 1) || hasIssues;
        } else if (nodeType.contains("Timer")) {
            hasIssues = analyzeTimer(node, nodeIssues, level + 1) || hasIssues;
        } else if (nodeType.contains("Assertion")) {
            hasIssues = analyzeAssertion(node, nodeIssues, level + 1) || hasIssues;
        } else if (nodeType.contains("Controller")) {
            hasIssues = analyzeController(node, nodeIssues, level + 1) || hasIssues;
        } else if (nodeType.contains("Listener")) {
            hasIssues = analyzeListener(node, nodeIssues, level + 1) || hasIssues;
        } else if (nodeType.contains("ConfigElement") || nodeType.contains("Config")) {
            hasIssues = analyzeConfigElement(node, nodeIssues, level + 1) || hasIssues;
        } else if (nodeType.contains("Sampler") || nodeType.contains("sampler")) {
            hasIssues = analyzeSampler(node, nodeIssues, level + 1) || hasIssues;
        } else if (nodeType.contains("Extractor") || nodeType.contains("PostProcessor") || nodeType.contains("PreProcessor")) {
            hasIssues = analyzeExtractor(node, nodeIssues, level + 1) || hasIssues;
        } else {
            // For any unrecognized element type, add a generic entry
            log.debug("Unrecognized element type: {}", nodeType);
        }
        
        // Recursively analyze child nodes
        boolean childrenHaveIssues = false;
        StringBuilder childrenIssues = new StringBuilder();
        for (int i = 0; i < node.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) node.getChildAt(i);
            childrenHaveIssues = analyzeNode(child, childrenIssues, level + 1) || childrenHaveIssues;
        }
        
        // Only add this node to the report if it or its children have issues
        if (hasIssues || childrenHaveIssues) {
            // Add the node name and type
            report.append(indent).append("- **").append(nodeName).append("** (").append(nodeType).append(")\n");
            
            // Add the node's issues if any
            if (hasIssues) {
                report.append(nodeIssues);
            }
            
            // Add the children's issues if any
            if (childrenHaveIssues) {
                report.append(childrenIssues);
            }
            
            return true;
        }
        
        return false;
    }

    /**
     * Analyzes a Thread Group node and adds optimization suggestions.
     * @return True if any issues were found, false otherwise
     */
    private static boolean analyzeThreadGroup(JMeterTreeNode node, StringBuilder report, int level) {
        String indent = "  ".repeat(level);
        boolean hasIssues = false;
        TestElement element = node.getTestElement();
        
        // Check thread count
        int numThreads = 0;
        try {
            numThreads = Integer.parseInt(element.getPropertyAsString("ThreadGroup.num_threads", "0"));
        } catch (NumberFormatException e) {
            // Ignore parsing errors
        }
        
        if (numThreads > 100) {
            report.append(indent).append("[WARNING] High thread count (").append(numThreads).append("). Consider reducing for better stability or ensure your system can handle this load.\n");
            hasIssues = true;
        } else if (numThreads <= 0) {
            report.append(indent).append("[WARNING] Thread count is zero or not set (").append(numThreads).append("). The test won't generate any load.\n");
            hasIssues = true;
        } else if (numThreads < 5 && !node.getName().toLowerCase().contains("debug")) {
            report.append(indent).append("[INFO] Low thread count (").append(numThreads).append("). This may not generate sufficient load for performance testing unless this is intentional.\n");
            hasIssues = true;
        }
        
        // Check ramp-up period
        int rampUp = 0;
        try {
            rampUp = Integer.parseInt(element.getPropertyAsString("ThreadGroup.ramp_time", "0"));
        } catch (NumberFormatException e) {
            // Ignore parsing errors
        }
        
        if (rampUp <= 0 && numThreads > 10) {
            report.append(indent).append("[WARNING] No ramp-up period for ").append(numThreads).append(" threads. Consider adding a ramp-up period to avoid sudden server load.\n");
            hasIssues = true;
        } else if (rampUp > 300 && numThreads > 50) {
            report.append(indent).append("[INFO] Long ramp-up period (").append(rampUp)
                    .append(" seconds) for many threads. Consider reducing unless this is intentional.\n");
            hasIssues = true;
        }
        
        // Check scheduler configuration
        boolean scheduler = false;
        try {
            scheduler = "true".equalsIgnoreCase(element.getPropertyAsString("ThreadGroup.scheduler", "false"));
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        if (scheduler) {
            // Check duration
            long duration = 0;
            try {
                duration = Long.parseLong(element.getPropertyAsString("ThreadGroup.duration", "0"));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (duration <= 0) {
                report.append(indent).append("[WARNING] Scheduler is enabled but duration is not set. Test may not run as expected.\n");
                hasIssues = true;
            } else if (duration < 30 && numThreads > 10) {
                report.append(indent).append("[INFO] Short test duration (").append(duration)
                        .append(" seconds). Consider increasing for more reliable performance metrics.\n");
                hasIssues = true;
            } else if (duration > 3600) {
                report.append(indent).append("[INFO] Very long test duration (").append(duration)
                        .append(" seconds / ").append(duration / 60).append(" minutes). Ensure this is intentional.\n");
                hasIssues = true;
            }
        } else if (numThreads > 50) {
            report.append(indent).append("[INFO] Consider enabling scheduler for high-volume tests to control test duration.\n");
            hasIssues = true;
        }
        
        // Check if there are samplers under this thread group
        boolean hasSamplers = false;
        boolean hasControllers = false;
        boolean hasTimers = false;
        boolean hasAssertions = false;
        
        for (int i = 0; i < node.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) node.getChildAt(i);
            String childType = child.getTestElement().getClass().getSimpleName();
            
            if (childType.contains("Sampler")) {
                hasSamplers = true;
            } else if (childType.contains("Controller")) {
                hasControllers = true;
                // Check if controllers have samplers
                for (int j = 0; j < child.getChildCount(); j++) {
                    JMeterTreeNode grandchild = (JMeterTreeNode) child.getChildAt(j);
                    if (grandchild.getTestElement().getClass().getSimpleName().contains("Sampler")) {
                        hasSamplers = true;
                        break;
                    }
                }
            } else if (childType.contains("Timer")) {
                hasTimers = true;
            } else if (childType.contains("Assertion")) {
                hasAssertions = true;
            }
        }
        
        if (!hasSamplers && !hasControllers) {
            report.append(indent).append("[WARNING] No samplers or controllers found. Thread Group will not perform any requests.\n");
            hasIssues = true;
        }
        
        if (!hasTimers && numThreads > 20) {
            report.append(indent).append("[INFO] No timers found with high thread count. Consider adding timers to prevent server overload.\n");
            hasIssues = true;
        }
        
        if (!hasAssertions) {
            report.append(indent).append("[INFO] No assertions found. Consider adding assertions to validate responses.\n");
            hasIssues = true;
        }
        
        // Check thread group type
        String threadGroupType = element.getClass().getSimpleName();
        if (threadGroupType.contains("SetupThreadGroup")) {
            if (numThreads > 1) {
                report.append(indent).append("[INFO] Setup Thread Group with multiple threads (").append(numThreads)
                        .append("). Usually, setup operations need only one thread unless parallel setup is required.\n");
                hasIssues = true;
            }
        } else if (threadGroupType.contains("PostThreadGroup")) {
            if (numThreads > 1) {
                report.append(indent).append("[INFO] Teardown Thread Group with multiple threads (").append(numThreads)
                        .append("). Usually, teardown operations need only one thread unless parallel cleanup is required.\n");
                hasIssues = true;
            }
        }
        
        return hasIssues;
    }

    /**
     * Analyzes an HTTP Sampler node and adds optimization suggestions.
     * @return True if any issues were found, false otherwise
     */
    private static boolean analyzeHttpSampler(JMeterTreeNode node, StringBuilder report, int level) {
        String indent = "  ".repeat(level);
        boolean hasIssues = false;
        TestElement element = node.getTestElement();
        
        // Check timeout settings
        int connectTimeout = 0;
        int responseTimeout = 0;
        try {
            connectTimeout = Integer.parseInt(element.getPropertyAsString("HTTPSampler.connect_timeout", "0"));
            responseTimeout = Integer.parseInt(element.getPropertyAsString("HTTPSampler.response_timeout", "0"));
        } catch (NumberFormatException e) {
            // Ignore parsing errors
        }
        
        if (connectTimeout <= 0) {
            report.append(indent).append("[WARNING] No connect timeout set. Consider adding a timeout (e.g., 5000ms) to avoid hanging requests.\n");
            hasIssues = true;
        } else if (connectTimeout < 1000) {
            report.append(indent).append("[INFO] Connect timeout (").append(connectTimeout)
                    .append("ms) may be too short for some network conditions. Consider increasing unless this is intentional.\n");
            hasIssues = true;
        } else if (connectTimeout > 30000) {
            report.append(indent).append("[INFO] Connect timeout (").append(connectTimeout)
                    .append("ms) is very long. Consider reducing to avoid prolonged test execution on failures.\n");
            hasIssues = true;
        }
        
        if (responseTimeout <= 0) {
            report.append(indent).append("[WARNING] No response timeout set. Consider adding a timeout (e.g., 10000ms) to avoid hanging requests.\n");
            hasIssues = true;
        } else if (responseTimeout < 1000) {
            report.append(indent).append("[INFO] Response timeout (").append(responseTimeout)
                    .append("ms) may be too short for some operations. Consider increasing unless this is intentional.\n");
            hasIssues = true;
        } else if (responseTimeout > 60000) {
            report.append(indent).append("[INFO] Response timeout (").append(responseTimeout)
                    .append("ms) is very long. Consider reducing to avoid prolonged test execution on failures.\n");
            hasIssues = true;
        }
        
        // Check HTTP method
        String method = element.getPropertyAsString("HTTPSampler.method", "");
        if (method.isEmpty()) {
            report.append(indent).append("[WARNING] HTTP method not specified. Default is GET but should be explicitly set.\n");
            hasIssues = true;
        }
        
        // Check URL
        String protocol = element.getPropertyAsString("HTTPSampler.protocol", "");
        String domain = element.getPropertyAsString("HTTPSampler.domain", "");
        String path = element.getPropertyAsString("HTTPSampler.path", "");
        
        if (domain.isEmpty()) {
            report.append(indent).append("[WARNING] Server name/IP is empty. Request will fail.\n");
            hasIssues = true;
        }
        
        if (protocol.isEmpty()) {
            report.append(indent).append("[INFO] Protocol not specified. Default is HTTP but should be explicitly set (HTTP or HTTPS).\n");
            hasIssues = true;
        }
        
        // Check for hardcoded credentials in URL
        if (domain.contains("@") || path.contains("@")) {
            report.append(indent).append("[WARNING] URL may contain hardcoded credentials. Consider using HTTP Authorization Manager instead.\n");
            hasIssues = true;
        }
        
        // Check for use of parameters
        boolean hasParameters = false;
        try {
            // Check if there are any parameters
            hasParameters = element.getPropertyAsString("HTTPSampler.arguments", "").length() > 0;
        } catch (Exception e) {
            // Ignore errors
        }
        
        if (!hasParameters && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
            report.append(indent).append("[INFO] ").append(method).append(" request without parameters. Verify if this is intentional.\n");
            hasIssues = true;
        }
        
        // Check for follow redirects
        boolean followRedirects = false;
        try {
            followRedirects = "true".equalsIgnoreCase(element.getPropertyAsString("HTTPSampler.follow_redirects", "false"));
        } catch (Exception e) {
            // Ignore errors
        }
        
        if (!followRedirects) {
            report.append(indent).append("[INFO] 'Follow Redirects' is disabled. Enable if you want to follow HTTP redirects automatically.\n");
            hasIssues = true;
        }
        
        // Check for use of connection keep-alive
        boolean useKeepAlive = false;
        try {
            useKeepAlive = "true".equalsIgnoreCase(element.getPropertyAsString("HTTPSampler.use_keepalive", "true"));
        } catch (Exception e) {
            // Ignore errors
        }
        
        if (!useKeepAlive) {
            report.append(indent).append("[INFO] 'Use KeepAlive' is disabled. Enable for better performance in most scenarios.\n");
            hasIssues = true;
        }
        
        // Check for extractors for dynamic data
        boolean hasExtractors = false;
        boolean hasAssertions = false;
        
        for (int i = 0; i < node.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) node.getChildAt(i);
            String childType = child.getTestElement().getClass().getSimpleName();
            
            if (childType.contains("Extractor") || childType.contains("PostProcessor")) {
                hasExtractors = true;
            } else if (childType.contains("Assertion")) {
                hasAssertions = true;
            }
        }
        
        if (!hasExtractors && !node.getName().toLowerCase().contains("login") && !node.getName().toLowerCase().contains("static")) {
            report.append(indent).append("[INFO] Consider adding extractors if this request returns dynamic data needed for subsequent requests.\n");
            // Not returning an error here as the element was still added successfully
        }
        
        if (!hasAssertions) {
            report.append(indent).append("[INFO] No assertions found. Consider adding assertions to validate responses.\n");
            hasIssues = true;
        }
        
        // Check embedded resources
        boolean downloadEmbeddedResources = false;
        try {
            downloadEmbeddedResources = "true".equalsIgnoreCase(element.getPropertyAsString("HTTPSampler.image_parser", "false"));
        } catch (Exception e) {
            // Ignore errors
        }
        
        if (downloadEmbeddedResources) {
            // Check concurrent pool size
            int concurrentPool = 0;
            try {
                concurrentPool = Integer.parseInt(element.getPropertyAsString("HTTPSampler.concurrentPool", "4"));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (concurrentPool <= 1) {
                report.append(indent).append("[INFO] Concurrent pool for embedded resources is set to ").append(concurrentPool)
                        .append(". Consider increasing (e.g., 4-6) for better performance when downloading embedded resources.\n");
                hasIssues = true;
            } else if (concurrentPool > 10) {
                report.append(indent).append("[INFO] High concurrent pool (").append(concurrentPool)
                        .append(") for embedded resources. This may cause excessive load. Consider reducing unless necessary.\n");
                hasIssues = true;
            }
        }
        
        return hasIssues;
    }

    /**
     * Analyzes a Sampler node and adds optimization suggestions.
     * @return True if any issues were found, false otherwise
     */
    private static boolean analyzeSampler(JMeterTreeNode node, StringBuilder report, int level) {
        String indent = "  ".repeat(level);
        boolean hasIssues = false;
        TestElement element = node.getTestElement();
        String samplerType = element.getClass().getSimpleName();
        
        // Check HTTP Sampler
        if (samplerType.contains("HTTPSampler")) {
            hasIssues = analyzeHttpSampler(node, report, level) || hasIssues;
        } else if (samplerType.contains("JDBCSampler")) {
            String query = element.getPropertyAsString("query", "");
            String dataSource = element.getPropertyAsString("dataSource", "");
            String queryType = element.getPropertyAsString("queryType", "");
            boolean queryTimeout = false;
            
            try {
                queryTimeout = Integer.parseInt(element.getPropertyAsString("queryTimeout", "0")) > 0;
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (query.isEmpty()) {
                report.append(indent).append("[WARNING] JDBC Sampler with empty query. Sampler will not execute any SQL.\n");
                hasIssues = true;
            }
            
            if (dataSource.isEmpty()) {
                report.append(indent).append("[WARNING] JDBC Sampler with no data source. Ensure a JDBC Connection Configuration is added to the test plan.\n");
                hasIssues = true;
            }
            
            if (!queryTimeout) {
                report.append(indent).append("[INFO] Consider setting a query timeout to prevent hanging tests if database is slow to respond.\n");
                hasIssues = true;
            }
            
            // Check for potentially slow queries
            if (query.toLowerCase().contains("select") && !query.toLowerCase().contains("where") && 
                !query.toLowerCase().contains("limit") && !query.toLowerCase().contains("top")) {
                report.append(indent).append("[WARNING] Unbounded SELECT query without WHERE clause or LIMIT. This may return a large result set and impact performance.\n");
                hasIssues = true;
            }
            
            // Check for proper query type
            if (query.toLowerCase().trim().startsWith("select") && !queryType.equals("Select Statement")) {
                report.append(indent).append("[INFO] SELECT query but query type is not set to 'Select Statement'. This may affect result handling.\n");
                hasIssues = true;
            } else if ((query.toLowerCase().trim().startsWith("update") || 
                       query.toLowerCase().trim().startsWith("insert") || 
                       query.toLowerCase().trim().startsWith("delete")) && 
                       !queryType.equals("Update Statement")) {
                report.append(indent).append("[INFO] DML query but query type is not set to 'Update Statement'. This may affect result handling.\n");
                hasIssues = true;
            }
        } else if (samplerType.contains("JMSSampler")) {
            String queueConnection = element.getPropertyAsString("jms.jndi_properties", "");
            String queue = element.getPropertyAsString("JMSSampler.destination", "");
            boolean useAuth = false;
            
            try {
                useAuth = "true".equalsIgnoreCase(element.getPropertyAsString("JMSSampler.useAuth", "false"));
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (queueConnection.isEmpty()) {
                report.append(indent).append("[WARNING] JMS Sampler with no JNDI properties. Connection will likely fail.\n");
                hasIssues = true;
            }
            
            if (queue.isEmpty()) {
                report.append(indent).append("[WARNING] JMS Sampler with no destination queue/topic. Message will not be sent.\n");
                hasIssues = true;
            }
            
            // Check for authentication if needed
            if (useAuth) {
                String username = element.getPropertyAsString("JMSSampler.username", "");
                if (username.isEmpty()) {
                    report.append(indent).append("[WARNING] JMS Sampler with authentication enabled but no username provided.\n");
                    hasIssues = true;
                }
            }
        } else if (samplerType.contains("SoapSampler") || samplerType.contains("XMLRPCSampler")) {
            String soapAction = element.getPropertyAsString("SoapSampler.URL", "");
            String soapData = element.getPropertyAsString("SoapSampler.xml_data", "");
            
            if (soapAction.isEmpty()) {
                report.append(indent).append("[WARNING] SOAP Sampler with no URL. Request will fail.\n");
                hasIssues = true;
            }
            
            if (soapData.isEmpty()) {
                report.append(indent).append("[WARNING] SOAP Sampler with no XML data. Request will be empty.\n");
                hasIssues = true;
            } else if (!soapData.trim().startsWith("<")) {
                report.append(indent).append("[WARNING] SOAP Sampler XML data does not appear to be valid XML. Request may fail.\n");
                hasIssues = true;
            }
            
            // Check for SOAP version
            if (samplerType.contains("SoapSampler")) {
                boolean maintainSession = false;
                try {
                    maintainSession = "true".equalsIgnoreCase(element.getPropertyAsString("SoapSampler.maintain_session", "false"));
                } catch (Exception e) {
                    // Ignore errors
                }
                
                if (!maintainSession) {
                    report.append(indent).append("[INFO] SOAP Sampler not maintaining session. If your web service requires session state, enable this option.\n");
                    // Not returning an error here as the element was still added successfully
                }
            }
        } else if (samplerType.contains("FTPSampler")) {
            String server = element.getPropertyAsString("FTPSampler.server", "");
            String filename = element.getPropertyAsString("FTPSampler.filename", "");
            boolean binaryMode = false;
            
            try {
                binaryMode = "true".equalsIgnoreCase(element.getPropertyAsString("FTPSampler.binarymode", "false"));
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (server.isEmpty()) {
                report.append(indent).append("[WARNING] FTP Sampler with no server specified. Connection will fail.\n");
                hasIssues = true;
            }
            
            if (filename.isEmpty()) {
                report.append(indent).append("[WARNING] FTP Sampler with no filename specified. Request will fail.\n");
                hasIssues = true;
            }
            
            // Check for binary mode for certain file types
            if (!binaryMode && filename.matches(".*\\.(zip|jar|exe|pdf|doc|jpg|png|gif)$")) {
                report.append(indent).append("[WARNING] FTP Sampler transferring binary file but binary mode is not enabled. File may be corrupted.\n");
                hasIssues = true;
            }
        } else if (samplerType.contains("JavaSampler")) {
            String className = element.getPropertyAsString("classname", "");
            
            if (className.isEmpty()) {
                report.append(indent).append("[WARNING] Java Sampler with no implementation class. Sampler will not function.\n");
                hasIssues = true;
            }
        } else if (samplerType.contains("JSR223Sampler")) {
            String script = element.getPropertyAsString("script", "");
            String language = element.getPropertyAsString("scriptLanguage", "");
            boolean cacheKey = false;
            
            try {
                cacheKey = "true".equalsIgnoreCase(element.getPropertyAsString("cacheKey", "false"));
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (script.isEmpty()) {
                report.append(indent).append("[WARNING] JSR223 Sampler with empty script. Sampler will have no effect.\n");
                hasIssues = true;
            }
            
            if (language.contains("groovy") && !script.contains("sleep") && !script.contains("delay")) {
                report.append(indent).append("[INFO] JSR223 Sampler script doesn't appear to include sleep/delay calls. Verify timer functionality.\n");
                hasIssues = true;
            }
            
            // Check for potentially resource-intensive operations
            if (script.contains("Thread.sleep") || script.contains("sleep(")) {
                report.append(indent).append("[WARNING] Script contains sleep operations. This may affect test throughput. Consider using JMeter Timers instead.\n");
                hasIssues = true;
            }
            
            if (script.contains("new File(") || script.contains("FileInputStream") || script.contains("FileOutputStream")) {
                report.append(indent).append("[INFO] Script contains file operations. Ensure files exist and are accessible on all test machines.\n");
                hasIssues = true;
            }
        } else if (samplerType.contains("SystemSampler")) {
            String command = element.getPropertyAsString("command", "");
            
            if (command.isEmpty()) {
                report.append(indent).append("[WARNING] OS Process Sampler with no command specified. Sampler will not execute anything.\n");
                hasIssues = true;
            }
            
            // Check for potentially dangerous commands
            if (command.contains("rm ") || command.contains("del ") || command.contains("format ")) {
                report.append(indent).append("[WARNING] OS Process Sampler contains potentially destructive command. Ensure this is intentional.\n");
                hasIssues = true;
            }
        } else if (samplerType.contains("TCPSampler")) {
            String server = element.getPropertyAsString("TCPSampler.server", "");
            String port = element.getPropertyAsString("TCPSampler.port", "");
            String timeout = element.getPropertyAsString("TCPSampler.timeout", "");
            
            if (server.isEmpty()) {
                report.append(indent).append("[WARNING] TCP Sampler with no server specified. Connection will fail.\n");
                hasIssues = true;
            }
            
            if (port.isEmpty()) {
                report.append(indent).append("[WARNING] TCP Sampler with no port specified. Connection will fail.\n");
                hasIssues = true;
            } else {
                try {
                    int portNum = Integer.parseInt(port);
                    if (portNum <= 0 || portNum > 65535) {
                        report.append(indent).append("[WARNING] TCP Sampler with invalid port number. Valid range is 1-65535.\n");
                        hasIssues = true;
                    }
                } catch (NumberFormatException e) {
                    report.append(indent).append("[WARNING] TCP Sampler with non-numeric port. Connection will fail.\n");
                    hasIssues = true;
                }
            }
            
            if (timeout.isEmpty() || "0".equals(timeout)) {
                report.append(indent).append("[INFO] TCP Sampler with no timeout. Consider setting a timeout to prevent hanging tests.\n");
                hasIssues = true;
            } else if (timeout.length() > 0) {
                try {
                    int timeoutNum = Integer.parseInt(timeout);
                    if (timeoutNum > 60000) {
                        report.append(indent).append("[INFO] Long timeout (").append(timeoutNum)
                                .append("ms). Consider reducing to avoid prolonged test execution on failures.\n");
                        hasIssues = true;
                    }
                } catch (NumberFormatException e) {
                    // Ignore parsing errors
                }
            }
        } else if (samplerType.contains("JSR223Timer")) {
            String script = element.getPropertyAsString("script", "");
            String language = element.getPropertyAsString("scriptLanguage", "");
            
            if (script.isEmpty()) {
                report.append(indent).append("[WARNING] JSR223 Timer with empty script. Timer will have no effect.\n");
                hasIssues = true;
            }
            
            if (language.contains("groovy") && !script.contains("sleep") && !script.contains("delay")) {
                report.append(indent).append("[INFO] JSR223 Timer script doesn't appear to include sleep/delay calls. Verify timer functionality.\n");
                hasIssues = true;
            }
        }
        
        return hasIssues;
    }

    /**
     * Analyzes a Timer node and adds optimization suggestions.
     * @return True if any issues were found, false otherwise
     */
    private static boolean analyzeTimer(JMeterTreeNode node, StringBuilder report, int level) {
        String indent = "  ".repeat(level);
        boolean hasIssues = false;
        TestElement element = node.getTestElement();
        String timerType = element.getClass().getSimpleName();
        
        // Check Constant Timer
        if (timerType.contains("ConstantTimer")) {
            long delay = 0;
            try {
                delay = Long.parseLong(element.getPropertyAsString("ConstantTimer.delay", "0"));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (delay <= 0) {
                report.append(indent).append("[WARNING] Constant Timer with zero or negative delay (").append(delay)
                        .append("ms). Timer will have no effect.\n");
                hasIssues = true;
            } else if (delay > 10000) {
                report.append(indent).append("[INFO] Long constant delay (").append(delay)
                        .append("ms). Consider if this long delay is necessary or if it could be reduced.\n");
                hasIssues = true;
            }
        }
        // Check Uniform Random Timer
        else if (timerType.contains("UniformRandomTimer")) {
            long delay = 0;
            long range = 0;
            try {
                delay = Long.parseLong(element.getPropertyAsString("RandomTimer.delay", "0"));
                range = Long.parseLong(element.getPropertyAsString("RandomTimer.range", "0"));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (delay < 0) {
                report.append(indent).append("[WARNING] Uniform Random Timer with negative constant delay (").append(delay)
                        .append("ms). This may cause unexpected behavior.\n");
                hasIssues = true;
            }
            
            if (range <= 0) {
                report.append(indent).append("[WARNING] Uniform Random Timer with zero or negative random delay (").append(range)
                        .append("ms). Timer will behave like a Constant Timer.\n");
                hasIssues = true;
            } else if (range > 30000) {
                report.append(indent).append("[INFO] Large random range (").append(range)
                        .append("ms). This may lead to unpredictable test execution times.\n");
                hasIssues = true;
            }
        }
        // Check Gaussian Random Timer
        else if (timerType.contains("GaussianRandomTimer")) {
            long delay = 0;
            double deviation = 0;
            try {
                delay = Long.parseLong(element.getPropertyAsString("Gaussian.delay", "0"));
                deviation = Double.parseDouble(element.getPropertyAsString("Gaussian.deviation", "0"));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (delay < 0) {
                report.append(indent).append("[WARNING] Gaussian Random Timer with negative constant delay (").append(delay)
                        .append("ms). This may cause unexpected behavior.\n");
                hasIssues = true;
            }
            
            if (deviation <= 0) {
                report.append(indent).append("[WARNING] Gaussian Random Timer with zero or negative deviation (").append(deviation)
                        .append("ms). Timer will behave like a Constant Timer.\n");
                hasIssues = true;
            } else if (deviation > delay && delay > 0) {
                report.append(indent).append("[INFO] Deviation (").append(deviation)
                        .append("ms) is greater than constant delay (").append(delay)
                        .append("ms). This may lead to negative delays which are treated as zero.\n");
                hasIssues = true;
            }
        }
        // Check Poisson Random Timer
        else if (timerType.contains("PoissonRandomTimer")) {
            double lambda = 0;
            long delay = 0;
            try {
                lambda = Double.parseDouble(element.getPropertyAsString("ConstantTimer.delay", "0"));
                delay = Long.parseLong(element.getPropertyAsString("RandomTimer.delay", "0"));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (lambda <= 0) {
                report.append(indent).append("[WARNING] Poisson Random Timer with zero or negative lambda (").append(lambda)
                        .append("). This will cause errors or unexpected behavior.\n");
                hasIssues = true;
            } else if (lambda > 100) {
                report.append(indent).append("[INFO] High lambda value (").append(lambda)
                        .append(") may lead to very long delays. Verify this is intentional.\n");
                hasIssues = true;
            }
            
            if (delay < 0) {
                report.append(indent).append("[WARNING] Poisson Random Timer with negative constant delay (").append(delay)
                        .append("ms). This may cause unexpected behavior.\n");
                hasIssues = true;
            }
        }
        // Check Synchronizing Timer
        else if (timerType.contains("SyncTimer")) {
            int groupSize = 0;
            long timeout = 0;
            try {
                groupSize = Integer.parseInt(element.getPropertyAsString("groupSize", "0"));
                timeout = Long.parseLong(element.getPropertyAsString("timeoutInMs", "0"));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (groupSize <= 0) {
                report.append(indent).append("[WARNING] Synchronizing Timer with invalid group size (").append(groupSize)
                        .append("). Timer will block indefinitely.\n");
                hasIssues = true;
            } else if (groupSize > 100) {
                report.append(indent).append("[WARNING] Large group size (").append(groupSize)
                        .append("). Ensure your test has enough threads to avoid blocking.\n");
                hasIssues = true;
            }
            
            if (timeout <= 0) {
                report.append(indent).append("[INFO] Synchronizing Timer with no timeout. Timer may block indefinitely if group size is not reached.\n");
                hasIssues = true;
            } else if (timeout > 60000) {
                report.append(indent).append("[INFO] Long timeout (").append(timeout)
                        .append("ms). Consider reducing to avoid long test execution times if group size is not reached.\n");
                hasIssues = true;
            }
        }
        // Check JSR223 Timer
        else if (timerType.contains("JSR223Timer")) {
            String script = element.getPropertyAsString("script", "");
            String language = element.getPropertyAsString("scriptLanguage", "");
            
            if (script.isEmpty()) {
                report.append(indent).append("[WARNING] JSR223 Timer with empty script. Timer will have no effect.\n");
                hasIssues = true;
            }
            
            if (language.contains("groovy") && !script.contains("sleep") && !script.contains("delay")) {
                report.append(indent).append("[INFO] JSR223 Timer script doesn't appear to include sleep/delay calls. Verify timer functionality.\n");
                hasIssues = true;
            }
        }
        
        return hasIssues;
    }

    /**
     * Analyzes an Assertion node and adds optimization suggestions.
     * @return True if any issues were found, false otherwise
     */
    private static boolean analyzeAssertion(JMeterTreeNode node, StringBuilder report, int level) {
        String indent = "  ".repeat(level);
        boolean hasIssues = false;
        TestElement element = node.getTestElement();
        String assertionType = element.getClass().getSimpleName();
        
        // Check Response Assertion
        if (assertionType.contains("ResponseAssertion")) {
            String testField = element.getPropertyAsString("Assertion.test_field", "");
            String testType = element.getPropertyAsString("Assertion.test_type", "");
            String testString = element.getPropertyAsString("Assertion.test_string", "");
            boolean notFlag = false;
            try {
                notFlag = "true".equalsIgnoreCase(element.getPropertyAsString("Assertion.not", "false"));
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (testString.isEmpty()) {
                report.append(indent).append("[WARNING] Response Assertion with empty pattern. Assertion may not work as expected.\n");
                hasIssues = true;
            } else if (testString.length() > 100) {
                report.append(indent).append("[WARNING] Long assertion pattern (").append(testString.length())
                        .append(" chars). Consider using a shorter, more specific pattern for better performance.\n");
                hasIssues = true;
            }
            
            // Check for overly generic patterns that might cause false positives
            if (testString.equals(".") || testString.equals(".*") || testString.equals("[a-zA-Z]")) {
                report.append(indent).append("[WARNING] Very generic regex pattern. This may match unintended content.\n");
                hasIssues = true;
            }
            
            // Check for proper regex syntax if using regex
            if (testType.equals("2") && testString.contains("(") && !testString.contains(")")) {
                report.append(indent).append("[WARNING] Possible malformed regex pattern. Check for matching parentheses.\n");
                hasIssues = true;
            }
            
            // Check if using substring without specifying a test field
            if (testType.equals("16") && testField.isEmpty()) {
                report.append(indent).append("[WARNING] Substring assertion without specified test field. Assertion may not work as expected.\n");
                hasIssues = true;
            }
        }
        // Check JSON Assertion
        else if (assertionType.contains("JSONPathAssertion")) {
            String jsonPath = element.getPropertyAsString("JSON_PATH", "");
            String expectedValue = element.getPropertyAsString("EXPECTED_VALUE", "");
            boolean jsonValidation = false;
            try {
                jsonValidation = "true".equalsIgnoreCase(element.getPropertyAsString("JSONVALIDATION", "false"));
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (jsonPath.isEmpty()) {
                report.append(indent).append("[WARNING] JSON Path Assertion with empty path. Assertion will fail.\n");
                hasIssues = true;
            }
            
            if (jsonValidation && expectedValue.isEmpty()) {
                report.append(indent).append("[WARNING] JSON Path Assertion with validation enabled but no expected value. Assertion may fail.\n");
                hasIssues = true;
            }
            
            // Check for common JSONPath syntax errors
            if (jsonPath.startsWith("$.") && jsonPath.contains("[") && !jsonPath.contains("]")) {
                report.append(indent).append("[WARNING] Possible malformed JSONPath. Check for matching brackets.\n");
                hasIssues = true;
            }
        }
        // Check XPath Assertion
        else if (assertionType.contains("XPathAssertion")) {
            String xpath = element.getPropertyAsString("xpath", "");
            
            if (xpath.isEmpty()) {
                report.append(indent).append("[WARNING] XPath Assertion with empty path. Assertion will fail.\n");
                hasIssues = true;
            }
            
            // Check for common XPath syntax errors
            if (xpath.contains("[") && !xpath.contains("]")) {
                report.append(indent).append("[WARNING] Possible malformed XPath. Check for matching brackets.\n");
                hasIssues = true;
            }
        }
        // Check Size Assertion
        else if (assertionType.contains("SizeAssertion")) {
            long size = 0;
            String sizeOperator = element.getPropertyAsString("size_operator", "");
            try {
                size = Long.parseLong(element.getPropertyAsString("size", "0"));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (size <= 0 && !sizeOperator.equals("1")) { // 1 = not equal
                report.append(indent).append("[WARNING] Size Assertion with zero or negative size (").append(size)
                        .append("). This may not work as expected unless using 'not equal' operator.\n");
                hasIssues = true;
            } else if (size > 1000000) {
                report.append(indent).append("[INFO] Very large size assertion (").append(size)
                        .append(" bytes). Verify this is intentional.\n");
                hasIssues = true;
            }
        }
        // Check Duration Assertion
        else if (assertionType.contains("DurationAssertion")) {
            long duration = 0;
            try {
                duration = Long.parseLong(element.getPropertyAsString("DurationAssertion.duration", "0"));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (duration <= 0) {
                report.append(indent).append("[WARNING] Duration Assertion with zero or negative duration (").append(duration)
                        .append("ms). Assertion will always fail.\n");
                hasIssues = true;
            } else if (duration < 100) {
                report.append(indent).append("[INFO] Very short duration assertion (").append(duration)
                        .append("ms). This may be too aggressive for most environments.\n");
                hasIssues = true;
            } else if (duration > 30000) {
                report.append(indent).append("[INFO] Very long duration assertion (").append(duration)
                        .append("ms). This may not catch performance issues effectively.\n");
                hasIssues = true;
            }
        }
        // Check HTML Assertion
        else if (assertionType.contains("HTMLAssertion")) {
            boolean errorThresholdEnabled = false;
            boolean warningThresholdEnabled = false;
            try {
                errorThresholdEnabled = "true".equalsIgnoreCase(element.getPropertyAsString("html_assertion_error_threshold_enabled", "false"));
                warningThresholdEnabled = "true".equalsIgnoreCase(element.getPropertyAsString("html_assertion_warning_threshold_enabled", "false"));
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (!errorThresholdEnabled && !warningThresholdEnabled) {
                report.append(indent).append("[WARNING] HTML Assertion with both error and warning thresholds disabled. Assertion will have no effect.\n");
                hasIssues = true;
            }
        }
        // Check JSR223 Assertion
        else if (assertionType.contains("JSR223Assertion")) {
            String script = element.getPropertyAsString("script", "");
            
            if (script.isEmpty()) {
                report.append(indent).append("[WARNING] JSR223 Assertion with empty script. Assertion will have no effect.\n");
                hasIssues = true;
            }
            
            // Check if script contains basic assertion logic
            if (!script.contains("SampleResult") && !script.contains("prev") && !script.contains("AssertionResult")) {
                report.append(indent).append("[INFO] JSR223 Assertion script doesn't appear to reference SampleResult or AssertionResult. Verify assertion logic.\n");
                hasIssues = true;
            }
        }
        
        return hasIssues;
    }

    /**
     * Analyzes a Controller node and adds optimization suggestions.
     * @return True if any issues were found, false otherwise
     */
    private static boolean analyzeController(JMeterTreeNode node, StringBuilder report, int level) {
        String indent = "  ".repeat(level);
        boolean hasIssues = false;
        TestElement element = node.getTestElement();
        String controllerType = element.getClass().getSimpleName();
        
        // Check Loop Controller
        if (controllerType.contains("LoopController")) {
            int loops = 0;
            boolean forever = false;
            try {
                loops = Integer.parseInt(element.getPropertyAsString("LoopController.loops", "0"));
                forever = "true".equalsIgnoreCase(element.getPropertyAsString("LoopController.continue_forever", "false"));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (forever) {
                report.append(indent).append("[WARNING] Loop Controller set to loop forever. This may cause tests to run indefinitely.\n");
                hasIssues = true;
            } else if (loops <= 0) {
                report.append(indent).append("[WARNING] Loop count is zero or negative (").append(loops)
                        .append("). Controller will not execute its children.\n");
                hasIssues = true;
            } else if (loops > 100) {
                report.append(indent).append("[WARNING] High loop count (").append(loops)
                        .append("). Consider reducing for better stability or ensure this is intentional.\n");
                hasIssues = true;
            }
            
            // Check if there are samplers under this controller
            boolean hasSamplers = false;
            for (int i = 0; i < node.getChildCount(); i++) {
                JMeterTreeNode child = (JMeterTreeNode) node.getChildAt(i);
                if (child.getTestElement().getClass().getSimpleName().contains("Sampler")) {
                    hasSamplers = true;
                    break;
                }
            }
            
            if (!hasSamplers && loops > 0) {
                report.append(indent).append("[WARNING] Loop Controller with no samplers. Controller will not perform any requests.\n");
                hasIssues = true;
            }
        }
        // Check If Controller
        else if (controllerType.contains("IfController")) {
            String condition = element.getPropertyAsString("condition", "");
            
            if (condition.isEmpty()) {
                report.append(indent).append("[WARNING] If Controller with empty condition. Controller will not execute its children.\n");
                hasIssues = true;
            } else if (condition.equals("true") || condition.equals("false")) {
                report.append(indent).append("[INFO] If Controller with static condition (").append(condition)
                        .append("). Consider using a Simple Controller instead if the condition never changes.\n");
                hasIssues = true;
            }
            
            // Check for common errors in condition syntax
            if (condition.contains("==") && !condition.contains("'") && !condition.contains("\"") && 
                (condition.contains("contains") || condition.contains("matches"))) {
                report.append(indent).append("[WARNING] If Controller condition may have syntax error. String comparisons should use .equals() not ==.\n");
                hasIssues = true;
            }
            
            // Check if evaluateAll is enabled
            boolean evaluateAll = false;
            try {
                evaluateAll = "true".equalsIgnoreCase(element.getPropertyAsString("IfController.evaluateAll", "false"));
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (!evaluateAll) {
                report.append(indent).append("[INFO] 'Evaluate for all children' is disabled. Enable if you want condition evaluated for each child.\n");
                // Not considering this a critical issue
            }
        }
        // Check While Controller
        else if (controllerType.contains("WhileController")) {
            String condition = element.getPropertyAsString("condition", "");
            
            if (condition.isEmpty()) {
                report.append(indent).append("[WARNING] While Controller with empty condition. Controller will not execute its children.\n");
                hasIssues = true;
            } else if (condition.equals("true")) {
                report.append(indent).append("[WARNING] While Controller with condition 'true'. This will loop indefinitely and may cause test to hang.\n");
                hasIssues = true;
            }
        }
        // Check Transaction Controller
        else if (controllerType.contains("TransactionController")) {
            boolean generateParentSample = false;
            boolean includeTimers = false;
            try {
                generateParentSample = "true".equalsIgnoreCase(element.getPropertyAsString("TransactionController.parent", "false"));
                includeTimers = "true".equalsIgnoreCase(element.getPropertyAsString("TransactionController.includeTimers", "false"));
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (!generateParentSample) {
                report.append(indent).append("[INFO] 'Generate parent sample' is disabled. Enable to get transaction metrics in reports.\n");
                hasIssues = true;
            }
            
            // Check if there are timers under this controller when includeTimers is true
            if (includeTimers) {
                boolean hasTimers = false;
                for (int i = 0; i < node.getChildCount(); i++) {
                    JMeterTreeNode child = (JMeterTreeNode) node.getChildAt(i);
                    if (child.getTestElement().getClass().getSimpleName().contains("Timer")) {
                        hasTimers = true;
                        break;
                    }
                }
                
                if (!hasTimers) {
                    report.append(indent).append("[INFO] 'Include duration of timers' is enabled but no timers found in transaction.\n");
                    // Not considering this a critical issue
                }
            }
        }
        // Check Runtime Controller
        else if (controllerType.contains("RuntimeController")) {
            long runtime = 0;
            try {
                runtime = Long.parseLong(element.getPropertyAsString("RuntimeController.seconds", "0"));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (runtime <= 0) {
                report.append(indent).append("[WARNING] Runtime Controller with zero or negative runtime (").append(runtime)
                        .append(" seconds). Controller will not execute its children.\n");
                hasIssues = true;
            } else if (runtime > 3600) {
                report.append(indent).append("[INFO] Very long runtime (").append(runtime)
                        .append(" seconds / ").append(runtime / 60).append(" minutes). Ensure this is intentional.\n");
                hasIssues = true;
            }
        }
        // Check For Each Controller
        else if (controllerType.contains("ForeachController")) {
            String inputVal = element.getPropertyAsString("ForeachController.inputVal", "");
            String returnVal = element.getPropertyAsString("ForeachController.returnVal", "");
            String useSeparator = element.getPropertyAsString("ForeachController.useSeparator", "");
            
            if (inputVal.isEmpty()) {
                report.append(indent).append("[WARNING] ForEach Controller with empty input variable. Controller will not execute.\n");
                hasIssues = true;
            }
            
            if (returnVal.isEmpty()) {
                report.append(indent).append("[WARNING] ForEach Controller with empty return variable. Values will not be accessible.\n");
                hasIssues = true;
            }
            
            if (inputVal.equals(returnVal)) {
                report.append(indent).append("[WARNING] ForEach Controller using same variable for input and output. This may cause unexpected behavior.\n");
                hasIssues = true;
            }
        }
        // Check Module Controller
        else if (controllerType.contains("ModuleController")) {
            String moduleNodeName = element.getPropertyAsString("ModuleController.node_path", "");
            
            if (moduleNodeName.isEmpty()) {
                report.append(indent).append("[WARNING] Module Controller with no target module selected. Controller will not execute.\n");
                hasIssues = true;
            }
        }
        // Check Include Controller
        else if (controllerType.contains("IncludeController")) {
            String includePath = element.getPropertyAsString("IncludeController.includepath", "");
            
            if (includePath.isEmpty()) {
                report.append(indent).append("[WARNING] Include Controller with no external test fragment path. Controller will not execute.\n");
                hasIssues = true;
            } else if (!includePath.toLowerCase().endsWith(".jmx")) {
                report.append(indent).append("[WARNING] Include Controller path does not end with .jmx. Verify the path is correct.\n");
                hasIssues = true;
            }
        }
        // Check for empty controllers
        if (node.getChildCount() == 0 && !controllerType.contains("ModuleController") && !controllerType.contains("IncludeController")) {
            report.append(indent).append("[WARNING] Empty controller. Controller will not perform any operations.\n");
            hasIssues = true;
        }
        
        return hasIssues;
    }

    /**
     * Analyzes a Listener node and adds optimization suggestions.
     * @return True if any issues were found, false otherwise
     */
    private static boolean analyzeListener(JMeterTreeNode node, StringBuilder report, int level) {
        String indent = "  ".repeat(level);
        boolean hasIssues = false;
        TestElement element = node.getTestElement();
        String listenerType = element.getClass().getSimpleName();
        
        // General warning for all listeners during load testing
        report.append(indent).append("[WARNING] Listeners consume resources. Consider disabling during actual load tests.\n");
        hasIssues = true;
        
        // Check View Results Tree
        if (listenerType.contains("ViewResultsFullVisualizer")) {
            report.append(indent).append("[WARNING] View Results Tree is very memory-intensive. Use only during test development and debugging.\n");
            hasIssues = true;
        }
        // Check Aggregate Report
        else if (listenerType.contains("StatVisualizer")) {
            boolean errorsOnly = false;
            try {
                errorsOnly = "true".equalsIgnoreCase(element.getPropertyAsString("StatVisualizer.errors", "false"));
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (!errorsOnly && node.getParent() != null && ((JMeterTreeNode)node.getParent()).getChildCount() > 10) {
                report.append(indent).append("[INFO] Consider enabling 'Errors Only' for Aggregate Report when testing with many samplers to reduce memory usage.\n");
                hasIssues = true;
            }
        }
        // Check Summary Report
        else if (listenerType.contains("SummaryReport")) {
            // No specific warnings for Summary Report as it's relatively lightweight
        }
        // Check Graph Results
        else if (listenerType.contains("GraphVisualizer")) {
            report.append(indent).append("[WARNING] Graph Results is memory-intensive and can slow down tests. Consider using Summary Report instead.\n");
            hasIssues = true;
        }
        // Check Response Time Graph
        else if (listenerType.contains("RespTimeGraphVisualizer")) {
            String graphTitle = element.getPropertyAsString("RespTimeGraphVisualizer.graphtitle", "");
            int granularity = 0;
            try {
                granularity = Integer.parseInt(element.getPropertyAsString("RespTimeGraphVisualizer.granularity", "0"));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (granularity < 100 && granularity != 0) {
                report.append(indent).append("[INFO] Low granularity (").append(granularity)
                        .append("ms). Consider increasing for long tests.\n");
                hasIssues = true;
            }
        }
        // Check Backend Listener
        else if (listenerType.contains("BackendListener")) {
            String className = element.getPropertyAsString("classname", "");
            
            if (className.isEmpty()) {
                report.append(indent).append("[WARNING] Backend Listener with no implementation class. Listener will not function.\n");
                hasIssues = true;
            }
            
            // Check if using InfluxDB without queue size
            if (className.contains("InfluxDB")) {
                int queueSize = 0;
                try {
                    queueSize = Integer.parseInt(element.getPropertyAsString("influxdbMetricsSender.queueSize", "0"));
                } catch (NumberFormatException e) {
                    // Ignore parsing errors
                }
                
                if (queueSize <= 0) {
                    report.append(indent).append("[INFO] InfluxDB Backend Listener with default queue size. Consider increasing for high-volume tests.\n");
                    hasIssues = true;
                } else if (queueSize > 50000) {
                    report.append(indent).append("[INFO] Very large InfluxDB queue size (").append(queueSize)
                            .append("). This may consume excessive memory.\n");
                    hasIssues = true;
                }
            }
        }
        // Check Simple Data Writer
        else if (listenerType.contains("ResultCollector") && !listenerType.contains("Visualizer")) {
            boolean saveResponseHeaders = false;
            boolean saveRequestHeaders = false;
            boolean saveResponseData = false;
            boolean saveSamplerData = false;
            
            try {
                saveResponseHeaders = "true".equalsIgnoreCase(element.getPropertyAsString("ResultCollector.saveResponseHeaders", "false"));
                saveRequestHeaders = "true".equalsIgnoreCase(element.getPropertyAsString("ResultCollector.saveRequestHeaders", "false"));
                saveResponseData = "true".equalsIgnoreCase(element.getPropertyAsString("ResultCollector.saveResponseData", "false"));
                saveSamplerData = "true".equalsIgnoreCase(element.getPropertyAsString("ResultCollector.saveSamplerData", "false"));
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (saveResponseData) {
                report.append(indent).append("[WARNING] Saving response data can consume large amounts of disk space. Disable for large tests.\n");
                hasIssues = true;
            }
            
            if (saveResponseHeaders && saveRequestHeaders && saveSamplerData) {
                report.append(indent).append("[WARNING] Saving all data types will create very large result files. Consider saving only what's needed.\n");
                hasIssues = true;
            }
            
            String filename = element.getPropertyAsString("filename", "");
            if (filename.isEmpty()) {
                report.append(indent).append("[WARNING] No filename specified for data writer. Results will not be saved.\n");
                hasIssues = true;
            }
        }
        
        return hasIssues;
    }

    /**
     * Analyzes a Config Element node and adds optimization suggestions.
     * @return True if any issues were found, false otherwise
     */
    private static boolean analyzeConfigElement(JMeterTreeNode node, StringBuilder report, int level) {
        String indent = "  ".repeat(level);
        boolean hasIssues = false;
        TestElement element = node.getTestElement();
        String configType = element.getClass().getSimpleName();
        
        // Check CSV Data Set Config
        if (configType.contains("CSVDataSet")) {
            String filename = element.getPropertyAsString("filename", "");
            String variableNames = element.getPropertyAsString("variableNames", "");
            String delimiter = element.getPropertyAsString("delimiter", "");
            boolean recycle = false;
            boolean stopThread = false;
            boolean shareMode = false;
            
            try {
                recycle = "true".equalsIgnoreCase(element.getPropertyAsString("recycle", "true"));
                stopThread = "true".equalsIgnoreCase(element.getPropertyAsString("stopThread", "false"));
                shareMode = "shareMode.all".equals(element.getPropertyAsString("shareMode", ""));
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (filename.isEmpty()) {
                report.append(indent).append("[WARNING] CSV Data Set with empty filename. Data will not be loaded.\n");
                hasIssues = true;
            }
            
            if (variableNames.isEmpty()) {
                report.append(indent).append("[WARNING] CSV Data Set with no variable names. Data will be loaded but not accessible.\n");
                hasIssues = true;
            }
            
            if (!recycle && !stopThread) {
                report.append(indent).append("[WARNING] CSV Data Set configured to not recycle and not stop thread. Test may continue with last used values after data is exhausted.\n");
                hasIssues = true;
            }
            
            // Check if delimiter matches the file content
            if (!delimiter.isEmpty() && !delimiter.equals(",") && filename.toLowerCase().endsWith(".csv")) {
                report.append(indent).append("[INFO] Using non-standard delimiter '").append(delimiter)
                        .append("' with a .csv file. Verify this is intentional.\n");
                hasIssues = true;
            }
            
            // Check share mode settings
            if (shareMode && node.getParent() != null && ((JMeterTreeNode)node.getParent()).getChildCount() > 1) {
                report.append(indent).append("[INFO] CSV Data Set using 'All threads' share mode. Ensure this is intentional for coordinated data access.\n");
                // Not considering this a critical issue
            }
        }
        // Check HTTP Header Manager
        else if (configType.contains("HeaderManager")) {
            int headerCount = 0;
            boolean hasContentType = false;
            boolean hasAccept = false;
            boolean hasUserAgent = false;
            boolean hasAuthorization = false;
            
            try {
                // Check headers
                String headersXml = element.getPropertyAsString("HeaderManager.headers", "");
                headerCount = (headersXml.split("<elementProp").length - 1);
                
                hasContentType = headersXml.toLowerCase().contains("content-type");
                hasAccept = headersXml.toLowerCase().contains("accept");
                hasUserAgent = headersXml.toLowerCase().contains("user-agent");
                hasAuthorization = headersXml.toLowerCase().contains("authorization");
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (headerCount == 0) {
                report.append(indent).append("[WARNING] HTTP Header Manager with no headers defined. It will have no effect.\n");
                hasIssues = true;
            }
            
            // Check for common headers in HTTP requests
            JMeterTreeNode parent = (JMeterTreeNode) node.getParent();
            if (parent != null && parent.getTestElement().getClass().getSimpleName().contains("HTTPSampler")) {
                String method = parent.getTestElement().getPropertyAsString("HTTPSampler.method", "");
                
                if ((method.equals("POST") || method.equals("PUT")) && !hasContentType) {
                    report.append(indent).append("[INFO] HTTP Header Manager for ").append(method)
                            .append(" request without Content-Type header. Consider adding for proper request handling.\n");
                    hasIssues = true;
                }
                
                if (!hasAccept) {
                    report.append(indent).append("[INFO] Consider adding Accept header to specify expected response format.\n");
                    // Not considering this a critical issue
                }
            }
        }
        // Check HTTP Cookie Manager
        else if (configType.contains("CookieManager")) {
            boolean clearCookiesEachIteration = false;
            try {
                clearCookiesEachIteration = "true".equalsIgnoreCase(element.getPropertyAsString("CookieManager.clearEachIteration", "false"));
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (!clearCookiesEachIteration) {
                report.append(indent).append("[INFO] Cookie Manager not clearing cookies between iterations. This may cause unexpected behavior for independent test iterations.\n");
                hasIssues = true;
            }
        }
        // Check HTTP Cache Manager
        else if (configType.contains("CacheManager")) {
            boolean clearCacheEachIteration = false;
            boolean useExpires = false;
            
            try {
                clearCacheEachIteration = "true".equalsIgnoreCase(element.getPropertyAsString("clearEachIteration", "false"));
                useExpires = "true".equalsIgnoreCase(element.getPropertyAsString("useExpires", "true"));
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (!clearCacheEachIteration) {
                report.append(indent).append("[INFO] Cache Manager not clearing cache between iterations. This may not accurately simulate first-time users.\n");
                hasIssues = true;
            }
            
            if (!useExpires) {
                report.append(indent).append("[INFO] Cache Manager not respecting expires header. This may not accurately simulate browser caching behavior.\n");
                hasIssues = true;
            }
        }
        // Check HTTP Authorization Manager
        else if (configType.contains("AuthManager")) {
            int authCount = 0;
            
            try {
                // Check auth entries
                String authXml = element.getPropertyAsString("AuthManager.auth_list", "");
                authCount = (authXml.split("<elementProp").length - 1);
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (authCount == 0) {
                report.append(indent).append("[WARNING] HTTP Authorization Manager with no credentials defined. It will have no effect.\n");
                hasIssues = true;
            }
        }
        // Check User Defined Variables
        else if (configType.contains("UserParameters") || configType.contains("Arguments")) {
            int variableCount = 0;
            
            try {
                // Check variables
                String argsXml = element.getPropertyAsString("arguments", "");
                variableCount = (argsXml.split("<elementProp").length - 1);
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (variableCount == 0) {
                report.append(indent).append("[WARNING] User Defined Variables with no variables defined. It will have no effect.\n");
                hasIssues = true;
            }
        }
        // Check Counter
        else if (configType.contains("CounterConfig")) {
            long start = 0;
            long end = 0;
            long increment = 0;
            boolean reset = false;
            
            try {
                start = Long.parseLong(element.getPropertyAsString("CounterConfig.start", "0"));
                end = Long.parseLong(element.getPropertyAsString("CounterConfig.end", "0"));
                increment = Long.parseLong(element.getPropertyAsString("CounterConfig.incr", "0"));
                reset = "true".equalsIgnoreCase(element.getPropertyAsString("CounterConfig.reset_on_tg_iteration", "false"));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (increment == 0) {
                report.append(indent).append("[WARNING] Counter with zero increment. Value will not change.\n");
                hasIssues = true;
            }
            
            if (end > 0 && end < start && increment > 0) {
                report.append(indent).append("[WARNING] Counter with end value (").append(end)
                        .append(") less than start value (").append(start)
                        .append(") and positive increment. Counter will never reach end value.\n");
                hasIssues = true;
            }
            
            if (end > 0 && end > start && increment < 0) {
                report.append(indent).append("[WARNING] Counter with end value (").append(end)
                        .append(") greater than start value (").append(start)
                        .append(") and negative increment. Counter will never reach end value.\n");
                hasIssues = true;
            }
            
            String varName = element.getPropertyAsString("CounterConfig.name", "");
            if (varName.isEmpty()) {
                report.append(indent).append("[WARNING] Counter with no variable name. Value will not be accessible.\n");
                hasIssues = true;
            }
        }
        // Check Random Variable
        else if (configType.contains("RandomVariableConfig")) {
            long minimum = 0;
            long maximum = 0;
            
            try {
                minimum = Long.parseLong(element.getPropertyAsString("RandomVariableConfig.minimumValue", "0"));
                maximum = Long.parseLong(element.getPropertyAsString("RandomVariableConfig.maximumValue", "0"));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
            
            if (maximum <= minimum) {
                report.append(indent).append("[WARNING] Random Variable with maximum value (").append(maximum)
                        .append(") less than or equal to minimum value (").append(minimum)
                        .append("). This will always generate the same value.\n");
                hasIssues = true;
            }
            
            String varName = element.getPropertyAsString("RandomVariableConfig.variableName", "");
            if (varName.isEmpty()) {
                report.append(indent).append("[WARNING] Random Variable with no variable name. Value will not be accessible.\n");
                hasIssues = true;
            }
        }
        
        return hasIssues;
    }

    /**
     * Analyzes an Extractor node and adds optimization suggestions.
     * @return True if any issues were found, false otherwise
     */
    private static boolean analyzeExtractor(JMeterTreeNode node, StringBuilder report, int level) {
        String indent = "  ".repeat(level);
        boolean hasIssues = false;
        TestElement element = node.getTestElement();
        String extractorType = element.getClass().getSimpleName();
        
        // Common variable checks for all extractors
        String refName = element.getPropertyAsString("refname", "");
        if (refName.isEmpty()) {
            report.append(indent).append("[WARNING] Extractor with no reference name. Extracted values will not be accessible.\n");
            hasIssues = true;
        }
        
        // Check Regular Expression Extractor
        if (extractorType.contains("RegexExtractor")) {
            String regex = element.getPropertyAsString("RegexExtractor.regex", "");
            String template = element.getPropertyAsString("RegexExtractor.template", "");
            String defaultValue = element.getPropertyAsString("RegexExtractor.default", "");
            String matchNumber = element.getPropertyAsString("RegexExtractor.match_number", "");
            
            if (regex.isEmpty()) {
                report.append(indent).append("[WARNING] Regular Expression Extractor with empty regex pattern. No values will be extracted.\n");
                hasIssues = true;
            } else if (regex.contains(".*") && regex.length() > 4) {
                report.append(indent).append("[INFO] Regular Expression using greedy wildcard (.*). Consider using non-greedy wildcard (.*?) for more precise matching.\n");
                hasIssues = true;
            }
            
            if (defaultValue.isEmpty()) {
                report.append(indent).append("[INFO] Regular Expression Extractor with no default value. If pattern doesn't match, variable will be empty.\n");
                hasIssues = true;
            }
            
            try {
                int matchNum = Integer.parseInt(matchNumber);
                if (matchNum == 0) {
                    report.append(indent).append("[INFO] Regular Expression Extractor set to random match. This may cause inconsistent test behavior.\n");
                    hasIssues = true;
                } else if (matchNum == -1) {
                    report.append(indent).append("[INFO] Regular Expression Extractor set to match all occurrences. Ensure your script handles multiple values correctly.\n");
                    // Not considering this a critical issue
                }
            } catch (NumberFormatException e) {
                report.append(indent).append("[WARNING] Regular Expression Extractor with invalid match number. Default (1) will be used.\n");
                hasIssues = true;
            }
        }
        // Check CSS/JQuery Extractor
        else if (extractorType.contains("HtmlExtractor")) {
            String expression = element.getPropertyAsString("expression", "");
            String attribute = element.getPropertyAsString("attribute", "");
            String defaultValue = element.getPropertyAsString("default", "");
            String matchNumber = element.getPropertyAsString("match_number", "");
            
            if (expression.isEmpty()) {
                report.append(indent).append("[WARNING] CSS/JQuery Extractor with empty expression. No values will be extracted.\n");
                hasIssues = true;
            }
            
            if (defaultValue.isEmpty()) {
                report.append(indent).append("[INFO] CSS/JQuery Extractor with no default value. If expression doesn't match, variable will be empty.\n");
                hasIssues = true;
            }
            
            try {
                int matchNum = Integer.parseInt(matchNumber);
                if (matchNum == 0) {
                    report.append(indent).append("[INFO] CSS/JQuery Extractor set to random match. This may cause inconsistent test behavior.\n");
                    hasIssues = true;
                }
            } catch (NumberFormatException e) {
                report.append(indent).append("[WARNING] CSS/JQuery Extractor with invalid match number. Default (0) will be used.\n");
                hasIssues = true;
            }
        }
        // Check XPath Extractor
        else if (extractorType.contains("XPathExtractor")) {
            String xpath = element.getPropertyAsString("XPathExtractor.xpathQuery", "");
            String defaultValue = element.getPropertyAsString("XPathExtractor.default", "");
            boolean namespaces = false;
            
            try {
                namespaces = "true".equalsIgnoreCase(element.getPropertyAsString("XPathExtractor.namespace", "false"));
            } catch (Exception e) {
                // Ignore errors
            }
            
            if (xpath.isEmpty()) {
                report.append(indent).append("[WARNING] XPath Extractor with empty XPath query. No values will be extracted.\n");
                hasIssues = true;
            } else if (xpath.contains("//") && !xpath.startsWith("//")) {
                report.append(indent).append("[INFO] XPath query using descendant axis (//). This may be inefficient for large XML documents.\n");
                hasIssues = true;
            }
            
            if (defaultValue.isEmpty()) {
                report.append(indent).append("[INFO] XPath Extractor with no default value. If query doesn't match, variable will be empty.\n");
                hasIssues = true;
            }
            
            // Check for XML with namespaces
            if (xpath.contains(":") && !namespaces) {
                report.append(indent).append("[WARNING] XPath query contains namespace prefixes but namespace support is not enabled. Query will likely fail.\n");
                hasIssues = true;
            }
        }
        // Check JSON Extractor
        else if (extractorType.contains("JSONPostProcessor")) {
            String jsonPath = element.getPropertyAsString("JSONPostProcessor.jsonPathExprs", "");
            String defaultValue = element.getPropertyAsString("JSONPostProcessor.defaultValues", "");
            String matchNumber = element.getPropertyAsString("JSONPostProcessor.match_numbers", "");
            
            if (jsonPath.isEmpty()) {
                report.append(indent).append("[WARNING] JSON Extractor with empty JSONPath expression. No values will be extracted.\n");
                hasIssues = true;
            }
            
            if (defaultValue.isEmpty()) {
                report.append(indent).append("[INFO] JSON Extractor with no default value. If expression doesn't match, variable will be empty.\n");
                hasIssues = true;
            }
            
            try {
                int matchNum = Integer.parseInt(matchNumber);
                if (matchNum == 0) {
                    report.append(indent).append("[INFO] JSON Extractor set to random match. This may cause inconsistent test behavior.\n");
                    hasIssues = true;
                }
            } catch (NumberFormatException e) {
                // Not an issue, as it could be a comma-separated list
            }
        }
        // Check Boundary Extractor
        else if (extractorType.contains("BoundaryExtractor")) {
            String leftBoundary = element.getPropertyAsString("leftBoundary", "");
            String rightBoundary = element.getPropertyAsString("rightBoundary", "");
            String defaultValue = element.getPropertyAsString("defaultValue", "");
            String matchNumber = element.getPropertyAsString("matchNumber", "");
            
            if (leftBoundary.isEmpty() && rightBoundary.isEmpty()) {
                report.append(indent).append("[WARNING] Boundary Extractor with no boundaries defined. All content will be extracted.\n");
                hasIssues = true;
            }
            
            if (defaultValue.isEmpty()) {
                report.append(indent).append("[INFO] Boundary Extractor with no default value. If boundaries don't match, variable will be empty.\n");
                hasIssues = true;
            }
            
            try {
                int matchNum = Integer.parseInt(matchNumber);
                if (matchNum == 0) {
                    report.append(indent).append("[INFO] Boundary Extractor set to random match. This may cause inconsistent test behavior.\n");
                    hasIssues = true;
                }
            } catch (NumberFormatException e) {
                report.append(indent).append("[WARNING] Boundary Extractor with invalid match number. Default (1) will be used.\n");
                hasIssues = true;
            }
        }
        // Check Debug PostProcessor
        else if (extractorType.contains("DebugPostProcessor")) {
            report.append(indent).append("[WARNING] Debug PostProcessor is enabled. This should be removed or disabled for production tests as it impacts performance.\n");
            hasIssues = true;
        }
        
        return hasIssues;
    }

    /**
     * Checks if the test plan has elements of a specific type.
     * 
     * @param root The root node to start searching from
     * @param type The type of element to check for
     * @return True if elements of the specified type are found, false otherwise
     */
    private static boolean hasElementOfType(JMeterTreeNode root, String type) {
        if (root == null) {
            return false;
        }
        
        if (root.getTestElement().getClass().getSimpleName().contains(type)) {
            return true;
        }
        
        for (int i = 0; i < root.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) root.getChildAt(i);
            if (hasElementOfType(child, type)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Processes a user message to determine if it's requesting to delete or remove an element.
     * 
     * @param message The user message to process
     * @return A response message if the request was handled, null otherwise
     */
    public static String processDeleteRequest(String message) {
        if (message == null) {
            return null;
        }

        // Check if the message matches the delete request pattern
        Matcher matcher = DELETE_REQUEST_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        log.info("Detected request to delete or remove an element");

        // Get the action verb used (delete, remove, etc.)
        String actionVerb = matcher.group(1);
        
        // Try to get the element type if specified
        String elementType = null;
        if (matcher.group(2) != null) {
            elementType = matcher.group(2).trim();
        } else if (matcher.group(3) != null) {
            elementType = matcher.group(3).trim();
        }

        if (elementType != null) {
            log.info("Element type specified: {}", elementType);
            return "I'm sorry, but for safety reasons, I cannot " + actionVerb + " " + elementType + "s from your test plan. " +
                   "To " + actionVerb + " elements, please use JMeter's built-in controls in the GUI. " +
                   "This helps prevent accidental data loss in your test plans.";
        } else {
            return "I'm sorry, but for safety reasons, I cannot " + actionVerb + " elements from your test plan. " +
                   "To " + actionVerb + " elements, please use JMeter's built-in controls in the GUI. " +
                   "This helps prevent accidental data loss in your test plans.";
        }
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
     * Processes a user message to determine if it's requesting to create a performance test plan.
     * 
     * @param userMessage The user's message
     * @return A response message, or null if the message is not a request to create a performance test plan
     */
    public static String processPerformanceTestPlanRequest(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return null;
        }

        // Check if the message is about creating a performance test plan
        String normalized = userMessage.toLowerCase().trim();
        if (normalized.contains("performance test plan") || 
            (normalized.contains("performance") && normalized.contains("test") && normalized.contains("plan")) ||
            (normalized.contains("create") && normalized.contains("performance") && normalized.contains("test"))) {
            
            log.info("Detected request to create a performance test plan: {}", userMessage);
            
            // Get the currently selected node to provide context-aware guidance
            GuiPackage guiPackage = GuiPackage.getInstance();
            JMeterTreeNode currentNode = null;
            if (guiPackage != null) {
                currentNode = guiPackage.getTreeListener().getCurrentNode();
            }
            
            StringBuilder response = new StringBuilder();
            response.append("Here's how to create a performance test plan in JMeter:\n\n");
            
            // Check if we need to create a test plan first
            boolean testPlanExists = false;
            if (currentNode != null) {
                String nodeType = currentNode.getTestElement().getClass().getSimpleName();
                testPlanExists = nodeType.equals("TestPlan") || hasParentOfType(currentNode, "TestPlan");
            }
            
            if (!testPlanExists) {
                response.append("1. **First, create a Test Plan**: This is the container for all test elements.\n");
            }
            
            response.append("2. **Add a Thread Group**: This represents your users and controls the number of concurrent users, ramp-up period, and loop count.\n");
            response.append("3. **Add HTTP Request samplers**: These define the requests your virtual users will make to the server.\n");
            response.append("4. **Add Assertions**: These validate that the responses meet your expectations (e.g., Response Assertion, JSON Path Assertion).\n");
            response.append("5. **Add Timers**: These add delays between requests to simulate realistic user behavior (e.g., Constant Timer, Uniform Random Timer).\n");
            response.append("6. **Add Listeners**: These collect and display test results (e.g., View Results Tree, Aggregate Report).\n\n");
            
            response.append("I'll show you some suggested elements that you can add to your test plan based on your current selection.");
            
            return response.toString();
        }
        
        return null;
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
