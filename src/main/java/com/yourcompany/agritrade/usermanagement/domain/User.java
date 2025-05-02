package com.yourcompany.agritrade.usermanagement.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "phoneNumber")
})
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
@SQLDelete(sql = "UPDATE users SET is_deleted = true WHERE id = ?") // Định nghĩa lệnh xóa mềm
@Where(clause = "is_deleted = false") // Tự động lọc các bản ghi chưa bị xóa
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash; // Đổi tên từ password thành passwordHash

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(unique = true, length = 20)
    private String phoneNumber;

    private String avatarUrl;

//    @Column(nullable = false)
//    private boolean isActive = true;



    // *** Thêm các trường count ***
    @Column(nullable = false)
    private Integer followerCount = 0; // Số người theo dõi user này

    @Column(nullable = false)
    private Integer followingCount = 0; // Số người user này đang theo dõi

    @ManyToMany(fetch = FetchType.EAGER) // EAGER để load roles cùng user khi xác thực
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    // ****** THÊM PHẦN NÀY ******
    @OneToOne(
            mappedBy = "user", // Quan trọng: Chỉ định rằng mối quan hệ được quản lý bởi trường 'user' trong FarmerProfile
            cascade = CascadeType.ALL, // Thường dùng ALL để khi xóa User thì Profile cũng bị xóa
            fetch = FetchType.LAZY,    // LAZY là tốt nhất để tránh load không cần thiết
            orphanRemoval = true     // Xóa Profile nếu nó không còn được tham chiếu bởi User nào
    )
    private FarmerProfile farmerProfile; // Tên trường để truy cập profile từ User
    // ****************************

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @Column(nullable = false)
    private boolean isActive = false; // Đổi default thành false

    // Thêm trường mới
    @Column(length = 100, unique = true)
    private String verificationToken;

    private LocalDateTime verificationTokenExpiry;

    // Constructors, other fields (verification code etc.) can be added later
}