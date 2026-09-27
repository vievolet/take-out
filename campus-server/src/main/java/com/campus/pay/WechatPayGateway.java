package com.campus.pay;

import com.alibaba.fastjson.JSONObject;
import com.campus.entity.Orders;
import com.campus.entity.User;
import com.campus.mapper.UserMapper;
import com.campus.utils.WeChatPayUtil;
import com.campus.vo.OrderPaymentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 真实微信支付网关（V3）。
 *
 * 委托 campus-common 中已实现的 WeChatPayUtil（jsapi 下单 + RSA 二次签名 / 申请退款）。
 * 启用条件：campus.pay.mode = real，且需在配置中提供商户号、商户API私钥、平台证书等（个人开发者无商户资质，默认不用）。
 */
@Component
@ConditionalOnProperty(name = "campus.pay.mode", havingValue = "real")
public class WechatPayGateway implements PaymentGateway {

    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private UserMapper userMapper;

    @Override
    public OrderPaymentVO createPayment(Orders orders) throws Exception {
        User user = userMapper.getById(orders.getUserId());
        // 统一下单 + 二次签名，返回小程序调起支付所需参数
        JSONObject result = weChatPayUtil.pay(orders.getNumber(), orders.getAmount(), "校园外卖订单", user.getOpenid());

        OrderPaymentVO orderPaymentVO = new OrderPaymentVO();
        orderPaymentVO.setNonceStr(result.getString("nonceStr"));
        orderPaymentVO.setTimeStamp(result.getString("timeStamp"));
        orderPaymentVO.setPackageStr(result.getString("package"));
        orderPaymentVO.setSignType(result.getString("signType"));
        orderPaymentVO.setPaySign(result.getString("paySign"));
        return orderPaymentVO;
    }

    @Override
    public void refund(Orders orders) throws Exception {
        // 退款单号用 原订单号 + "R" 区分，与原订单金额等额退款
        weChatPayUtil.refund(orders.getNumber(), orders.getNumber() + "R", orders.getAmount(), orders.getAmount());
    }
}
