package com.campus.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.campus.ai.AiChatClient;
import com.campus.context.BaseContext;
import com.campus.dto.AiRecommendQueryDTO;
import com.campus.entity.AiRecommendLog;
import com.campus.entity.Dish;
import com.campus.entity.Setmeal;
import com.campus.mapper.AiRecommendLogMapper;
import com.campus.mapper.CategoryMapper;
import com.campus.mapper.DishMapper;
import com.campus.mapper.SetmealMapper;
import com.campus.service.AiRecommendService;
import com.campus.vo.AiRecommendItemVO;
import com.campus.vo.AiRecommendVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 智能点餐推荐。
 *
 * 三步流水线：
 *   ① LLM 从自然语言中提取结构化参数（预算/人数/辣度/品类，JSON 输出，失败重试后取默认值）
 *   ② 按参数查库过滤候选菜品/套餐（价格、辣度、起售状态——结构化条件由数据库保证，大模型不参与筛选，防止幻觉）
 *   ③ 候选列表 + 用户原话拼 prompt，LLM 生成推荐方案；服务端逐项反幻觉校验（id 必须在候选集、价格以库为准、总价重算）
 *
 * 降级：大模型不可用/超时/输出非法时，走 buildFallbackPlan 规则推荐（贪心凑单），接口始终返回可用结果。
 */
@Service
@Slf4j
public class AiRecommendServiceImpl implements AiRecommendService {

    @Autowired
    private AiChatClient aiChatClient;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private AiRecommendLogMapper aiRecommendLogMapper;

    /**
     * 参数提取提示词：只输出 JSON
     */
    private static final String PARAM_EXTRACT_SYSTEM_PROMPT =
            "你是点餐参数提取助手。从用户的点餐需求中提取结构化参数，只输出一个 JSON 对象，不要输出任何其他文字。\n" +
            "JSON 字段：budget（总预算，单位元，数值，可 null）、peopleCount（用餐人数，整数，可 null）、" +
            "maxSpicy（最大辣度，整数 0不辣 1微辣 2中辣 3特辣，可 null）、categoryName（想吃的品类名称，如\"汤类\"\"鱼类\"，可 null）。\n" +
            "示例输入：\"帮我推荐50块以内不辣的双人餐\"\n" +
            "示例输出：{\"budget\":50,\"peopleCount\":2,\"maxSpicy\":0,\"categoryName\":null}";

    /**
     * 推荐方案提示词：只从候选中挑选，输出 JSON
     */
    private static final String PLAN_SYSTEM_PROMPT =
            "你是校园食堂点餐推荐助手。根据用户需求，从候选列表中挑选菜品/套餐组合，只输出一个 JSON 对象，不要输出任何其他文字。\n" +
            "JSON 字段：items（数组，每项含：dishId 菜品id（可 null）、setmealId 套餐id（可 null，dishId 与 setmealId 二选一）、" +
            "quantity 数量（正整数）、reason 推荐理由（一句话，结合用户需求））、totalPrice（组合总价，数值）、summary（整体推荐说明，一句话）。\n" +
            "要求：dishId/setmealId 只能从候选列表里选；组合总价不超过用户预算（如给出）；辣度不超过用户上限（如给出）；" +
            "按用餐人数合理搭配份量（人均一份主菜的量）；理由要结合用户需求（如清淡/实惠/下饭）。";

    @Override
    public AiRecommendVO recommend(AiRecommendQueryDTO aiRecommendQueryDTO) {
        String query = aiRecommendQueryDTO.getQuery();
        Long userId = BaseContext.getCurrentId();

        // ① LLM 提取结构化参数（失败自动回退默认参数，不阻塞主流程）
        RecommendParams params = extractParams(query);

        try {
            AiRecommendVO vo = doRecommend(query, params);
            logRecommend(userId, query, params, vo, false);
            return vo;
        } catch (Exception e) {
            log.error("AI 推荐失败，降级为规则推荐：query={}, 原因={}", query, e.getMessage());
            AiRecommendVO vo = buildFallbackPlan(query, params);
            logRecommend(userId, query, params, vo, true);
            return vo;
        }
    }

