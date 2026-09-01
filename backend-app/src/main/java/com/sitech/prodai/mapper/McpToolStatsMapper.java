package com.sitech.prodai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sitech.prodai.domain.entity.McpToolStats;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface McpToolStatsMapper extends BaseMapper<McpToolStats> {

    /** 删除指定工具的统计行 */
    @Delete("DELETE FROM pd_ai_mcp_tool_stats WHERE tool_name = #{toolName}")
    int deleteByToolName(@Param("toolName") String toolName);
}
