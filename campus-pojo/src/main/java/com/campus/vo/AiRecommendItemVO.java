package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendItemVO implements Serializable {

    // 菜品id（与 setmealId 二选一，前端可直接用于加入购物车）
    private Long dishId;

    // 套餐id
    private Long setmealId;

    // 名称
    private String name;

    // 单价（数据库价格，服务端校验后的准确值）
    private BigDecimal price;

    // 数量
    private Integer quantity;

    // 图片
    private String image;

    // 辣度 0不辣 1微辣 2中辣 3特辣
    private Integer spicyLevel;

    // 推荐理由（大模型生成，降级模式为固定文案）
    private String reason;
}