    /**
     * ① 参数提取：LLM 输出 JSON，解析失败重试一次，仍失败使用默认参数
     */
    private RecommendParams extractParams(String query) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String content = aiChatClient.chat(PARAM_EXTRACT_SYSTEM_PROMPT, query);
                JSONObject json = JSON.parseObject(content);
                RecommendParams params = new RecommendParams();
                params.budget = json.getBigDecimal("budget");
                params.peopleCount = json.getInteger("peopleCount");
                params.maxSpicy = json.getInteger("maxSpicy");
                params.categoryName = json.getString("categoryName");
                log.info("参数提取成功：{}", JSON.toJSONString(params));
                return params;
            } catch (Exception e) {
                log.warn("参数提取失败（第{}次）：{}", attempt + 1, e.getMessage());
            }
        }
        log.warn("参数提取重试后仍失败，使用默认参数");
        RecommendParams params = new RecommendParams();
        params.peopleCount = 1;
        return params;
    }

    /**
     * ② 查库过滤候选 + ③ LLM 生成方案 + 反幻觉校验
     */
    private AiRecommendVO doRecommend(String query, RecommendParams params) throws Exception {
        List<Candidate> candidates = loadCandidates(params);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("没有符合条件的候选菜品");
        }

        String userPrompt = "用户需求：" + query + "\n\n候选列表（只能从中选择）：\n" + buildCandidateText(candidates);
        String content = aiChatClient.chat(PLAN_SYSTEM_PROMPT, userPrompt);
        JSONObject json = JSON.parseObject(content);

        // 反幻觉校验：id 必须在候选集内，价格以数据库为准，总价由服务端重算
        Map<String, Candidate> candidateMap = new HashMap<>();
        for (Candidate candidate : candidates) {
            candidateMap.put(candidate.key(), candidate);
        }

        JSONArray items = json.getJSONArray("items");
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("大模型返回的推荐列表为空");
        }

        List<AiRecommendItemVO> resultItems = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            Long dishId = item.getLong("dishId");
            Long setmealId = item.getLong("setmealId");
            Candidate candidate = null;
            if (dishId != null) {
                candidate = candidateMap.get("dish:" + dishId);
            } else if (setmealId != null) {
                candidate = candidateMap.get("setmeal:" + setmealId);
            }
            // 幻觉项（候选集之外的 id）直接丢弃
            if (candidate == null) {
                log.warn("丢弃幻觉推荐项：dishId={}, setmealId={}", dishId, setmealId);
                continue;
            }
            Integer quantity = item.getInteger("quantity");
            if (quantity == null || quantity < 1 || quantity > 10) {
                quantity = 1;
            }

            AiRecommendItemVO itemVO = AiRecommendItemVO.builder()
                    .dishId(dishId)
                    .setmealId(setmealId)
                    .name(candidate.name)
                    .price(candidate.price)
                    .quantity(quantity)
                    .image(candidate.image)
                    .spicyLevel(candidate.spicyLevel)
                    .reason(item.getString("reason"))
                    .build();
            resultItems.add(itemVO);
            totalPrice = totalPrice.add(candidate.price.multiply(new BigDecimal(quantity)));
        }
        if (resultItems.isEmpty()) {
            throw new IllegalStateException("推荐结果反幻觉校验后为空");
        }

        // 预算兜底：超预算时从高价往低价剔除，仍无法满足则交给规则降级
        if (params.budget != null && totalPrice.compareTo(params.budget) > 0) {
            resultItems.sort(Comparator.comparing(AiRecommendItemVO::getPrice).reversed());
            while (!resultItems.isEmpty() && totalPrice.compareTo(params.budget) > 0) {
                AiRecommendItemVO removed = resultItems.remove(resultItems.size() - 1);
                totalPrice = totalPrice.subtract(removed.getPrice().multiply(new BigDecimal(removed.getQuantity())));
            }
            if (resultItems.isEmpty()) {
                throw new IllegalStateException("预算内无法组合出推荐方案");
            }
        }

        return AiRecommendVO.builder()
                .items(resultItems)
                .totalPrice(totalPrice)
                .summary(json.getString("summary"))
                .fallback(false)
                .build();
    }

    /**
     * 规则降级：贪心凑单（价格升序，凑到预算 90% 内；双人及以上优先选便宜的套餐），
     * 独立纯方法，便于单元测试
     */
    AiRecommendVO buildFallbackPlan(String query, RecommendParams params) {
        int people = params.peopleCount == null || params.peopleCount < 1 ? 1 : params.peopleCount;
        // 未给预算时按人均 20 元估算
        BigDecimal budget = params.budget != null ? params.budget : new BigDecimal(people * 20L);
        BigDecimal cap = budget.multiply(new BigDecimal("0.9"));

        List<Candidate> candidates = loadCandidates(params);
        List<AiRecommendItemVO> items = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;

        // 双人及以上：优先选便宜的套餐（最多 people 个）
        if (people >= 2) {
            List<Candidate> setmeals = candidates.stream()
                    .filter(c -> c.type == TYPE_SETMEAL)
                    .sorted(Comparator.comparing(c -> c.price))
                    .collect(Collectors.toList());
            for (Candidate candidate : setmeals) {
                if (items.size() >= people) {
                    break;
                }
                if (totalPrice.add(candidate.price).compareTo(cap) <= 0) {
                    items.add(toItem(candidate, "双人套餐性价比高"));
                    totalPrice = totalPrice.add(candidate.price);
                }
            }
        }

        // 价格升序补菜品，凑到预算上限为止
        List<Candidate> dishes = candidates.stream()
                .filter(c -> c.type == TYPE_DISH)
                .sorted(Comparator.comparing(c -> c.price))
                .collect(Collectors.toList());
        for (Candidate candidate : dishes) {
            if (totalPrice.add(candidate.price).compareTo(cap) > 0) {
                break; // 已按价格升序，后续只会更贵
            }
            items.add(toItem(candidate, "价格实惠，符合预算与口味约束"));
            totalPrice = totalPrice.add(candidate.price);
        }

        // 一个都没选上时，放宽到预算内最便宜的一项
        if (items.isEmpty() && !candidates.isEmpty()) {
            Candidate cheapest = candidates.stream().min(Comparator.comparing(c -> c.price)).get();
            items.add(toItem(cheapest, "预算内最实惠的选择"));
            totalPrice = cheapest.price;
        }

        return AiRecommendVO.builder()
                .items(items)
                .totalPrice(totalPrice)
                .summary("当前为规则推荐模式（离线降级），已按预算与辣度约束为您挑选以下组合。")
                .fallback(true)
                .build();
    }

    private AiRecommendItemVO toItem(Candidate candidate, String reason) {
        return AiRecommendItemVO.builder()
                .dishId(candidate.type == TYPE_DISH ? candidate.id : null)
                .setmealId(candidate.type == TYPE_SETMEAL ? candidate.id : null)
                .name(candidate.name)
                .price(candidate.price)
                .quantity(1)
                .image(candidate.image)
                .spicyLevel(candidate.spicyLevel)
                .reason(reason)
                .build();
    }

    /**
     * 查库过滤候选（结构化条件：起售状态、价格、辣度、品类）
     */
    private List<Candidate> loadCandidates(RecommendParams params) {
        Long categoryId = null;
        if (params.categoryName != null && !params.categoryName.isEmpty()) {
            // 品类名解析为分类 id，查不到时忽略该条件（不做强校验，避免误伤）
            categoryId = categoryMapper.getDishCategoryIdByName(params.categoryName);
        }

        List<Candidate> candidates = new ArrayList<>();
        List<Dish> dishes = dishMapper.listForRecommend(params.budget, params.maxSpicy, categoryId);
        for (Dish dish : dishes) {
            candidates.add(new Candidate(TYPE_DISH, dish.getId(), dish.getName(), dish.getPrice(),
                    dish.getSpicyLevel(), dish.getImage(), dish.getDescription()));
        }
        // 套餐品类与菜品分类体系不同，品类条件只作用于菜品，套餐不做品类过滤
        List<Setmeal> setmeals = setmealMapper.listForRecommend(params.budget, params.maxSpicy, null);
        for (Setmeal setmeal : setmeals) {
            candidates.add(new Candidate(TYPE_SETMEAL, setmeal.getId(), setmeal.getName(), setmeal.getPrice(),
                    setmeal.getSpicyLevel(), setmeal.getImage(), setmeal.getDescription()));
        }
        return candidates;
    }

    /**
     * 候选列表拼成提示词文本
     */
    private String buildCandidateText(List<Candidate> candidates) {
        StringBuilder sb = new StringBuilder();
        for (Candidate candidate : candidates) {
            sb.append(candidate.type == TYPE_DISH ? "菜品" : "套餐")
                    .append(" | ").append(candidate.type == TYPE_DISH ? "dishId:" : "setmealId:").append(candidate.id)
                    .append(" | ").append(candidate.name)
                    .append(" | 价格:").append(candidate.price).append("元")
                    .append(" | 辣度:").append(spicyText(candidate.spicyLevel))
                    .append(candidate.description != null && !candidate.description.isEmpty()
                            ? " | 简介:" + candidate.description : "")
                    .append("\n");
        }
        return sb.toString();
    }

    private String spicyText(Integer spicyLevel) {
        if (spicyLevel == null) {
            return "未知";
        }
        switch (spicyLevel) {
            case 0: return "不辣";
            case 1: return "微辣";
            case 2: return "中辣";
            case 3: return "特辣";
            default: return "未知";
        }
    }

    /**
     * 推荐日志入库（失败不影响主流程）
     */
    private void logRecommend(Long userId, String query, RecommendParams params, AiRecommendVO vo, boolean fallback) {
        try {
            AiRecommendLog logEntity = AiRecommendLog.builder()
                    .userId(userId)
                    .query(query)
                    .paramsJson(JSON.toJSONString(params))
                    .resultJson(JSON.toJSONString(vo))
                    .fallback(fallback ? 1 : 0)
                    .createTime(LocalDateTime.now())
                    .build();
            aiRecommendLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.warn("AI 推荐日志记录失败：{}", e.getMessage());
        }
    }

    private static final int TYPE_DISH = 1;
    private static final int TYPE_SETMEAL = 2;

    /**
     * 结构化参数（LLM 提取结果）
     */
    public static class RecommendParams {
        /** 总预算（元），null 表示未指定 */
        public BigDecimal budget;
        /** 用餐人数 */
        public Integer peopleCount;
        /** 最大辣度 0-3，null 表示未指定 */
        public Integer maxSpicy;
        /** 品类名称，null 表示未指定 */
        public String categoryName;
    }

    /**
     * 候选菜品/套餐（查库过滤后的内部结构）
     */
    private static class Candidate {
        int type;             // 1菜品 2套餐
        Long id;
        String name;
        BigDecimal price;
        Integer spicyLevel;
        String image;
        String description;

        Candidate(int type, Long id, String name, BigDecimal price, Integer spicyLevel, String image, String description) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.price = price;
            this.spicyLevel = spicyLevel;
            this.image = image;
            this.description = description;
        }

        String key() {
            return (type == TYPE_DISH ? "dish:" : "setmeal:") + id;
        }
    }
}
