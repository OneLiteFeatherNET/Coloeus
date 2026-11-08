/**
 * Types of the staff API under /admin/api.
 *
 * Written by hand from the JSON shapes of the staff-api contract, because the backend does not expose its
 * OpenAPI document yet. Once it does, `pnpm api:types` regenerates this file with openapi-typescript (the same
 * layout: `components.schemas`), and the paths below are replaced by the generated `paths` type.
 * Money is always a plain decimal string; never a JSON number.
 */

export type Instant = string
export type Uuid = string

export interface components {
  schemas: {
    Me: {
      oid: string
      name: string
      roles: ('Coloeus.Moderator' | 'Coloeus.Admin')[]
      canWrite: boolean
      csrfToken: string
      sessionExpiresAt: Instant
      idleExpiresAt: Instant
    }
    AccountHit: {
      uuid: Uuid
      name: string | null
      lastSeen: Instant | null
      accountId: Uuid | null
    }
    AmountJson: {
      amount: string
      currency: string
    }
    AccountDetail: {
      id: Uuid
      kind: 'PLAYER' | 'BANK' | 'SOURCE' | 'SINK' | 'FEE' | 'ADMIN_SOURCE' | 'ADMIN_SINK'
      owner: { uuid: Uuid, name: string | null } | null
      client: { clientId: Uuid, displayName: string } | null
      currency: string | null
      balances: components['schemas']['AmountJson'][]
      latestPostingAt: Instant | null
    }
    StaffPosting: {
      id: Uuid
      transactionId: Uuid
      kind: 'TRANSFER' | 'MINT' | 'BURN' | 'BATCH' | 'RESET' | 'ADJUST'
      reason: string | null
      actor: string | null
      actorName: string | null
      currency: string
      amount: string
      counterAccounts: Uuid[]
      createdAt: Instant
    }
    StaffBooking: {
      transactionId: Uuid
      kind: 'RESET' | 'ADJUST'
      accountId: Uuid
      createdAt: Instant
      movements: components['schemas']['AmountJson'][]
    }
    MoneyFlowCurrency: {
      currency: string
      created: string
      destroyed: string
      netInWindow: string
      circulation: string
    }
    MoneyFlowClientCurrency: {
      currency: string
      created: string
      destroyed: string
      netInWindow: string
    }
    MoneyFlow: {
      window: '7D' | 'ALL'
      from: Instant | null
      to: Instant
      currencies: components['schemas']['MoneyFlowCurrency'][]
      clients: {
        clientId: Uuid
        displayName: string
        currencies: components['schemas']['MoneyFlowClientCurrency'][]
      }[]
      staff: {
        currency: string
        credited: string
        debited: string
        netInWindow: string
      }[]
    }
    MoneySeriesDay: {
      date: string
      created: string
      destroyed: string
    }
    MoneySeries: {
      currency: string
      days: components['schemas']['MoneySeriesDay'][]
    }
    Grant: {
      currency: string
      permission: 'MINT' | 'BURN' | 'TRANSFER_ON_BEHALF' | 'READ'
    }
    ClientView: {
      clientId: Uuid
      displayName: string
      createdAt: Instant
      grants: components['schemas']['Grant'][]
    }
    Currency: {
      code: string
      nameSingular: string
      namePlural: string
      symbol: string
      fractionDigits: number
      defaultCurrency: boolean
      createdAt: Instant
      updatedAt: Instant
    }
    StaffAction: {
      id: Uuid
      actorOid: string
      actorName: string | null
      action: 'RESET' | 'ADJUST' | 'CLIENT_REGISTERED' | 'GRANTS_REPLACED' | 'CURRENCY_CREATED' | 'CURRENCY_DEFAULT_SET'
      target: string
      transactionId: Uuid | null
      detail: Record<string, unknown>
      createdAt: Instant
    }
    Problem: {
      type: string
      title: string
      status: number
      code: string
      detail?: string
    }
    LogoutAnswer: {
      logoutUrl: string
    }
    ResetRequest: {
      currency: string | null
      reason: string
    }
    AdjustRequest: {
      currency: string
      direction: 'CREDIT' | 'DEBIT'
      amount: string
      reason: string
    }
    RegisterClientRequest: {
      clientId: Uuid
      displayName: string
    }
    GrantsRequest: {
      grants: components['schemas']['Grant'][]
    }
    CreateCurrencyRequest: {
      code: string
      nameSingular: string
      namePlural: string
      symbol: string
      fractionDigits: number
    }
  }
}

/** A list answer. `nextCursor` is present on paginated lists only. */
export interface Page<T> {
  items: T[]
  nextCursor?: string | null
}

type Schemas = components['schemas']

// The identity of the browser session is shared with the BFF (openspec staff-api, "JSON shapes").
export type { Me } from '../../shared/me'
export type AccountHit = Schemas['AccountHit']
export type AccountDetail = Schemas['AccountDetail']
export type AmountJson = Schemas['AmountJson']
export type StaffPosting = Schemas['StaffPosting']
export type StaffBooking = Schemas['StaffBooking']
export type MoneyFlow = Schemas['MoneyFlow']
export type MoneySeries = Schemas['MoneySeries']
export type ClientView = Schemas['ClientView']
export type Grant = Schemas['Grant']
export type Currency = Schemas['Currency']
export type StaffAction = Schemas['StaffAction']
export type Problem = Schemas['Problem']
export type ResetRequest = Schemas['ResetRequest']
export type AdjustRequest = Schemas['AdjustRequest']
export type RegisterClientRequest = Schemas['RegisterClientRequest']
export type CreateCurrencyRequest = Schemas['CreateCurrencyRequest']
export type Permission = Grant['permission']
export type StaffActionKind = StaffAction['action']
export type MoneyFlowCurrency = Schemas['MoneyFlowCurrency']
export type MoneyFlowClientCurrency = Schemas['MoneyFlowClientCurrency']
export type MoneySeriesDay = Schemas['MoneySeriesDay']
