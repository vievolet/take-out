package com.campus.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 点餐推荐配置（DeepSeek，OpenAI 兼容协议）。
 *
 * api-key 走环境变量，未配置时推荐接口自动降级为规则推荐，保证离线可用。
 */
@Component
@ConfigurationProperties(prefix = "campus.ai")
@Data
public class AiProperties {

    /** 大模型服务地址（OpenAI 兼容，可换成任意兼容厂商） */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** API Key（环境变量 CAMPUS_AI_API_KEY 注入，为空则走降级） */
    private String apiKey;

    /** 请求超时（毫秒），大模型推理较慢，比通用接口超时更大 */
    private int timeoutMs = 30000;
}
