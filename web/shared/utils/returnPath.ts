// The return path after a login is a path under /admin/ (the backend checks it again). Anything else falls
// back to the start page, so a link cannot send the browser to another site.

export const START_PATH = '/admin/'

export function validReturnPath(candidate: string | null | undefined): string {
  if (!candidate || candidate.length > 512) {
    return START_PATH
  }
  if (!candidate.startsWith('/admin/') || candidate.startsWith('//') || /[\\:]/.test(candidate)) {
    return START_PATH
  }
  return candidate
}
