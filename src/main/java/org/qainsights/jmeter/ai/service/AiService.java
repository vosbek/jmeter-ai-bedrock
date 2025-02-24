package org.qainsights.jmeter.ai.service;

import java.util.List;

public interface AiService {
    String generateResponse(List<String> conversation);
    String getName();
}