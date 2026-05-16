/** Display labels for ResourceVisibility (backend enum strings). */
export function visibilityLabel(visibility) {
  if (visibility === 'L1') return 'Public'
  if (visibility === 'L2') return 'Member: Level1'
  if (visibility === 'L3') return 'Member: Level2'
  if (visibility === 'HIDDEN') return 'HIDDEN'
  return visibility || '-'
}

/** Message if the user cannot open preview/detail for this visibility; otherwise null. */
export function previewBlockedReason(authStore, visibility) {
  const u = authStore.user
  const approved = u?.status === 'APPROVED'
  const memberRole = u?.role === 'MEMBER'

  if (visibility === 'L2') {
    if (authStore.isAdmin || (approved && memberRole)) return null
    return 'This resource is Member: Level1 (approved SIG members and administrators only).'
  }
  if (visibility === 'L3') {
    const l2Tier = approved && memberRole && u?.memberSiteTier === 'L2'
    if (authStore.isAdmin || l2Tier) return null
    return 'This resource is Member: Level2 (Level 2 members and administrators only).'
  }
  return null
}
