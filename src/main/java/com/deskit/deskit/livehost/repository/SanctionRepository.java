package com.deskit.deskit.livehost.repository;

import com.deskit.deskit.livehost.common.enums.SanctionType;
import com.deskit.deskit.livehost.entity.Broadcast;
import com.deskit.deskit.livehost.entity.Sanction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface SanctionRepository extends JpaRepository<Sanction, Long>, SanctionRepositoryCustom {
    Integer countByBroadcast(Broadcast broadcast);

    int countByBroadcastAndStatusIn(Broadcast broadcast, Collection<SanctionType> statuses);
}
