package com.campus.pay;

import com.campus.entity.Orders;
import com.campus.vo.OrderPaymentVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 模拟支付网关（默认启用）。
 *
 * 按微信支付 V3 的返回结构构造假的调起支付参数（nonceStr/timeStamp/package/signType/paySign），
 * 上层业务代码与真实支付完全一致；"支付成功"由 /user/order/payment/mock-notify 接口模拟微信回调触发，
 * 对应真实场景中微信 notify_url 回调的落点。
 */
@Component
@ConditionalOnProperty(name = "campus.pay.mode", havingValue = "mock", matchIfMissing = true)
@Slf4j
public class MockWechatPayGateway implements PaymentGateway {

    @Override
    public OrderPaymentVO createPayment(Orders orders) {
        OrderPaymentVO orderPaymentVO = new OrderPaymentVO();
        orderPaymentVO.setNonceStr(RandomStringUtils.randomNumeric(32));
        orderPaymentVO.setTimeStamp(String.valueOf(System.currentTimeMillis() / 1000));
        orderPaymentVO.setSignType("MOCK");
        orderPaymentVO.setPackageStr("prepay_id=mock_" + orders.getNumber());
        orderPaymentVO.setPaySign("MOCK");
        log.info("模拟支付下单：orderNumber={}, amount={}", orders.getNumber(), orders.getAmount());
        return orderPaymentVO;
    }

    @Override
    public void refund(Orders orders) {
        log.info("模拟退款：orderNumber={}, amount={}", orders.getNumber(), orders.getAmount());
    }

    @Override
    public boolean isMock() {
        return true;
    }
}
