package org.qainsights.jmeter.ai.lint;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.JMeterGUIComponent;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.PropertyIterator;
import org.qainsights.jmeter.ai.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * A utility class for renaming JMeter test plan elements using AI suggestions.
 * This class analyzes test plan elements and suggests meaningful names based on their properties.
 */
public class ElementRenamer {
    private static final Logger log = LoggerFactory.getLogger(ElementRenamer.class);
    private final AiService aiService;
    private static final String DISABLED_PREFIX = "Disabled_";
    
    // Store the original names for undo functionality
    private static List<NameBackup> lastRenameOperation = new ArrayList<>();
    
    // Store the undone operations for redo functionality
    private static List<NameBackup> lastUndoneOperation = new ArrayList<>();
    
    /**
     * Constructor for ElementRenamer.
     * 
     * @param aiService The AI service to use for generating name suggestions
     */
    public ElementRenamer(AiService aiService) {
        this.aiService = aiService;
    }
    
    /**
     * Renames elements in the test plan based on the current selection.
     * If the root test plan is selected, all elements are renamed.
     * If specific elements are selected, only those elements are renamed.
     * 
     * @return A message indicating the result of the renaming operation
     */
    public String renameElements() {
        // Call the overloaded method with null command for backward compatibility
        return renameElements(null);
    }
    
    /**
     * Renames elements in the test plan based on the current selection.
     * If the root test plan is selected, all elements are renamed.
     * If specific elements are selected, only those elements are renamed.
     * 
     * @param command The user's command for how to rename elements (e.g., "make it all caps", "use camelcase")
     * @return A message indicating the result of the renaming operation
     */
    public String renameElements(String command) {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null) {
            log.error("GuiPackage is null");
            return "Error: JMeter GUI is not available";
        }
        
        JMeterTreeNode[] selectedNodes = guiPackage.getTreeListener().getSelectedNodes();
        if (selectedNodes == null || selectedNodes.length == 0) {
            log.error("No elements selected");
            return "Please select at least one element in the test plan";
        }
        
        // Check if the root test plan is selected
        boolean isRootSelected = false;
        for (JMeterTreeNode node : selectedNodes) {
            if (node.getName().equals("Test Plan")) {
                isRootSelected = true;
                break;
            }
        }
        
        List<ElementInfo> elementsToRename = new ArrayList<>();
        
        if (isRootSelected) {
            // Rename all elements in the test plan
            JMeterTreeNode root = (JMeterTreeNode) guiPackage.getTreeModel().getRoot();
            collectElementsToRename(root, elementsToRename);
        } else {
            // Rename only selected elements
            for (JMeterTreeNode node : selectedNodes) {
                collectElementsToRename(node, elementsToRename);
            }
        }
        
        if (elementsToRename.isEmpty()) {
            return "No elements found to rename";
        }
        
        // Generate AI suggestions for renaming
        String suggestions = getAiSuggestions(elementsToRename, command);
        if (suggestions == null || suggestions.isEmpty()) {
            return "Failed to get AI suggestions for renaming";
        }
        
        // Apply the suggestions to rename the elements
        int renamedCount = applyRenameSuggestions(elementsToRename, suggestions);
        
