import { useId } from 'react';

/**
 * Motif zellige géométrique — signature visuelle IT6.
 *
 * Étoile à huit branches (khatam), motif classique du zellige marocain,
 * dessinée par deux carrés superposés pivotés de 45°, avec losanges de
 * liaison pour la continuité du carrelage. Tracé 100 % SVG inline :
 * aucune image ni ressource externe (compatible CSP stricte, `img-src`
 * non requis). La teinte hérite de currentColor et l'opacité se règle
 * côté CSS selon le fond ; le motif est symétrique, donc compatible
 * RTL.
 *
 * @param {string} className classes additionnelles (positionnement CSS)
 * @param {number} taille   côté de la tuile du motif (56 = fond de page,
 *                          12 = bandeau d'en-tête fin)
 */
export default function MotifZellige({ className = '', taille = 56 }) {
  const id = `zellige-${useId().replace(/:/g, '')}`;
  const echelle = taille / 56;
  return (
    <svg
      className={`motif-zellige ${className}`}
      aria-hidden="true"
      focusable="false"
      preserveAspectRatio="xMidYMid slice"
    >
      <defs>
        <pattern id={id} width={taille} height={taille} patternUnits="userSpaceOnUse">
          <g fill="none" stroke="currentColor" strokeWidth={1.2} transform={`scale(${echelle})`}>
            {/* Étoile centrale : deux carrés de 28×28 pivotés de 45° */}
            <rect x="14" y="14" width="28" height="28" />
            <rect x="14" y="14" width="28" height="28" transform="rotate(45 28 28)" />
            {/* Losanges de continuité sur les quatre coins de la tuile */}
            <rect x="0" y="0" width="8" height="8" transform="rotate(45 4 4)" />
            <rect x="48" y="0" width="8" height="8" transform="rotate(45 52 4)" />
            <rect x="0" y="48" width="8" height="8" transform="rotate(45 4 52)" />
            <rect x="48" y="48" width="8" height="8" transform="rotate(45 52 52)" />
          </g>
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill={`url(#${id})`} />
    </svg>
  );
}
