package com.campus.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.campus.constant.MessageConstant;
import com.campus.context.BaseContext;
import com.campus.dto.OrdersCancelDTO;
import com.campus.dto.OrdersConfirmDTO;
import com.campus.dto.OrdersPageQueryDTO;
import com.campus.dto.OrdersPaymentDTO;
import com.campus.dto.OrdersRejectionDTO;
import com.campus.dto.OrdersSubmitDTO;
import com.campus.entity.AddressBook;
import com.campus.entity.OrderDetail;
import com.campus.entity.Orders;
import com.campus.entity.ShoppingCart;
import com.campus.entity.User;
import com.campus.exception.AddressBookBusinessException;
import com.campus.exception.OrderBusinessException;
import com.campus.exception.ShoppingCartBusinessException;
import com.campus.mapper.AddressBookMapper;
import com.campus.mapper.OrderDetailMapper;
import com.campus.mapper.OrderMapper;
import com.campus.mapper.ShoppingCartMapper;
import com.campus.mapper.UserMapper;
import com.campus.pay.PaymentGateway;
import com.campus.queue.OrderRabbitSender;
import com.campus.result.PageResult;
import com.campus.service.OrderService;
import com.campus.websocket.WebSocketServer;
import com.campus.vo.OrderPaymentVO;
import com.campus.vo.OrderStatisticsVO;
import com.campus.vo.OrderSubmitVO;
import com.campus.vo.OrderVO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PaymentGateway paymentGateway;
    @Autowired
    private OrderRabbitSender orderRabbitSender;
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * mock 支付模式下调用支付接口即视为支付成功（模拟真实用户完成支付后的回调）。
     * 置为 false 时，需手动调 /user/order/payment/mock-notify 模拟微信回调
     */
    @Value("${campus.pay.mock-auto-pay:true}")
    private boolean mockAutoPay;

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
        // 地址快照：省市区 + 详细地址
        orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName()
                + addressBook.getDistrictName() + addressBook.getDetail());
        orders.setUserId(userId);
        User user = userMapper.getById(userId);
        if (user != null) {
            orders.setUserName(user.getName());
        }

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

        // 发送延时消息：支付超时未支付则自动取消（超时时间由延时队列的队列级 TTL 控制，见 RabbitConfig）
        try {
            orderRabbitSender.sendDelayOrder(orders.getId());
        } catch (Exception e) {
            // 发送失败不回滚下单流程，但记录日志（可扩展报警/补偿扫描）
            log.error("订单延时消息发送失败：orderId={}", orders.getId(), e);
        }

        OrderSubmitVO build = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return build;
    }

    /**
     * 处理订单超时（由延时队列触发）。
     * 条件更新（status=1 and pay_status=0）自带幂等：与支付并发时先到先得，后到者影响行数为 0
     */
    @Override
    public void handleTimeout(Long orderId) {
        int updated = orderMapper.updateStatusToCancelledIfUnpaid(orderId, LocalDateTime.now());
        if (updated > 0) {
            log.info("超时未支付订单已自动取消：orderId={}", orderId);
        }
    }

    /**
     * 订单支付
     */
    @Override
    @Transactional
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) {
        Orders orders = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 订单归属校验
        if (!orders.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_USER_MISMATCH);
        }
        // 只有 待付款 + 未支付 的订单可以发起支付（重复支付同样报订单状态错误）
        if (!Orders.PENDING_PAYMENT.equals(orders.getStatus()) || !Orders.UN_PAID.equals(orders.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        OrderPaymentVO orderPaymentVO;
        try {
            orderPaymentVO = paymentGateway.createPayment(orders);
        } catch (Exception e) {
            log.error("创建支付失败：orderNumber={}", orders.getNumber(), e);
            throw new OrderBusinessException(MessageConstant.PAYMENT_FAILED);
        }

        // mock 模式下模拟"用户完成支付"：立即走支付成功逻辑，效果等同真实微信回调
        if (mockAutoPay && paymentGateway.isMock()) {
            paymentSuccess(orders.getNumber());
        }

        return orderPaymentVO;
    }

    /**
     * 支付成功（模拟微信支付回调入口；真实模式下对应微信 notify 回调处理器：验签 + 幂等 + 快速应答）
     */
    @Override
    @Transactional
    public void paymentSuccess(String orderNumber) {
        Orders orders = orderMapper.getByNumber(orderNumber);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 条件更新保证幂等：并发/重复回调只有一次能更新成功
        int rows = orderMapper.markPaid(orders.getId(), LocalDateTime.now(), orders.getPayMethod());
        if (rows == 1) {
            log.info("订单支付成功：orderId={}, number={}", orders.getId(), orderNumber);
            // 向管理端推送"来单提醒"（type=1），打包前端收到后自动语音播报
            JSONObject message = new JSONObject();
            message.put("type", 1);
            message.put("orderId", orders.getId());
            message.put("content", "订单号：" + orderNumber);
            webSocketServer.sendToAllClient(message.toJSONString());
        } else {
            // 更新失败：可能是订单已支付（重复回调，幂等忽略）或已被延时关单
            Orders latest = orderMapper.getById(orders.getId());
            if (latest != null && Orders.PAID.equals(latest.getPayStatus())) {
                log.info("订单已支付，重复回调忽略：orderId={}", orders.getId());
                return;
            }
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 用户端历史订单分页查询
     */
    @Override
    public PageResult historyOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        // 只能查询当前用户的订单
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        return new PageResult(page.getTotal(), buildOrderVOs(page.getResult()));
    }

    /**
     * 用户端订单详情（校验归属）
     */
    @Override
    public OrderVO orderDetail(Long id) {
        Orders orders = getOwnedOrder(id);
        return buildSingleOrderVO(orders);
    }

    /**
     * 用户取消订单
     */
    @Override
    @Transactional
    public void userCancel(OrdersCancelDTO ordersCancelDTO) {
        Orders orders = getOwnedOrder(ordersCancelDTO.getId());
        Integer status = orders.getStatus();
        // 待付款/待接单/已接单/派送中 可取消，已完成不可取消
        if (!(Orders.PENDING_PAYMENT.equals(status) || Orders.TO_BE_CONFIRMED.equals(status)
                || Orders.CONFIRMED.equals(status) || Orders.DELIVERY_IN_PROGRESS.equals(status))) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders update = new Orders();
        update.setId(orders.getId());
        update.setStatus(Orders.CANCELLED);
        update.setCancelReason(ordersCancelDTO.getCancelReason());
        update.setCancelTime(LocalDateTime.now());
        if (Orders.PAID.equals(orders.getPayStatus())) {
            refundIfPaid(orders);
            update.setPayStatus(Orders.REFUND);
        }
        guardUpdate(update, status);
    }

    /**
     * 用户催单（仅已接单/派送中可催）
     */
    @Override
    public void reminder(Long id) {
        Orders orders = getOwnedOrder(id);
        Integer status = orders.getStatus();
        if (!(Orders.CONFIRMED.equals(status) || Orders.DELIVERY_IN_PROGRESS.equals(status))) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        log.info("用户催单：orderId={}, number={}", id, orders.getNumber());
        // 向管理端推送"催单"提醒（type=2）
        JSONObject message = new JSONObject();
        message.put("type", 2);
        message.put("orderId", id);
        message.put("content", "订单号：" + orders.getNumber());
        webSocketServer.sendToAllClient(message.toJSONString());
    }

    /**
     * 再来一单：按订单明细快照重新加入购物车（快照保证菜品改价后仍按当时内容展示）
     */
    @Override
    public void repetition(Long id) {
        Orders orders = getOwnedOrder(id);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderIds(Collections.singletonList(id));
        if (orderDetailList == null || orderDetailList.isEmpty()) {
            return;
        }
        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
            shoppingCart.setUserId(orders.getUserId());
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * 管理端条件分页查询
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        return new PageResult(page.getTotal(), buildOrderVOs(page.getResult()));
    }

    /**
     * 管理端订单各状态数量统计
     */
    @Override
    public OrderStatisticsVO statistics() {
        return orderMapper.statistics();
    }

    /**
     * 管理端订单详情
     */
    @Override
    public OrderVO details(Long id) {
        Orders orders = getOrder(id);
        return buildSingleOrderVO(orders);
    }

    /**
     * 管理端接单：待接单 -> 已接单
     */
    @Override
    @Transactional
    public void adminConfirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = getOrder(ordersConfirmDTO.getId());
        if (!Orders.TO_BE_CONFIRMED.equals(orders.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders update = new Orders();
        update.setId(orders.getId());
        update.setStatus(Orders.CONFIRMED);
        guardUpdate(update, orders.getStatus());
    }

    /**
     * 管理端拒单：仅 待接单 + 已支付 的订单可拒单，退款并取消
     */
    @Override
    @Transactional
    public void adminRejection(OrdersRejectionDTO ordersRejectionDTO) {
        Orders orders = getOrder(ordersRejectionDTO.getId());
        if (!Orders.TO_BE_CONFIRMED.equals(orders.getStatus()) || !Orders.PAID.equals(orders.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        refundIfPaid(orders);

        Orders update = new Orders();
        update.setId(orders.getId());
        update.setStatus(Orders.CANCELLED);
        update.setPayStatus(Orders.REFUND);
        update.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        update.setCancelTime(LocalDateTime.now());
        guardUpdate(update, orders.getStatus());
    }

    /**
     * 管理端取消订单：待接单/已接单/派送中 可取消
     */
    @Override
    @Transactional
    public void adminCancel(OrdersCancelDTO ordersCancelDTO) {
        Orders orders = getOrder(ordersCancelDTO.getId());
        Integer status = orders.getStatus();
        if (!(Orders.TO_BE_CONFIRMED.equals(status) || Orders.CONFIRMED.equals(status)
                || Orders.DELIVERY_IN_PROGRESS.equals(status))) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders update = new Orders();
        update.setId(orders.getId());
        update.setStatus(Orders.CANCELLED);
        update.setCancelReason(ordersCancelDTO.getCancelReason());
        update.setCancelTime(LocalDateTime.now());
        if (Orders.PAID.equals(orders.getPayStatus())) {
            refundIfPaid(orders);
            update.setPayStatus(Orders.REFUND);
        }
        guardUpdate(update, status);
    }

    /**
     * 管理端派送：已接单 -> 派送中
     */
    @Override
    @Transactional
    public void delivery(Long id) {
        Orders orders = getOrder(id);
        if (!Orders.CONFIRMED.equals(orders.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders update = new Orders();
        update.setId(orders.getId());
        update.setStatus(Orders.DELIVERY_IN_PROGRESS);
        guardUpdate(update, orders.getStatus());
    }

    /**
     * 管理端完成：派送中 -> 已完成。
     * checkoutTime 为 null 时回填（保证统计口径 status=5 and checkout_time in 范围 覆盖所有完成订单）
     */
    @Override
    @Transactional
    public void complete(Long id) {
        Orders orders = getOrder(id);
        if (!Orders.DELIVERY_IN_PROGRESS.equals(orders.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders update = new Orders();
        update.setId(orders.getId());
        update.setStatus(Orders.COMPLETED);
        update.setDeliveryTime(LocalDateTime.now());
        if (orders.getCheckoutTime() == null) {
            update.setCheckoutTime(LocalDateTime.now());
        }
        guardUpdate(update, orders.getStatus());
    }

    // ==================== 私有方法 ====================

    /**
     * 查询订单，不存在则抛业务异常
     */
    private Orders getOrder(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return orders;
    }

    /**
     * 查询订单并校验归属当前用户
     */
    private Orders getOwnedOrder(Long id) {
        Orders orders = getOrder(id);
        if (!orders.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_USER_MISMATCH);
        }
        return orders;
    }

    /**
     * 已支付订单发起退款（mock 网关仅记录日志）
     */
    private void refundIfPaid(Orders orders) {
        if (Orders.PAID.equals(orders.getPayStatus())) {
            try {
                paymentGateway.refund(orders);
            } catch (Exception e) {
                log.error("退款失败：orderNumber={}", orders.getNumber(), e);
                throw new OrderBusinessException(MessageConstant.REFUND_FAILED);
            }
        }
    }

    /**
     * 带期望状态的更新，影响行数不为 1 说明状态已被并发修改，抛业务异常
     */
    private void guardUpdate(Orders update, Integer expectStatus) {
        int rows = orderMapper.updateWithGuard(update, expectStatus);
        if (rows != 1) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 订单列表装配 VO：批量查明细（一次 in 查询避免 N+1），orderDishes 拼接为 "名称*数量;" 格式与前端约定一致
     */
    private List<OrderVO> buildOrderVOs(List<Orders> ordersList) {
        List<OrderVO> orderVOList = new ArrayList<>();
        if (ordersList == null || ordersList.isEmpty()) {
            return orderVOList;
        }
        List<Long> orderIds = ordersList.stream().map(Orders::getId).collect(Collectors.toList());
        List<OrderDetail> allDetails = orderDetailMapper.getByOrderIds(orderIds);
        Map<Long, List<OrderDetail>> detailMap = allDetails.stream()
                .collect(Collectors.groupingBy(OrderDetail::getOrderId));

        for (Orders orders : ordersList) {
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);
            List<OrderDetail> details = detailMap.getOrDefault(orders.getId(), new ArrayList<>());
            orderVO.setOrderDetailList(details);
            orderVO.setOrderDishes(details.stream()
                    .map(d -> d.getName() + "*" + d.getNumber() + ";")
                    .collect(Collectors.joining()));
            orderVOList.add(orderVO);
        }
        return orderVOList;
    }

    private OrderVO buildSingleOrderVO(Orders orders) {
        return buildOrderVOs(Collections.singletonList(orders)).get(0);
    }
}
