package com.campus.service.impl;

import com.campus.entity.Dish;
import com.campus.entity.Setmeal;
import com.campus.mapper.AiRecommendLogMapper;
import com.campus.mapper.CategoryMapper;
import com.campus.mapper.DishMapper;
import com.campus.mapper.SetmealMapper;
import com.campus.vo.AiRecommendVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * AI 推荐规则降级逻辑单测（纯逻辑，不依赖大模型）
 */
@ExtendWith(MockitoExtension.class)
class AiRecommendServiceImplTest {

    @Mock
    private DishMapper dishMapper;
    @Mock
    private SetmealMapper setmealMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private AiRecommendLogMapper aiRecommendLogMapper;
    @Mock
    private com.campus.ai.AiChatClient aiChatClient;

    @InjectMocks
    private AiRecommendServiceImpl aiRecommendService;

    /**
     * 降级：预算 50 双人不辣，候选只有 2 元的米饭和 98 元的鲈鱼（超预算被 DB 过滤），
     * 套餐为空 → 贪心应选中米饭，总价 2
     */
    @Test
    void buildFallbackPlan_shouldRespectBudget() {
        Dish rice = Dish.builder().id(49L).name("米饭").price(new BigDecimal("2.00"))
                .spicyLevel(0).image("img").build();
        when(dishMapper.listForRecommend(eq(new BigDecimal("50")), eq(0), isNull()))
                .thenReturn(Arrays.asList(rice));
        when(setmealMapper.listForRecommend(any(), any(), any())).thenReturn(Collections.emptyList());

        AiRecommendServiceImpl.RecommendParams params = new AiRecommendServiceImpl.RecommendParams();
        params.budget = new BigDecimal("50");
        params.peopleCount = 2;
        params.maxSpicy = 0;

        AiRecommendVO vo = aiRecommendService.buildFallbackPlan("推荐50块以内不辣的双人餐", params);

        assertEquals(1, vo.getItems().size());
        assertEquals(49L, vo.getItems().get(0).getDishId());
        assertEquals(0, new BigDecimal("2.00").compareTo(vo.getTotalPrice()));
    }

    /**
     * 降级：双人场景优先选套餐，剩余预算按价格升序补菜品
     */
    @Test
    void buildFallbackPlan_shouldPreferSetmealForMultiplePeople() {
        Dish rice = Dish.builder().id(49L).name("米饭").price(new BigDecimal("2.00"))
                .spicyLevel(0).image("img").build();
        Setmeal meal = Setmeal.builder().id(1L).name("双人套餐").price(new BigDecimal("40.00"))
                .spicyLevel(0).image("img").build();
        when(dishMapper.listForRecommend(eq(new BigDecimal("50")), eq(0), isNull()))
                .thenReturn(Collections.singletonList(rice));
        when(setmealMapper.listForRecommend(any(), any(), any()))
                .thenReturn(Collections.singletonList(meal));

        AiRecommendServiceImpl.RecommendParams params = new AiRecommendServiceImpl.RecommendParams();
        params.budget = new BigDecimal("50");
        params.peopleCount = 2;
        params.maxSpicy = 0;

        AiRecommendVO vo = aiRecommendService.buildFallbackPlan("推荐50块以内不辣的双人餐", params);

        // 套餐 40 + 米饭 2 = 42，未超预算上限 45
        assertEquals(2, vo.getItems().size());
        assertEquals(0, new BigDecimal("42.00").compareTo(vo.getTotalPrice()));
        assertTrue(vo.getItems().stream().anyMatch(i -> i.getSetmealId() != null));
    }

    /**
     * 降级：没有任何候选时返回空列表但接口可用（fallback 不抛异常）
     */
    @Test
    void buildFallbackPlan_shouldNotFailOnEmptyCandidates() {
        when(dishMapper.listForRecommend(any(), any(), any())).thenReturn(Collections.emptyList());
        when(setmealMapper.listForRecommend(any(), any(), any())).thenReturn(Collections.emptyList());

        AiRecommendServiceImpl.RecommendParams params = new AiRecommendServiceImpl.RecommendParams();
        params.peopleCount = 1;

        AiRecommendVO vo = aiRecommendService.buildFallbackPlan("随便推荐点", params);

        assertTrue(vo.getItems().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getTotalPrice()));
    }
}
