package com.sitech.prodai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sitech.prodai.domain.entity.ChatMessageMetadata;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMessageMetadataMapper extends BaseMapper<ChatMessageMetadata> {

    /** 按消息 ID 删除全部 KV（对齐原 deleteByMessageId） */
    @Delete("DELETE FROM pd_ai_chat_message_metadata WHERE message_id = #{messageId}")
    int deleteByMessageId(@Param("messageId") String messageId);
}
