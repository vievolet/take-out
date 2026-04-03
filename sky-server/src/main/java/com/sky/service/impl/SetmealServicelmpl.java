package com.sky.service.impl;


import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class SetmealServicelmpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;
    /**
     * 新增套餐，同时需要保存套餐和菜品的关联关系
     * @param setmealDTO
     */
    @Override
    @Transactional
    public void saveWithSetmealAndDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        //向套餐表插入数据
        setmealMapper.insert(setmeal);
        //获取生成的套餐id
        Long setmealId = setmeal.getId();

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        setmealDishes.forEach(setmealDish -> {setmealDish.setSetmealId(setmealId);});
        setmealDishMapper.insertBatch(setmealDishes);
    }

    /**
     * 分页查询套餐
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(),setmealPageQueryDTO.getPageSize());
         Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(),page.getResult());
    }

    /**
     * 套餐批量删除
     * @param ids
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
       //判断当前套餐是否能够删除---是否存在起售中的套餐？？
        for (Long id : ids) {
           Setmeal setmeal = setmealMapper.getById(id);
            if(setmeal.getStatus() == StatusConstant.ENABLE){
                //起售中的套餐不能删除
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        //批量删除套餐表中的数据
        setmealMapper.deleteBatchByIds(ids);
        //批量删除套餐菜品关系表中的数据
        setmealDishMapper.deleteBatchBySetmealIds(ids);

    }

    /**
     * 根据id查询套餐信息和套餐菜品关系
     * @return
     */
    @Override
    public SetmealVO getByIdWithSetmealDish(Long id) {
        //根据套餐id获得套餐数据
        Setmeal setmeal = setmealMapper.getById(id);
        //根据套餐id获得套餐里的所有菜品数据
        List<SetmealDish> setmealDishes = setmealDishMapper.getSetmealDishBySetmealId(id);

        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal,setmealVO);
        setmealVO.setSetmealDishes(setmealDishes);
        return setmealVO;
    }

    /**
     * 修改套餐
     * @param setmealDTO
     */
    @Override
    @Transactional
    public void updateWithSetmealDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        //修改套餐表
        setmealMapper.updateSetmeal(setmeal);

        //更新套餐菜品关系表
        //先删掉套餐下的菜品
        //复用前面的删除方法
        List<Long> setmealIds = Collections.singletonList(setmeal.getId());
        setmealDishMapper.deleteBatchBySetmealIds(setmealIds);
        //插入新的菜品
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        setmealDishes.forEach(setmealDish ->{
            setmealDish.setSetmealId(setmealDTO.getId());
        });
        setmealDishMapper.insertBatch(setmealDishes);
    }

    /**
     * 起售禁售套餐
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        //起售套餐时判断套餐下是否有禁售的菜品
        if(status == StatusConstant.ENABLE){
            //根据菜品Id批量查询菜品状态看是否有禁售的菜品
            List<Integer> dishStatusList = dishMapper.getStatusBySetmealId(id);

            if (dishStatusList.contains(StatusConstant.DISABLE)){
                throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
            }
        }

        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.updateSetmeal(setmeal);
    }
}
