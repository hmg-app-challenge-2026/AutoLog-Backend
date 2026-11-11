import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api, ApiError, type Place, type PlaceKind } from './api'
import { PLACE_KINDS, PURPOSES, kindLabel, purposeLabel } from './labels'
import { ErrorBox } from './ErrorBox'

/**
 * 장소 (F-52). 거래처·지점·본사·주유소를 등록하면 모든 차로 배포된다(F-43).
 * 차는 이 좌표로 도착지를 알아보고 목적을 추천한다. 자택은 본인이 차 안에서만 등록한다.
 */
export function PlacesScreen() {
  const [places, setPlaces] = useState<Place[]>([])
  const [error, setError] = useState<ApiError | null>(null)
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [useCoords, setUseCoords] = useState(false)
  const [form, setForm] = useState({
    label: '',
    kind: 'CLIENT' as PlaceKind,
    address: '',
    lat: '',
    lng: '',
    radiusM: '150',
    defaultPurpose: 'CLIENT_VISIT',
  })
  const fileRef = useRef<HTMLInputElement>(null)

  const load = () =>
    api
      .places()
      .then(setPlaces)
      .catch(e => setError(e as ApiError))

  useEffect(() => {
    load()
  }, [])

  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setNotice('')
    if (!form.label.trim()) return setError(new ApiError(400, 'FORM', '장소 이름을 입력해 주세요'))
    if (!useCoords && !form.address.trim()) return setError(new ApiError(400, 'FORM', '주소를 입력해 주세요'))
    if (useCoords && (!form.lat || !form.lng)) return setError(new ApiError(400, 'FORM', '위도와 경도를 입력해 주세요'))
    setBusy(true)
    try {
      const p = await api.createPlace({
        label: form.label.trim(),
        kind: form.kind,
        ...(useCoords ? { lat: Number(form.lat), lng: Number(form.lng) } : { address: form.address.trim() }),
        radiusM: Number(form.radiusM) || 150,
        defaultPurpose: form.defaultPurpose || undefined,
      })
      setNotice(`'${p.label}'을(를) 등록했어요. 모든 차로 보냈어요.`)
      setForm({ ...form, label: '', address: '', lat: '', lng: '' })
      await load()
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const remove = async (p: Place) => {
    if (!confirm(`'${p.label}'을(를) 지울까요?\n이 장소가 목적지인 배차는 목적지가 비워집니다.`)) return
    setError(null)
    try {
      await api.deletePlace(p.placeId)
      setNotice(`'${p.label}'을(를) 지웠어요.`)
      await load()
    } catch (err) {
      setError(err as ApiError)
    }
  }

  const upload = async (file: File | undefined) => {
    if (!file) return
    setError(null)
    setNotice('')
    try {
      const r = await api.importPlaces(file)
      setNotice(`CSV에서 ${r.imported}곳을 등록했어요.`)
      await load()
    } catch (err) {
      setError(err as ApiError)
    } finally {
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  return (
    <section>
      <header className="screen-head">
        <div>
          <h2>장소</h2>
          <p className="muted">등록한 장소는 모든 차로 보내져, 도착지를 알아보고 목적을 추천하는 데 쓰여요.</p>
        </div>
        <label className="button secondary">
          CSV 올리기
          <input ref={fileRef} type="file" accept=".csv,text/csv" hidden onChange={e => upload(e.target.files?.[0])} />
        </label>
      </header>

      <form className="card form" onSubmit={submit}>
        <div className="row">
          <label className="field grow">
            <span>이름</span>
            <input value={form.label} onChange={set('label')} placeholder="예: 판교 A고객사" />
          </label>
          <label className="field">
            <span>종류</span>
            <select value={form.kind} onChange={set('kind')}>
              {PLACE_KINDS.map(k => (
                <option key={k.value} value={k.value}>
                  {k.label}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>기본 목적</span>
            <select value={form.defaultPurpose} onChange={set('defaultPurpose')}>
              <option value="">(없음)</option>
              {PURPOSES.map(p => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="row">
          {useCoords ? (
            <>
              <label className="field grow">
                <span>위도</span>
                <input value={form.lat} onChange={set('lat')} inputMode="decimal" placeholder="37.4020" />
              </label>
              <label className="field grow">
                <span>경도</span>
                <input value={form.lng} onChange={set('lng')} inputMode="decimal" placeholder="127.1108" />
              </label>
            </>
          ) : (
            <label className="field grow">
              <span>주소</span>
              <input value={form.address} onChange={set('address')} placeholder="예: 경기 성남시 분당구 판교역로 166" />
            </label>
          )}
          <label className="field narrow">
            <span>반경(m)</span>
            <input value={form.radiusM} onChange={set('radiusM')} inputMode="numeric" />
          </label>
        </div>
        <div className="row between">
          <button type="button" className="link" onClick={() => setUseCoords(!useCoords)}>
            {useCoords ? '주소로 입력' : '위도·경도로 직접 입력'}
          </button>
          <button type="submit" className="button" disabled={busy}>
            {busy ? '등록 중…' : '장소 등록'}
          </button>
        </div>
      </form>

      <ErrorBox error={error} />
      {notice && <p className="notice">{notice}</p>}

      <table className="table">
        <thead>
          <tr>
            <th>이름</th>
            <th>종류</th>
            <th>기본 목적</th>
            <th>좌표</th>
            <th>반경</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {places.length === 0 && (
            <tr>
              <td colSpan={6} className="empty">
                아직 등록한 장소가 없어요.
              </td>
            </tr>
          )}
          {places.map(p => (
            <tr key={p.placeId}>
              <td>{p.label}</td>
              <td>
                <span className={`chip kind-${p.kind}`}>{kindLabel(p.kind)}</span>
              </td>
              <td>{purposeLabel(p.defaultPurpose)}</td>
              <td className="mono">
                {p.lat.toFixed(4)}, {p.lng.toFixed(4)}
              </td>
              <td>{p.radiusM} m</td>
              <td className="right">
                <button className="link danger" onClick={() => remove(p)}>
                  지우기
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="muted small">
        CSV 머리줄: <code>이름,종류,주소,lat,lng,radius_m,default_purpose</code> (종류는 거래처·지점·본사·주유소, 좌표가
        비면 주소로 찾습니다)
      </p>
    </section>
  )
}
