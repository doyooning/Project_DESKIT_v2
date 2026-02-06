package com.deskit.deskit.livechat.repository;

import com.deskit.deskit.livechat.entity.ForbiddenWord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForbiddenWordRepository extends JpaRepository<ForbiddenWord, Integer> {
}
