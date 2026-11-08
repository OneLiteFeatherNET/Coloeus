import type { Me } from '~/types/api'
import { createStaffApi } from '~/utils/staffApi'
import { validReturnPath } from '#shared/utils/returnPath'

/**
 * Session state of the staff UI, shared by all pages of one page load. The `me` answer is read once per page load;
 * its roles decide which write controls are enabled. A call answered 401 SESSION_REQUIRED sends the user to the
 * login page with the current path as the return path.
 */
export function useStaff() {
  const me = useState<Me | null>('staff-me', () => null)
  const announcement = useState<string>('staff-announcement', () => '')

  const api = createStaffApi({
    fetch: (url, init) => globalThis.fetch(url, init),
    csrfToken: () => me.value?.csrfToken,
    onSessionRequired: () => {
      me.value = null
      if (window.location.pathname !== '/admin/anmelden') {
        const returnTo = validReturnPath(`${window.location.pathname}${window.location.search}`)
        window.location.assign(`/admin/anmelden?returnTo=${encodeURIComponent(returnTo)}&expired=1`)
      }
    }
  })

  const canWrite = computed(() => me.value?.canWrite ?? false)

  async function ensureMe(): Promise<Me> {
    if (me.value) {
      return me.value
    }
    const loaded = await api.me()
    me.value = loaded
    return loaded
  }

  function announce(message: string) {
    announcement.value = message
  }

  async function logout() {
    const answer = await api.logout()
    me.value = null
    window.location.assign(answer.logoutUrl)
  }

  return { me, canWrite, api, ensureMe, announce, announcement, logout }
}
