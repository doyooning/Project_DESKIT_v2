package com.deskit.deskit.setup.repository;

import com.deskit.deskit.setup.entity.SetupTag;
import com.deskit.deskit.setup.entity.SetupTag.SetupTagId;
import com.deskit.deskit.tag.entity.TagCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SetupTagRepository extends JpaRepository<SetupTag, SetupTagId> {

  interface SetupTagRow {
    Long getSetupId();
    TagCategory.TagCode getTagCode();
    String getTagName();
  }

  @Query("""
      select st.setup.id as setupId,
             tc.tagCode as tagCode,
             t.tagName as tagName
        from SetupTag st
        join st.tag t
        join t.tagCategory tc
       where st.deletedAt is null
         and t.deletedAt is null
         and tc.deletedAt is null
         and st.setup.id in :setupIds
       order by st.setup.id, tc.tagCode, t.tagName
      """)
  List<SetupTagRow> findActiveTagsBySetupIds(@Param("setupIds") List<Long> setupIds);
}
