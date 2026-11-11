// 중계 서버 관리자 API (F-50~54). nginx와 vite dev 서버가 /admin/api/* → relay /admin/* 로 넘긴다.
// 인증은 JWT 도입 전까지 X-Admin-Token(서버 환경변수 ADMIN_TOKEN). 이 브라우저에만 저장한다.

const BASE = '/admin/api'
const TOKEN_KEY = 'autolog.adminToken'

export function getToken(): string {
  try {
    return localStorage.getItem(TOKEN_KEY) ?? ''
  } catch {
    return ''
  }
}

export function setToken(token: string) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // 저장소를 못 쓰는 브라우저 — 새로고침하면 다시 입력
  }
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public lines: { line: number; message: string }[] = [],
  ) {
    super(message)
  }
}

async function request<T>(method: string, path: string, body?: unknown, contentType = 'application/json'): Promise<T> {
  let res: Response
  try {
    res = await fetch(BASE + path, {
      method,
      headers: {
        'X-Admin-Token': getToken(),
        ...(body !== undefined ? { 'Content-Type': contentType } : {}),
      },
      body: body === undefined ? undefined : contentType === 'application/json' ? JSON.stringify(body) : (body as BodyInit),
    })
  } catch {
    throw new ApiError(0, 'NETWORK', '서버에 연결하지 못했어요. 서버가 켜져 있는지 확인해 주세요')
  }
  const text = await res.text()
  const json = text ? safeParse(text) : undefined
  if (!res.ok) {
    const j = (json ?? {}) as { code?: string; message?: string; errors?: { line: number; message: string }[] }
    const fallback = res.status === 403 ? '관리자 토큰이 틀렸어요' : `요청이 실패했어요 (HTTP ${res.status})`
    throw new ApiError(res.status, j.code ?? 'HTTP', j.message ?? fallback, j.errors ?? [])
  }
  return json as T
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

// ---- 타입 (relay 응답과 같은 모양) ----

export type PlaceKind = 'OFFICE' | 'BRANCH' | 'CLIENT' | 'GAS_STATION'

export interface Place {
  placeId: string
  label: string
  kind: PlaceKind
  lat: number
  lng: number
  radiusM: number
  defaultPurpose: string | null
}

export interface PlaceInput {
  label: string
  kind: PlaceKind
  address?: string
  lat?: number
  lng?: number
  radiusM?: number
  defaultPurpose?: string
}

export interface Vehicle {
  vehicleId: string
  plateNo: string
  model: string | null
  registered: boolean
}

export interface User {
  userId: string
  name: string
  dept: string | null
}

export interface Schedule {
  scheduleId: string
  vehicleId: string
  userId: string | null
  startsAt: string
  endsAt: string
  destPlaceId: string | null
  purposeDetail: string | null
}

export interface ScheduleInput {
  vehicleId: string
  userId?: string
  startsAt: string
  endsAt: string
  destPlaceId?: string
  purposeDetail?: string
}

// ---- 호출 ----

export const api = {
  places: () => request<Place[]>('GET', '/places'),
  createPlace: (p: PlaceInput) => request<Place>('POST', '/places', p),
  deletePlace: (id: string) => request<void>('DELETE', `/places/${id}`),
  importPlaces: (csv: File) => request<{ imported: number }>('POST', '/places/import', csv, 'text/csv'),

  vehicles: () => request<Vehicle[]>('GET', '/vehicles'),
  users: () => request<User[]>('GET', '/users'),

  schedules: (from: string, to: string) =>
    request<Schedule[]>('GET', `/schedules?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  createSchedule: (s: ScheduleInput) => request<Schedule>('POST', '/schedules', s),
  deleteSchedule: (id: string) => request<void>('DELETE', `/schedules/${id}`),
}
