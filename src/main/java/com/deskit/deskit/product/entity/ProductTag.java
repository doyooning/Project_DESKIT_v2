package com.deskit.deskit.product.entity;

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
@Table(name = "product_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductTag {

  @Embeddable
  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class ProductTagId implements Serializable {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    public ProductTagId(Long productId, Long tagId) {
      this.productId = productId;
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
      ProductTagId that = (ProductTagId) o;
      return Objects.equals(productId, that.productId)
          && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(productId, tagId);
    }
  }

  @EmbeddedId
  private ProductTagId id;

  @MapsId("productId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @MapsId("tagId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tag_id", nullable = false)
  private Tag tag;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public ProductTag(Product product, Tag tag) {
    this.product = product;
    this.tag = tag;
  }

  public void setId(ProductTagId id) {
    this.id = id;
  }
}
