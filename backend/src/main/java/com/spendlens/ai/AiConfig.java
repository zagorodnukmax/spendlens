package com.spendlens.ai;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.spendlens.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * Created only when an API key is configured. Services inject {@code Optional<ClaudeGateway>},
     * so without a key the app still works and falls back to rule-based categorization.
     */
    @Bean
    @ConditionalOnExpression("'${app.ai.api-key:}' != ''")
    ClaudeGateway claudeGateway(AppProperties props) {
        var client = AnthropicOkHttpClient.builder().apiKey(props.ai().apiKey()).build();
        return new AnthropicClaudeGateway(client, props.ai().model());
    }
}
