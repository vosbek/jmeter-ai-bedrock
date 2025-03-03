# 🚀 Feather Wand - JMeter Agent

This plugin provides a simple way to chat with AI in JMeter. Feather Wand serves as your intelligent assistant for JMeter test plan development, optimization, and troubleshooting.

> 🪄 **About the name**: The name "Feather Wand" was suggested by my children who were inspired by an episode of the animated show Bluey. In the episode, a simple feather becomes a magical wand that transforms the ordinary into something special (heavy) - much like how this plugin aims to transform your JMeter experience with a touch of AI magic!

## ✨ Features

- Chat with AI directly within JMeter
- Get suggestions for JMeter elements based on your needs
- Ask questions about JMeter functionality and best practices
- Use `@this` command to get detailed information about the currently selected element
- Customize AI behavior through configuration properties

## ⚠️ Disclaimer and Best Practices

While the Feather Wand plugin aims to provide helpful assistance, please keep the following in mind:

- **AI Limitations**: The AI can make mistakes or provide incorrect information. Always verify critical suggestions before implementing them in production tests.
- **Backup Your Test Plans**: Always backup your test plans before making significant changes, especially when implementing AI suggestions.
- **Test Verification**: After making changes based on AI recommendations, thoroughly verify your test plan functionality in a controlled environment before running it against production systems.
- **Performance Impact**: Some AI-suggested configurations may impact test performance. Monitor resource usage when implementing new configurations.
- **Security Considerations**: Do not share sensitive information (credentials, proprietary code, etc.) in your conversations with the AI.
- **API Costs**: Be aware that using the Claude API incurs costs based on token usage. The plugin is designed to minimize token usage, but extensive use will affect your Anthropic account billing.

This plugin is provided as a tool to assist JMeter users, but the ultimate responsibility for test plan design, implementation, and execution remains with the user.

## ⚙️ Configuration

The Feather Wand plugin can be configured through JMeter properties. Copy the `jmeter-ai-sample.properties` file content to your `jmeter.properties` or `user.properties` file and modify the properties as needed.

### 🔧 Available Configuration Options

| Property | Description | Default Value |
|----------|-------------|---------------|
| `anthropic.api.key` | Your Claude API key | Required |
| `claude.default.model` | Default Claude model to use | claude-3-sonnet-20240229 |
| `claude.temperature` | Temperature setting (0.0-1.0) | 0.7 |
| `claude.max.tokens` | Maximum tokens for AI responses | 1024 |
| `claude.max.history.size` | Maximum conversation history size | 10 |
| `claude.system.prompt` | System prompt that guides Claude's responses | See sample properties file |
| `anthropic.log.level` | Logging level for Anthropic API requests ("info" or "debug") | Empty (disabled) |

### 💬 Customizing the System Prompt

The system prompt defines how Claude responds to your queries. You can customize this in the properties file to focus on specific aspects of JMeter or add your own guidelines. The default prompt is designed to provide helpful, JMeter-specific responses.

## 🔍 Special Commands

### 🪄 @this Command

Use the `@this` command in your message to get detailed information about the currently selected element in your test plan. For example:

- "Tell me about @this element"
- "How can I optimize @this?"
- "What are the best practices for @this?"

Feather Wand will analyze the selected element and provide tailored information and advice.
