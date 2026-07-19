package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.ChatMessageMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageMetadataRepository extends JpaRepository<ChatMessageMetadata, Integer> {

    List<ChatMessageMetadata> findByMessageId(String messageId);

    List<ChatMessageMetadata> findByMessageIdIn(List<String> messageIds);

    void deleteByMessageId(String messageId);
}
