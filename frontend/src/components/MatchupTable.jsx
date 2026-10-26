import { championKoName, championRole } from '../ddragon.js';
import { signed } from '../format.js';

/**
 * 상대 유형(전사·탱커·마법사 …)별 라인전 성적.
 * 같은 챔피언은 10판 안에 잘 안 겹치니, 역할군으로 묶어야 패턴이 보인다.
 */
export function matchupRows(perGame, champData) {
  const groups = new Map();
  perGame.forEach((g) => {
    const role = championRole(champData, g.opponentChampion);
    if (!role) return;
    if (!groups.has(role)) groups.set(role, []);
    groups.get(role).push(g);
  });
  return [...groups.entries()]
    .map(([role, games]) => {
      const cs = games.map((g) => g.csDiffAt15).filter((v) => v !== null && v !== undefined);
      return {
        role,
        games: games.length,
        wins: games.filter((g) => g.win).length,
        cs15: cs.length ? Math.round(cs.reduce((a, b) => a + b, 0) / cs.length) : null,
        deaths: Math.round((games.reduce((a, g) => a + g.deathsBefore15, 0) / games.length) * 10) / 10,
        champions: [...new Set(games.map((g) => championKoName(champData, g.opponentChampion)))],
      };
    })
    .sort((a, b) => b.games - a.games);
}

/** 2판 이상인 유형끼리 비교해서, 확실히 약한 유형이 있으면 한 줄로 알려준다 */
export function weakestMatchup(rows) {
  const enough = rows.filter((r) => r.games >= 2 && r.cs15 !== null);
  if (enough.length < 2) return null;
  const worst = [...enough].sort((a, b) => a.cs15 - b.cs15)[0];
  const others = enough.filter((r) => r !== worst);
  const otherAvg = others.reduce((a, r) => a + r.cs15 * r.games, 0) / others.reduce((a, r) => a + r.games, 0);
  return worst.cs15 <= otherAvg - 5 ? { ...worst, gap: Math.round(otherAvg - worst.cs15) } : null;
}

export default function MatchupTable({ perGame, champData }) {
  if (!perGame?.length) return null;
  const rows = matchupRows(perGame, champData);
  if (rows.length < 2) return null;
  const weak = weakestMatchup(rows);

  return (
    <div className="matchup">
      <p className="matchup__title">상대 유형별 라인전</p>
      {weak ? (
        <p className="matchup__insight">
          <b>{weak.role}</b> 상대로 15분 CS가 평균 {signed(weak.cs15)}개, 다른 유형보다 {weak.gap}개 더 밀려요
          <span> ({weak.champions.join(', ')})</span>
        </p>
      ) : rows.filter((r) => r.games >= 2).length < 2 ? (
        <p className="quiet">비교하려면 같은 유형 상대를 2판 이상씩 더 해봐야 해요.</p>
      ) : (
        <p className="quiet">상대 유형에 따라 크게 달라지는 패턴은 없어요.</p>
      )}
      <details className="more">
        <summary>유형별로 보기</summary>
        <table className="matchup__table">
          <thead>
            <tr>
              <th>상대 유형</th>
              <th>판</th>
              <th>승</th>
              <th>15분 CS 차이</th>
              <th>15분 전 데스</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.role} className={weak?.role === r.role ? 'is-weak' : ''}>
                <th title={r.champions.join(', ')}>{r.role}</th>
                <td>{r.games}</td>
                <td>{r.wins}</td>
                <td>{signed(r.cs15)}</td>
                <td>{r.deaths}번</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  );
}
