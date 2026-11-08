import { getMethod, getRequestHeaders, getRequestURL, readRawBody, setResponseHeaders, setResponseStatus, type H3Event } from 'h3'
import type { CoreRequest, CoreResponse } from '../lib/http'

// The Nitro adapter of the BFF: it turns an h3 event into the framework-free request of server/lib and writes the
// answer back. The routes under server/routes contain no rule of their own.

export function toCoreRequest(event: H3Event): CoreRequest {
  const headers = new Headers()
  for (const [name, value] of Object.entries(getRequestHeaders(event))) {
    if (typeof value === 'string') {
      headers.set(name, value)
    }
  }
  return {
    method: getMethod(event),
    url: getRequestURL(event),
    headers,
    readBody: async () => (await readRawBody(event, 'utf8')) ?? ''
  }
}

export function sendCore(event: H3Event, response: CoreResponse): string {
  setResponseStatus(event, response.status)
  const headers: Record<string, string> = {}
  response.headers.forEach((value, name) => {
    headers[name] = value
  })
  setResponseHeaders(event, headers)
  return response.body
}
