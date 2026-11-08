import { notFound } from '../../lib/problems'
import { sendCore } from '../../utils/bff'

// Any other path under /admin/auth is not a page: it is answered as unknown, never with the application shell.
export default defineEventHandler(event => sendCore(event, notFound()))
