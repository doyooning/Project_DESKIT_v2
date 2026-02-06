package com.deskit.deskit.livehost.repository;

import com.deskit.deskit.livehost.entity.Broadcast;
import com.deskit.deskit.livehost.entity.Qcard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QcardRepository extends JpaRepository<Qcard, Long> {
    void deleteByBroadcast(Broadcast broadcast);
}
