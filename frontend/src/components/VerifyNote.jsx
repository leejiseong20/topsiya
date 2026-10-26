/** AI 글이 실제 경기 기록과 맞는지 한 줄로 알려준다 (자세한 숫자는 개발자 화면에서) */
export default function VerifyNote({ verification }) {
  if (!verification) return null;
  const { unmatched } = verification;

  if (unmatched.length === 0) {
    return (
      <p className="verify verify--ok">
        <span aria-hidden="true">✓</span>
        실제 경기 기록과 대조한 내용이에요
      </p>
    );
  }

  return (
    <details className="verify verify--warn">
      <summary>
        <span aria-hidden="true">!</span>
        기록에서 확인되지 않은 내용이 있어요
      </summary>
      <ul>
        {unmatched.map((u) => (
          <li key={u}>{u}</li>
        ))}
      </ul>
    </details>
  );
}
