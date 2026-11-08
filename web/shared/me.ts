// The identity of the browser session, answered by GET /admin/api/me. Shared by the BFF and the UI; the only shape
// that exists on the browser path alone (see openspec staff-api, "JSON shapes").

export const STAFF_ROLES = ['Coloeus.Moderator', 'Coloeus.Admin'] as const

export type StaffRole = (typeof STAFF_ROLES)[number]

export interface Me {
  oid: string
  name: string
  roles: StaffRole[]
  canWrite: boolean
  csrfToken: string
  sessionExpiresAt: string
  idleExpiresAt: string
}

/** The staff roles of a role list, in the order of STAFF_ROLES. Other roles are ignored. */
export function staffRoles(roles: readonly string[]): StaffRole[] {
  return STAFF_ROLES.filter(role => roles.includes(role))
}

export function canWrite(roles: readonly string[]): boolean {
  return roles.includes('Coloeus.Admin')
}
