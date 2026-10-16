import { useState } from 'react';
import { requestCoaching } from '../api.js';

/** 분석 패널 맨 위의 AI 코칭. 결론(다음 판 할 일)부터 보여주고, 자세한 건 접어둔다 */
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
      <div className="coach-cta">
        <p>AI 코치가 이 판의 라인전을 복기해 드려요</p>
        <button className="btn-primary" onClick={handleClick}>
          {status === 'error' ? '다시 시도' : 'AI 코칭 받기'}
        </button>
        {status === 'error' && <p className="notice notice--error">{error}</p>}
      </div>
    );
  }

  if (status === 'loading') {
    return (
      <div className="coach-cta coach-cta--wait">
        <div className="waiting__bar" />
        코치가 경기를 보고 있어요. 10~30초 걸려요
      </div>
    );
  }

  const [first, ...rest] = report.mistakes;

  return (
    <section className="coach">
      <p className="coach__label">다음 판엔 이것만</p>
      <p className="coach__headline">{report.nextGameTip}</p>
      <p className="coach__summary">{report.summary}</p>

      {first && <Mistake mistake={first} big />}

      {rest.length > 0 && (
        <details className="more">
          <summary>다른 실수 {rest.length}개 더 보기</summary>
          {rest.map((m, i) => (
            <Mistake key={i} mistake={m} />
          ))}
        </details>
      )}

      {report.goodPoints.length > 0 && (
        <div className="good">
          <span className="good__label">잘한 점</span>
          {report.goodPoints.map((g, i) => (
            <p key={i}>{g}</p>
          ))}
        </div>
      )}
    </section>
  );
}

/** 실수 하나: 무엇을 → 이렇게 해보세요. 원인은 눌러야 보인다 */
export function Mistake({ mistake, big = false, badge }) {
  return (
    <article className={`mistake ${big ? 'mistake--big' : ''}`}>
      <div className="mistake__top">
        <span className="mistake__when">{badge ?? mistake.time}</span>
        <h5>{mistake.what ?? mistake.title}</h5>
      </div>
      <p className="mistake__fix">
        <b>이렇게 해보세요</b>
        {mistake.fix}
      </p>
      <details className="mistake__why">
        <summary>왜 그런가요?</summary>
        <p>{mistake.why ?? mistake.evidence}</p>
      </details>
    </article>
  );
}
