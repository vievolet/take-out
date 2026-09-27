package com.campus.ai;

/**
 * 大模型对话客户端抽象。
 *
 * 默认实现 DeepSeekAiChatClient（OpenAI 兼容协议），
 * 抽象成接口便于测试时用 Mockito 打桩，也让推荐管线的规则降级路径可单测。
 */
public interface AiChatClient {

    /**
     * 发起一次对话
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 模型回复文本
     */
    String chat(String systemPrompt, String userPrompt) throws Exception;
}
