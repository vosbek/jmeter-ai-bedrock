package org.qainsights.jmeter.ai.gui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.qainsights.jmeter.ai.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Adds a right-click context menu to JSR223 script areas.
 */
public class JSR223ContextMenu {
    private static final Logger log = LoggerFactory.getLogger(JSR223ContextMenu.class);
    private static boolean initialized = false;
    private static AiService sharedAiService;
    private final AiService aiService;

    public JSR223ContextMenu(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * Initialize the JSR223 context menu functionality.
     * This should be called when JMeter starts.
     */
    public static synchronized void initialize(AiService aiService) {
        if (initialized) {
            return;
        }

        // Store the AI service for later use
        sharedAiService = aiService;

        // Start a background thread to avoid slowing down JMeter startup
        new Thread(() -> {
            try {
                // Wait a bit for JMeter to fully initialize
                Thread.sleep(2000);
                setupContextMenus(aiService);
                initialized = true;
                log.info("JSR223 context menus initialized");
            } catch (Exception e) {
                log.error("Failed to initialize JSR223 context menus", e);
            }
        }).start();
    }

    /**
     * Setup context menus for all JSR223 components.
     * This will be called periodically to catch newly created components.
     */
    private static void setupContextMenus(AiService aiService) {
        // Find all JSR223 script editors and add context menus
        RSyntaxTextArea scriptEditor = CodeCommandHandler.findJSR223ScriptEditor();
        if (scriptEditor != null) {
            addContextMenu(scriptEditor, aiService);
        }

        // We're removing the timer-based polling because it can interfere with typing
        // by periodically resetting the cursor position.
        // Instead, we'll rely on the initialization during plugin startup to add the
        // context menu.

        // If you need to ensure that newly created components get context menus,
        // consider integrating with JMeter's component creation lifecycle rather than
        // polling.
    }

    /**
     * Adds a context menu to the specified RSyntaxTextArea.
     * 
     * @param textArea  The text area to add the context menu to
     * @param aiService The AI service to use for refactoring
     */
    private static void addContextMenu(RSyntaxTextArea textArea, AiService aiService) {
        // Check if the text area already has a context menu
        if (textArea.getClientProperty("contextMenuAdded") != null) {
            return;
        }

        JPopupMenu popupMenu = new JPopupMenu();

        // Add menu items
        JMenuItem cutItem = new JMenuItem("Cut");
        cutItem.addActionListener(e -> textArea.cut());
        popupMenu.add(cutItem);

        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.addActionListener(e -> textArea.copy());
        popupMenu.add(copyItem);

        JMenuItem pasteItem = new JMenuItem("Paste");
        pasteItem.addActionListener(e -> textArea.paste());
        popupMenu.add(pasteItem);

        popupMenu.addSeparator();

        JMenuItem selectAllItem = new JMenuItem("Select All");
        selectAllItem.addActionListener(e -> textArea.selectAll());
        popupMenu.add(selectAllItem);

        popupMenu.addSeparator();

        // Add AI help menu
        JMenuItem aiHelpItem = new JMenuItem("Refactor Code");
        final String REFACTOR_PROMPT = "Please refactor this code by following the best practices to improve its structure, readability, and maintainability while preserving all functionality. Focus on:"
                + "Proper code organization and separation of concerns"
                + "Meaningful naming conventions"
                + "Reducing code duplication"
                + "Improving error handling"
                + "Provide the refactored code in a single complete code block with explanations of key changes."
                + "Give me only the code, no other text or comments."
                + "Do not include backticks in the code block.";

        aiHelpItem.addActionListener(e -> {
            String selectedText = textArea.getSelectedText();
            if (selectedText != null && !selectedText.isEmpty()) {
                String prompt = REFACTOR_PROMPT + "\n\n" + selectedText;
                String refactoredCode = aiService.generateResponse(List.of(prompt));
                textArea.setText(refactoredCode);
            } else {
                JOptionPane.showMessageDialog(
                        textArea,
                        "Please select some code first",
                        "Feather Wand Help",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
        popupMenu.add(aiHelpItem);

        // Add format code menu item
        JMenuItem formatCodeItem = new JMenuItem("Format Code");
        formatCodeItem.addActionListener(e -> {
            // This is a placeholder - you'd implement or connect to a code formatter
            String code = textArea.getText();
            if (code != null && !code.trim().isEmpty()) {
                try {
                    // Simple indentation formatting
                    code = formatGroovyCode(code);
                    textArea.setText(code);
                } catch (Exception ex) {
                    log.error("Error formatting code", ex);
                    JOptionPane.showMessageDialog(
                            textArea,
                            "Error formatting code: " + ex.getMessage(),
                            "Format Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(
                        textArea,
                        "No code to format",
                        "Format Code",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
        popupMenu.add(formatCodeItem);

        // Add mouse listener to show the popup menu
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showMenu(e);
                }
            }

            private void showMenu(MouseEvent e) {
                // Update menu items based on selection state
                boolean hasSelection = textArea.getSelectedText() != null && !textArea.getSelectedText().isEmpty();
                cutItem.setEnabled(hasSelection);
                copyItem.setEnabled(hasSelection);
                aiHelpItem.setEnabled(hasSelection);

                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        // Mark the text area as having a context menu
        textArea.putClientProperty("contextMenuAdded", Boolean.TRUE);
        log.debug("Added context menu to JSR223 script editor");
    }

    /**
     * Very simple code formatter for Groovy scripts
     * This is a basic implementation and could be improved
     */
    private static String formatGroovyCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return code;
        }

        StringBuilder formatted = new StringBuilder();
        String[] lines = code.split("\n");
        int indentLevel = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            // Adjust indent level based on closing braces at the start of the line
            if (trimmed.startsWith("}") || trimmed.startsWith(")")) {
                indentLevel = Math.max(0, indentLevel - 1);
            }

            // Add the appropriate indentation
            if (!trimmed.isEmpty()) {
                for (int i = 0; i < indentLevel; i++) {
                    formatted.append("    "); // 4 spaces per indent level
                }
                formatted.append(trimmed).append("\n");
            } else {
                formatted.append("\n"); // Preserve empty lines
            }

            // Increase indent level for lines ending with opening braces
            if (trimmed.endsWith("{") || trimmed.endsWith("(")) {
                indentLevel++;
            }

            // Decrease indent level for lines ending with closing braces
            if (trimmed.endsWith("}") || trimmed.endsWith(")")) {
                indentLevel = Math.max(0, indentLevel - 1);
            }
        }

        return formatted.toString();
    }

    /**
     * Public method to add a context menu to the current JSR223 editor.
     * This can be called when a user interacts with a JSR223 component to ensure
     * it has a context menu without using timers that might interfere with typing.
     */
    public static void addContextMenuToCurrentEditor() {
        if (!initialized || sharedAiService == null) {
            log.warn("Cannot add context menu - not initialized or no AI service available");
            return;
        }

        RSyntaxTextArea scriptEditor = CodeCommandHandler.findJSR223ScriptEditor();
        if (scriptEditor != null) {
            addContextMenu(scriptEditor, sharedAiService);
        }
    }
}