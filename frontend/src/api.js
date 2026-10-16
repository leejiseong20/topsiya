/**
 * "Hide on bush#KR1" → { gameName: "Hide on bush", tagLine: "KR1" }
 * 태그가 없으면 null
 */
export function parseRiotId(input) {
  const text = input.trim();
  const hashIndex = text.lastIndexOf('#');
  if (hashIndex <= 0 || hashIndex === text.length - 1) {
    return null;
  }
  return {
    gameName: text.slice(0, hashIndex).trim(),
    tagLine: text.slice(hashIndex + 1).trim(),
  };
}

/** 백엔드에서 최근 경기 목록을 가져온다. 실패하면 서버가 준 메시지로 Error 를 던진다. */
export function fetchRecentMatches(gameName, tagLine, count = 10) {
  return getJson(
    `/api/summoners/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}` +
      `/matches?count=${count}`,
  );
}

/** 한 경기의 라인전 요약 (CS·골드 격차, 데스 상황 등) */
export function fetchLaneReport(matchId, puuid) {
  return getJson(
    `/api/matches/${encodeURIComponent(matchId)}/lane?puuid=${encodeURIComponent(puuid)}`,
  );
}

/** AI 코칭 리포트 생성 (호출할 때마다 비용이 들 수 있어서 버튼을 눌렀을 때만 부른다) */
export function requestCoaching(matchId, puuid) {
  return getJson(
    `/api/matches/${encodeURIComponent(matchId)}/coach?puuid=${encodeURIComponent(puuid)}`,
    { method: 'POST' },
  );
}

/** 같은 포지션의 여러 경기를 묶어 반복 약점 분석 */
export function requestWeaknesses(puuid, position, matchIds) {
  return getJson('/api/weaknesses', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ puuid, position, matchIds }),
  });
}

async function getJson(url, options) {
  let response;
  try {
    response = await fetch(url, options);
  } catch {
    throw new Error('서버에 연결할 수 없어요. 백엔드가 실행 중인지 확인하세요.');
  }

  if (!response.ok) {
    let message = `오류가 발생했어요. (${response.status})`;
    try {
      const body = await response.json();
      if (body?.message) message = body.message;
    } catch {
      // 본문이 JSON 이 아니면 기본 메시지 사용
    }
    throw new Error(message);
  }
  return response.json();
}
