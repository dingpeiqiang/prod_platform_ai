package com.sitech.prodai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sitech.prodai.domain.entity.WorkflowExecution;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecution> {

    /** 删除指定工作流的全部执行记录 */
    @Delete("DELETE FROM pd_ai_workflow_executions WHERE workflow_id = #{workflowId}")
    int deleteByWorkflowId(@Param("workflowId") Integer workflowId);
}
