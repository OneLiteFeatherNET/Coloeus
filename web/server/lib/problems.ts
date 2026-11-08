import { problem, type CoreResponse } from './http'

// The problem codes the BFF answers itself (openspec problem-details). Titles and statuses match docs/problems.md.

export function sessionRequired(): CoreResponse {
  return problem(401, 'SESSION_REQUIRED', 'Staff session is required', 'There is no valid staff session. Sign in again.')
}

export function csrfInvalid(): CoreResponse {
  return problem(403, 'CSRF_INVALID', 'CSRF check failed', 'The X-CSRF-Token header or the Origin header is missing or does not match.')
}

export function notFound(): CoreResponse {
  return problem(404, 'NOT_FOUND', 'Endpoint is not found', 'No endpoint of the staff API has this method and path.')
}

export function forbiddenRole(): CoreResponse {
  return problem(403, 'FORBIDDEN_ROLE', 'Required role is missing', 'The session lacks the role this endpoint requires.')
}

export function requestInvalid(detail: string): CoreResponse {
  return problem(400, 'REQUEST_INVALID', 'Request is invalid', detail)
}

export function internalError(): CoreResponse {
  return problem(500, 'INTERNAL', 'Internal error', 'The request could not be completed. Try again later.')
}
