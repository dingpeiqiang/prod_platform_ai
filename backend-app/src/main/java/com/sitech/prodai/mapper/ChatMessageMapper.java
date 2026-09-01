package com.sitech.prodai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sitech.prodai.domain.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /** 各会话消息数（sessionId → count），对齐原 countBySessionIdIn 聚合查询 */
    @Select("""
            <script>
            SELECT session_id AS sessionId, COUNT(*) AS cnt
            FROM pd_ai_chat_messages
            WHERE session_id IN
            <foreach item="id" collection="sessionIds" open="(" separator="," close=")">
                #{id}
            </foreach>
            GROUP BY session_id
            </script>
            """)
    List<java.util.Map<String, Object>> countGroupBySessionId(@Param("sessionIds") List<String> sessionIds);

    /** 会话内最大排序号（无记录返回 0） */
    @Select("SELECT COALESCE(MAX(sort_order), 0) FROM pd_ai_chat_messages WHERE session_id = #{sessionId}")
    Integer findMaxSortOrderBySessionId(@Param("sessionId") String sessionId);
}
