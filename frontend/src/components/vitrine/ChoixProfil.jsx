import { useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { IconShield, IconLayers, IconUser } from '../Icons';

/**
 * Choix de profil : « Je suis Directeur / Responsable / Visiteur ».
 * Sélectionner un profil déplie ce que ce rôle voit dans l'application
 * (modules, périmètre, exemples de décisions) — en s'appuyant sur les
 * vrais noms de rôles et les vrais nombres de permissions (17/14/3).
 */

const PROFILS = [
  {
    cle: 'admin',
    Icone: IconShield,
    permissions: 17,
    modules: [
      'landing.profils.admin.m1',
      'landing.profils.admin.m2',
      'landing.profils.admin.m3'
    ],
    vision: 'landing.profils.admin.vision'
  },
  {
    cle: 'resp',
    Icone: IconLayers,
    permissions: 14,
    modules: [
      'landing.profils.resp.m1',
      'landing.profils.resp.m2',
      'landing.profils.resp.m3'
    ],
    vision: 'landing.profils.resp.vision'
  },
  {
    cle: 'visiteur',
    Icone: IconUser,
    permissions: 3,
    modules: [
      'landing.profils.visiteur.m1',
      'landing.profils.visiteur.m2',
      'landing.profils.visiteur.m3'
    ],
    vision: 'landing.profils.visiteur.vision'
  }
];

export default function ChoixProfil() {
  const { t } = useI18n();
  const [actif, setActif] = useState('admin');

  return (
    <div className="cp-zone">
      <p className="cp-question" id="cp-question">
        {t('landing.profils.question')}
      </p>
      <div className="cp-onglets" role="tablist" aria-labelledby="cp-question">
        {PROFILS.map(({ cle, Icone }) => (
          <button
            key={cle}
            type="button"
            role="tab"
            id={`cp-tab-${cle}`}
            aria-selected={actif === cle}
            aria-controls={`cp-panel-${cle}`}
            className={`cp-onglet${actif === cle ? ' actif' : ''}`}
            onClick={() => setActif(cle)}
          >
            <Icone size={17} />
            {t(`landing.profils.${cle}.nom`)}
          </button>
        ))}
      </div>

      {PROFILS.map(({ cle, permissions, modules, vision }) => (
        <div
          key={cle}
          role="tabpanel"
          id={`cp-panel-${cle}`}
          aria-labelledby={`cp-tab-${cle}`}
          hidden={actif !== cle}
          className="cp-panneau"
        >
          <p className="cp-vision">{t(vision)}</p>
          <ul className="cp-modules">
            {modules.map((m) => (
              <li key={m}>{t(m)}</li>
            ))}
          </ul>
          <p className="cp-droits">
            <strong>{permissions}</strong>
            {t('landing.profils.droits')}
          </p>
        </div>
      ))}
    </div>
  );
}
