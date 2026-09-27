package com.campus.service.impl;

import com.campus.context.BaseContext;
import com.campus.dto.OrdersCancelDTO;
import com.campus.dto.OrdersPaymentDTO;
import com.campus.dto.OrdersSubmitDTO;
import com.campus.entity.AddressBook;
import com.campus.entity.Orders;
import com.campus.entity.ShoppingCart;
import com.campus.entity.User;
import com.campus.exception.OrderBusinessException;
import com.campus.mapper.AddressBookMapper;
import com.campus.mapper.OrderDetailMapper;
import com.campus.mapper.OrderMapper;
import com.campus.mapper.ShoppingCartMapper;
import com.campus.mapper.UserMapper;
import com.campus.pay.PaymentGateway;
import com.campus.queue.OrderRabbitSender;
import com.campus.vo.OrderSubmitVO;
import com.campus.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单核心逻辑单测（无 Spring 上下文、无外部依赖）
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private AddressBookMapper addressBookMapper;
    @Mock
    private OrderDetailMapper orderDetailMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private ShoppingCartMapper shoppingCartMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private OrderRabbitSender orderRabbitSender;
    @Mock
    private WebSocketServer webSocketServer;

    @InjectMocks
    private OrderServiceImpl orderService;

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    /**
     * 下单：订单组装正确（状态/支付状态/地址快照/用户名），购物车清空，延时消息发送
     */
    @Test
    void submitOrder_shouldAssembleOrderCorrectly() {
        BaseContext.setCurrentId(1L);

        AddressBook addressBook = AddressBook.builder()
                .id(1L).userId(1L).phone("13800138000").consignee("张三")
                .provinceName("安徽省").cityName("合肥市").districtName("蜀山区").detail("XX大学1号宿舍楼101")
                .build();
        ShoppingCart cart = ShoppingCart.builder()
                .id(1L).userId(1L).dishId(46L).name("王老吉").image("img.png")
                .number(1).amount(new BigDecimal("6.00")).build();
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(1L);
        dto.setAmount(new BigDecimal("6.00"));
        dto.setPayMethod(1);
        dto.setDeliveryStatus(1);
        dto.setTablewareNumber(1);
        dto.setTablewareStatus(1);
        dto.setPackAmount(1);

        when(addressBookMapper.getById(1L)).thenReturn(addressBook);
        when(shoppingCartMapper.list(any())).thenReturn(Collections.singletonList(cart));
        when(userMapper.getById(1L)).thenReturn(User.builder().id(1L).name("张三").build());
        // 模拟 insert 回填主键
        doAnswer(invocation -> {
            Orders orders = invocation.getArgument(0);
            orders.setId(100L);
            return null;
        }).when(orderMapper).insert(any());

        OrderSubmitVO result = orderService.submitOrder(dto);

        assertNotNull(result.getOrderNumber());

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(captor.capture());
        Orders saved = captor.getValue();
        assertEquals(Orders.PENDING_PAYMENT, saved.getStatus());
        assertEquals(Orders.UN_PAID, saved.getPayStatus());
        assertEquals("安徽省合肥市蜀山区XX大学1号宿舍楼101", saved.getAddress());
        assertEquals("张三", saved.getUserName());

        verify(orderDetailMapper).insertBatch(anyList());
        verify(shoppingCartMapper).deleteByUserId(1L);
        verify(orderRabbitSender).sendDelayOrder(eq(100L));
    }

    /**
     * 取消：已完成订单不可取消
     */
    @Test
    void userCancel_shouldRejectCompletedOrder() {
        BaseContext.setCurrentId(1L);
        Orders completed = Orders.builder().id(1L).userId(1L)
                .status(Orders.COMPLETED).payStatus(Orders.PAID).build();
        when(orderMapper.getById(1L)).thenReturn(completed);

        OrdersCancelDTO dto = new OrdersCancelDTO();
        dto.setId(1L);
        dto.setCancelReason("不想吃了");

        assertThrows(OrderBusinessException.class, () -> orderService.userCancel(dto));
    }

    /**
     * 支付：非待付款状态不可发起支付
     */
    @Test
    void payment_shouldRejectNonPendingOrder() {
        BaseContext.setCurrentId(1L);
        Orders paid = Orders.builder().id(1L).userId(1L).number("123")
                .status(Orders.TO_BE_CONFIRMED).payStatus(Orders.PAID).payMethod(1).build();
        when(orderMapper.getByNumber("123")).thenReturn(paid);

        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber("123");
        dto.setPayMethod(1);

        assertThrows(OrderBusinessException.class, () -> orderService.payment(dto));
    }

    /**
     * 支付成功回调幂等：重复回调（条件更新影响行数为0且订单已支付）不抛异常
     */
    @Test
    void paymentSuccess_shouldBeIdempotentOnDuplicateCallback() {
        Orders pending = Orders.builder().id(1L).number("123").payMethod(1).build();
        when(orderMapper.getByNumber("123")).thenReturn(pending);
        // 条件更新未生效（已被并发回调抢先更新）
        when(orderMapper.markPaid(eq(1L), any(), eq(1))).thenReturn(0);
        when(orderMapper.getById(1L)).thenReturn(
                Orders.builder().id(1L).status(Orders.TO_BE_CONFIRMED).payStatus(Orders.PAID).build());

        // 不抛异常即幂等通过
        orderService.paymentSuccess("123");
    }
}
