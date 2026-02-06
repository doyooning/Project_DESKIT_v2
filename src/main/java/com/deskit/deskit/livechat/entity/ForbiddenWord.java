package com.deskit.deskit.livechat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "forbidden_word")
@Getter
@NoArgsConstructor
public class ForbiddenWord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "word_id")
    private Integer wordId;

    @Column(length = 50, nullable = false)
    private String word;
}