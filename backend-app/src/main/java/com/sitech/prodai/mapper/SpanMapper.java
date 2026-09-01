package com.sitech.prodai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sitech.prodai.domain.entity.Span;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SpanMapper extends BaseMapper<Span> {

    /** 删除指定 Trace 的全部 Span */
    @Delete("DELETE FROM pd_ai_spans WHERE trace_id = #{traceId}")
    int deleteByTraceId(@Param("traceId") String traceId);
}
