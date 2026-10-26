import { Fragment, useEffect, useState } from 'react';
import { fetchLaneReport } from '../api.js';
import { championKoName } from '../ddragon.js';
import CsDiffChart from './CsDiffChart.jsx';
import CoachCard from './CoachCard.jsx';
import DeathMap from './DeathMap.jsx';

import { isAlertDeath, signed } from '../format.js';

export { signed };

/** CS 격차가 이 이상 줄었을 때만 "흐름이 꺾인 구간"으로 보여준다 */
const SWING_MIN = 5;

function isMeaningful(swing) {
  return swing && Math.abs(swing.change) >= SWING_MIN;
}

/** 숫자 뒤 조사: 끝자리가 0·3·6(영·삼·육)이면 '으로', 나머지는 '로' */
function ro(n) {
  return [0, 3, 6].includes(Math.abs(n) % 10) ? '으로' : '로';
}

/** "12~15분에 앞서던 CS 격차가 +9 → +6으로 줄었어요" 처럼 실제 값 변화로 설명한다 */
export function describeSwing(swing, timeline) {
  if (!swing) return null;
  const at = (m) => timeline.find((p) => p.minute === m)?.csDiff;
  const from = at(swing.fromMinute);
  const to = at(swing.toMinute);
  const when = `${swing.fromMinute}~${swing.toMinute}분에`;
  if (from === undefined || to === undefined) {
    return `${when} CS 격차가 ${Math.abs(swing.change)}개 줄었어요`;
  }
  const change = `${signed(from)} → ${signed(to)}${ro(to)}`;
  if (from > 0 && to > 0) return `${when} 앞서던 CS 격차가 ${change} 줄었어요`;
  if (from > 0 && to === 0) return `${when} 앞서던 CS 격차가 ${change} 따라잡혔어요`;
  if (from > 0 && to < 0) return `${when} CS 격차가 ${change} 뒤집혔어요`;
  if (from === 0) return `${when} CS 격차가 ${change} 밀렸어요`;
  return `${when} CS 격차가 ${change} 더 벌어졌어요`;
}

function mmss(seconds) {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

function tone(n) {
  if (n > 0) return 'good';
  if (n < 0) return 'bad';
  return 'even';
}

export default function LanePanel({ matchId, puuid, position, champData }) {
  const [report, setReport] = useState(null);
  const [error, setError] = useState('');
  const [activeDeath, setActiveDeath] = useState(null);

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
  const meaningfulSwing = isMeaningful(report.biggestCsSwing) ? report.biggestCsSwing : null;
  const swingNote = describeSwing(meaningfulSwing, report.timeline);

  return (
    <section className="panel">
      <CoachCard matchId={matchId} puuid={puuid} position={position} />

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
        {swingNote && <p className="chart-box__note">{swingNote}</p>}
        <CsDiffChart timeline={report.timeline} swing={meaningfulSwing} deaths={report.deaths} />
      </div>

      <h4 className="panel__title">
        죽은 순간 <span>20분까지 {report.deaths.length}번</span>
      </h4>
      {report.deaths.length === 0 ? (
        <p className="quiet">20분까지 한 번도 안 죽었어요.</p>
      ) : (
        <div className="death-wrap">
          <DeathMap
            deaths={report.deaths}
            version={champData.version}
            myTeamId={report.myTeamId}
            active={activeDeath}
            onSelect={setActiveDeath}
          />
          <ul className="deaths">
            {report.deaths.map((d, i) => (
              <Fragment key={d.seconds}>
                {!d.lanePhase && (i === 0 || report.deaths[i - 1].lanePhase) && report.laneEndSeconds != null && (
                  <li className="death-divider">
                    라인전 끝 · {mmss(report.laneEndSeconds)} {report.laneEndReason}
                  </li>
                )}
                <li
                  className={`death ${activeDeath === d.seconds ? 'is-active' : ''} ${d.lanePhase ? '' : 'is-late'}`}
                  onMouseEnter={() => setActiveDeath(d.seconds)}
                  onMouseLeave={() => setActiveDeath(null)}
                >
                  <span className={`death__no ${isAlertDeath(d.type) ? 'death__no--alert' : ''}`}>{i + 1}</span>
                  <span className="death__time">{d.time}</span>
                  <span className={`death__type ${isAlertDeath(d.type) ? 'death__type--alert' : ''}`}>
                    {d.type}
                  </span>
                  <span className="death__who">
                    {[d.killer, ...d.assisters].map(name).join(', ')}
                  </span>
                  <span className={`death__zone ${d.zone === '상대 진영' ? 'death__zone--enemy' : ''}`}>
                    {d.zone}
                  </span>
                  {d.myWardsLast2Min === 0 && <span className="death__ward">와드 없음</span>}
                </li>
              </Fragment>
            ))}
          </ul>
        </div>
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
