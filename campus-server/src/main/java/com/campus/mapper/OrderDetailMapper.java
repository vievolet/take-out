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
}
