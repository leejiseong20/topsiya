// Data Dragon: Riot 이 제공하는 챔피언 이름·이미지 (API 키 불필요)
const DDRAGON = 'https://ddragon.leagueoflegends.com';

let cache = null;

/** 최신 패치 버전과 { 영문ID(소문자): 한글 이름 } 맵을 한 번만 받아온다 */
export async function loadChampionData() {
  if (cache) return cache;
  try {
    const versions = await fetch(`${DDRAGON}/api/versions.json`).then((r) => r.json());
    const version = versions[0];
    const json = await fetch(`${DDRAGON}/cdn/${version}/data/ko_KR/champion.json`).then((r) =>
      r.json(),
    );
    const names = {};
    const ids = {};
    Object.values(json.data).forEach((champ) => {
      names[champ.id.toLowerCase()] = champ.name;
      ids[champ.id.toLowerCase()] = champ.id;
    });
    cache = { version, names, ids };
  } catch {
    cache = { version: null, names: {}, ids: {} };
  }
  return cache;
}

/** 경기 데이터의 championName("FiddleSticks" 등)을 Data Dragon 기준으로 맞춘다 */
function normalizeId(data, championName) {
  return data.ids[championName?.toLowerCase()] ?? championName;
}

export function championKoName(data, championName) {
  if (!championName) return '';
  return data.names[championName.toLowerCase()] ?? championName;
}

export function championIconUrl(data, championName) {
  if (!data.version || !championName) return null;
  return `${DDRAGON}/cdn/${data.version}/img/champion/${normalizeId(data, championName)}.png`;
}
