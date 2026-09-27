-- =====================================================================
-- AI 点餐推荐模块迁移脚本（对已有数据库执行一次）
-- 1. dish 表新增辣度字段（结构化字段，替代 dish_flavor 里的自由文本口味，支持范围过滤）
-- 2. 按现有种子菜品名称回填辣度
-- 3. 新建 AI 推荐日志表
-- =====================================================================

USE sky_take_out;

-- 1. dish 表加辣度列
ALTER TABLE dish ADD COLUMN spicy_level TINYINT NOT NULL DEFAULT 0
    COMMENT '辣度 0不辣 1微辣 2中辣 3特辣' AFTER description;

-- 2. 按种子菜品名称回填（按实际菜品名调整）
UPDATE dish SET spicy_level = 1 WHERE name IN ('老坛酸菜鱼', '经典酸菜鮰鱼', '金汤酸菜牛蛙');
UPDATE dish SET spicy_level = 2 WHERE name IN ('香锅牛蛙', '馋嘴牛蛙');
UPDATE dish SET spicy_level = 3 WHERE name IN ('蜀味水煮草鱼', '剁椒鱼头');

-- 3. AI 推荐日志表
CREATE TABLE IF NOT EXISTS ai_recommend_log (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint DEFAULT NULL COMMENT '用户id',
    `query` varchar(512) DEFAULT NULL COMMENT '用户原始自然语言请求',
    `params_json` text COMMENT 'LLM提取的结构化参数JSON',
    `result_json` text COMMENT '推荐结果JSON',
    `fallback` tinyint NOT NULL DEFAULT 0 COMMENT '是否命中规则降级 0否 1是',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='AI推荐日志表';

-- 4. 校验
SELECT id, name, price, spicy_level FROM dish ORDER BY spicy_level, price;
