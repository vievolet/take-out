package com.campus.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiRecommendQueryDTO implements Serializable {

    // 用户自然语言点餐需求，如："推荐50块以内不辣的双人餐"
    private String query;
}
