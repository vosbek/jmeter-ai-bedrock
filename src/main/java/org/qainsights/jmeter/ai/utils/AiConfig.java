package org.qainsights.jmeter.ai.utils;

import org.apache.jmeter.util.JMeterUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AiConfig {
    public static String getProperty(String key, String defaultValue) {
        return JMeterUtils.getPropDefault(key, defaultValue);
    }
}
