import { championIconUrl, championKoName } from '../ddragon.js';

export default function ChampionIcon({ data, championName, size = 48 }) {
  const url = championIconUrl(data, championName);
  const name = championKoName(data, championName);
  const style = { width: size, height: size };

  if (!url) {
    return (
      <div className="champ-icon champ-icon--empty" style={style} title={name}>
        {name.slice(0, 1)}
      </div>
    );
  }
  return <img className="champ-icon" style={style} src={url} alt={name} title={name} />;
}
