package com.campus.controller.user;

import com.campus.dto.AiRecommendQueryDTO;
import com.campus.result.Result;
import com.campus.service.AiRecommendService;
import com.campus.vo.AiRecommendVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 智能点餐推荐。
 *
 * 返回结果中的 items 自带 dishId/setmealId，前端可逐项调用现有购物车接口
 * POST /user/shoppingCart/add 完成一键加购（推荐接口本身不操作购物车，职责单一）。
 */
@RestController("userAiRecommendController")
@RequestMapping("/user/ai")
@Api(tags = "AI智能点餐推荐接口")
@Slf4j
public class AiRecommendController {

    @Autowired
    private AiRecommendService aiRecommendService;

    /**
     * AI 智能点餐推荐
     * @param aiRecommendQueryDTO 用户自然语言需求，如 {"query":"推荐50块以内不辣的双人餐"}
     * @return 推荐组合（大模型不可用时 fallback=true 返回规则推荐）
     */
    @PostMapping("/recommend")
    @ApiOperation("AI智能点餐推荐")
    public Result<AiRecommendVO> recommend(@RequestBody AiRecommendQueryDTO aiRecommendQueryDTO){
        log.info("AI 点餐推荐请求：{}", aiRecommendQueryDTO.getQuery());
        AiRecommendVO aiRecommendVO = aiRecommendService.recommend(aiRecommendQueryDTO);
        return Result.success(aiRecommendVO);
    }
}
