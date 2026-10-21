import { LANGUES, LANGUE_DEFAUT } from '../langues';

import common from './common';
import layout from './layout';
import login from './login';
import landing from './landing';
import decouvrir from './decouvrir';
import dashboard from './dashboard';
import initiatives from './initiatives';
import initiativeDetail from './initiativeDetail';
import initiativeForm from './initiativeForm';
import roadmap from './roadmap';
import objectifs from './objectifs';
import demandes from './demandes';
import historique from './historique';
import users from './users';
import roles from './roles';
import motdepasse from './motdepasse';

/**
 * Chaque fichier de ce dossier exporte un « fragment » :
 *   { fr: {...}, en: {...}, ar: {...}, es: {...} }
 * Les fragments sont fusionnés, par langue, en un dictionnaire unique.
 */
const FRAGMENTS = [
  common,
  layout,
  login,
  landing,
  decouvrir,
  dashboard,
  initiatives,
  initiativeDetail,
  initiativeForm,
  roadmap,
  objectifs,
  demandes,
  historique,
  users,
  roles,
  motdepasse
];

const dictionnaires = {};
for (const langue of LANGUES) {
  dictionnaires[langue.code] = {};
  for (const fragment of FRAGMENTS) {
    Object.assign(dictionnaires[langue.code], fragment[langue.code] || {});
  }
}

export default dictionnaires;
export { LANGUES, LANGUE_DEFAUT };
