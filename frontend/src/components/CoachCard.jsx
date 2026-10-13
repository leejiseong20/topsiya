import { useState } from 'react';
import { requestCoaching } from '../api.js';

/** 라인전 분석 패널 아래의 AI 코칭 영역. 버튼을 눌러야 AI 를 부른다 */
export default function CoachCard({ matchId, puuid }) {
  const [status, setStatus] = useState('idle'); // idle | loading | done | error
  const [report, setReport] = useState(null);
  const [error, setError] = useState('');

  async function handleClick() {
    setStatus('loading');
    setError('');
    try {
      setReport(await requestCoaching(matchId, puuid));
      setStatus('done');
    } catch (err) {
      setError(err.message);
      setStatus('error');
    }
  }

  if (status === 'idle' || status === 'error') {
    return (
      <div className="coach coach--cta">
        <div>
          <strong>AI 코칭 리포트</strong>
          <p>위 데이터를 바탕으로 AI가 실수 구간, 원인, 개선 방법을 정리해 줘요.</p>
          {status === 'error' && <p className="coach__error">{error}</p>}
        </div>
        <button className="btn-coach" onClick={handleClick}>
          {status === 'error' ? '다시 시도' : 'AI 코칭 받기'}
        </button>
      </div>
    );
  }

  if (status === 'loading') {
    return (
      <div className="coach coach--loading">
        <div className="loading__bar" />
        AI가 경기를 복기하는 중이에요… (10~30초)
      </div>
    );
  }

  return (
    <section className="coach">
      <div className="coach__badge">AI 코칭</div>
      <p className="coach__summary">{report.summary}</p>

      {report.mistakes.length > 0 && (
        <ol className="mistakes">
          {report.mistakes.map((m, i) => (
            <li key={i} className="mistake">
              <div className="mistake__head">
                <span className="mistake__time">{m.time}</span>
                <span className="mistake__what">{m.what}</span>
              </div>
              <div className="mistake__row">
                <em>원인</em>
                <span>{m.why}</span>
              </div>
              <div className="mistake__row mistake__row--fix">
                <em>개선</em>
                <span>{m.fix}</span>
              </div>
            </li>
          ))}
        </ol>
      )}

      {report.goodPoints.length > 0 && (
        <div className="coach__good">
          <em>잘한 점</em>
          <ul>
            {report.goodPoints.map((g, i) => (
              <li key={i}>{g}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="coach__tip">
        <em>다음 경기 팁</em>
        <span>{report.nextGameTip}</span>
      </div>
    </section>
  );
}
