package com.campus.service.impl;

import com.campus.constant.MessageConstant;
import com.campus.context.BaseContext;
import com.campus.dto.OrdersSubmitDTO;
import com.campus.entity.AddressBook;
import com.campus.entity.OrderDetail;
import com.campus.entity.Orders;
import com.campus.entity.ShoppingCart;
import com.campus.exception.AddressBookBusinessException;
import com.campus.exception.ShoppingCartBusinessException;
import com.campus.mapper.AddressBookMapper;
import com.campus.mapper.OrderDetailMapper;
import com.campus.mapper.OrderMapper;
import com.campus.mapper.ShoppingCartMapper;
import com.campus.service.OrderService;
import com.campus.vo.OrderSubmitVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private com.campus.queue.OrderRabbitSender orderRabbitSender;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);

        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);

        orderMapper.insert(orders);

        List<OrderDetail> orderDetailList = new ArrayList<>();

        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart,orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);

        shoppingCartMapper.deleteByUserId(userId);

        // 发送延时消息：例如 30 分钟后检查订单是否已支付（30*60*1000 ms）
        try {
            orderRabbitSender.sendDelayOrder(orders.getId(), 30 * 60 * 1000L);
        } catch (Exception e) {
            // 发送失败不回滚下单流程，但应记录日志/报警。这里简单打印堆栈。
            e.printStackTrace();
        }

        OrderSubmitVO build = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return build;
    }

    @Override
    public void handleTimeout(Long orderId) {
        // 仅当订单仍处于待付款且未支付时，更新为取消，利用 mapper 的条件更新保证幂等
        int updated = orderMapper.updateStatusToCancelledIfUnpaid(orderId, LocalDateTime.now());
        if (updated > 0) {
            // 执行额外的补偿逻辑，例如回滚库存或释放套餐锁定（如果有），此处留空或调用对应服务
        }
    }
}
