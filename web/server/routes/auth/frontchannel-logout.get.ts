import { frontchannelLogout } from '../../lib/auth'
import { runtimeDeps } from '../../lib/runtime'
import { sendCore, toCoreRequest } from '../../utils/bff'

export default defineEventHandler(async event => sendCore(event, await frontchannelLogout(toCoreRequest(event), runtimeDeps())))
