package com.deskit.deskit.tag.repository;

import com.deskit.deskit.tag.entity.TagCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagCategoryRepository extends JpaRepository<TagCategory, Long> {
}
