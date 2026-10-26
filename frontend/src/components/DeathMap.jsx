import { useState } from 'react';
import { isAlertDeath } from '../format.js';

// 소환사의 협곡 좌표 범위 (Riot 타임라인 기준, 왼쪽 아래가 0)
const MAP_MAX_X = 14870;
const MAP_MAX_Y = 14980;
// 서버(LaneReport.BLUE_SIDE_MAX / RED_SIDE_MIN)와 같은 경계: x + y 로 블루 진영 / 강가 / 레드 진영을 나눈다
const BLUE_SIDE_MAX = 13800;
const RED_SIDE_MIN = 15800;

const toPercent = (x, y) => ({ left: (x / MAP_MAX_X) * 100, top: 100 - (y / MAP_MAX_Y) * 100 });

/** 가까이 붙은 핀은 살짝 비켜 놓는다 (번호가 겹쳐 안 보이지 않게) */
function spread(pins) {
  const placed = [];
  pins.forEach((pin) => {
    let { left, top } = pin;
    for (let tries = 0; tries < 6; tries++) {
      const crowded = placed.some((p) => Math.hypot(p.left - left, p.top - top) < 7);
      if (!crowded) break;
      left += 5;
      top -= 4;
    }
    placed.push({ ...pin, left, top });
  });
  return placed;
}

/** 지도 좌표 → 지도 이미지 위 % 로 바꾼 다각형 */
function polygon(points) {
  return points.map(([x, y]) => {
    const p = toPercent(x, y);
    return `${p.left},${p.top}`;
  }).join(' ');
}

/** 미니맵 위에 죽은 위치를 번호로 찍는다. 우리 진영은 파랑, 상대 진영은 빨강으로 칠한다 */
export default function DeathMap({ deaths, version, myTeamId, active, onSelect }) {
  const [imageOk, setImageOk] = useState(true);
  const pins = spread(
    deaths
      .map((d, i) => ({ ...d, no: i + 1 }))
      .filter((d) => d.x != null && d.y != null)
      .map((d) => ({ ...d, ...toPercent(d.x, d.y) })),
  );
  if (pins.length === 0) return null;

  const known = myTeamId === 100 || myTeamId === 200;
  const iAmBlue = myTeamId === 100;
  // 블루 진영 = 왼쪽 아래 삼각형, 레드 진영 = 오른쪽 위 삼각형 (가운데 띠가 강가)
  const blueArea = polygon([[0, 0], [BLUE_SIDE_MAX, 0], [0, BLUE_SIDE_MAX]]);
  const redArea = polygon([
    [MAP_MAX_X, RED_SIDE_MIN - MAP_MAX_X],
    [MAP_MAX_X, MAP_MAX_Y],
    [RED_SIDE_MIN - MAP_MAX_Y, MAP_MAX_Y],
  ]);

  return (
    <figure className="deathmap-box">
      <div className="deathmap" role="img" aria-label="미니맵에 표시한 데스 위치">
        {version && imageOk ? (
          <img className="deathmap__img" src={`/api/ddragon/${version}/map`} alt="" onError={() => setImageOk(false)} />
        ) : (
          <MapFallback />
        )}

        {known && (
          <svg className="deathmap__zones" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
            <polygon points={blueArea} className={iAmBlue ? 'zone--mine' : 'zone--enemy'} />
            <polygon points={redArea} className={iAmBlue ? 'zone--enemy' : 'zone--mine'} />
          </svg>
        )}
        {known && (
          <>
            <span className={`deathmap__base deathmap__base--blue ${iAmBlue ? 'is-mine' : 'is-enemy'}`}>
              {iAmBlue ? '우리 기지' : '상대 기지'}
            </span>
            <span className={`deathmap__base deathmap__base--red ${iAmBlue ? 'is-enemy' : 'is-mine'}`}>
              {iAmBlue ? '상대 기지' : '우리 기지'}
            </span>
          </>
        )}

        {pins.map((d) => (
          <button
            key={d.seconds}
            type="button"
            className={`deathmap__pin ${isAlertDeath(d.type) ? 'deathmap__pin--alert' : ''} ${
              d.lanePhase ? '' : 'is-late'
            } ${active === d.seconds ? 'is-active' : ''}`}
            style={{ left: `${d.left}%`, top: `${d.top}%` }}
            onClick={() => onSelect(active === d.seconds ? null : d.seconds)}
            aria-label={`${d.no}번 데스 ${d.time} ${d.type} ${d.zone}`}
          >
            {d.no}
          </button>
        ))}
      </div>
    </figure>
  );
}

/** 지도 이미지를 못 불러왔을 때: 세 라인과 강만 간단히 그린다 */
function MapFallback() {
  return (
    <svg className="deathmap__img" viewBox="0 0 100 100" aria-hidden="true">
      <rect width="100" height="100" fill="#163035" />
      <path d="M8 92 V8 H92 M8 92 H92 V8 M8 92 L92 8" stroke="#2f5157" strokeWidth="5" fill="none" />
    </svg>
  );
}
