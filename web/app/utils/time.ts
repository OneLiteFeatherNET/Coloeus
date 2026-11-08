// API instants are ISO-8601 UTC strings and stay UTC in the model. Only the display converts them, and the
// time zone is passed in so that tests can fix it.

export function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}

/** `2026-11-08T10:15:30Z` in Europe/Berlin is shown as `08.11.2026, 11:15`. */
export function formatInstant(instant: string, timeZone: string): string {
  const date = new Date(instant)
  if (Number.isNaN(date.getTime())) {
    return instant
  }
  return new Intl.DateTimeFormat('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    timeZone
  }).format(date)
}

/** A `YYYY-MM-DD` date of the API (UTC days of the money series) shown as `08.11.2026`. */
export function formatDay(day: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day)
  if (!match) {
    return day
  }
  return `${match[3]}.${match[2]}.${match[1]}`
}
