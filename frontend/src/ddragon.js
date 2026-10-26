// Data Dragon: Riot 이 제공하는 챔피언 이름·이미지 (API 키 불필요)
// 백엔드가 대신 받아서 저장해 두므로, 인터넷이 끊겨도 한 번 본 아이콘은 나온다
const DDRAGON = '/api/ddragon';

let cache = null;

/** 최신 패치 버전과 { 영문ID(소문자): 한글 이름 } 맵을 한 번만 받아온다 */
export async function loadChampionData() {
  if (cache) return cache;
  try {
    const versions = await fetch(`${DDRAGON}/versions.json`).then((r) => r.json());
    const version = versions[0];
    const json = await fetch(`${DDRAGON}/${version}/champion.json`).then((r) =>
      r.json(),
    );
    const names = {};
    const ids = {};
    const tags = {};
    Object.values(json.data).forEach((champ) => {
      names[champ.id.toLowerCase()] = champ.name;
      ids[champ.id.toLowerCase()] = champ.id;
      tags[champ.id.toLowerCase()] = champ.tags ?? [];
    });
    cache = { version, names, ids, tags };
  } catch {
    cache = { version: null, names: {}, ids: {}, tags: {} };
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
  return `${DDRAGON}/${data.version}/icon/${normalizeId(data, championName)}`;
}

const TAG_LABEL = {
  Fighter: '전사',
  Tank: '탱커',
  Mage: '마법사',
  Assassin: '암살자',
  Marksman: '원거리 딜러',
  Support: '서포터',
};

/** 챔피언의 대표 역할군 (Data Dragon 첫 번째 태그). 모르면 null */
export function championRole(data, championName) {
  const tag = data.tags?.[championName?.toLowerCase()]?.[0];
  return tag ? TAG_LABEL[tag] ?? tag : null;
}
