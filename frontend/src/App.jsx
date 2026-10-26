import { useEffect, useState } from 'react';
import { fetchAppInfo, fetchRecentMatches, parseRiotId } from './api.js';
import { loadChampionData } from './ddragon.js';
import MatchCard from './components/MatchCard.jsx';
import WeaknessPanel from './components/WeaknessPanel.jsx';
import GoalPanel, { GoalProvider } from './components/GoalPanel.jsx';
import EvalPage from './components/EvalPage.jsx';

/** 주소 끝이 #eval 이면 정확도 측정 화면 */
function useHash() {
  const [hash, setHash] = useState(window.location.hash);
  useEffect(() => {
    const onChange = () => setHash(window.location.hash);
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return hash;
}

export default function App() {
  const [input, setInput] = useState('');
  const [status, setStatus] = useState('idle'); // idle | loading | done | error
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);
  const [champData, setChampData] = useState({ version: null, names: {}, ids: {}, tags: {} });
  const hash = useHash();
  const [appInfo, setAppInfo] = useState({ devTools: false, demoMode: false });

  useEffect(() => {
    fetchAppInfo().then(setAppInfo).catch(() => {});
  }, []);

  useEffect(() => {
    loadChampionData().then(setChampData);
  }, []);

  async function handleSubmit(e) {
    e.preventDefault();
    const riotId = parseRiotId(input);
    if (!riotId) {
      setStatus('error');
      setError('닉네임 뒤에 #태그까지 입력해 주세요. 예) Hide on bush#KR1');
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

  const searched = Boolean(result) || status === 'loading';

  // 개발자 화면: 서버에서 dev-tools 를 켰을 때만
  if (hash === '#eval' && appInfo.devTools) {
    return (
      <div className="page">
        <EvalPage />
      </div>
    );
  }

  return (
    <div className="page">
      <header className={`hero ${searched ? 'hero--top' : ''}`}>
        <h1 className="logo">
          <LogoMark />
          <span className="logo__text">
            <span className="logo__name">탑시야</span>
            <span className="logo__sub">AI LANE COACH</span>
          </span>
        </h1>
        {!searched && (
          <p className="hero__lead">
            경기 타임라인을 분석해, 라인전에서 무엇을 고쳐야 하는지 알려드려요
          </p>
        )}

        <form className="search" onSubmit={handleSubmit}>
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="닉네임#태그"
            aria-label="Riot ID (닉네임#태그)"
            autoFocus
          />
          <button type="submit" disabled={status === 'loading'}>
            {status === 'loading' ? '찾는 중' : '검색'}
          </button>
        </form>

        {status === 'error' && <p className="notice notice--error">{error}</p>}
      </header>

      {status === 'loading' && (
        <div className="waiting">
          <div className="waiting__bar" />
          최근 경기를 불러오고 있어요
        </div>
      )}

      {status === 'done' && result && (
        <GoalProvider puuid={result.puuid}>
          <main className="results">
            <h2 className="player">
              {result.gameName}
              <span>#{result.tagLine}</span>
            </h2>

            {result.matches.length === 0 ? (
              <p className="notice">최근 경기 기록이 없어요.</p>
            ) : (
              <>
                <GoalPanel champData={champData} />
                <WeaknessPanel
                  key={result.puuid}
                  matches={result.matches}
                  puuid={result.puuid}
                  champData={champData}
                />

                <h3 className="section-title">최근 {result.matches.length}경기</h3>
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
              </>
            )}
          </main>
        </GoalProvider>
      )}

      <footer className="footer">
        {appInfo.devTools && <a href="#eval">개발자: AI 답변 정확도</a>}
        {appInfo.demoMode && <span className="footer__demo">데모 모드 · 저장된 데이터로 동작 중</span>}
        <span>탑시야는 Riot Games가 보증하거나 후원하는 서비스가 아닙니다.</span>
      </footer>
    </div>
  );
}

/** 미니맵 모양 심볼: 왼쪽·위 가장자리를 따라 꺾이는 탑 라인을 강조 */
function LogoMark() {
  return (
    <svg className="logo__mark" viewBox="0 0 48 48" aria-hidden="true">
      <rect x="1" y="1" width="46" height="46" rx="11" fill="#13282c" stroke="#24444a" strokeWidth="2" />
      {/* 미드·봇 라인 (흐리게) */}
      <path d="M14 34 L34 14" stroke="#2f5157" strokeWidth="3" strokeLinecap="round" />
      <path d="M14 34 H34 V14" stroke="#2f5157" strokeWidth="3" fill="none" strokeLinecap="round" strokeLinejoin="round" />
      {/* 탑 라인 (강조) */}
      <path d="M14 34 V18 Q14 14 18 14 H34" stroke="#f4b740" strokeWidth="4.5" fill="none" strokeLinecap="round" />
    </svg>
  );
}
