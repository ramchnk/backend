package com.globalisor.backend.security;

import com.globalisor.backend.model.User;
import com.globalisor.backend.repository.UserRepository;
import com.globalisor.backend.security.EncryptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Autowired
    UserRepository userRepository;

    @Autowired
    EncryptionUtils encryptionUtils;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String encryptedEmail = encryptionUtils != null ? encryptionUtils.encryptQueryable(email) : null;
        User user = null;
        if (encryptedEmail != null) {
            user = userRepository.findByEmail(encryptedEmail).orElse(null);
        }
        if (user == null) {
            user = userRepository.findByEmailIgnoreCase(email)
                    .orElseGet(() -> userRepository.findById(email).orElse(null));
        }

        if (user == null) {
            throw new UsernameNotFoundException("User Not Found with email/ID: " + email);
        }

        return UserDetailsImpl.build(user);
    }
}
