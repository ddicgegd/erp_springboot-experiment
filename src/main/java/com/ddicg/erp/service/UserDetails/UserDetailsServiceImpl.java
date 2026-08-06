package com.ddicg.erp.service.UserDetails;

import com.ddicg.erp.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var userOptional = username.contains("@") 
                ? userRepository.findByEmail(username)
                : userRepository.findByName(username);
                
        var user = userOptional.orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng: " + username));

        Collection<GrantedAuthority> authorities = user.getRoles().stream()
                .map(roleType -> new SimpleGrantedAuthority("ROLE_" + roleType.name()))
                .collect(Collectors.toList());

        return new CustomUserDetails(user, authorities);

    }
}
