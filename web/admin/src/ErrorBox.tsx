import type { ApiError } from './api'

/** 서버 오류 표시. CSV 업로드 오류면 줄별 이유를 함께 보여 준다. */
export function ErrorBox({ error }: { error: ApiError | null }) {
  if (!error) return null
  return (
    <div className="error" role="alert">
      <strong>{error.message}</strong>
      {error.lines.length > 0 && (
        <ul>
          {error.lines.map(l => (
            <li key={l.line}>
              {l.line}번째 줄: {l.message}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
