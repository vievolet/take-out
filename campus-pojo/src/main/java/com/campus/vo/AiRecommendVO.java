package com.campus.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendVO implements Serializable {

    // 推荐组合明细
    private List<AiRecommendItemVO> items;

    // 组合总价（服务端按数据库价格重算，不信任大模型输出）
    private BigDecimal totalPrice;

    // 整体推荐说明（大模型生成）
    private String summary;

    // 是否命中规则降级（true = 大模型不可用时返回的离线推荐）
    private boolean fallback;
}
