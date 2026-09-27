package com.campus.mapper;

import com.campus.entity.AiRecommendLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiRecommendLogMapper {

    /**
     * 插入推荐日志
     * @param aiRecommendLog
     */
    void insert(AiRecommendLog aiRecommendLog);
}
