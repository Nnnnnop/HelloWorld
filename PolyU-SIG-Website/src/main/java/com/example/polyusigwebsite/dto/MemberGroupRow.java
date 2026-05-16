package com.example.polyusigwebsite.dto;

import java.time.LocalDateTime;

/** One SIG group row for admin member lookup, including DB id for removal. */
public record MemberGroupRow(Long membershipId, Long groupId, String groupName, LocalDateTime joinedAt) {}
