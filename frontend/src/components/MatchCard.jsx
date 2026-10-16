import { useState } from 'react';
import ChampionIcon from './ChampionIcon.jsx';
import LanePanel from './LanePanel.jsx';
import { championKoName } from '../ddragon.js';
import { positionLabel, queueLabel, timeAgo } from '../format.js';

export default function MatchCard({ match, puuid, champData }) {
  const [open, setOpen] = useState(false);
  const canAnalyze = Boolean(match.opponentChampionName);
  const result = match.win ? 'win' : 'lose';

  return (
    <div className={`match match--${result} ${open ? 'match--open' : ''}`}>
      <div className="match__row">
        <span className="match__result">{match.win ? '승' : '패'}</span>

        <ChampionIcon data={champData} championName={match.championName} size={48} />
        <div className="match__who">
          <strong>{championKoName(champData, match.championName)}</strong>
          <span>
            {match.position ? positionLabel(match.position) : queueLabel(match.queueId)}
            <i>{timeAgo(match.gameEndTimestamp)}</i>
          </span>
        </div>

        <div className="match__kda" aria-label="킬 데스 어시스트">
          {match.kills}/<em>{match.deaths}</em>/{match.assists}
        </div>

        <div className="match__vs">
          {canAnalyze && (
            <>
              <span>vs</span>
              <ChampionIcon data={champData} championName={match.opponentChampionName} size={32} />
            </>
          )}
        </div>

        {canAnalyze ? (
          <button
            className="match__open"
            onClick={() => setOpen((v) => !v)}
            aria-expanded={open}
          >
            {open ? '닫기' : '분석'}
          </button>
        ) : (
          <span className="match__na">분석 불가</span>
        )}
      </div>

      {open && <LanePanel matchId={match.matchId} puuid={puuid} champData={champData} />}
    </div>
  );
}
