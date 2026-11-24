package com.spendlens.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.spendlens.audit.AuditAgent;
import com.spendlens.audit.ClaudeAuditAgent;
import com.spendlens.config.AppProperties;
import com.spendlens.insights.InsightsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Claude beans exist only when an API key is configured. Services inject {@code Optional<...>}, so without
 * a key the app still works: categorization falls back to rules, Ask and Audit answer 503.
 */
@Configuration
@ConditionalOnExpression("'${app.ai.api-key:}' != ''")
public class AiConfig {

    @Bean
    AnthropicClient anthropicClient(AppProperties props) {
        return AnthropicOkHttpClient.builder().apiKey(props.ai().apiKey()).build();
    }

    @Bean
    ClaudeGateway claudeGateway(AnthropicClient client, AppProperties props) {
        return new AnthropicClaudeGateway(client, props.ai().model());
    }

    @Bean
    AuditAgent auditAgent(AnthropicClient client, AppProperties props, InsightsService insights) {
        return new ClaudeAuditAgent(client, props.ai().model(), insights);
    }
}
