import { json } from '../lib/http'
import { sendCore } from '../utils/bff'

// Readiness probe: answers without a session and without a call to Dragonfly or the backend.
export default defineEventHandler(event => sendCore(event, json(200, { status: 'ok' })))
