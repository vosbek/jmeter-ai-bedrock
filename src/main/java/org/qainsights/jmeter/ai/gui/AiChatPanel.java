package org.qainsights.jmeter.ai.gui;

import org.qainsights.jmeter.ai.service.ClaudeService;
import org.qainsights.jmeter.ai.utils.Models;
import com.anthropic.models.ModelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiChatPanel extends JPanel {
    private JTextPane chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private List<String> conversationHistory;
    private ClaudeService claudeService;
    private JComboBox<ModelInfo> modelSelector;
    private static final Logger log = LoggerFactory.getLogger(AiChatPanel.class);
    
    // Pattern to match code blocks in markdown (```language code ```)
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```([\\w-]*)\\s*([\\s\\S]*?)```");
    
    // Map to store code snippets for copying
    private Map<String, String> codeSnippets = new HashMap<>();

    public AiChatPanel() {
        claudeService = new ClaudeService();
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(400, 600));

        // Create the top panel for model selection
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        // Initialize model selector with loading state
        modelSelector = new JComboBox<>();
        modelSelector.addItem(null); // Add empty item while loading
        modelSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                if (value == null) {
                    return super.getListCellRendererComponent(list, "Loading models...", index, isSelected, cellHasFocus);
                }
                ModelInfo model = (ModelInfo) value;
                return super.getListCellRendererComponent(list, model.id(), index, isSelected, cellHasFocus);
            }
        });
        
        // Load models in background
        new SwingWorker<List<ModelInfo>, Void>() {
            @Override
            protected List<ModelInfo> doInBackground() {
                return Models.getModels(claudeService.getClient()).data();
            }

            @Override
            protected void done() {
                try {
                    List<ModelInfo> models = get();
                    modelSelector.removeAllItems();
                    for (ModelInfo model : models) {
                        modelSelector.addItem(model);
                    }
                    // Select the default model
                    String defaultModelId = claudeService.getCurrentModel();
                    log.info("Setting initial model selection to: {}", defaultModelId);
                    for (int i = 0; i < modelSelector.getItemCount(); i++) {
                        ModelInfo model = modelSelector.getItemAt(i);
                        if (model != null && model.id().equals(defaultModelId)) {
                            modelSelector.setSelectedIndex(i);
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to load models", e);
                }
            }
        }.execute();

        // Add a listener to log model changes
        modelSelector.addActionListener(e -> {
            ModelInfo selectedModel = (ModelInfo) modelSelector.getSelectedItem();
            if (selectedModel != null) {
                log.info("Model selected from dropdown: {}", selectedModel.id());
            }
        });

        JLabel modelLabel = new JLabel("Model: ");
        topPanel.add(modelLabel);
        topPanel.add(modelSelector);
        add(topPanel, BorderLayout.NORTH);

        // Initialize chat area
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        add(scrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        sendButton = new JButton("Send");

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        add(inputPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        conversationHistory = new ArrayList<>();
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            appendToChat("You: " + message, Color.BLUE, false);
            inputField.setText("");

            conversationHistory.add(message);

            // Get the currently selected model from the dropdown
            ModelInfo selectedModel = (ModelInfo) modelSelector.getSelectedItem();
            if (selectedModel != null) {
                // Set the current model ID before generating the response
                log.info("Using model from dropdown for message: {}", selectedModel.id());
                claudeService.setModel(selectedModel.id());
            } else {
                log.warn("No model selected in dropdown, using default model: {}", claudeService.getCurrentModel());
            }

            // Call Claude API with full conversation history
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    return claudeService.generateResponse(new ArrayList<>(conversationHistory));
                }

                @Override
                protected void done() {
                    try {
                        String response = get();
                        appendToChat("Claude: " + response, Color.BLACK, true);
                        conversationHistory.add(response);
                    } catch (Exception e) {
                        log.error("Error getting response", e);
                        appendToChat("Error: " + e.getMessage(), Color.RED, false);
                    }
                }
            }.execute();
        }
    }

    private void appendToChat(String message, Color color, boolean parseMarkdown) {
        StyledDocument doc = chatArea.getStyledDocument();
        
        try {
            // Add the sender part with the specified color
            SimpleAttributeSet senderStyle = new SimpleAttributeSet();
            StyleConstants.setForeground(senderStyle, color);
            StyleConstants.setBold(senderStyle, true);
            
            // For Claude messages, only style the "Claude: " part
            if (message.startsWith("Claude: ") && parseMarkdown) {
                doc.insertString(doc.getLength(), "Claude: ", senderStyle);
                
                // Process the rest of the message for markdown
                String claudeMessage = message.substring("Claude: ".length());
                processMarkdownMessage(doc, claudeMessage);
            } else {
                // For user messages or non-markdown messages
                doc.insertString(doc.getLength(), message + "\n\n", senderStyle);
            }
            
            // Scroll to the end
            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            log.error("Error appending to chat", e);
        }
    }
    
    private void processMarkdownMessage(StyledDocument doc, String message) throws BadLocationException {
        // Find all code blocks in the message
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(message);
        
        int lastEnd = 0;
        int blockCount = 0;
        
        // Process each code block
        while (matcher.find()) {
            blockCount++;
            
            // Add the text before the code block
            String textBefore = message.substring(lastEnd, matcher.start());
            if (!textBefore.isEmpty()) {
                // Process basic markdown in the text before the code block
                processBasicMarkdown(doc, textBefore);
            }
            
            // Get the code block content
            String language = matcher.group(1).trim();
            String codeContent = matcher.group(2).trim();
            String codeId = "code_" + System.currentTimeMillis() + "_" + blockCount;
            
            // Store the code for copying
            codeSnippets.put(codeId, codeContent);
            
            // Add code block header with language and copy button
            SimpleAttributeSet codeHeaderStyle = new SimpleAttributeSet();
            StyleConstants.setBackground(codeHeaderStyle, new Color(240, 240, 240));
            StyleConstants.setForeground(codeHeaderStyle, Color.GRAY);
            StyleConstants.setFontFamily(codeHeaderStyle, "Monospaced");
            
            // Insert a newline before the code block
            doc.insertString(doc.getLength(), "\n", null);
            
            // Create a panel for the code block header
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(new Color(240, 240, 240));
            headerPanel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            
            JLabel languageLabel = new JLabel(language.isEmpty() ? "code" : language);
            languageLabel.setForeground(Color.GRAY);
            
            JButton copyButton = new JButton("Copy");
            copyButton.setFocusPainted(false);
            copyButton.setBackground(new Color(76, 175, 80));
            copyButton.setForeground(Color.BLACK); 
            copyButton.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            
            final String snippetId = codeId;
            copyButton.addActionListener(e -> {
                String codeToCopy = codeSnippets.get(snippetId);
                if (codeToCopy != null) {
                    StringSelection selection = new StringSelection(codeToCopy);
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(selection, selection);
                    
                    // Change button text temporarily
                    copyButton.setText("Copied!");
                    copyButton.setBackground(new Color(33, 150, 243));
                    copyButton.setForeground(Color.BLACK); 
                    
                    // Reset button text after delay
                    Timer timer = new Timer(2000, evt -> {
                        copyButton.setText("Copy");
                        copyButton.setBackground(new Color(76, 175, 80));
                        copyButton.setForeground(Color.BLACK); 
                    });
                    timer.setRepeats(false);
                    timer.start();
                }
            });
            
            headerPanel.add(languageLabel, BorderLayout.WEST);
            headerPanel.add(copyButton, BorderLayout.EAST);
            
            // Insert the header panel as a component
            int headerPos = doc.getLength();
            doc.insertString(headerPos, " ", null); // Placeholder for component
            
            // Add the component to the document
            StyleConstants.setComponent(codeHeaderStyle, headerPanel);
            doc.setCharacterAttributes(headerPos, 1, codeHeaderStyle, false);
            
            // Insert the code content with code styling
            SimpleAttributeSet codeStyle = new SimpleAttributeSet();
            StyleConstants.setBackground(codeStyle, new Color(245, 245, 245));
            StyleConstants.setFontFamily(codeStyle, "Monospaced");
            StyleConstants.setFontSize(codeStyle, 12);
            
            // Add the code content in a bordered area
            doc.insertString(doc.getLength(), "\n" + codeContent + "\n", codeStyle);
            
            // Insert a newline after the code block
            doc.insertString(doc.getLength(), "\n", null);
            
            // Update lastEnd for the next iteration
            lastEnd = matcher.end();
        }
        
        // Add any remaining text after the last code block
        if (lastEnd < message.length()) {
            String textAfter = message.substring(lastEnd);
            if (!textAfter.isEmpty()) {
                processBasicMarkdown(doc, textAfter);
            }
        }
        
        // Add extra newline at the end
        doc.insertString(doc.getLength(), "\n", null);
    }
    
    private void processBasicMarkdown(StyledDocument doc, String text) throws BadLocationException {
        // This is a simple implementation of basic markdown
        // For a more complete solution, you might want to use a proper markdown parser
        
        // Process the text line by line
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Check for bold text (**text**)
            Matcher boldMatcher = Pattern.compile("\\*\\*(.*?)\\*\\*").matcher(line);
            StringBuffer sb = new StringBuffer();
            
            while (boldMatcher.find()) {
                String boldText = boldMatcher.group(1);
                boldMatcher.appendReplacement(sb, "");
                
                // Add the text before the bold part
                doc.insertString(doc.getLength(), sb.toString(), null);
                sb.setLength(0);
                
                // Add the bold text
                SimpleAttributeSet boldStyle = new SimpleAttributeSet();
                StyleConstants.setBold(boldStyle, true);
                doc.insertString(doc.getLength(), boldText, boldStyle);
            }
            
            boldMatcher.appendTail(sb);
            
            // Add any remaining text
            if (sb.length() > 0) {
                doc.insertString(doc.getLength(), sb.toString(), null);
            }
            
            // Add a newline unless it's the last line
            if (i < lines.length - 1) {
                doc.insertString(doc.getLength(), "\n", null);
            }
        }
    }
}