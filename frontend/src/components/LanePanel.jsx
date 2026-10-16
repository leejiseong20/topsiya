import { useEffect, useState } from 'react';
import { fetchLaneReport } from '../api.js';
import { championKoName } from '../ddragon.js';
import CsDiffChart from './CsDiffChart.jsx';
import CoachCard from './CoachCard.jsx';

/** 화면용 부호: 양수 +, 음수는 진짜 마이너스 기호 */
export function signed(n) {
  if (n === null || n === undefined) return '-';
  if (n > 0) return `+${n}`;
  if (n < 0) return `−${Math.abs(n)}`;
  return '0';
}

function tone(n) {
  if (n > 0) return 'good';
  if (n < 0) return 'bad';
  return 'even';
}

export default function LanePanel({ matchId, puuid, champData }) {
  const [report, setReport] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    fetchLaneReport(matchId, puuid)
      .then((data) => !cancelled && setReport(data))
      .catch((err) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [matchId, puuid]);

  if (error) return <div className="panel panel--msg notice--error">{error}</div>;
  if (!report) {
    return (
      <div className="panel panel--msg">
        <div className="waiting__bar" />
        경기 기록을 살펴보는 중이에요
      </div>
    );
  }

  const name = (c) => championKoName(champData, c);

  return (
    <section className="panel">
      <CoachCard matchId={matchId} puuid={puuid} />

      <h4 className="panel__title">
        라인전 숫자로 보기 <span>{name(report.myChampion)} vs {name(report.opponentChampion)}</span>
      </h4>

      <div className="checkpoints">
        {report.checkpoints.map((c) => (
          <div key={c.minute} className="checkpoint">
            <span className="checkpoint__min">{c.minute}분 CS</span>
            <strong className={`checkpoint__val checkpoint__val--${tone(c.csDiff)}`}>
              {signed(c.csDiff)}
            </strong>
            <span className="checkpoint__sub">골드 {signed(c.goldDiff)}</span>
          </div>
        ))}
      </div>

      <div className="chart-box">
        {report.biggestCsSwing && (
          <p className="chart-box__note">
            {report.biggestCsSwing.fromMinute}~{report.biggestCsSwing.toMinute}분에 CS{' '}
            {Math.abs(report.biggestCsSwing.change)}개를 가장 크게 잃었어요
          </p>
        )}
        <CsDiffChart timeline={report.timeline} swing={report.biggestCsSwing} deaths={report.deaths} />
      </div>

      <h4 className="panel__title">
        죽은 순간 <span>20분까지 {report.deaths.length}번</span>
      </h4>
      {report.deaths.length === 0 ? (
        <p className="quiet">20분까지 한 번도 안 죽었어요.</p>
      ) : (
        <ul className="deaths">
          {report.deaths.map((d) => (
            <li key={d.seconds} className="death">
              <span className="death__time">{d.time}</span>
              <span className={`death__type ${d.type === '라인 교전' ? '' : 'death__type--alert'}`}>
                {d.type}
              </span>
              <span className="death__who">
                {[d.killer, ...d.assisters].map(name).join(', ')}
              </span>
              {d.myWardsLast2Min === 0 && <span className="death__ward">와드 없음</span>}
            </li>
          ))}
        </ul>
      )}

      <details className="more">
        <summary>라인 지표 더 보기</summary>
        <dl className="facts">
          <dt>솔로킬 / 솔로데스 (15분까지)</dt>
          <dd>
            {report.soloKills} / {report.soloDeaths}
          </dd>
          <dt>1차 포탑 방패 획득 / 손실</dt>
          <dd>
            {report.platesTaken} / {report.platesLost}
          </dd>
          <dt>내 1차 포탑이 깨진 시각</dt>
          <dd>{report.myOuterTowerLostAt ?? '안 깨짐'}</dd>
          <dt>상대 1차 포탑을 깬 시각</dt>
          <dd>{report.enemyOuterTowerDestroyedAt ?? '못 깸'}</dd>
          <dt>15분까지 설치한 와드</dt>
          <dd>{report.wardsPlacedBefore15}개</dd>
        </dl>
        <ul className="deaths deaths--detail">
          {report.deaths.map((d) => (
            <li key={d.seconds}>
              {d.time} {d.zone}에서, 죽기 전 2분 동안 와드 {d.myWardsLast2Min}개
            </li>
          ))}
        </ul>
      </details>
    </section>
  );
}
