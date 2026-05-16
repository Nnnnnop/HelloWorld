package com.example.polyusigwebsite.entity;

public enum ResourceVisibility {
    HIDDEN,
    /** Shown as "Public" in UI: any visitor (including not logged in). */
    L1,
    /** Shown as "Member: Level1": approved MEMBER (tier L1 or L2) and ADMIN; site role STUDENT alone (not yet MEMBER) does not qualify. */
    L2,
    /** Shown as "Member: Level2": ADMIN or approved MEMBER with site tier L2 ({@link com.example.polyusigwebsite.entity.MemberSiteTier#L2}). */
    L3
}
