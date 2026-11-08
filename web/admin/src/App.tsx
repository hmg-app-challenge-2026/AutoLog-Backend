import { useState } from 'react'

// 운영 웹 5화면 (F-50~54). 운행·비용 화면은 없다 — 운행 데이터는 서버에 없음.
const SCREENS = [
  { key: 'vehicles', label: '차량', id: 'F-50' },
  { key: 'users', label: '직원', id: 'F-51' },
  { key: 'places', label: '장소', id: 'F-52' },
  { key: 'schedules', label: '배차', id: 'F-53' },
  { key: 'recipients', label: '수신자', id: 'F-54' },
] as const

export default function App() {
  const [current, setCurrent] = useState<string>('vehicles')
  const screen = SCREENS.find(s => s.key === current)!
  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', display: 'flex', minHeight: '100vh' }}>
      <nav style={{ width: 180, borderRight: '1px solid #ddd', padding: 16 }}>
        <h3>AutoLog 운영</h3>
        {SCREENS.map(s => (
          <div key={s.key}>
            <button onClick={() => setCurrent(s.key)} style={{ fontWeight: s.key === current ? 700 : 400 }}>
              {s.label}
            </button>
          </div>
        ))}
      </nav>
      <main style={{ padding: 24 }}>
        <h2>{screen.label} <small>({screen.id})</small></h2>
        <p>TODO</p>
      </main>
    </div>
  )
}
