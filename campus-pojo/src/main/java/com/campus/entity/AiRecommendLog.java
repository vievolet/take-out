package com.campus.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 推荐日志：记录每次推荐的原始请求、LLM 提取的参数、返回结果与降级标记。
 * 用途：推荐链路可追溯 + 后续做用户画像/冷启动推荐的数据底座。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    // 用户id
    private Long userId;

    // 用户原始自然语言请求
    private String query;

    // LLM 提取的结构化参数 JSON
    private String paramsJson;

    // 推荐结果 JSON
    private String resultJson;

    // 是否命中降级 0否 1是
    private Integer fallback;

    // 创建时间
    private LocalDateTime createTime;
}
