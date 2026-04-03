package com.sky.mapper;

import com.sky.entity.SetmealDish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SetmealDishMapper {

    /**
     * 根据菜品id查询套餐id
     * @param dishIds
     * @return
     */
    List<Long> getSetmealIdsByDishIds(List<Long> dishIds);

    /**
     * 向套餐菜品关系表批量插入数据
     * @param setmealDishes
     */
    void insertBatch(List<SetmealDish> setmealDishes);

    /**
     * 根据套餐id批量删除数据
     * @param ids
     */
    void deleteBatchBySetmealIds(List<Long> ids);

    /**
     * 根据套餐id获得套餐里的所有菜品数据
     * @param SetmealId
     * @return
     */
    @Select("select * from setmeal_dish where setmeal_id = #{SetmealId}")
    List<SetmealDish> getSetmealDishBySetmealId(Long SetmealId);
}
