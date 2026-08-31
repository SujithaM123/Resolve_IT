package com.resolveit.security;

import com.resolveit.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public CustomUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserByUsername(String email) throws UsernameNotFoundException {
        return appUserRepository.findByEmailIgnoreCase(email)
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
    }
}
