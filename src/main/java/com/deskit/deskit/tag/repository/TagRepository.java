package com.deskit.deskit.tag.repository;

import com.deskit.deskit.tag.dto.TagListResponse;
import com.deskit.deskit.tag.entity.Tag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TagRepository extends JpaRepository<Tag, Long> {
  List<Tag> findByDeletedAtIsNullOrderByTagNameAsc();

  @Query("""
    select t from Tag t
    left join t.tagCategory c
    where t.deletedAt is null
    order by
      case when c.tagCategoryName is null then 1 else 0 end,
      c.tagCategoryName asc,
      c.id asc,
      t.tagName asc
  """)
  List<Tag> findActiveTagsOrderByCategoryAndName();

  @Query("""
    select new com.deskit.deskit.tag.dto.TagListResponse(
      t.id,
      t.tagName,
      tc.id,
      tc.tagCategoryName
    )
    from Tag t
    left join t.tagCategory tc
    where t.deletedAt is null
    order by
      case when tc.tagCategoryName is null then 1 else 0 end,
      tc.tagCategoryName asc,
      tc.id asc,
      t.tagName asc
  """)
  List<TagListResponse> findActiveTagResponsesOrderByCategoryAndName();
}
