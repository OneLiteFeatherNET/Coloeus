import { handleApi } from '../../lib/api'
import { runtimeDeps } from '../../lib/runtime'
import { sendCore, toCoreRequest } from '../../utils/bff'

// Every method and path under /admin/api. Unknown paths are answered inside handleApi, without forwarding.
export default defineEventHandler(async event => sendCore(event, await handleApi(toCoreRequest(event), runtimeDeps())))
