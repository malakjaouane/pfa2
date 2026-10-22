import { useEffect, useState } from 'react';

/**
 * Détecte la préférence système `prefers-reduced-motion: reduce`.
 * Retourne `true` (animations à désactiver) lorsque l'utilisateur a
 * demandé des animations réduites, et suit les changements en direct.
 *
 * Usage : `const reduit = useReducedMotion();` puis neutraliser les
 * effets JS (machine à écrire, compteurs, défilement fluide…). Les
 * animations CSS sont couvertes par la règle globale du même nom.
 */
export default function useReducedMotion() {
  const [reduit, setReduit] = useState(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  });

  useEffect(() => {
    if (!window.matchMedia) return undefined;
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
    const surChangement = (e) => setReduit(e.matches);
    mq.addEventListener('change', surChangement);
    setReduit(mq.matches);
    return () => mq.removeEventListener('change', surChangement);
  }, []);

  return reduit;
}
