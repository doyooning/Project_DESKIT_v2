package com.deskit.deskit.livechat.repository;

import com.deskit.deskit.livechat.dto.LiveMessageType;
import com.deskit.deskit.livechat.entity.LiveChat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface LiveChatRepository extends JpaRepository<LiveChat, Long> {
    List<LiveChat> findByBroadcastIdOrderByMessageIdAsc(Long broadcastId);

    long countByBroadcastIdAndMsgTypeIn(Long broadcastId, Collection<LiveMessageType> msgTypes);
}
