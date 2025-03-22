package org.qainsights.jmeter.ai.gui;

import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.ThreadGroup;
import org.qainsights.jmeter.ai.utils.JMeterElementManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.tree.TreeNode;
import java.util.*;

/**
 * Handler for the @wrap command that intelligently groups HTTP request samplers
 * under a Transaction Controller when a Thread Group is selected.
 */
public class WrapCommandHandler {
    private static final Logger log = LoggerFactory.getLogger(WrapCommandHandler.class);

    /**
     * Processes the @wrap command, checking if a Thread Group is selected and grouping
     * HTTP request samplers under Transaction Controllers.
     *
     * @return A response message indicating the result of the operation
     */
    public String processWrapCommand() {
        log.info("Processing @wrap command");

        try {
            // Check if a Thread Group is selected
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage == null) {
                log.error("GuiPackage is null, cannot process @wrap command");
                return "Error: JMeter GUI is not available.";
            }

            JMeterTreeNode currentNode = guiPackage.getTreeListener().getCurrentNode();
            if (currentNode == null) {
                log.error("No node is currently selected in the test plan");
                return "Please select a Thread Group in the test plan before using the @wrap command.";
            }

            TestElement element = currentNode.getTestElement();
            if (!(element instanceof ThreadGroup)) {
                log.error("Selected element is not a Thread Group");
                return "Please select a Thread Group in the test plan before using the @wrap command. " +
                       "The currently selected element is a " + element.getClass().getSimpleName() + ".";
            }

            // Get all HTTP samplers in the Thread Group
            log.info("Finding samplers in Thread Group: {}", currentNode.getName());
            List<JMeterTreeNode> httpSamplers = findSamplers(currentNode);
            log.info("Found {} samplers in the Thread Group", httpSamplers.size());
            
            // Log the names of all found samplers for debugging
            for (JMeterTreeNode sampler : httpSamplers) {
                log.info("Found sampler: {} ({})", sampler.getName(), sampler.getTestElement().getClass().getSimpleName());
            }
            
            if (httpSamplers.isEmpty()) {
                log.info("No samplers found in the Thread Group");
                return "No samplers found in the Thread Group. Nothing to wrap.";
            }

            // Group samplers by similarity
            log.info("Grouping samplers by similarity");
            Map<String, List<JMeterTreeNode>> samplerGroups = groupSamplersBySimilarity(httpSamplers);
            
            // Log the groups that were created
            log.info("Created {} sampler groups", samplerGroups.size());
            for (Map.Entry<String, List<JMeterTreeNode>> entry : samplerGroups.entrySet()) {
                log.info("Group key: '{}', contains {} samplers", entry.getKey(), entry.getValue().size());
            }
            
            // Create Transaction Controllers and move samplers
            log.info("Creating Transaction Controllers and moving samplers");
            int groupsCreated = createTransactionControllersAndMoveSamplers(currentNode, samplerGroups);

            // Refresh the tree
            guiPackage.getTreeModel().nodeStructureChanged(currentNode);

            return "Successfully grouped samplers into " + groupsCreated + " Transaction Controllers based on similarity.";
        } catch (Exception e) {
            log.error("Error processing @wrap command", e);
            return "Error processing @wrap command: " + e.getMessage();
        }
    }

    /**
     * Finds all samplers in the given node and its children.
     *
     * @param node The node to search in
     * @return A list of sampler nodes
     */
    private List<JMeterTreeNode> findSamplers(JMeterTreeNode node) {
        List<JMeterTreeNode> samplers = new ArrayList<>();
        
        log.debug("Searching for samplers in node: {} ({})", node.getName(), 
                node.getTestElement().getClass().getSimpleName());
        log.debug("Node has {} children", node.getChildCount());
        
        // Skip the node itself, only process children
        for (int i = 0; i < node.getChildCount(); i++) {
            TreeNode childNode = node.getChildAt(i);
            if (childNode instanceof JMeterTreeNode) {
                JMeterTreeNode jmeterChildNode = (JMeterTreeNode) childNode;
                TestElement childElement = jmeterChildNode.getTestElement();
                
                log.debug("Examining child node: {} ({})", jmeterChildNode.getName(), 
                        childElement.getClass().getSimpleName());
                
                if (childElement instanceof Sampler) {
                    // Check if the class name contains "Sampler"
                    String className = childElement.getClass().getSimpleName();
                    if (className.contains("Sampler")) {
                        // Found a valid sampler
                        log.debug("Found sampler: {}", jmeterChildNode.getName());
                        samplers.add(jmeterChildNode);
                    } else {
                        log.debug("Skipping non-Sampler element: {} ({})", jmeterChildNode.getName(), className);
                    }
                } else if (!(childElement instanceof TransactionController)) {
                    // If it's not a Transaction Controller, recursively search its children
                    // This ensures we don't process samplers that are already in Transaction Controllers
                    log.debug("Recursively searching in: {}", jmeterChildNode.getName());
                    List<JMeterTreeNode> childSamplers = findSamplers(jmeterChildNode);
                    log.debug("Found {} samplers in child node: {}", childSamplers.size(), jmeterChildNode.getName());
                    samplers.addAll(childSamplers);
                } else {
                    log.debug("Skipping Transaction Controller: {}", jmeterChildNode.getName());
                }
            }
        }
        
        return samplers;
    }

    /**
     * Groups samplers by similarity based on their name and properties.
     *
     * @param samplers The list of sampler nodes to group
     * @return A map of group keys to lists of sampler nodes
     */
    private Map<String, List<JMeterTreeNode>> groupSamplersBySimilarity(List<JMeterTreeNode> samplers) {
        Map<String, List<JMeterTreeNode>> samplerGroups = new HashMap<>();
        
        log.info("Grouping {} samplers by similarity", samplers.size());
        
        // First, build a map of the full path for each sampler to maintain hierarchy context
        Map<JMeterTreeNode, String> samplerPaths = new HashMap<>();
        for (JMeterTreeNode samplerNode : samplers) {
            // Build the full path of the sampler (including all parent controllers)
            TreeNode parent = samplerNode.getParent();
            List<String> parentNames = new ArrayList<>();
            
            while (parent != null && parent instanceof JMeterTreeNode) {
                JMeterTreeNode jmeterParent = (JMeterTreeNode) parent;
                parentNames.add(0, jmeterParent.getName());
                parent = parent.getParent();
            }
            
            // Join the parent names to create a path
            String path = String.join(" > ", parentNames);
            samplerPaths.put(samplerNode, path);
            log.debug("Sampler: '{}' has path: '{}'", samplerNode.getName(), path);
        }
        
        // Group samplers by their parent node to maintain hierarchy
        Map<JMeterTreeNode, List<JMeterTreeNode>> parentToSamplersMap = new HashMap<>();
        for (JMeterTreeNode samplerNode : samplers) {
            JMeterTreeNode parentNode = (JMeterTreeNode) samplerNode.getParent();
            parentToSamplersMap.computeIfAbsent(parentNode, k -> new ArrayList<>()).add(samplerNode);
        }
        
        log.debug("Grouped samplers by {} different parent nodes", parentToSamplersMap.size());
        
        // Now, for each parent, group its samplers by name similarity
        for (Map.Entry<JMeterTreeNode, List<JMeterTreeNode>> parentEntry : parentToSamplersMap.entrySet()) {
            JMeterTreeNode parentNode = parentEntry.getKey();
            List<JMeterTreeNode> parentSamplers = parentEntry.getValue();
            
            log.debug("Processing {} samplers under parent: '{}'", parentSamplers.size(), parentNode.getName());
            
            // Sort samplers by their index in the parent to maintain order
            parentSamplers.sort(Comparator.comparingInt(parentNode::getIndex));
            
            // Group samplers under this parent by name similarity
            for (JMeterTreeNode samplerNode : parentSamplers) {
                Sampler sampler = (Sampler) samplerNode.getTestElement();
                
                // Create a key based on the sampler's name pattern
                String name = sampler.getName();
                String groupKey = simplifyName(name);
                
                // Include parent path in the group key to ensure unique groups per hierarchy
                String path = samplerPaths.get(samplerNode);
                String fullGroupKey = path + " > " + groupKey;
                
                log.debug("Sampler: '{}' under path '{}' simplified to group key: '{}'", 
                        name, path, fullGroupKey);
                
                // Add to the appropriate group
                samplerGroups.computeIfAbsent(fullGroupKey, k -> new ArrayList<>()).add(samplerNode);
            }
        }
        
        return samplerGroups;
    }

    /**
     * Simplifies a name by replacing numeric IDs and UUIDs with placeholders.
     *
     * @param name The name to simplify
     * @return The simplified name pattern
     */
    private String simplifyName(String name) {
        log.debug("Simplifying name: '{}'", name);
        
        // Replace numeric IDs with {id}
        String simplified = name.replaceAll("\\d+", "{id}");
        
        // Replace UUIDs with {uuid}
        simplified = simplified.replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "{uuid}");
        
        log.debug("Simplified name: '{}' to '{}'", name, simplified);
        return simplified;
    }

    /**
     * Creates Transaction Controllers and moves samplers into them while preserving the original order.
     *
     * @param threadGroupNode The Thread Group node
     * @param samplerGroups The map of sampler groups
     * @return The number of Transaction Controllers created
     */
    private int createTransactionControllersAndMoveSamplers(JMeterTreeNode threadGroupNode, Map<String, List<JMeterTreeNode>> samplerGroups) {
        int groupsCreated = 0;
        
        log.info("Creating Transaction Controllers for {} groups", samplerGroups.size());
        
        // Sort the groups by the position of their first sampler to maintain the order
        List<Map.Entry<String, List<JMeterTreeNode>>> sortedGroups = new ArrayList<>(samplerGroups.entrySet());
        sortedGroups.sort((e1, e2) -> {
            // Get the first sampler from each group
            JMeterTreeNode firstSampler1 = e1.getValue().isEmpty() ? null : e1.getValue().get(0);
            JMeterTreeNode firstSampler2 = e2.getValue().isEmpty() ? null : e2.getValue().get(0);
            
            if (firstSampler1 == null || firstSampler2 == null) {
                return 0; // Handle empty groups
            }
            
            // Compare based on parent hierarchy first
            JMeterTreeNode parent1 = (JMeterTreeNode) firstSampler1.getParent();
            JMeterTreeNode parent2 = (JMeterTreeNode) firstSampler2.getParent();
            
            if (parent1.equals(parent2)) {
                // If same parent, compare by index
                return Integer.compare(parent1.getIndex(firstSampler1), parent2.getIndex(firstSampler2));
            }
            
            // Different parents, preserve the hierarchy
            return 0;
        });
        
        // Track created Transaction Controllers to avoid duplicates
        Map<String, JMeterTreeNode> createdControllers = new HashMap<>();
        
        // Create Transaction Controllers for each group and insert them at the position of the first sampler in the group
        for (Map.Entry<String, List<JMeterTreeNode>> entry : sortedGroups) {
            String groupKey = entry.getKey();
            List<JMeterTreeNode> samplers = entry.getValue();
            
            if (samplers.isEmpty()) {
                log.info("Skipping empty group: '{}'", groupKey);
                continue;
            }
            
            log.info("Processing group '{}' with {} samplers", groupKey, samplers.size());
            
            // Find the position of the first sampler in this group
            JMeterTreeNode firstSampler = samplers.get(0);
            JMeterTreeNode parentNode = (JMeterTreeNode) firstSampler.getParent();
            int insertIndex = parentNode.getIndex(firstSampler);
            
            log.info("First sampler '{}' found at index {} under parent '{}'", 
                    firstSampler.getName(), insertIndex, parentNode.getName());
            
            // Create a descriptive name for the Transaction Controller
            // Include parent name in the controller name to ensure uniqueness
            String controllerName = "Transaction - " + (groupKey.contains(":") ? groupKey : parentNode.getName() + ": " + groupKey);
            log.info("Creating Transaction Controller with name: '{}'", controllerName);
            
            // Check if we've already created this controller (for this parent)
            JMeterTreeNode transactionNode = createdControllers.get(parentNode.getName() + "-" + controllerName);
            if (transactionNode == null) {
                // Create a Transaction Controller
                boolean success = JMeterElementManager.addElement("transactioncontroller", controllerName);
                if (!success) {
                    log.error("Failed to create Transaction Controller");
                    continue;
                }
                log.info("Successfully created Transaction Controller: '{}'", controllerName);
                
                // Get the newly created Transaction Controller node
                transactionNode = findNewlyCreatedTransactionController(threadGroupNode, controllerName);
                if (transactionNode == null) {
                    log.error("Could not find the created Transaction Controller: '{}'", controllerName);
                    continue;
                }
                log.info("Found Transaction Controller node: '{}'", transactionNode.getName());
                
                // Move the Transaction Controller to the position of the first sampler
                GuiPackage guiPackage = GuiPackage.getInstance();
                try {
                    // First, move the Transaction Controller to the parent of the first sampler
                    guiPackage.getTreeModel().removeNodeFromParent(transactionNode);
                    guiPackage.getTreeModel().insertNodeInto(transactionNode, parentNode, insertIndex);
                    log.info("Moved Transaction Controller to index {} under parent '{}'", 
                            insertIndex, parentNode.getName());
                    
                    // Store the created controller
                    createdControllers.put(parentNode.getName() + "-" + controllerName, transactionNode);
                } catch (Exception e) {
                    log.error("Failed to move Transaction Controller: {}", e.getMessage());
                    continue;
                }
            } else {
                log.info("Using existing Transaction Controller: '{}'", controllerName);
            }
            
            // Move samplers and their direct children to the Transaction Controller
            // Process samplers in order of their original position to maintain sequence
            List<JMeterTreeNode> orderedSamplers = new ArrayList<>(samplers);
            orderedSamplers.sort((s1, s2) -> {
                JMeterTreeNode parent1 = (JMeterTreeNode) s1.getParent();
                JMeterTreeNode parent2 = (JMeterTreeNode) s2.getParent();
                
                if (parent1.equals(parent2)) {
                    return Integer.compare(parent1.getIndex(s1), parent2.getIndex(s2));
                }
                return 0; // Different parents, preserve original order
            });
            
            for (JMeterTreeNode samplerNode : orderedSamplers) {
                log.info("Moving sampler: '{}' to Transaction Controller: '{}'", 
                        samplerNode.getName(), controllerName);
                
                // Store the sampler's original parent and index for reference
                JMeterTreeNode originalParent = (JMeterTreeNode) samplerNode.getParent();
                int originalIndex = originalParent.getIndex(samplerNode);
                
                // Get all direct children of the sampler (pre/post processors, etc.)
                List<JMeterTreeNode> samplerChildren = new ArrayList<>();
                for (int i = 0; i < samplerNode.getChildCount(); i++) {
                    TreeNode childNode = samplerNode.getChildAt(i);
                    if (childNode instanceof JMeterTreeNode) {
                        JMeterTreeNode jmeterChild = (JMeterTreeNode) childNode;
                        log.debug("Found child '{}' of sampler '{}'", jmeterChild.getName(), samplerNode.getName());
                        samplerChildren.add(jmeterChild);
                    }
                }
                
                // Move the sampler to the Transaction Controller
                // This preserves the hierarchy by using our enhanced moveNode method
                moveNode(samplerNode, transactionNode);
                
                // No need to manually move children as our enhanced moveNode method handles this
                // Just log the operation for clarity
                log.debug("Moved sampler from parent '{}' at index {} to Transaction Controller '{}' with {} children",
                        originalParent.getName(), originalIndex, transactionNode.getName(), samplerChildren.size());
            }
            
            groupsCreated++;
            log.info("Successfully created group {} with Transaction Controller: '{}'", 
                    groupsCreated, controllerName);
        }
        
        return groupsCreated;
    }
    
    /**
     * Finds a newly created Transaction Controller by name.
     *
     * @param parentNode The parent node to search in
     * @param controllerName The name of the controller to find
     * @return The Transaction Controller node, or null if not found
     */
    private JMeterTreeNode findNewlyCreatedTransactionController(JMeterTreeNode parentNode, String controllerName) {
        log.debug("Searching for Transaction Controller: '{}' in parent: '{}'", controllerName, parentNode.getName());
        log.debug("Parent node has {} children", parentNode.getChildCount());
        
        for (int i = 0; i < parentNode.getChildCount(); i++) {
            TreeNode child = parentNode.getChildAt(i);
            if (child instanceof JMeterTreeNode) {
                JMeterTreeNode jmeterChild = (JMeterTreeNode) child;
                log.debug("Examining child: '{}' ({})", jmeterChild.getName(), 
                        jmeterChild.getTestElement().getClass().getSimpleName());
                
                if (jmeterChild.getTestElement() instanceof TransactionController && 
                        jmeterChild.getName().equals(controllerName)) {
                    log.debug("Found Transaction Controller: '{}'", controllerName);
                    return jmeterChild;
                }
            }
        }
        return null;
    }
    
    /**
     * Moves a node to a new parent node while preserving its children.
     *
     * @param nodeToMove The node to move
     * @param newParent The new parent node
     */
    private void moveNode(JMeterTreeNode nodeToMove, JMeterTreeNode newParent) {
        try {
            log.debug("Moving node: '{}' to new parent: '{}'", nodeToMove.getName(), newParent.getName());
            
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage == null) {
                log.error("GuiPackage is null, cannot move node");
                return;
            }
            
            // Get the current parent of the node to move
            JMeterTreeNode oldParent = (JMeterTreeNode) nodeToMove.getParent();
            if (oldParent == null) {
                log.error("Cannot move node '{}' as it has no parent", nodeToMove.getName());
                return;
            }
            
            int oldIndex = oldParent.getIndex(nodeToMove);
            log.debug("Current parent of node: '{}' is '{}' at index {}", 
                    nodeToMove.getName(), oldParent.getName(), oldIndex);
            
            // First, collect all children of the node to move to preserve them
            List<JMeterTreeNode> children = new ArrayList<>();
            for (int i = 0; i < nodeToMove.getChildCount(); i++) {
                TreeNode child = nodeToMove.getChildAt(i);
                if (child instanceof JMeterTreeNode) {
                    children.add((JMeterTreeNode) child);
                }
            }
            log.debug("Node '{}' has {} children to preserve", nodeToMove.getName(), children.size());
            
            // Remove the node from its current parent
            guiPackage.getTreeModel().removeNodeFromParent(nodeToMove);
            log.debug("Removed node: '{}' from parent: '{}'", nodeToMove.getName(), oldParent.getName());
            
            // Add the node to the new parent
            guiPackage.getTreeModel().insertNodeInto(nodeToMove, newParent, newParent.getChildCount());
            log.debug("Added node: '{}' to new parent: '{}' at position {}", 
                    nodeToMove.getName(), newParent.getName(), newParent.getChildCount() - 1);
            
            // If this is a sampler being moved to a Transaction Controller, ensure children are preserved
            if (!children.isEmpty()) {
                log.debug("Preserving {} children of node '{}'", children.size(), nodeToMove.getName());
                
                // Reattach all children to the moved node in their original order
                for (JMeterTreeNode child : children) {
                    // The child might have been moved already if it was a direct child
                    if (child.getParent() != null) {
                        log.debug("Moving child '{}' to parent '{}'", child.getName(), nodeToMove.getName());
                        guiPackage.getTreeModel().removeNodeFromParent(child);
                        guiPackage.getTreeModel().insertNodeInto(child, nodeToMove, nodeToMove.getChildCount());
                    }
                }
            }
            
            // Refresh the tree
            guiPackage.getTreeModel().nodeStructureChanged(oldParent);
            guiPackage.getTreeModel().nodeStructureChanged(newParent);
            guiPackage.getTreeModel().nodeStructureChanged(nodeToMove);
            log.debug("Tree structure updated for all affected nodes");
        } catch (Exception e) {
            log.error("Error moving node: {}", e.getMessage(), e);
        }
    }
}
