import { useState } from 'react';

// 소환사의 협곡 좌표 범위 (Riot 타임라인 기준, 왼쪽 아래가 0)
const MAP_MAX_X = 14870;
const MAP_MAX_Y = 14980;

const toPercent = (d) => ({
  left: (d.x / MAP_MAX_X) * 100,
  top: 100 - (d.y / MAP_MAX_Y) * 100,
});

/** 미니맵 위에 죽은 위치를 번호로 찍는다. 목록의 번호와 같다 */
export default function DeathMap({ deaths, version, active, onSelect }) {
  const [imageOk, setImageOk] = useState(true);
  const located = deaths.map((d, i) => ({ ...d, no: i + 1 })).filter((d) => d.x != null && d.y != null);
  if (located.length === 0) return null;

  return (
    <div className="deathmap" role="img" aria-label="미니맵에 표시한 데스 위치">
      {version && imageOk ? (
        <img
          className="deathmap__img"
          src={`/api/ddragon/${version}/map`}
          alt=""
          onError={() => setImageOk(false)}
        />
      ) : (
        <MapFallback />
      )}
      {located.map((d) => {
        const { left, top } = toPercent(d);
        return (
          <button
            key={d.seconds}
            type="button"
            className={`deathmap__pin ${d.type === '라인 교전' ? '' : 'deathmap__pin--alert'} ${
              active === d.seconds ? 'is-active' : ''
            }`}
            style={{ left: `${left}%`, top: `${top}%` }}
            onClick={() => onSelect(active === d.seconds ? null : d.seconds)}
            aria-label={`${d.no}번 데스 ${d.time} ${d.type}`}
          >
            {d.no}
          </button>
        );
      })}
    </div>
  );
}

/** 지도 이미지를 못 불러왔을 때: 세 라인과 강만 간단히 그린다 */
function MapFallback() {
  return (
    <svg className="deathmap__img" viewBox="0 0 100 100" aria-hidden="true">
      <rect width="100" height="100" fill="#163035" />
      <path d="M8 92 V8 H92 M8 92 H92 V8 M8 92 L92 8" stroke="#2f5157" strokeWidth="5" fill="none" />
      <path d="M0 40 Q50 50 60 100 M40 0 Q50 50 100 60" stroke="#1d4a5a" strokeWidth="4" fill="none" opacity="0.6" />
    </svg>
  );
}
