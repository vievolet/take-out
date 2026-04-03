package com.sky.controller.admin;

import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/setmeal")
@Api(tags = "套餐相关接口")
@Slf4j
public class SetmealController {
    @Autowired
    private SetmealService setmealService;

    /**
     * 新增套餐
     * @return
     */
    @PostMapping
    @ApiOperation("新增套餐")
    public Result save(@RequestBody SetmealDTO setmealDTO){
        log.info("新增套餐：{}",setmealDTO);
        setmealService.saveWithSetmealAndDish(setmealDTO);
        return Result.success();
    }

    /**
     * 分页查询套餐
     * @param setmealPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("分页查询套餐")
    public Result<PageResult> page(SetmealPageQueryDTO setmealPageQueryDTO){
        log.info("分页查询套餐：{}",setmealPageQueryDTO);
        PageResult pageResult = setmealService.pageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 套餐批量删除
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("套餐批量删除")
    public Result delete(@RequestParam List<Long> ids){
        //因为前端没搞没勾选提示啊
        if(ids == null || ids.isEmpty()){
            return Result.error("请选择要删除的菜品");
        }
        log.info("套餐批量删除：{}",ids);
        setmealService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 根据id查询套餐信息和套餐菜品关系
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询套餐信息和套餐菜品关系")
    public Result<SetmealVO> getById(@PathVariable Long id){
        log.info("根据id查询套餐信息和套餐菜品关系:{}",id);
        SetmealVO setmealVO = setmealService.getByIdWithSetmealDish(id);
        return Result.success(setmealVO);
    }

    /**
     * 修改套餐
     * @param setmealDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改套餐")
    public Result update(@RequestBody SetmealDTO setmealDTO){
        log.info("修改套餐:{}",setmealDTO);
        setmealService.updateWithSetmealDish(setmealDTO);
        return Result.success();
    }

    /**
     * 起售禁售套餐
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("起售禁售套餐")
    public Result startOrStop(@PathVariable Integer status,Long id){
        log.info("起售禁售套餐:{},{}",status,id);
        setmealService.startOrStop(status,id);
        return Result.success();
    }
}
