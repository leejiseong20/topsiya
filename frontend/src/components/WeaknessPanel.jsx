import { useMemo, useState } from 'react';
import { requestWeaknesses } from '../api.js';
import { positionLabel } from '../format.js';
import { Mistake } from './CoachCard.jsx';
import { signed } from './LanePanel.jsx';

const MIN_GAMES = 2;

/** 같은 포지션 경기를 묶어, 여러 판에 걸쳐 반복되는 실수를 찾는다 */
export default function WeaknessPanel({ matches, puuid }) {
  const groups = useMemo(() => {
    const map = new Map();
    matches
      .filter((m) => m.position && m.opponentChampionName)
      .forEach((m) => {
        if (!map.has(m.position)) map.set(m.position, []);
        map.get(m.position).push(m.matchId);
      });
    return [...map.entries()].sort((a, b) => b[1].length - a[1].length);
  }, [matches]);

  const [selected, setSelected] = useState(null);
  const [results, setResults] = useState({}); // position → { status, data, error }

  async function analyze(position, matchIds) {
    setSelected(position);
    if (results[position]?.status === 'done') return;
    setResults((r) => ({ ...r, [position]: { status: 'loading' } }));
    try {
      const data = await requestWeaknesses(puuid, position, matchIds);
      setResults((r) => ({ ...r, [position]: { status: 'done', data } }));
    } catch (err) {
      setResults((r) => ({ ...r, [position]: { status: 'error', error: err.message } }));
    }
  }

  if (groups.length === 0) return null;

  const current = selected ? results[selected] : null;
  const count = groups.find(([p]) => p === selected)?.[1].length ?? 0;

  return (
    <section className="habit">
      <div className="habit__head">
        <h3>내가 자꾸 반복하는 실수</h3>
        <div className="habit__tabs" role="group" aria-label="포지션 선택">
          {groups.map(([position, ids]) => (
            <button
              key={position}
              className={`chip ${selected === position ? 'chip--on' : ''}`}
              disabled={ids.length < MIN_GAMES}
              onClick={() => analyze(position, ids)}
              title={ids.length < MIN_GAMES ? `${MIN_GAMES}경기 이상 필요해요` : undefined}
            >
              {positionLabel(position)} {ids.length}판
            </button>
          ))}
        </div>
      </div>

      {!current && <p className="quiet">포지션을 고르면 최근 경기를 묶어서 찾아드려요.</p>}

      {current?.status === 'loading' && (
        <div className="panel--msg">
          <div className="waiting__bar" />
          {positionLabel(selected)} {count}판을 모아 보는 중이에요. 1분까지 걸릴 수 있어요
        </div>
      )}

      {current?.status === 'error' && <p className="notice notice--error">{current.error}</p>}

      {current?.status === 'done' && <HabitResult data={current.data} />}
    </section>
  );
}

function HabitResult({ data }) {
  const { stats, ai } = data;
  const [first, ...rest] = ai.weaknesses;
  const noWardRate =
    stats.deathsBefore15 > 0
      ? Math.round((stats.deathsWithNoWardBefore15 / stats.deathsBefore15) * 100)
      : null;

  return (
    <div className="habit__result">
      <p className="coach__label">다음 몇 판은 이것만</p>
      <p className="coach__headline">{ai.focus}</p>

      <div className="numbers">
        <Stat label="15분 CS 차이" value={signed(stats.avgCsDiffAt15)} bad={stats.avgCsDiffAt15 < 0} />
        <Stat label="15분 전 데스" value={`${stats.avgDeathsBefore15}번`} bad={stats.avgDeathsBefore15 >= 1.5} />
        <Stat
          label="와드 없이 죽음"
          value={noWardRate === null ? '-' : `${noWardRate}%`}
          bad={noWardRate !== null && noWardRate >= 50}
        />
      </div>

      {first && <Mistake mistake={first} big badge={first.frequency} />}

      {rest.length > 0 && (
        <details className="more">
          <summary>다른 습관 {rest.length}개 더 보기</summary>
          {rest.map((w, i) => (
            <Mistake key={i} mistake={w} badge={w.frequency} />
          ))}
        </details>
      )}

      {ai.strengths.length > 0 && (
        <div className="good">
          <span className="good__label">잘하고 있는 점</span>
          {ai.strengths.map((s, i) => (
            <p key={i}>{s}</p>
          ))}
        </div>
      )}

      <details className="more">
        <summary>자세한 통계 보기</summary>
        <p className="quiet">{ai.summary}</p>
        <dl className="facts">
          <dt>분석한 경기</dt>
          <dd>
            {stats.games}판 ({stats.wins}승)
          </dd>
          <dt>15분 전 데스 중 정글 갱 / 로밍</dt>
          <dd>
            {stats.jungleGankDeathsBefore15} / {stats.roamDeathsBefore15}
          </dd>
          <dt>솔로킬 / 솔로데스</dt>
          <dd>
            {stats.soloKills} / {stats.soloDeaths}
          </dd>
          <dt>1차 포탑을 먼저 잃은 판</dt>
          <dd>
            {stats.gamesLostOuterTowerFirst} / {stats.games}
          </dd>
          <dt>15분까지 평균 와드</dt>
          <dd>{stats.avgWardsBefore15}개</dd>
        </dl>
      </details>
    </div>
  );
}

function Stat({ label, value, bad }) {
  return (
    <div className="number">
      <strong className={bad ? 'number__val--bad' : ''}>{value}</strong>
      <span>{label}</span>
    </div>
  );
}
