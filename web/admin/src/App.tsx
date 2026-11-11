import { useState, type FormEvent } from 'react'
import { api, ApiError, getToken, setToken } from './api'
import { PlacesScreen } from './PlacesScreen'
import { SchedulesScreen } from './SchedulesScreen'
import { ErrorBox } from './ErrorBox'

// 운영 웹 (F-50~54). 운행·비용 화면은 없다 — 운행 데이터는 서버에 없음(차 Room이 원본).
// 지금은 장소·배차만 있다. 차량·직원·수신자는 API만 있고 화면은 아직이다.
const SCREENS = [
  { key: 'places', label: '장소', id: 'F-52', ready: true },
  { key: 'schedules', label: '배차', id: 'F-53', ready: true },
  { key: 'vehicles', label: '차량', id: 'F-50', ready: false },
  { key: 'users', label: '직원', id: 'F-51', ready: false },
  { key: 'recipients', label: '수신자', id: 'F-54', ready: false },
] as const

type ScreenKey = (typeof SCREENS)[number]['key']

export default function App() {
  const [authed, setAuthed] = useState(() => getToken() !== '')
  const [current, setCurrent] = useState<ScreenKey>('places')

  if (!authed) return <Login onDone={() => setAuthed(true)} />

  return (
    <div className="layout">
      <nav className="nav">
        <div className="brand">AutoLog 운영</div>
        {SCREENS.map(s => (
          <button
            key={s.key}
            className={`nav-item ${s.key === current ? 'active' : ''}`}
            disabled={!s.ready}
            onClick={() => setCurrent(s.key)}
          >
            {s.label}
            {!s.ready && <small> 준비 중</small>}
          </button>
        ))}
        <button
          className="link logout"
          onClick={() => {
            setToken('')
            setAuthed(false)
          }}
        >
          토큰 지우기
        </button>
      </nav>
      <main className="main">
        {current === 'places' && <PlacesScreen />}
        {current === 'schedules' && <SchedulesScreen />}
      </main>
    </div>
  )
}

/** 관리자 토큰 입력. 서버 .env의 ADMIN_TOKEN 값. 확인 요청이 성공해야 저장한다. */
function Login({ onDone }: { onDone: () => void }) {
  const [value, setValue] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!value.trim()) return setError(new ApiError(400, 'FORM', '관리자 토큰을 입력해 주세요'))
    setBusy(true)
    setError(null)
    setToken(value.trim())
    try {
      await api.places()
      onDone()
    } catch (err) {
      setToken('')
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <form className="card form" onSubmit={submit}>
        <h2>AutoLog 운영</h2>
        <p className="muted">서버 관리자 토큰(ops/.env의 ADMIN_TOKEN)을 입력하세요. 이 브라우저에만 저장됩니다.</p>
        <label className="field">
          <span>관리자 토큰</span>
          <input type="password" value={value} onChange={e => setValue(e.target.value)} autoFocus />
        </label>
        <ErrorBox error={error} />
        <button type="submit" className="button" disabled={busy}>
          {busy ? '확인 중…' : '들어가기'}
        </button>
      </form>
    </div>
  )
}
