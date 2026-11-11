import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { api, ApiError, type Place, type Schedule, type User, type Vehicle } from './api'
import { PURPOSES, purposeLabel } from './labels'
import { ErrorBox } from './ErrorBox'
import { addDays, dayLabel, kstDate, kstIso, kstTime, mondayOf, todayKst } from './time'

/**
 * 배차 (F-53). 주간 표(차량 행 × 요일 열)로 보고, 차량·운전자·시간·목적지·목적을 등록한다.
 * 등록하면 그 차로만 배포된다(F-43). 차는 이 배차로 운전자를 정하고(±1시간) 목적을 추천한다(±2시간, 목적지 일치).
 */
export function SchedulesScreen() {
  const [week, setWeek] = useState(() => mondayOf(todayKst()))
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [users, setUsers] = useState<User[]>([])
  const [places, setPlaces] = useState<Place[]>([])
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [error, setError] = useState<ApiError | null>(null)
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [form, setForm] = useState({
    vehicleId: '',
    userId: '',
    date: todayKst(),
    start: '09:00',
    end: '12:00',
    destPlaceId: '',
    purposeDetail: 'CLIENT_VISIT',
  })

  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(week, i)), [week])

  const loadWeek = () =>
    api
      .schedules(kstIso(week), kstIso(addDays(week, 7)))
      .then(setSchedules)
      .catch(e => setError(e as ApiError))

  useEffect(() => {
    Promise.all([api.vehicles(), api.users(), api.places()])
      .then(([v, u, p]) => {
        setVehicles(v)
        setUsers(u)
        setPlaces(p)
        setForm(f => ({ ...f, vehicleId: f.vehicleId || v[0]?.vehicleId || '' }))
      })
      .catch(e => setError(e as ApiError))
  }, [])

  useEffect(() => {
    loadWeek()
  }, [week])

  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => {
    const value = e.target.value
    setForm(f => {
      const next = { ...f, [k]: value }
      // 목적지를 고르면 그 장소의 기본 목적을 미리 채운다
      if (k === 'destPlaceId') {
        const dp = places.find(p => p.placeId === value)?.defaultPurpose
        if (dp) next.purposeDetail = dp
      }
      return next
    })
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setNotice('')
    if (!form.vehicleId) return setError(new ApiError(400, 'FORM', '차량을 먼저 등록해 주세요'))
    if (form.end <= form.start) return setError(new ApiError(400, 'FORM', '끝나는 시각이 시작보다 늦어야 해요'))
    setBusy(true)
    try {
      await api.createSchedule({
        vehicleId: form.vehicleId,
        userId: form.userId || undefined,
        startsAt: kstIso(form.date, form.start),
        endsAt: kstIso(form.date, form.end),
        destPlaceId: form.destPlaceId || undefined,
        purposeDetail: form.purposeDetail || undefined,
      })
      setNotice('배차를 등록했어요. 그 차로 보냈어요.')
      const w = mondayOf(form.date)
      if (w !== week) setWeek(w)
      else await loadWeek()
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const remove = async (s: Schedule) => {
    if (!confirm(`${describe(s)}\n이 배차를 지울까요?`)) return
    setError(null)
    try {
      await api.deleteSchedule(s.scheduleId)
      setNotice('배차를 지웠어요.')
      await loadWeek()
    } catch (err) {
      setError(err as ApiError)
    }
  }

  const userName = (id: string | null) => (id ? (users.find(u => u.userId === id)?.name ?? '?') : '운전자 미정')
  const placeName = (id: string | null) => (id ? (places.find(p => p.placeId === id)?.label ?? '?') : '목적지 없음')
  const describe = (s: Schedule) =>
    `${kstTime(Date.parse(s.startsAt))}–${kstTime(Date.parse(s.endsAt))} ${placeName(s.destPlaceId)} · ${userName(s.userId)}`

  const cell = (vehicleId: string, day: string) =>
    schedules
      .filter(s => s.vehicleId === vehicleId && kstDate(Date.parse(s.startsAt)) === day)
      .sort((a, b) => a.startsAt.localeCompare(b.startsAt))

  return (
    <section>
      <header className="screen-head">
        <div>
          <h2>배차</h2>
          <p className="muted">
            누가 언제 어느 차로 어디에 가는지 등록하면 그 차로 보내져요. 차는 이걸로 운전자와 운행 목적을 추천해요.
          </p>
        </div>
      </header>

      <form className="card form" onSubmit={submit}>
        <div className="row">
          <label className="field">
            <span>차량</span>
            <select value={form.vehicleId} onChange={set('vehicleId')}>
              {vehicles.length === 0 && <option value="">(차량 없음)</option>}
              {vehicles.map(v => (
                <option key={v.vehicleId} value={v.vehicleId}>
                  {v.plateNo}
                  {v.model ? ` · ${v.model}` : ''}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>운전자</span>
            <select value={form.userId} onChange={set('userId')}>
              <option value="">(미정)</option>
              {users.map(u => (
                <option key={u.userId} value={u.userId}>
                  {u.name}
                  {u.dept ? ` · ${u.dept}` : ''}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>날짜</span>
            <input type="date" value={form.date} onChange={set('date')} />
          </label>
          <label className="field narrow">
            <span>시작</span>
            <input type="time" value={form.start} onChange={set('start')} />
          </label>
          <label className="field narrow">
            <span>끝</span>
            <input type="time" value={form.end} onChange={set('end')} />
          </label>
        </div>
        <div className="row">
          <label className="field grow">
            <span>목적지</span>
            <select value={form.destPlaceId} onChange={set('destPlaceId')}>
              <option value="">(없음)</option>
              {places.map(p => (
                <option key={p.placeId} value={p.placeId}>
                  {p.label}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>목적</span>
            <select value={form.purposeDetail} onChange={set('purposeDetail')}>
              <option value="">(없음)</option>
              {PURPOSES.map(p => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </select>
          </label>
          <div className="field end">
            <button type="submit" className="button" disabled={busy}>
              {busy ? '등록 중…' : '배차 등록'}
            </button>
          </div>
        </div>
        {places.length === 0 && <p className="muted small">목적지를 고르려면 먼저 [장소]에서 장소를 등록하세요.</p>}
      </form>

      <ErrorBox error={error} />
      {notice && <p className="notice">{notice}</p>}

      <div className="week-nav">
        <button className="button secondary" onClick={() => setWeek(addDays(week, -7))}>
          ‹ 지난주
        </button>
        <strong>
          {dayLabel(days[0])} – {dayLabel(days[6])}
        </strong>
        <button className="button secondary" onClick={() => setWeek(addDays(week, 7))}>
          다음 주 ›
        </button>
        <button className="link" onClick={() => setWeek(mondayOf(todayKst()))}>
          이번 주
        </button>
      </div>

      <div className="week-scroll">
        <table className="table week">
          <thead>
            <tr>
              <th>차량</th>
              {days.map(d => (
                <th key={d} className={d === todayKst() ? 'today' : ''}>
                  {dayLabel(d)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {vehicles.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  등록된 차량이 없어요.
                </td>
              </tr>
            )}
            {vehicles.map(v => (
              <tr key={v.vehicleId}>
                <th className="vehicle">{v.plateNo}</th>
                {days.map(d => (
                  <td key={d} className={d === todayKst() ? 'today' : ''}>
                    {cell(v.vehicleId, d).map(s => (
                      <button key={s.scheduleId} className="slot" title="눌러서 지우기" onClick={() => remove(s)}>
                        <span className="slot-time">
                          {kstTime(Date.parse(s.startsAt))}–{kstTime(Date.parse(s.endsAt))}
                        </span>
                        <span>{placeName(s.destPlaceId)}</span>
                        <span className="muted">
                          {userName(s.userId)}
                          {s.purposeDetail ? ` · ${purposeLabel(s.purposeDetail)}` : ''}
                        </span>
                      </button>
                    ))}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="muted small">배차를 누르면 지울 수 있어요.</p>
    </section>
  )
}
