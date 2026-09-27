package com.campus.service;

import com.campus.dto.AiRecommendQueryDTO;
import com.campus.vo.AiRecommendVO;

public interface AiRecommendService {

    /**
     * AI 智能点餐推荐。
     *
     * 三步流水线：① LLM 提取结构化参数（预算/人数/辣度/品类）→ ② 按参数查库过滤候选 → ③ LLM 生成推荐方案（服务端反幻觉校验）。
     * 大模型不可用/超时/输出非法时自动降级为规则推荐，接口始终返回可用结果。
     *
     * @param aiRecommendQueryDTO 用户自然语言需求
     * @return
     */
    AiRecommendVO recommend(AiRecommendQueryDTO aiRecommendQueryDTO);
}
