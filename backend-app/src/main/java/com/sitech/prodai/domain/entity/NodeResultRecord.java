package com.sitech.prodai.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 节点结果存储记录（产销品加载 V1.6 · save_node_result / query_node_result 后端持久化表）。
 * <p>
 * 双键形态兼容：
 * <ul>
 *   <li>legacy：req_id + node_name（req_id=需求单号，node_name=requirement/config/spec/fee/test）</li>
 *   <li>V1.6：key（plan_id 或 EXEC{execution_id}_STAGE{n}）</li>
 * </ul>
 * 同键覆盖（重跑环节仅保留最新一条），result_json 透传存储不做格式解析。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pd_ai_node_results")
public class NodeResultRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 记录ID：REC + yyyyMMdd + 6位序号 */
    @TableField("record_id")
    private String recordId;

    /** 需求单号（legacy 查询键，V1.6 场景存 execution_id） */
    @TableField("req_id")
    private String reqId;

    /** 环节名：requirement/config/spec/fee/test（legacy 查询键，V1.6 场景留空） */
    @TableField("node_name")
    private String nodeName;

    /** V1.6 key：plan_id 或 EXEC{execution_id}_STAGE{n}（legacy 场景留空） */
    @TableField("result_key")
    private String resultKey;

    /** 环节结果 JSON 原文（透传存储，≤64KB） */
    @TableField("result_json")
    private String resultJson;

    /** 环节状态：ok（默认）/ 失败原因码，统一小写 */
    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
