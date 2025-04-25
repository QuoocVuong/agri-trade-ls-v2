package com.yourcompany.agritrade.usermanagement.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.Objects;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 100, unique = true, nullable = false)
    private String name; // Ví dụ: "USER_READ", "USER_MANAGE", "PRODUCT_CREATE", "ORDER_APPROVE"

    @Column(length = 255)
    private String description;

    public Permission(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // equals and hashCode based on name recommended
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Permission that = (Permission) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}