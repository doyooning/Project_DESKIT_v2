package com.deskit.deskit.tag.dto;

import com.deskit.deskit.tag.entity.Tag;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public record TagListResponse(
  @JsonProperty("tag_id")
  Long tagId,

  @JsonProperty("tag_name")
  String tagName,

  @JsonProperty("tag_category_id")
  Long tagCategoryId,

  @JsonProperty("tag_category_name")
  String tagCategoryName
) {
  public static TagListResponse from(Tag tag) {
    Objects.requireNonNull(tag, "tag must not be null");
    var category = tag.getTagCategory();
    Long categoryId = category != null ? category.getId() : null;
    String categoryName = category != null ? category.getTagCategoryName() : null;
    return new TagListResponse(tag.getId(), tag.getTagName(), categoryId, categoryName);
  }
}
