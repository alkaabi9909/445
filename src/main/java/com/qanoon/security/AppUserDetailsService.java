package com.qanoon.security;

import com.qanoon.domain.User;
import com.qanoon.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * تحميل المستخدم لسياق الأمان.
 * كل صلاحية دقيقة تُمنح كما هي (بدون بادئة ROLE_)، ويُضاف "ROLE_" + رمز الدور.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String name = username == null ? "" : username.trim();
        User user = userRepository.findByUsername(name)
                .orElseThrow(() -> new UsernameNotFoundException("اسم المستخدم أو كلمة المرور غير صحيحة"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authoritiesOf(user))
                .accountLocked(user.isLocked())
                .disabled(!user.isActive())
                .accountExpired(false)
                .credentialsExpired(false)
                .build();
    }

    /** صلاحيات المستخدم: نص الصلاحية كما هو + ROLE_ + رمز الدور. */
    public List<GrantedAuthority> authoritiesOf(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (user.getRole() != null) {
            for (String permission : user.getRole().getPermissions()) {
                if (permission != null && !permission.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(permission.trim()));
                }
            }
            String code = user.getRole().getCode();
            if (code != null && !code.isBlank()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + code.trim()));
            }
        }
        return authorities;
    }
}
