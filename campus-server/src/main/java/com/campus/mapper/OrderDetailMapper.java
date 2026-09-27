package com.campus.mapper;

import com.campus.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderDetailMapper {
    /**
     * 批量插入订单详情
     *
     * @param orderDetailList 订单详情列表
     */
    void insertBatch(List<OrderDetail> orderDetailList);

    /**
     * 根据订单id集合批量查询明细（配合订单分页结果装配，避免逐单查询的 N+1）
     *
     * @param orderIds 订单id列表
     * @return
     */
    List<OrderDetail> getByOrderIds(List<Long> orderIds);
}
