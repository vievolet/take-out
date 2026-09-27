package com.campus.controller.admin;

import com.campus.dto.DataOverViewQueryDTO;
import com.campus.result.Result;
import com.campus.service.WorkspaceService;
import com.campus.vo.BusinessDataVO;
import com.campus.vo.DishOverViewVO;
import com.campus.vo.OrderOverViewVO;
import com.campus.vo.SetmealOverViewVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 管理端工作台
 */
@RestController
@RequestMapping("/admin/workspace")
@Api(tags = "工作台相关接口")
@Slf4j
public class WorkspaceController {

    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 今日运营数据（未传时间默认查询当天）
     * @param dataOverViewQueryDTO
     * @return
     */
    @GetMapping("/businessData")
    @ApiOperation("查询今日运营数据")
    public Result<BusinessDataVO> businessData(DataOverViewQueryDTO dataOverViewQueryDTO){
        LocalDateTime begin = dataOverViewQueryDTO.getBegin();
        LocalDateTime end = dataOverViewQueryDTO.getEnd();
        if (begin == null) {
            begin = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        }
        if (end == null) {
            end = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        }
        return Result.success(workspaceService.getBusinessData(begin, end));
    }

    /**
     * 菜品总览
     * @return
     */
    @GetMapping("/overviewDishes")
    @ApiOperation("查询菜品总览")
    public Result<DishOverViewVO> dishOverview(){
        return Result.success(workspaceService.getDishOverView());
    }

    /**
     * 套餐总览
     * @return
     */
    @GetMapping("/overviewSetmeals")
    @ApiOperation("查询套餐总览")
    public Result<SetmealOverViewVO> setmealOverview(){
        return Result.success(workspaceService.getSetmealOverView());
    }

    /**
     * 订单概览
     * @return
     */
    @GetMapping("/overviewOrders")
    @ApiOperation("查询订单概览")
    public Result<OrderOverViewVO> orderOverview(){
        return Result.success(workspaceService.getOrderOverView());
    }
}
