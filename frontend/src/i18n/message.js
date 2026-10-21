/**
 * Messages mémorisés dans les pages (toasts de succès, erreurs de saisie).
 *
 * <p>Une page peut stocker :</p>
 * <ul>
 *   <li>une <strong>clé de traduction</strong> `{ cle, params }` — elle est
 *       alors retraduite <em>à chaque affichage</em> : si l'utilisateur change
 *       de langue pendant que le toast est visible, le texte change aussi ;</li>
 *   <li>un <strong>texte déjà formé</strong> — message renvoyé par l'API
 *       (toujours en français, indépendant de l'interface).</li>
 * </ul>
 *
 * @param {(cle: string, params?: object) => string} t fonction de traduction
 * @param {string | {cle: string, params?: object} | null | undefined} message
 * @returns {string|null}
 */
export function afficherMessage(t, message) {
  if (!message) return null;
  if (typeof message === 'string') return message;
  return t(message.cle, message.params);
}
