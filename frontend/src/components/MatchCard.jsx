import { useState } from 'react';
import ChampionIcon from './ChampionIcon.jsx';
import LanePanel from './LanePanel.jsx';
import { championKoName } from '../ddragon.js';
import {
  csPerMinute,
  durationLabel,
  kdaRatio,
  positionLabel,
  queueLabel,
  timeAgo,
} from '../format.js';

export default function MatchCard({ match, puuid, champData }) {
  const [open, setOpen] = useState(false);
  const isTop = match.position === 'TOP';
  const canAnalyze = Boolean(match.opponentChampionName);
  const result = match.win ? 'win' : 'lose';

  return (
    <div className={`match-wrap ${open ? 'match-wrap--open' : ''}`}>
      <article className={`match match--${result} ${isTop ? 'match--top' : ''}`}>
        <div className="match__meta">
          <span className="match__queue">{queueLabel(match.queueId)}</span>
          <span className={`match__result match__result--${result}`}>
            {match.win ? '승리' : '패배'}
          </span>
          <span className="match__sub">{durationLabel(match.gameDurationSeconds)}</span>
          <span className="match__sub">{timeAgo(match.gameEndTimestamp)}</span>
        </div>

        <div className="match__champ">
          <ChampionIcon data={champData} championName={match.championName} size={52} />
          <div>
            <div className="match__champ-name">{championKoName(champData, match.championName)}</div>
            <div className="match__position">
              {match.position ? `${positionLabel(match.position)} · ` : ''}Lv.{match.champLevel}
            </div>
          </div>
        </div>

        <div className="match__stats">
          <div className="match__kda">
            {match.kills} <span>/</span> <em>{match.deaths}</em> <span>/</span> {match.assists}
          </div>
          <div className="match__sub">평점 {kdaRatio(match.kills, match.deaths, match.assists)}</div>
          <div className="match__sub">
            CS {match.cs} ({csPerMinute(match.cs, match.gameDurationSeconds)})
          </div>
        </div>

        <div className="match__vs">
          {match.opponentChampionName ? (
            <>
              <span className="match__vs-label">VS</span>
              <ChampionIcon data={champData} championName={match.opponentChampionName} size={36} />
            </>
          ) : (
            <span className="match__sub">맞라인 없음</span>
          )}
        </div>

        <div className="match__action">
          {canAnalyze ? (
            <button
              className={`btn-analyze ${open ? 'btn-analyze--open' : ''}`}
              onClick={() => setOpen((v) => !v)}
              aria-expanded={open}
            >
              {open ? '접기' : '라인전 분석'}
              {!isTop && !open && <small>탑 외 라인</small>}
            </button>
          ) : (
            <span className="match__sub">분석 불가</span>
          )}
        </div>
      </article>

      {open && <LanePanel matchId={match.matchId} puuid={puuid} champData={champData} />}
    </div>
  );
}
