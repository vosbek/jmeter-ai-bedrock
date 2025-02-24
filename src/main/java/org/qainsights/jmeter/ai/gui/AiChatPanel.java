package org.qainsights.jmeter.ai.gui;

import org.qainsights.jmeter.ai.service.ClaudeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AiChatPanel extends JPanel {
    private JTextPane chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private List<String> conversationHistory;
    private ClaudeService claudeService;
    private static final Logger log = LoggerFactory.getLogger(AiChatPanel.class);


    public AiChatPanel() {
        claudeService = new ClaudeService();
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(400, 600));

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
            appendToChat("You: " + message, Color.BLUE);
            inputField.setText("");

            conversationHistory.add(message);

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
                        appendToChat("Claude: " + response, Color.BLACK);
                        conversationHistory.add(response);
                    } catch (Exception e) {
                        appendToChat("Error: " + e.getMessage(), Color.RED);
                    }
                }
            }.execute();
        }
    }

    private void appendToChat(String message, Color color) {
        StyledDocument doc = chatArea.getStyledDocument();
        SimpleAttributeSet keyWord = new SimpleAttributeSet();
        StyleConstants.setForeground(keyWord, color);
        StyleConstants.setBold(keyWord, true);
        try {
            doc.insertString(doc.getLength(), message + "\n", keyWord);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
        chatArea.setCaretPosition(doc.getLength());
    }
}