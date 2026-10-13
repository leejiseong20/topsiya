import { useEffect, useState } from 'react';
import { fetchRecentMatches, parseRiotId } from './api.js';
import { loadChampionData } from './ddragon.js';
import MatchCard from './components/MatchCard.jsx';

export default function App() {
  const [input, setInput] = useState('');
  const [status, setStatus] = useState('idle'); // idle | loading | done | error
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);
  const [champData, setChampData] = useState({ version: null, names: {}, ids: {} });

  useEffect(() => {
    loadChampionData().then(setChampData);
  }, []);

  async function handleSubmit(e) {
    e.preventDefault();
    const riotId = parseRiotId(input);
    if (!riotId) {
      setStatus('error');
      setError('닉네임#태그 형식으로 입력하세요. (예: Hide on bush#KR1)');
      return;
    }

    setStatus('loading');
    setError('');
    try {
      const data = await fetchRecentMatches(riotId.gameName, riotId.tagLine, 10);
      setResult(data);
      setStatus('done');
    } catch (err) {
      setStatus('error');
      setError(err.message);
    }
  }

  const topCount = result?.matches.filter((m) => m.position === 'TOP').length ?? 0;

  return (
    <div className="page">
      <header className={`hero ${result ? 'hero--compact' : ''}`}>
        <div className="brand">
          <span className="brand__mark">▲</span>
          <h1>탑시야</h1>
        </div>
        <p className="hero__tagline">소환사명만 입력하면, AI가 당신의 라인전을 복기합니다</p>

        <form className="search" onSubmit={handleSubmit}>
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="닉네임#태그  (예: Hide on bush#KR1)"
            aria-label="Riot ID"
            autoFocus
          />
          <button type="submit" disabled={status === 'loading'}>
            {status === 'loading' ? '불러오는 중' : '검색'}
          </button>
        </form>

        {status === 'error' && <p className="message message--error">{error}</p>}
      </header>

      {status === 'loading' && (
        <div className="loading">
          <div className="loading__bar" />
          <p>Riot 서버에서 최근 경기를 가져오고 있어요…</p>
        </div>
      )}

      {status === 'done' && result && (
        <main className="results">
          <div className="results__head">
            <h2>
              {result.gameName}
              <span>#{result.tagLine}</span>
            </h2>
            <p>
              최근 {result.matches.length}경기 · 탑 {topCount}경기
            </p>
          </div>

          {result.matches.length === 0 ? (
            <p className="message">최근 경기 기록이 없어요.</p>
          ) : (
            <div className="match-list">
              {result.matches.map((match) => (
                <MatchCard
                  key={match.matchId}
                  match={match}
                  puuid={result.puuid}
                  champData={champData}
                />
              ))}
            </div>
          )}
        </main>
      )}

      <footer className="footer">
        탑시야는 Riot Games가 보증하거나 후원하는 서비스가 아닙니다.
      </footer>
    </div>
  );
}
