import { useEffect, useState } from 'react';
import { fetchLaneReport } from '../api.js';
import { championKoName } from '../ddragon.js';
import CsDiffChart from './CsDiffChart.jsx';

function signed(n) {
  return n > 0 ? `+${n}` : `${n}`;
}

function tone(n) {
  if (n > 0) return 'up';
  if (n < 0) return 'down';
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

  if (error) return <div className="lane lane--error">{error}</div>;
  if (!report) {
    return (
      <div className="lane lane--loading">
        <div className="loading__bar" />
        경기 타임라인을 분석하는 중…
      </div>
    );
  }

  const name = (c) => championKoName(champData, c);

  return (
    <section className="lane">
      <header className="lane__head">
        <h3>
          라인전 분석 <span>{name(report.myChampion)} vs {name(report.opponentChampion)}</span>
        </h3>
      </header>

      <div className="lane__checkpoints">
        {report.checkpoints.map((c) => (
          <div key={c.minute} className="tile">
            <div className="tile__label">{c.minute}분</div>
            <div className={`tile__value tile__value--${tone(c.csDiff)}`}>CS {signed(c.csDiff)}</div>
            <div className="tile__sub">
              골드 {signed(c.goldDiff)} · 레벨 {signed(c.levelDiff)}
            </div>
          </div>
        ))}
      </div>

      <div className="lane__chart">
        <div className="lane__chart-title">
          분당 CS 격차
          {report.biggestCsSwing && (
            <span className="lane__swing">
              {report.biggestCsSwing.fromMinute}~{report.biggestCsSwing.toMinute}분에{' '}
              {Math.abs(report.biggestCsSwing.change)}개 손해
            </span>
          )}
        </div>
        <CsDiffChart
          timeline={report.timeline}
          swing={report.biggestCsSwing}
          deaths={report.deaths}
        />
      </div>

      <div className="lane__grid">
        <div>
          <h4>20분까지 데스 {report.deaths.length}회</h4>
          {report.deaths.length === 0 ? (
            <p className="lane__empty">라인전 동안 한 번도 죽지 않았어요.</p>
          ) : (
            <ul className="deaths">
              {report.deaths.map((d) => (
                <li key={d.seconds}>
                  <span className="deaths__time">{d.time}</span>
                  <span>
                    {name(d.killer)}
                    {d.assisters.length > 0 && ` + ${d.assisters.map(name).join(', ')}`}
                  </span>
                  <span className="deaths__tags">
                    {d.ganked && <em className="tag tag--warn">갱</em>}
                    <em className="tag">{d.zone}</em>
                    <em className={`tag ${d.myWardsLast2Min === 0 ? 'tag--warn' : ''}`}>
                      직전 와드 {d.myWardsLast2Min}
                    </em>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div>
          <h4>라인 지표</h4>
          <dl className="facts">
            <dt>솔로킬 / 솔로데스 (15분)</dt>
            <dd>
              {report.soloKills} / {report.soloDeaths}
            </dd>
            <dt>포탑 방패 획득 / 손실</dt>
            <dd>
              {report.platesTaken} / {report.platesLost}
            </dd>
            <dt>내 1차 포탑 파괴</dt>
            <dd>{report.myOuterTowerLostAt ?? '안 깨짐'}</dd>
            <dt>상대 1차 포탑 파괴</dt>
            <dd>{report.enemyOuterTowerDestroyedAt ?? '못 깸'}</dd>
            <dt>15분까지 와드 설치</dt>
            <dd>{report.wardsPlacedBefore15}개</dd>
          </dl>
        </div>
      </div>

      <p className="lane__note">AI 코칭 리포트는 다음 단계에서 이 데이터를 바탕으로 추가돼요.</p>
    </section>
  );
}
