import { useId } from 'react';

/**
 * 분 단위 CS 격차(나 - 상대) 그래프.
 * 0 선 위는 내가 앞선 구간(청록), 아래는 뒤처진 구간(빨강).
 * 가장 크게 손해 본 3분 구간은 배경을 칠하고, 데스 시점은 아래에 점으로 표시한다.
 */
export default function CsDiffChart({ timeline, swing, deaths }) {
  const uid = useId().replace(/:/g, '');
  if (!timeline || timeline.length < 2) {
    return <p className="lane__empty">그래프를 그릴 데이터가 부족해요.</p>;
  }

  const W = 600;
  const H = 180;
  const PAD = { top: 14, right: 12, bottom: 26, left: 36 };
  const innerW = W - PAD.left - PAD.right;
  const innerH = H - PAD.top - PAD.bottom;

  const maxMinute = timeline[timeline.length - 1].minute || 1;
  const maxAbs = Math.max(10, ...timeline.map((p) => Math.abs(p.csDiff)));
  const yMax = Math.ceil(maxAbs / 10) * 10;

  const x = (minute) => PAD.left + (minute / maxMinute) * innerW;
  const y = (diff) => PAD.top + innerH / 2 - (diff / yMax) * (innerH / 2);
  const zeroY = y(0);

  const line = timeline.map((p) => `${x(p.minute)},${y(p.csDiff)}`).join(' ');
  const area = `${x(timeline[0].minute)},${zeroY} ${line} ${x(maxMinute)},${zeroY}`;

  const xTicks = [0, 5, 10, 15, 20].filter((m) => m <= maxMinute);

  return (
    <svg className="chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="분당 CS 격차 그래프">
      <defs>
        <clipPath id={`above-${uid}`}>
          <rect x="0" y="0" width={W} height={zeroY} />
        </clipPath>
        <clipPath id={`below-${uid}`}>
          <rect x="0" y={zeroY} width={W} height={H - zeroY} />
        </clipPath>
      </defs>

      {swing && (
        <rect
          className="chart__swing"
          x={x(swing.fromMinute)}
          y={PAD.top}
          width={x(swing.toMinute) - x(swing.fromMinute)}
          height={innerH}
        />
      )}

      {[yMax, 0, -yMax].map((v) => (
        <g key={v}>
          <line
            className={v === 0 ? 'chart__zero' : 'chart__grid'}
            x1={PAD.left}
            x2={W - PAD.right}
            y1={y(v)}
            y2={y(v)}
          />
          <text className="chart__label" x={PAD.left - 6} y={y(v) + 4} textAnchor="end">
            {v > 0 ? `+${v}` : v}
          </text>
        </g>
      ))}

      <polygon className="chart__area chart__area--up" points={area} clipPath={`url(#above-${uid})`} />
      <polygon className="chart__area chart__area--down" points={area} clipPath={`url(#below-${uid})`} />
      <polyline className="chart__line" points={line} />

      {deaths?.map((d) => (
        <circle
          key={d.seconds}
          className="chart__death"
          cx={x(Math.min(d.seconds / 60, maxMinute))}
          cy={H - PAD.bottom + 4}
          r="4"
        >
          <title>{`${d.time} 데스`}</title>
        </circle>
      ))}

      {xTicks.map((m) => (
        <text key={m} className="chart__label" x={x(m)} y={H - 4} textAnchor="middle">
          {m}분
        </text>
      ))}
    </svg>
  );
}
