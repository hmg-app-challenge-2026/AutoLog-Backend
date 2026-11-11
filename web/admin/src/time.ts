// 배차 시각은 서울 기준으로 다룬다. 서버에는 "2026-11-12T09:00"(서울 시각)이나 ISO +09:00 문자열로 보낸다.

const DAY_MS = 24 * 60 * 60 * 1000
const KST_MS = 9 * 60 * 60 * 1000

/** 서울 날짜 "YYYY-MM-DD" → 그 날 0시의 epoch millis. */
export function kstMidnight(ymd: string): number {
  return Date.parse(`${ymd}T00:00:00+09:00`)
}

/** epoch millis → 서울 날짜 "YYYY-MM-DD". */
export function kstDate(ms: number): string {
  return new Date(ms + KST_MS).toISOString().slice(0, 10)
}

/** epoch millis → 서울 "HH:mm". */
export function kstTime(ms: number): string {
  return new Date(ms + KST_MS).toISOString().slice(11, 16)
}

/** 오늘(서울)이 든 주의 월요일 "YYYY-MM-DD". */
export function mondayOf(ymd: string): string {
  const t = kstMidnight(ymd)
  const dow = new Date(t + KST_MS).getUTCDay() // 0=일
  return kstDate(t - ((dow + 6) % 7) * DAY_MS)
}

export function addDays(ymd: string, days: number): string {
  return kstDate(kstMidnight(ymd) + days * DAY_MS)
}

export function todayKst(): string {
  return kstDate(Date.now())
}

/** 서버에 보낼 ISO 문자열(+09:00). */
export function kstIso(ymd: string, hhmm = '00:00'): string {
  return `${ymd}T${hhmm}:00+09:00`
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

export function dayLabel(ymd: string): string {
  const d = new Date(kstMidnight(ymd) + KST_MS)
  return `${d.getUTCMonth() + 1}/${d.getUTCDate()} (${WEEKDAYS[d.getUTCDay()]})`
}
