package com.deskit.deskit.livehost.repository;

import com.deskit.deskit.livehost.entity.Broadcast;
import com.deskit.deskit.livehost.entity.Vod;
import com.deskit.deskit.livehost.common.enums.VodStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VodRepository extends JpaRepository<Vod, Long> {
    Optional<Vod> findByBroadcast(Broadcast broadcast);

    List<Vod> findByStatusNotAndCreatedAtBefore(VodStatus status, LocalDateTime threshold);
}
