import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../components/ui/tabs';
import { EmptyState } from '../components/ui/empty-state';
import { IconShield } from '../components/Icons';
import HistoriquePage from './HistoriquePage';
import ConnexionsPage from './ConnexionsPage';
import AuditPage from './AuditPage';
import JournalIaPanel from '../components/JournalIaPanel';

/**
 * « Activité et sécurité » : page unique réunissant les 4 registres
 * (historique des statuts, connexions, journal d'audit, journal IA)
 * derrière des onglets. Les anciennes routes /historique,
 * /historique-connexions et /audit restent valides : elles ouvrent la
 * page avec l'onglet correspondant (liens externes et signets préservés).
 */
const ONGLETS = [
  { cle: 'historique', perms: ['INITIATIVE_CONSULTER'], Page: HistoriquePage },
  { cle: 'connexions', perms: ['UTILISATEUR_GERER'], Page: ConnexionsPage },
  { cle: 'audit', perms: ['UTILISATEUR_GERER'], Page: AuditPage },
  { cle: 'journalIa', perms: ['UTILISATEUR_GERER'], Page: JournalIaPanel }
];

export default function ActiviteSecuritePage({ ongletInitial = 'historique' }) {
  const { hasPermission } = useAuth();
  const { t } = useI18n();
  const navigate = useNavigate();
  const [params] = useSearchParams();

  // L'onglet actif vit dans l'URL (?tab=…) ; l'état local sert de repli
  // quand un registre (filtres, pagination) réécrit les paramètres sans
  // conserver tab — la page reste alors sur l'onglet choisi.
  const [onglet, setOnglet] = useState(ongletInitial);

  const visibles = ONGLETS.filter((o) => o.perms.some((p) => hasPermission(p)));
  const param = params.get('tab');
  const connu = (cle) => visibles.some((o) => o.cle === cle);
  const actif = connu(param) ? param : connu(onglet) ? onglet : visibles[0]?.cle;

  const changerOnglet = (valeur) => {
    setOnglet(valeur);
    // Canonicalise l'URL : les anciennes routes ne sont que des points d'entrée.
    navigate(`/activite?tab=${valeur}`);
  };

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-4">
        <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
          {t('activite.titre')}
        </h2>
        <p className="mt-0.5 text-[13.5px] text-muted-foreground">
          {t('activite.sousTitre')}
        </p>
      </header>

      {visibles.length === 0 ? (
        <EmptyState
          icon={<IconShield size={40} />}
          titre={t('activite.aucunAcces.titre')}
          description={t('activite.aucunAcces.texte')}
        />
      ) : (
        <Tabs value={actif} onValueChange={changerOnglet}>
          <TabsList>
            {visibles.map((o) => (
              <TabsTrigger key={o.cle} value={o.cle}>
                {t(`activite.onglet.${o.cle}`)}
              </TabsTrigger>
            ))}
          </TabsList>
          {visibles.map((o) => (
            <TabsContent key={o.cle} value={o.cle}>
              <o.Page />
            </TabsContent>
          ))}
        </Tabs>
      )}
    </div>
  );
}
