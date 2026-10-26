import { useEffect, useState } from 'react';
import { fetchEval, fetchProbe, fetchRecentMatches, parseRiotId, runEval } from '../api.js';

/** AI 정확도 측정: AI 가 쓴 시각·숫자가 실제 경기 기록과 얼마나 맞는지 */
export default function EvalPage() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [input, setInput] = useState('');
  const [count, setCount] = useState(10);
  const [running, setRunning] = useState(false);
  const [probe, setProbe] = useState(null);

  useEffect(() => {
    fetchEval().then(setData).catch((e) => setError(e.message));
    fetchProbe().then(setProbe).catch(() => {});
  }, []);

  async function handleRun(e) {
    e.preventDefault();
    const id = parseRiotId(input);
    if (!id) {
      setError('닉네임#태그 형식으로 입력해 주세요.');
      return;
    }
    setRunning(true);
    setError('');
    try {
      const player = await fetchRecentMatches(id.gameName, id.tagLine, 1);
      setData(await runEval(player.puuid, count));
      setProbe(await fetchProbe());
    } catch (err) {
      setError(err.message);
    } finally {
      setRunning(false);
    }
  }

  return (
    <main className="eval">
      <a className="link-btn" href="#">
        ← 검색으로
      </a>
      <p className="eval__tag">개발자 화면</p>
      <h2 className="player">AI 답변 사실 일치율</h2>
      <p className="quiet">
        AI가 쓴 시각(13:20), 그 시각 데스의 유형·위치·직전 와드 수, 이름 붙은 숫자(와드 8개, CS 격차 -5)를 실제 경기 기록과
        하나씩 대조해요. 안 맞으면 무엇이 틀렸는지 알려주고 한 번 다시 쓰게 해요. 조언이 좋은지는 판단하지 않아요.
      </p>

      {data && (
        <div className="eval__grid">
          <EvalCard title="경기별 AI 코칭" s={data.coaching} />
          <EvalCard title="반복 실수 분석" s={data.weakness} />
        </div>
      )}

      {probe && probe.planted > 0 && <ProbeCard p={probe} />}

      <form className="eval__run" onSubmit={handleRun}>
        <h3>최근 경기로 측정하기</h3>
        <p className="quiet">최근 경기마다 AI 코칭을 받아요. 이미 받은 경기는 저장된 답을 써요. (경기 수만큼 AI 비용이 들어요)</p>
        <div className="search">
          <input value={input} onChange={(e) => setInput(e.target.value)} placeholder="닉네임#태그" />
          <select value={count} onChange={(e) => setCount(Number(e.target.value))} aria-label="경기 수">
            {[5, 10, 20].map((n) => (
              <option key={n} value={n}>
                {n}경기
              </option>
            ))}
          </select>
          <button type="submit" disabled={running}>
            {running ? '측정 중' : '측정'}
          </button>
        </div>
        {running && <p className="quiet">경기마다 10~30초 걸려요. 창을 닫지 마세요.</p>}
      </form>

      {error && <p className="notice notice--error">{error}</p>}
    </main>
  );
}

function rate(v) {
  return v === null || v === undefined ? '-' : `${v}%`;
}

function EvalCard({ title, s }) {
  if (!s || s.reports === 0) {
    return (
      <section className="eval__card">
        <h3>{title}</h3>
        <p className="quiet">아직 측정할 답이 없어요.</p>
      </section>
    );
  }
  return (
    <section className="eval__card">
      <h3>{title}</h3>
      <p className="eval__big">
        <span>최종 일치율</span>
        <strong>{rate(s.afterCheck.overallRate)}</strong>
      </p>
      <table className="eval__table">
        <thead>
          <tr>
            <th />
            <th>AI 첫 답</th>
            <th>검증 후</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <th>시각</th>
            <td>{rate(s.firstDraft.timeRate)}</td>
            <td>{rate(s.afterCheck.timeRate)}</td>
          </tr>
          <tr>
            <th>시각+내용</th>
            <td>{rate(s.firstDraft.contextRate)}</td>
            <td>{rate(s.afterCheck.contextRate)}</td>
          </tr>
          <tr>
            <th>숫자</th>
            <td>{rate(s.firstDraft.numberRate)}</td>
            <td>{rate(s.afterCheck.numberRate)}</td>
          </tr>
          <tr>
            <th>전체</th>
            <td>{rate(s.firstDraft.overallRate)}</td>
            <td>{rate(s.afterCheck.overallRate)}</td>
          </tr>
        </tbody>
      </table>
      <p className="quiet">
        답 {s.reports}개 · 다시 쓰게 한 답 {s.rewritten}개 · 모두 일치 {s.fullyMatched}개 · 대조한 항목{' '}
        {s.afterCheck.timeClaims + s.afterCheck.numberClaims + (s.afterCheck.contextClaims ?? 0)}개
      </p>
    </section>
  );
}

/** 검사기 시험: 실제 AI 답에 틀린 내용을 일부러 심었을 때 몇 개를 잡았는지 */
function ProbeCard({ p }) {
  const pct = (a, b) => (b === 0 ? '-' : `${Math.round((100 * a) / b)}%`);
  return (
    <section className="eval__card eval__probe">
      <h3>검사기 시험</h3>
      <p className="quiet">
        저장된 AI 답 {p.reports}개에 틀린 내용을 일부러 하나씩 심고(시각·데스 유형·위치·와드 수 등), 검사기가 잡아내는지 봤어요.
        일치율 100%가 검사기가 허술해서가 아니라는 근거예요.
      </p>
      <p className="eval__big">
        <span>심은 실수 {p.planted}개 중</span>
        <strong>{p.caught}개 적발</strong>
      </p>
      <table className="eval__table">
        <thead>
          <tr>
            <th>심은 실수</th>
            <th>개수</th>
            <th>지금 검사</th>
            <th>느슨한 검사였다면</th>
          </tr>
        </thead>
        <tbody>
          {p.byType.map((r) => (
            <tr key={r.type}>
              <th>{r.type}</th>
              <td>{r.planted}</td>
              <td>{pct(r.caught, r.planted)}</td>
              <td>{pct(r.caughtLoose, r.planted)}</td>
            </tr>
          ))}
          <tr>
            <th>전체</th>
            <td>{p.planted}</td>
            <td>{pct(p.caught, p.planted)}</td>
            <td>{pct(p.caughtLoose, p.planted)}</td>
          </tr>
        </tbody>
      </table>
    </section>
  );
}
