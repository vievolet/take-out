package com.campus.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.campus.properties.AiProperties;
import com.campus.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * DeepSeek 大模型客户端（OpenAI 兼容协议）。
 *
 * 通过 campus.ai.base-url/model/api-key 配置化，换成任意 OpenAI 兼容厂商（通义千问/豆包等）无需改代码。
 * api-key 未配置时 chat() 直接抛异常，由上层推荐服务捕获并降级为规则推荐。
 */
@Component
@Slf4j
public class DeepSeekAiChatClient implements AiChatClient {

    @Autowired
    private AiProperties aiProperties;

    @Override
    public String chat(String systemPrompt, String userPrompt) throws Exception {
        if (aiProperties.getApiKey() == null || aiProperties.getApiKey().isEmpty()) {
            throw new IllegalStateException("campus.ai.api-key 未配置，无法调用大模型");
        }

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", aiProperties.getModel());
        // 低温度让输出更稳定、更贴近 JSON 约束
        requestBody.put("temperature", 0.2);
        // 要求模型输出 JSON 对象
        JSONObject responseFormat = new JSONObject();
        responseFormat.put("type", "json_object");
        requestBody.put("response_format", responseFormat);

        JSONArray messages = new JSONArray();
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        messages.add(systemMessage);
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);
        messages.add(userMessage);
        requestBody.put("messages", messages);

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + aiProperties.getApiKey());
        headers.put("Content-Type", "application/json");

        String response = HttpClientUtil.doPostJson(
                aiProperties.getBaseUrl() + "/chat/completions",
                requestBody.toJSONString(),
                headers,
                aiProperties.getTimeoutMs());

        JSONObject responseJson = JSON.parseObject(response);
        JSONArray choices = responseJson.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("大模型响应异常：" + response);
        }
        String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
        log.info("大模型调用成功：model={}, 回复长度={}", aiProperties.getModel(), content == null ? 0 : content.length());
        return content;
    }
}
