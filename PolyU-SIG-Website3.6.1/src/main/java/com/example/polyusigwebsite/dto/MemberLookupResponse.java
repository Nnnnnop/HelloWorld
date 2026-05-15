package com.example.polyusigwebsite.dto;

import java.util.List;

public record MemberLookupResponse(List<MembershipLookupResult> matches) {}
