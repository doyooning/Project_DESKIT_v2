package com.deskit.deskit.setup.entity;

import com.deskit.deskit.tag.entity.Tag;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "setup_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SetupTag {

  @Embeddable
  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class SetupTagId implements Serializable {

    @Column(name = "setup_id", nullable = false)
    private Long setupId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    public SetupTagId(Long setupId, Long tagId) {
      this.setupId = setupId;
      this.tagId = tagId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      SetupTagId that = (SetupTagId) o;
      return Objects.equals(setupId, that.setupId)
          && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(setupId, tagId);
    }
  }

  @EmbeddedId
  private SetupTagId id;

  @MapsId("setupId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "setup_id", nullable = false)
  private Setup setup;

  @MapsId("tagId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tag_id", nullable = false)
  private Tag tag;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public SetupTag(Setup setup, Tag tag) {
    this.setup = setup;
    this.tag = tag;
  }
}
