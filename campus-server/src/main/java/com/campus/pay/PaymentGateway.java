package com.campus.pay;

import com.campus.entity.Orders;
import com.campus.vo.OrderPaymentVO;

/**
 * 支付网关抽象。
 *
 * 个人开发者无法注册微信支付商户号，因此项目默认走 MockWechatPayGateway（模拟支付），
 * 但网关的接口契约与真实微信支付 V3 完全对齐：
 *   createPayment -> 真实实现委托 WeChatPayUtil.pay（jsapi 下单 + 二次签名，返回调起支付参数）
 *   refund        -> 真实实现委托 WeChatPayUtil.refund（申请退款）
 * 通过配置 campus.pay.mode = mock | real 一键切换，业务代码只依赖本接口。
 */
public interface PaymentGateway {

    /**
     * 创建支付（生成前端调起支付所需的参数）
     *
     * @param orders 待支付订单
     * @return 调起支付参数
     */
    OrderPaymentVO createPayment(Orders orders) throws Exception;

    /**
     * 退款
     *
     * @param orders 已支付订单
     */
    void refund(Orders orders) throws Exception;

    /**
     * 是否为模拟实现（用于 mock-auto-pay 等演示逻辑判断）
     */
    default boolean isMock() {
        return false;
    }
}
