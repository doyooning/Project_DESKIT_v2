package com.deskit.deskit.livehost.repository;

import com.deskit.deskit.livehost.common.enums.BroadcastStatus;
import com.deskit.deskit.livehost.entity.Broadcast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long>, BroadcastRepositoryCustom {
    @Query("SELECT COUNT(b) FROM Broadcast b WHERE b.seller.sellerId = :sellerId AND b.status = :status")
    long countBySellerIdAndStatus(@Param("sellerId") Long sellerId, @Param("status") BroadcastStatus status);

    List<Broadcast> findByStatusAndStartedAtBefore(BroadcastStatus status, LocalDateTime threshold);

    @Query("""
            SELECT DISTINCT b
            FROM Broadcast b
            LEFT JOIN Vod v ON v.broadcast = b
            LEFT JOIN BroadcastResult br ON br.broadcast = b
            WHERE b.status IN :statuses
              AND (v IS NULL OR br IS NULL)
            """)
    List<Broadcast> findMissingVodOrResultByStatus(@Param("statuses") List<BroadcastStatus> statuses);
}
