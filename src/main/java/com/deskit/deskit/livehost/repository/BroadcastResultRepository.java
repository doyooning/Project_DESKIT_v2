package com.deskit.deskit.livehost.repository;

import com.deskit.deskit.livehost.entity.BroadcastResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastResultRepository extends JpaRepository<BroadcastResult, Long>, BroadcastResultRepositoryCustom {
}
