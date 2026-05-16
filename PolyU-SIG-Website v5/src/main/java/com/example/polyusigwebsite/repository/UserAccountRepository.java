package com.example.polyusigwebsite.repository;

import com.example.polyusigwebsite.entity.UserAccount;
import com.example.polyusigwebsite.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByEmail(String email);

    List<UserAccount> findByStatusOrderByCreatedAtAsc(UserStatus status);
}
