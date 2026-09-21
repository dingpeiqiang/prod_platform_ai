package com.sitech.prodai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sitech.prodai.domain.entity.ApiRequestHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 接口管理 · 请求历史 Mapper。
 */
@Mapper
public interface ApiRequestHistoryMapper extends BaseMapper<ApiRequestHistory> {

    /** 清理指定时间之前的请求历史。 */
    @Delete("DELETE FROM pd_ai_api_request_history WHERE created_at < #{before}")
    int deleteByCreatedAtBefore(@Param("before") LocalDateTime before);
}
