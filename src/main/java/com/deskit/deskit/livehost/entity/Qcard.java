package com.deskit.deskit.livehost.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "qcard")
public class Qcard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "qcard_id")
    private Long qcardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcast_id", nullable = false)
    private Broadcast broadcast;

    @Column(name = "qcard_question", length = 50, nullable = false)
    private String qcardQuestion;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
