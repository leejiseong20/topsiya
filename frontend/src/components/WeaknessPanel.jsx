import { useMemo, useState } from 'react';
import { requestWeaknesses } from '../api.js';
import { positionLabel } from '../format.js';

const MIN_GAMES = 2;

function signed(n) {
  if (n === null || n === undefined) return '-';
  return n > 0 ? `+${n}` : `${n}`;
}

/** 최근 경기 중 같은 포지션 경기를 묶어 반복되는 약점을 분석한다 */
export default function WeaknessPanel({ matches, puuid }) {
  // 포지션별로 분석 가능한(맞라인 있는) 경기 모으기
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
  const currentCount = groups.find(([p]) => p === selected)?.[1].length ?? 0;

  return (
    <section className="weak">
      <div className="weak__head">
        <div>
          <h3>반복되는 약점 찾기</h3>
          <p>같은 포지션 경기를 묶어서, 여러 판에 걸쳐 반복되는 습관을 찾아요.</p>
        </div>
        <div className="weak__tabs">
          {groups.map(([position, ids]) => (
            <button
              key={position}
              className={`weak__tab ${selected === position ? 'weak__tab--on' : ''}`}
              disabled={ids.length < MIN_GAMES}
              onClick={() => analyze(position, ids)}
              title={ids.length < MIN_GAMES ? `${MIN_GAMES}경기 이상 필요해요` : ''}
            >
              {positionLabel(position)} <span>{ids.length}경기</span>
            </button>
          ))}
        </div>
      </div>

      {current?.status === 'loading' && (
        <div className="weak__loading">
          <div className="loading__bar" />
          {positionLabel(selected)} {currentCount}경기 데이터를 모아 분석하는 중이에요… (최대 1분)
        </div>
      )}

      {current?.status === 'error' && <p className="weak__error">{current.error}</p>}

      {current?.status === 'done' && <WeaknessResult data={current.data} />}
    </section>
  );
}

function WeaknessResult({ data }) {
  const { stats, ai } = data;
  const noWardRate =
    stats.deathsBefore15 > 0
      ? Math.round((stats.deathsWithNoWardBefore15 / stats.deathsBefore15) * 100)
      : null;

  const tiles = [
    {
      label: '15분 평균 CS 격차',
      value: signed(stats.avgCsDiffAt15),
      sub: `${stats.gamesBehindInCsAt15}경기에서 뒤처짐`,
      bad: stats.avgCsDiffAt15 !== null && stats.avgCsDiffAt15 < 0,
    },
    {
      label: '15분 전 평균 데스',
      value: `${stats.avgDeathsBefore15}회`,
      sub: `갱 ${stats.gankedDeathsBefore15} · 솔로 ${stats.soloDeaths}`,
      bad: stats.avgDeathsBefore15 >= 1.5,
    },
    {
      label: '와드 없이 죽은 비율',
      value: noWardRate === null ? '-' : `${noWardRate}%`,
      sub: `${stats.deathsWithNoWardBefore15} / ${stats.deathsBefore15} 데스`,
      bad: noWardRate !== null && noWardRate >= 50,
    },
    {
      label: '1차 포탑 먼저 잃음',
      value: `${stats.gamesLostOuterTowerFirst} / ${stats.games}`,
      sub: `방패 평균 ${stats.avgPlatesTaken} 획득 · ${stats.avgPlatesLost} 손실`,
      bad: stats.gamesLostOuterTowerFirst * 2 > stats.games,
    },
  ];

  return (
    <div className="weak__result">
      <div className="weak__tiles">
        {tiles.map((t) => (
          <div key={t.label} className="tile">
            <div className="tile__label">{t.label}</div>
            <div className={`tile__value ${t.bad ? 'tile__value--down' : ''}`}>{t.value}</div>
            <div className="tile__sub">{t.sub}</div>
          </div>
        ))}
      </div>

      <div className="coach">
        <div className="coach__badge">AI 분석 · {stats.games}경기 ({stats.wins}승)</div>
        <p className="coach__summary">{ai.summary}</p>

        {ai.weaknesses.length > 0 && (
          <ol className="mistakes">
            {ai.weaknesses.map((w, i) => (
              <li key={i} className="mistake">
                <div className="mistake__head">
                  <span className="mistake__time">약점 {i + 1}</span>
                  <span className="mistake__what">{w.title}</span>
                  <span className="weak__freq">{w.frequency}</span>
                </div>
                <div className="mistake__row">
                  <em>근거</em>
                  <span>{w.evidence}</span>
                </div>
                <div className="mistake__row mistake__row--fix">
                  <em>개선</em>
                  <span>{w.fix}</span>
                </div>
              </li>
            ))}
          </ol>
        )}

        {ai.strengths.length > 0 && (
          <div className="coach__good">
            <em>강점</em>
            <ul>
              {ai.strengths.map((s, i) => (
                <li key={i}>{s}</li>
              ))}
            </ul>
          </div>
        )}

        <div className="coach__tip">
          <em>다음 몇 판 집중</em>
          <span>{ai.focus}</span>
        </div>
      </div>
    </div>
  );
}
