package com.sitech.prodai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sitech.prodai.domain.entity.McpCallLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface McpCallLogMapper extends BaseMapper<McpCallLog> {

    /** 清理指定时间之前的调用日志 */
    @Delete("DELETE FROM pd_ai_mcp_call_logs WHERE timestamp &lt; #{before}")
    int deleteByTimestampBefore(@Param("before") java.time.LocalDateTime before);
}
