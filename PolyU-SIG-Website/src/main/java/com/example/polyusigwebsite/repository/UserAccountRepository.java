package com.example.polyusigwebsite.repository;

import com.example.polyusigwebsite.entity.MemberSiteTier;
import com.example.polyusigwebsite.entity.RoleType;
import com.example.polyusigwebsite.entity.UserAccount;
import com.example.polyusigwebsite.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    long countByRole(RoleType role);

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    List<UserAccount> findTop10ByUsernameContainingIgnoreCaseOrderByUsernameAsc(String username);

    /** Bordered list for admin “members by site tier” lookup (cap size in service if needed). */
    List<UserAccount> findTop200ByRoleAndMemberSiteTierOrderByUsernameAsc(RoleType role, MemberSiteTier memberSiteTier);

    List<UserAccount> findByStatusOrderByCreatedAtAsc(UserStatus status);

    Optional<UserAccount> findByProviderUserId(String providerUserId);
}
