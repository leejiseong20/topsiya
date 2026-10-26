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

/** 화면용 부호: 양수 +, 음수는 진짜 마이너스 기호 */
export function signed(n) {
  if (n === null || n === undefined) return '-';
  if (n > 0) return `+${n}`;
  if (n < 0) return `−${Math.abs(n)}`;
  return '0';
}

/** 눈에 띄게 표시할 데스 (상대가 더 와서 죽은 것). 라인 교전·소규모 교전·포탑은 기본색 */
export function isAlertDeath(type) {
  return !['라인 교전', '소규모 교전', '포탑/미니언'].includes(type);
}

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
