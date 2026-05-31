package com.campus.mapper;

import com.campus.entity.Orders;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper {

    void insert(Orders orders);
}
