package com.campus.mapper;

import com.campus.entity.Orders;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper {

    void insert(Orders orders);

    /**
     * 将未支付且处于待付款状态的订单更新为取消状态，返回影响行数（用于幂等判断）
     */
    int updateStatusToCancelledIfUnpaid(@org.apache.ibatis.annotations.Param("id") Long id,
                                       @org.apache.ibatis.annotations.Param("cancelTime") java.time.LocalDateTime cancelTime);
}
