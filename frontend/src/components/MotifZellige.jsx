import { useId } from 'react';

/**
 * Motif zellige géométrique — signature visuelle IT6.
 *
 * Zellige traditionnel riche, tracé 100 % SVG inline (aucune image ni
 * ressource externe, compatible CSP stricte) et dessiné sur une tuile de
 * 56×56 qui se répète sans couture :
 *
 *  - Couche 1 (trait plein) : khatam central — étoile à 8 branches formée
 *    de deux carrés pivotés de 45° — doublée d'un khatam intérieur, de
 *    8 rayons (pétales) et d'un cœur circulaire.
 *  - Couche 2 (opacité réduite, profondeur) : losanges d'angle (avec losange
 *    imbriqué), losanges de bord moitié et traits de liaison qui relient le
 *    khatam aux angles et au bords de la tuile — l'entrelacs classique.
 *
 * La teinte hérite de currentColor, l'opacité se règle côté CSS selon le
 * fond ; le motif est symétrique (rotations de 45° et reflets), donc
 * compatible RTL.
 *
 * @param {string} className classes additionnelles (positionnement CSS)
 * @param {number} taille   côté de la tuile du motif (56 = fond de page,
 *                          12 = bandeau d'en-tête fin)
 */
export default function MotifZellige({ className = '', taille = 56 }) {
  const id = `zellige-${useId().replace(/:/g, '')}`;
  const echelle = taille / 56;

  // --- Géométrie de la tuile 56×56, centre (28, 28) ---------------------
  const C = 28;                 // centre de la tuile
  const ext = 19.8;             // rayon des pointes du khatam extérieur
  const int = 11.3;             // rayon des pointes du khatam intérieur
  const R = (deg) => (deg * Math.PI) / 180;
  const pol = (r, deg) => [C + r * Math.cos(R(deg)), C + r * Math.sin(R(deg))];
  const fmt = (n) => Number(n.toFixed(2));

  // 8 pétales : rayons du cœur vers les pointes du khatam extérieur
  const petales = [0, 45, 90, 135, 180, 225, 270, 315].map((d) => {
    const [x1, y1] = pol(4.2, d);
    const [x2, y2] = pol(ext, d);
    return (
      <line key={d} x1={fmt(x1)} y1={fmt(y1)} x2={fmt(x2)} y2={fmt(y2)} />
    );
  });

  // Traits de liaison : pointes du khatam → losanges d'angle / de bord
  const diagonales = [
    [3.18, 3.18, 14, 14],       // angle haut-gauche
    [52.82, 3.18, 42, 14],      // angle haut-droit
    [3.18, 52.82, 14, 42],      // angle bas-gauche
    [52.82, 52.82, 42, 42],     // angle bas-droit
  ].map(([x1, y1, x2, y2], i) => (
    <line key={`d${i}`} x1={x1} y1={y1} x2={x2} y2={y2} />
  ));
  const axes = [
    [47.8, 28, 49.64, 28],      // bord droit
    [8.2, 28, 6.36, 28],        // bord gauche
    [28, 47.8, 28, 49.64],      // bord bas
    [28, 8.2, 28, 6.36],        // bord haut
  ].map(([x1, y1, x2, y2], i) => (
    <line key={`a${i}`} x1={x1} y1={y1} x2={x2} y2={y2} />
  ));

  // Losanges d'angle (R = 6.36, demi-côté 4.5) centrés sur les 4 angles ;
  // imbriqués d'un petit losange (R = 3, demi-côté 2.12).
  const angles = [
    [0, 0],
    [56, 0],
    [0, 56],
    [56, 56],
  ].map(([cx, cy]) => (
    <g key={`${cx}-${cy}`}>
      <rect
        x={cx - 4.5} y={cy - 4.5} width={9} height={9}
        transform={`rotate(45 ${cx} ${cy})`}
      />
      <rect
        x={cx - 2.12} y={cy - 2.12} width={4.24} height={4.24}
        transform={`rotate(45 ${cx} ${cy})`}
      />
    </g>
  ));

  // Losanges de bord « moitié visibles » (R = 6.36) aux milieux des bords
  const bords = [
    'M56,21.64 L49.64,28 L56,34.36',
    'M0,21.64 L6.36,28 L0,34.36',
    'M21.64,0 L28,6.36 L34.36,0',
    'M21.64,56 L28,49.64 L34.36,56',
  ].map((d, i) => <path key={`b${i}`} d={d} />);

  return (
    <svg
      className={`motif-zellige ${className}`}
      aria-hidden="true"
      focusable="false"
      preserveAspectRatio="xMidYMid slice"
    >
      <defs>
        <pattern id={id} width={taille} height={taille} patternUnits="userSpaceOnUse">
          <g
            fill="none"
            stroke="currentColor"
            transform={`scale(${echelle})`}
            strokeLinejoin="round"
          >
            {/* --- Couche 2 : entrelacs et losanges (profondeur) --- */}
            <g opacity="0.5" strokeWidth="1">
              {diagonales}
              {axes}
              {angles}
              {bords}
            </g>

            {/* --- Couche 1 : khatam central, pétales, cœur --- */}
            <g strokeWidth="1.3">
              {/* Khatam extérieur : deux carrés 28×28, l'un pivoté de 45° */}
              <rect x="14" y="14" width="28" height="28" />
              <rect x="14" y="14" width="28" height="28" transform="rotate(45 28 28)" />
              {/* Khatam intérieur : deux carrés 16×16 */}
              <rect x="20" y="20" width="16" height="16" />
              <rect x="20" y="20" width="16" height="16" transform="rotate(45 28 28)" />
              {/* 8 pétales rayonnant du cœur aux pointes */}
              {petales}
              {/* Cœur : cercle + point */}
              <circle cx="28" cy="28" r="3.6" />
              <circle cx="28" cy="28" r="1.2" fill="currentColor" stroke="none" />
            </g>
          </g>
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill={`url(#${id})`} />
    </svg>
  );
}
