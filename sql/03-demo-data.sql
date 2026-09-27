-- =====================================================================
-- 演示数据脚本（在演示库上执行一次）
-- 用途：
--   1. 造一个测试用户 + 地址簿（curl 验证用户端接口时用 userId=1 签发 JWT）
--   2. 造近30天已完成的历史订单（数据统计/工作台/销量Top10 图表的演示数据）
--   3. 造几条不同状态的订单（管理端订单管理 + 来单提醒演示）
-- 注意：依赖 sky.sql 的种子菜品（dish id 46-69），用户表/订单表需为空库或与现有数据不冲突
-- =====================================================================

USE sky_take_out;

-- 1. 测试用户（openid 唯一）
INSERT INTO `user` (`id`, `openid`, `name`, `phone`, `sex`, `id_number`, `avatar`, `create_time`)
VALUES (1, 'demo-openid-001', '测试用户', '13800138000', '1', NULL, NULL, DATE_SUB(NOW(), INTERVAL 40 DAY));

-- 2. 地址簿
INSERT INTO `address_book` (`id`, `user_id`, `consignee`, `sex`, `phone`, `province_code`, `province_name`, `city_code`, `city_name`, `district_code`, `district_name`, `detail`, `label`, `is_default`)
VALUES (1, 1, '测试用户', '1', '13800138000', '340000', '安徽省', '340100', '合肥市', '340104', '蜀山区', 'XX大学1号宿舍楼101', '宿舍', 1);

-- 3. 近30天已完成订单（每天1单，金额/菜品轮换，供统计图表使用）
INSERT INTO `orders` (`number`, `status`, `user_id`, `address_book_id`, `order_time`, `checkout_time`, `pay_method`, `pay_status`, `amount`, `remark`, `phone`, `address`, `user_name`, `consignee`, `pack_amount`, `tableware_number`, `tableware_status`)
SELECT
    CONCAT('demo', 1000 + n),
    5,
    1,
    1,
    DATE_SUB(CURDATE(), INTERVAL n DAY) + INTERVAL 11 HOUR,
    DATE_SUB(CURDATE(), INTERVAL n DAY) + INTERVAL 11 HOUR,
    1,
    1,
    30 + (n * 7) % 60,
    '演示数据',
    '13800138000',
    '安徽省合肥市蜀山区XX大学1号宿舍楼101',
    '测试用户',
    '测试用户',
    1,
    1,
    1
FROM (
    SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
    UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
    UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25
    UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30
) t;

-- 4. 历史订单明细（每单2个菜品：米饭 + 按订单号轮换一个主菜）
INSERT INTO `order_detail` (`name`, `order_id`, `dish_id`, `setmeal_id`, `dish_flavor`, `number`, `amount`, `image`)
SELECT d.name, o.id, d.id, NULL, '', 1, d.price, d.image
FROM orders o
JOIN dish d ON d.id = 49
WHERE o.status = 5;

INSERT INTO `order_detail` (`name`, `order_id`, `dish_id`, `setmeal_id`, `dish_flavor`, `number`, `amount`, `image`)
SELECT d.name, o.id, d.id, NULL, '', 1, d.price, d.image
FROM orders o
JOIN dish d ON d.id = 46 + (o.id % 12)
WHERE o.status = 5;

-- 5. 各状态演示订单（待接单/已接单/派送中/待付款，供订单管理与来单提醒演示）
INSERT INTO `orders` (`number`, `status`, `user_id`, `address_book_id`, `order_time`, `checkout_time`, `pay_method`, `pay_status`, `amount`, `remark`, `phone`, `address`, `user_name`, `consignee`, `pack_amount`, `tableware_number`, `tableware_status`)
VALUES
    ('demo-wait-confirm', 2, 1, 1, NOW(), NOW(), 1, 1, 45.00, '待接单演示', '13800138000', '安徽省合肥市蜀山区XX大学1号宿舍楼101', '测试用户', '测试用户', 1, 1, 1),
    ('demo-confirmed', 3, 1, 1, NOW(), NOW(), 1, 1, 66.00, '已接单演示', '13800138000', '安徽省合肥市蜀山区XX大学1号宿舍楼101', '测试用户', '测试用户', 1, 1, 1),
    ('demo-delivering', 4, 1, 1, NOW(), NOW(), 1, 1, 88.00, '派送中演示', '13800138000', '安徽省合肥市蜀山区XX大学1号宿舍楼101', '测试用户', '测试用户', 1, 1, 1),
    ('demo-pending-pay', 1, 1, 1, NOW(), NULL, 1, 0, 38.00, '待付款演示（可用于延时关单测试）', '13800138000', '安徽省合肥市蜀山区XX大学1号宿舍楼101', '测试用户', '测试用户', 1, 1, 1);

-- 6. 状态演示订单的明细
INSERT INTO `order_detail` (`name`, `order_id`, `dish_id`, `setmeal_id`, `dish_flavor`, `number`, `amount`, `image`)
SELECT d.name, o.id, d.id, NULL, '', 1, d.price, d.image
FROM orders o
JOIN dish d ON d.id = 51
WHERE o.number IN ('demo-wait-confirm', 'demo-confirmed', 'demo-delivering', 'demo-pending-pay');

-- 7. 校验
SELECT id, number, status, pay_status, order_time FROM orders ORDER BY id DESC LIMIT 5;
