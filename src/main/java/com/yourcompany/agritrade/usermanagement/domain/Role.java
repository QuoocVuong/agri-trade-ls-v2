package com.yourcompany.agritrade.usermanagement.domain;

import com.yourcompany.agritrade.common.model.RoleType;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, unique = true, nullable = false)
  private RoleType name;

  // Thêm mối quan hệ ManyToMany với Permission
  @ManyToMany(
      fetch = FetchType.EAGER) // Dùng EAGER để load permissions cùng role khi cần lấy authorities
  @JoinTable(
      name = "role_permissions",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  private Set<Permission> permissions = new HashSet<>();

  public Role(RoleType name) {
    this.name = name;
  }

  // equals and hashCode based on name
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Role role = (Role) o;
    return name == role.name;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }
}
