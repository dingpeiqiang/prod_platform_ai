package com.sitech.prodai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sitech.prodai.domain.entity.WorkflowHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowHistoryMapper extends BaseMapper<WorkflowHistory> {

    /** 删除指定工作流的全部历史版本 */
    @Delete("DELETE FROM pd_ai_workflow_history WHERE workflow_id = #{workflowId}")
    int deleteByWorkflowId(@Param("workflowId") Integer workflowId);
}