        return "Successfully renamed " + renamedCount + " elements in the test plan";
    }
    
    /**
     * Recursively collects elements to be renamed from the test plan.
     * 
     * @param node The current node to process
     * @param elementsToRename The list to populate with elements to rename
     */
    private void collectElementsToRename(JMeterTreeNode node, List<ElementInfo> elementsToRename) {
        TestElement element = node.getTestElement();
        
        // Skip the Test Plan node itself
        if (!node.getName().equals("Test Plan")) {
            ElementInfo info = new ElementInfo();
            info.node = node;
            info.element = element;
            info.name = node.getName();
            info.type = element.getClass().getSimpleName();
            info.isDisabled = !element.isEnabled();
            info.properties = extractRelevantProperties(element);
            
            elementsToRename.add(info);
        }
        
        // Process child nodes
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode childNode = (JMeterTreeNode) children.nextElement();
            collectElementsToRename(childNode, elementsToRename);
        }
    }
    
    /**
     * Extracts relevant properties from a test element for AI analysis.
     * 
     * @param element The test element to extract properties from
     * @return A string containing relevant property information
     */
    private String extractRelevantProperties(TestElement element) {
        StringBuilder properties = new StringBuilder();
        
        PropertyIterator iter = element.propertyIterator();
        while (iter.hasNext()) {
            JMeterProperty prop = iter.next();
            String name = prop.getName();
            String value = prop.getStringValue();
            
            // Filter out properties that are likely to be useful for naming
            if (isRelevantProperty(name, value)) {
                properties.append(name).append(": ").append(value).append("\n");
            }
        }
        
        return properties.toString();
    }
    
    /**
     * Determines if a property is relevant for naming purposes.
     * 
     * @param name The property name
     * @param value The property value
     * @return True if the property is relevant, false otherwise
     */
    private boolean isRelevantProperty(String name, String value) {
        // Skip empty values
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        // Skip technical properties that don't provide semantic meaning
        String lowerName = name.toLowerCase();
        if (lowerName.contains("enabled") || 
            lowerName.contains("guiclass") || 
            lowerName.contains("testclass") || 
            lowerName.contains("testname") ||
            lowerName.contains("testplan") ||
            lowerName.equals("name")) {
            return false;
        }
        
        // Include properties that are likely to be useful for naming
        return lowerName.contains("path") ||
               lowerName.contains("url") ||
               lowerName.contains("domain") ||
               lowerName.contains("port") ||
               lowerName.contains("method") ||
               lowerName.contains("query") ||
               lowerName.contains("resource") ||
               lowerName.contains("endpoint") ||
               lowerName.contains("script") ||
               lowerName.contains("command") ||
               lowerName.contains("statement") ||
               lowerName.contains("expression") ||
               lowerName.contains("pattern") ||
               lowerName.contains("assertion") ||
               lowerName.contains("variable");
    }
    
    /**
     * Gets AI suggestions for renaming elements.
     * 
     * @param elementsToRename The list of elements to rename
     * @param command The user's command for how to rename elements
     * @return AI-generated suggestions for renaming
     */
    private String getAiSuggestions(List<ElementInfo> elementsToRename, String command) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Help the user to rename the elements in the test plan. Here are the elements details. Give me meaningful names for the elements.\n");
        prompt.append("e.g. HTTP_PostLogin, HTTP_Request_To_Authenticate_Users_And_Get_Token, TG_GenerateOrders\n");
        
        // Add user's specific naming style instructions if provided
        if (command != null && !command.equalsIgnoreCase("rename")) {
            prompt.append("User has requested the following naming style: " + command + "\n");
            
            // Check if the user has specific instructions about numbering or beginning format
            boolean hasNumberingInstructions = command.toLowerCase().contains("number") || 
                                             command.toLowerCase().contains("sequenc") || 
                                             command.toLowerCase().contains("order");
                                             
            boolean hasBeginningInstructions = command.toLowerCase().contains("beginning") || 
                                             command.toLowerCase().contains("start") || 
                                             command.toLowerCase().contains("prefix");
            
            // Check for the specific combined case of camel case with numbers at the beginning
            boolean isCamelCase = command.toLowerCase().contains("camel case") || command.toLowerCase().contains("camelcase");
            boolean isNumbersAtBeginning = command.toLowerCase().contains("add the numbers in the beginning") || 
                                         command.toLowerCase().contains("numbers in the beginning") || 
                                         command.toLowerCase().contains("numbers at the beginning");
            
            if (isCamelCase && isNumbersAtBeginning) {
                prompt.append("\nIMPORTANT: The user wants camelCase with numbers at the BEGINNING of each name. " +
                              "For example: '10httpLogin', '20getUsers', '30verifyOrder'. " +
                              "Do NOT use underscores or any other format. The first part should be the number, " +
                              "followed immediately by the camelCase name (first word lowercase, subsequent words capitalized).\n");
            }
            // Special case for "add the numbers in the beginning" only
            else if (isNumbersAtBeginning) {
                prompt.append("\nIMPORTANT: The user wants numbers at the BEGINNING of each name. For example, instead of 'httpLogin' use '10HttpLogin', '20HttpGetUsers', etc.\n");
            } 
            // Only add the default sequencing suggestion if the user hasn't specified numbering or beginning preferences
            else if (!hasNumberingInstructions && !hasBeginningInstructions) {
                prompt.append("\nIf possible, sequentially rename the elements in the test plan. e.g. HTTP_10_Login, HTTP_20_Request_To_Authenticate_Users_And_Get_Token, TG_10_GenerateOrders, TG_20_Verify_Orders\n");
            }
            
            // Special case for camel case (only if not already handled in the combined case)
            if (isCamelCase && !isNumbersAtBeginning) {
                prompt.append("\nFor camelCase, the first letter should be lowercase, and the first letter of each subsequent word should be uppercase. For example: 'httpRequest', 'getUsers', 'verifyLogin'.\n");
            }
        } else {
            prompt.append("Use the snake_case by default, unless the user specifies otherwise.\n");
            prompt.append("\nIf possible, sequentially rename the elements in the test plan. e.g. HTTP_10_Login, HTTP_20_Request_To_Authenticate_Users_And_Get_Token, TG_10_GenerateOrders, TG_20_Verify_Orders\n");
        }
        
        prompt.append("\n");
        prompt.append("Elements to rename:\n\n");
        
        AtomicInteger counter = new AtomicInteger(1);
        elementsToRename.forEach(info -> {
            prompt.append("Element ").append(counter.getAndIncrement()).append(":\n");
            prompt.append("Type: ").append(info.type).append("\n");
            prompt.append("Current Name: ").append(info.name).append("\n");
            prompt.append("Is Disabled: ").append(info.isDisabled).append("\n");
            if (!info.properties.isEmpty()) {
                prompt.append("Properties:\n").append(info.properties).append("\n");
            }
            prompt.append("\n");
        });
        
        prompt.append("For each element, provide a new name in the format 'Element X: NEW_NAME'. Make sure the names are meaningful and reflect the purpose of the element.\n");
        prompt.append("DO NOT add any 'Disabled_' prefix to the names. The system will handle disabled elements automatically.\n");
        
        try {
            return aiService.generateResponse(java.util.Collections.singletonList(prompt.toString()));
        } catch (Exception e) {
            log.error("Error getting AI suggestions", e);
            return null;
        }
    }
    
    /**
     * Applies the AI-suggested names to the elements.
     * 
     * @param elementsToRename The list of elements to rename
     * @param suggestions The AI-generated suggestions
     * @return The number of elements successfully renamed
     */
    private int applyRenameSuggestions(List<ElementInfo> elementsToRename, String suggestions) {
        int renamedCount = 0;
        
        // Clear previous rename operation backup
        lastRenameOperation.clear();
        
        // Parse the AI response to extract suggested names
        List<String> suggestionLines = suggestions.lines()
                .filter(line -> line.startsWith("Element ") && line.contains(":"))
                .collect(Collectors.toList());
        
        // Get the currently selected node for special handling
        GuiPackage guiPackage = GuiPackage.getInstance();
        JMeterTreeNode currentNode = guiPackage != null ? guiPackage.getTreeListener().getCurrentNode() : null;
        
        // Apply suggestions if available
        if (!suggestionLines.isEmpty()) {
            for (int i = 0; i < Math.min(elementsToRename.size(), suggestionLines.size()); i++) {
                String line = suggestionLines.get(i);
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String newName = parts[1].trim();
                    ElementInfo info = elementsToRename.get(i);
                    
                    // Add "Disabled_" prefix if the element is disabled
                    // First, remove any existing "Disabled_" prefix that might have been added by the AI
                    if (newName.startsWith(DISABLED_PREFIX)) {
                        newName = newName.substring(DISABLED_PREFIX.length());
                    }
                    
                    // Now add the prefix if the element is actually disabled
                    if (info.isDisabled) {
                        newName = DISABLED_PREFIX + newName;
                    }
                    
                    // Backup the original name before changing it
                    lastRenameOperation.add(new NameBackup(info.node, info.element, info.name));
                    
                    // Apply the new name
                    info.node.setName(newName);
                    info.element.setName(newName);
                    info.element.setProperty(TestElement.NAME, newName);
                    
                    // Check if this is the currently selected node
                    boolean isCurrentNode = (currentNode != null && currentNode.equals(info.node));
                    
                    // Update the GUI component if this is the currently selected node
                    if (isCurrentNode && guiPackage != null) {
                        // Get the current GUI component and update it
                        JMeterGUIComponent comp = guiPackage.getCurrentGui();
                        if (comp != null) {
                            comp.configure(info.element);
                        }
                    }
                    
                    // Notify the tree model that the node has changed
                    if (guiPackage != null) {
                        guiPackage.getTreeModel().nodeChanged(info.node);
                    }
                    
                    renamedCount++;
                }
            }
        }
        
        return renamedCount;
    }
    
    /**
     * Undoes the last rename operation.
     * 
     * @return A message indicating the result of the undo operation
     */
    public static String undoLastRename() {
        if (lastRenameOperation.isEmpty()) {
            return "Nothing to undo.";
        }
        
        int restoredCount = 0;
        
        // Clear previous undone operation backup before storing new ones
        lastUndoneOperation.clear();
        
        // Get the currently selected node for special handling
        GuiPackage guiPackage = GuiPackage.getInstance();
        JMeterTreeNode currentNode = guiPackage != null ? guiPackage.getTreeListener().getCurrentNode() : null;
        
        for (NameBackup backup : lastRenameOperation) {
            // Store the current name before undoing for redo functionality
            lastUndoneOperation.add(new NameBackup(backup.node, backup.element, backup.node.getName()));
            
            backup.node.setName(backup.originalName);
            backup.element.setName(backup.originalName);
            backup.element.setProperty(TestElement.NAME, backup.originalName);
            
            // Check if this is the currently selected node
            boolean isCurrentNode = (currentNode != null && currentNode.equals(backup.node));
            
            // Update the GUI component if this is the currently selected node
            if (isCurrentNode && guiPackage != null) {
                // Get the current GUI component and update it
                JMeterGUIComponent comp = guiPackage.getCurrentGui();
                if (comp != null) {
                    comp.configure(backup.element);
                }
            }
            
            // Notify the tree model that the node has changed
            if (guiPackage != null) {
                guiPackage.getTreeModel().nodeChanged(backup.node);
            }
            
            restoredCount++;
        }
        
        // Clear the backup after undoing
        lastRenameOperation = new ArrayList<>();
        
        return "Successfully restored " + restoredCount + " element names.";
    }
    
    /**
     * Redoes the last undone rename operation.
     * 
     * @return A message indicating the result of the redo operation
     */
    public static String redoLastUndo() {
        if (lastUndoneOperation.isEmpty()) {
            return "Nothing to redo.";
        }
        
        int redoneCount = 0;
        
        // Clear previous rename operation backup before storing new ones
        lastRenameOperation.clear();
        
        // Get the currently selected node for special handling
        GuiPackage guiPackage = GuiPackage.getInstance();
        JMeterTreeNode currentNode = guiPackage != null ? guiPackage.getTreeListener().getCurrentNode() : null;
        
        for (NameBackup backup : lastUndoneOperation) {
            // Store the current name before redoing for undo functionality
            lastRenameOperation.add(new NameBackup(backup.node, backup.element, backup.node.getName()));
            
            backup.node.setName(backup.originalName);
            backup.element.setName(backup.originalName);
            backup.element.setProperty(TestElement.NAME, backup.originalName);
            
            // Check if this is the currently selected node
            boolean isCurrentNode = (currentNode != null && currentNode.equals(backup.node));
            
            // Update the GUI component if this is the currently selected node
            if (isCurrentNode && guiPackage != null) {
                // Get the current GUI component and update it
                JMeterGUIComponent comp = guiPackage.getCurrentGui();
                if (comp != null) {
                    comp.configure(backup.element);
                }
            }
            
            // Notify the tree model that the node has changed
            if (guiPackage != null) {
                guiPackage.getTreeModel().nodeChanged(backup.node);
            }
            
            redoneCount++;
        }
        
        // Clear the undone backup after redoing
        lastUndoneOperation = new ArrayList<>();
        
        return "Successfully redone " + redoneCount + " element renames.";
    }
    
    /**
     * Inner class to hold information about elements to be renamed.
     */
    private static class ElementInfo {
        JMeterTreeNode node;
        TestElement element;
        String name;
        String type;
        boolean isDisabled;
        String properties;
    }
    
    /**
     * Inner class to store original element names for undo functionality.
     */
    private static class NameBackup {
        JMeterTreeNode node;
        TestElement element;
        String originalName;
        
        public NameBackup(JMeterTreeNode node, TestElement element, String originalName) {
            this.node = node;
            this.element = element;
            this.originalName = originalName;
        }
    }
}
