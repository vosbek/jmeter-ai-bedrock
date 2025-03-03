# 🚀 JMeter Plugin: Chat with AI

This plugin provides a simple way to chat with AI in JMeter.

## Features

- Chat with AI directly within JMeter
- Get suggestions for JMeter elements based on your needs
- Ask questions about JMeter functionality and best practices
- Use `@this` command to get detailed information about the currently selected element
- Customize AI behavior through configuration properties

## Configuration

The JMeter AI plugin can be configured through JMeter properties. Copy the `jmeter-ai-sample.properties` file to your JMeter's `bin` directory and rename it to match your JMeter properties file (usually `jmeter.properties` or `user.properties`).

### Available Configuration Options

| Property | Description | Default Value |
|----------|-------------|---------------|
| `anthropic.api.key` | Your Claude API key | Required |
| `claude.default.model` | Default Claude model to use | claude-3-sonnet-20240229 |
| `claude.temperature` | Temperature setting (0.0-1.0) | 0.7 |
| `claude.max.history.size` | Maximum conversation history size | 10 |
| `claude.system.prompt` | System prompt that guides Claude's responses | See sample properties file |
| `anthropic.log.level` | Logging level for Anthropic API requests ("info" or "debug") | Empty (disabled) |

### Customizing the System Prompt

The system prompt defines how Claude responds to your queries. You can customize this in the properties file to focus on specific aspects of JMeter or add your own guidelines. The default prompt is designed to provide helpful, JMeter-specific responses.

## Special Commands

### @this Command

Use the `@this` command in your message to get detailed information about the currently selected element in your test plan. For example:

- "Tell me about @this element"
- "How can I optimize @this?"
- "What are the best practices for @this?"

The AI will analyze the selected element and provide tailored information and advice.
