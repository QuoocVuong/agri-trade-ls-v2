package com.yourcompany.agritrade.catalog.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "categories",
    uniqueConstraints = {@UniqueConstraint(columnNames = "slug")})
@Getter
@Setter
@NoArgsConstructor
public class Category {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, unique = true, length = 120)
  private String slug;

  @Lob private String description;

  @Transient // <<< Đánh dấu Transient
  private String imageUrl;

  @Column(length = 1024) // <<< Thêm cột blobPath, cho phép NULL
  private String blobPath;

  @ManyToOne(fetch = FetchType.LAZY) // Quan hệ cha
  @JoinColumn(name = "parent_id")
  private Category parent;

  @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true) // Quan hệ con
  private Set<Category> children = new HashSet<>();

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  // Helper methods để quản lý quan hệ hai chiều (tùy chọn)
  public void addChild(Category child) {
    children.add(child);
    child.setParent(this);
  }

  public void removeChild(Category child) {
    children.remove(child);
    child.setParent(null);
  }
}
