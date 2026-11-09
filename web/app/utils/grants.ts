export interface GrantGroup { currency: string, permissions: string[] }

// The order in which permissions are listed within one currency. Codes not listed here come last, in input order.
const PERMISSION_ORDER = ['READ', 'MINT', 'BURN', 'TRANSFER_ON_BEHALF']

// Groups grants by currency (currencies in order of first appearance) and maps each permission code to its label,
// falling back to the code itself. Permissions within a currency follow PERMISSION_ORDER.
export function groupGrants(
  grants: readonly { currency: string, permission: string }[],
  labels: Record<string, string>
): GrantGroup[] {
  const codesByCurrency = new Map<string, string[]>()
  for (const grant of grants) {
    const codes = codesByCurrency.get(grant.currency) ?? []
    codes.push(grant.permission)
    codesByCurrency.set(grant.currency, codes)
  }
  return [...codesByCurrency].map(([currency, codes]) => ({
    currency,
    permissions: [...codes]
      .sort((a, b) => rank(a) - rank(b))
      .map(code => (Object.hasOwn(labels, code) ? labels[code]! : code))
  }))
}

function rank(code: string): number {
  const index = PERMISSION_ORDER.indexOf(code)
  return index === -1 ? PERMISSION_ORDER.length : index
}
