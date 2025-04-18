package com.yourcompany.agritradels.config.security; // Đặt trong package config.security

import com.yourcompany.agritradels.usermanagement.domain.Role;
import com.yourcompany.agritradels.usermanagement.domain.User;
import com.yourcompany.agritradels.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true) // Chỉ đọc dữ liệu
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Tìm user đang active bằng email
        User user = userRepository.findByEmailAndIsActiveTrue(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found with email: " + email));

        // Lấy cả Roles và Permissions làm Authorities
        Collection<? extends GrantedAuthority> authorities = mapRolesAndPermissionsToAuthorities(user.getRoles());

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                user.isActive(), // Đảm bảo user active
                true, // accountNonExpired
                true, // credentialsNonExpired
                true, // accountNonLocked
                authorities); // Truyền authorities đã bao gồm cả permissions
    }

    // Đổi tên hàm và logic để lấy cả role và permission
    private Collection<? extends GrantedAuthority> mapRolesAndPermissionsToAuthorities(Set<Role> roles) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        if (roles == null) {
            return authorities;
        }
        for (Role role : roles) {
            // Thêm chính tên Role (ví dụ: "ROLE_ADMIN")
            authorities.add(new SimpleGrantedAuthority(role.getName().name()));
            // Thêm các Permissions của Role đó (ví dụ: "USER_READ_ALL")
            if (role.getPermissions() != null) {
                role.getPermissions().forEach(permission ->
                        authorities.add(new SimpleGrantedAuthority(permission.getName()))
                );
            }
        }
        return authorities;
    }
}