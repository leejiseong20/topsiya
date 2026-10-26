import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { clearGoal, fetchGoal, saveGoal } from '../api.js';
import { championKoName } from '../ddragon.js';
import { positionLabel, signed } from '../format.js';
import ChampionIcon from './ChampionIcon.jsx';

/** 목표 상태를 화면 여러 곳(맨 위 목표 카드, AI 코칭, 반복 실수)에서 같이 쓴다 */
const GoalContext = createContext(null);

export function GoalProvider({ puuid, children }) {
  const [progress, setProgress] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setProgress(null);
    fetchGoal(puuid)
      .then((p) => !cancelled && setProgress(p))
      .catch(() => {}); // 목표 조회 실패는 조용히 넘긴다 (핵심 기능이 아님)
    return () => {
      cancelled = true;
    };
  }, [puuid]);

  const choose = useCallback(
    async (goal) => {
      setError('');
      try {
        setProgress(await saveGoal(puuid, goal));
        document.querySelector('.goal')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      } catch (err) {
        setError(err.message);
      }
    },
    [puuid],
  );

  const stop = useCallback(async () => {
    await clearGoal(puuid);
    setProgress(null);
  }, [puuid]);

  return (
    <GoalContext.Provider value={{ progress, choose, stop, error }}>{children}</GoalContext.Provider>
  );
}

/** 검색 결과 맨 위: 도전 중인 목표와, 그 뒤 경기에서 지켰는지 */
export default function GoalPanel({ champData }) {
  const { progress, stop } = useContext(GoalContext);
  if (!progress) return null;

  const { goal, criterion, unit, checks, achievedCount } = progress;
  const showValue = (v) => (goal.metric.startsWith('CS_DIFF') ? signed(v) : v);

  return (
    <section className="goal">
      <div className="goal__head">
        <p className="coach__label">도전 중인 목표</p>
        <button className="link-btn" onClick={stop}>
          그만하기
        </button>
      </div>
      <p className="goal__text">{goal.text}</p>
      <p className="goal__criterion">
        확인 기준 · {criterion}
        {goal.position && ` · ${positionLabel(goal.position)} 경기만`}
      </p>

      {checks.length === 0 ? (
        <p className="goal__empty">
          목표를 정한 뒤 아직 판정할 경기가 없어요. 한 판 하고 다시 검색하면 지켰는지 알려드려요.
        </p>
      ) : (
        <>
          <p className="goal__score">
            <strong>
              {checks.length}판 중 {achievedCount}판
            </strong>{' '}
            지켰어요
          </p>
          <ul className="goal__checks">
            {checks.map((c) => (
              <li key={c.matchId} className={c.achieved ? 'is-ok' : 'is-miss'}>
                <ChampionIcon data={champData} championName={c.myChampion} size={36} />
                <span className="goal__champ">{championKoName(champData, c.myChampion)}</span>
                <b>
                  {showValue(c.value)}
                  {unit}
                </b>
                <span className="goal__mark">{c.achieved ? '지킴' : '놓침'}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}

/** AI 가 제안한 목표를 "도전 중인 목표"로 정하는 버튼 */
export function GoalButton({ suggested, text, position, sourceMatchId }) {
  const ctx = useContext(GoalContext);
  const [busy, setBusy] = useState(false);
  if (!ctx || !suggested) return null;

  const current = ctx.progress?.goal;
  const isCurrent =
    current &&
    current.metric === suggested.metric &&
    current.target === suggested.target &&
    current.text === text;

  if (isCurrent) {
    return <p className="goal-btn goal-btn--on">✓ 도전 중인 목표예요. 다음 판 뒤에 다시 검색해 보세요</p>;
  }

  async function handleClick() {
    setBusy(true);
    await ctx.choose({ metric: suggested.metric, target: suggested.target, text, position, sourceMatchId });
    setBusy(false);
  }

  return (
    <>
      <button className="goal-btn" onClick={handleClick} disabled={busy}>
        {busy ? '저장 중' : current ? '이걸로 목표 바꾸기' : '이걸 다음 판 목표로 정하기'}
      </button>
      {ctx.error && <p className="notice notice--error">{ctx.error}</p>}
    </>
  );
}
