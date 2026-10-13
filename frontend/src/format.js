const POSITION_LABEL = {
  TOP: '탑',
  JUNGLE: '정글',
  MIDDLE: '미드',
  BOTTOM: '원딜',
  UTILITY: '서폿',
};

const QUEUE_LABEL = {
  420: '솔로랭크',
  440: '자유랭크',
  400: '일반',
  430: '일반',
  490: '빠른 대전',
  450: '칼바람',
  1700: '아레나',
};

export const positionLabel = (p) => POSITION_LABEL[p] ?? '-';
export const queueLabel = (q) => QUEUE_LABEL[q] ?? '기타';

export function durationLabel(seconds) {
  const m = Math.floor(seconds / 60);
  const s = String(seconds % 60).padStart(2, '0');
  return `${m}:${s}`;
}

export function kdaRatio(k, d, a) {
  if (d === 0) return 'Perfect';
  return ((k + a) / d).toFixed(2);
}

export function csPerMinute(cs, seconds) {
  if (!seconds) return '0.0';
  return (cs / (seconds / 60)).toFixed(1);
}

export function timeAgo(timestamp) {
  const diff = Date.now() - timestamp;
  const minutes = Math.floor(diff / 60000);
  if (minutes < 60) return `${Math.max(minutes, 1)}분 전`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;
  return `${Math.floor(hours / 24)}일 전`;
}
