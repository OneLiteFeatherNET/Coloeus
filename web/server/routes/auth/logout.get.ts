import { empty } from '../../lib/http'
import { sendCore } from '../../utils/bff'

// Logout is a POST with the CSRF token; a GET never changes anything.
export default defineEventHandler(event => sendCore(event, empty(405, { Allow: 'POST' })))
