import { useEffect, useRef, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import { useReferentiels } from '../i18n/referentiels';
import LanguageSwitcher from './LanguageSwitcher';
import NotificationsBell from './NotificationsBell';
import CommandPalette from './CommandPalette';
import { Toaster } from './ui/use-toast';
import { ThemeToggle } from './ui/theme-toggle';
import { cn } from '../lib/utils';
import { Separator } from './ui/separator';
import {
  IconDashboard,
  IconUser,
  IconProject,
  IconRoadmap,
  IconInbox,
  IconAlert,
  IconTarget,
  IconWallet,
  IconUsers,
  IconLogout,
  IconClock,
  IconSearch,
  IconMenu,
  IconClose,
  IconSettings,
  IconShield,
} from './Icons';

const LINKS = [
  {
    section: 'layout.nav.pilotage',
    items: [
      { to: '/dashboard', cle: 'layout.nav.dashboard', Icone: IconDashboard },
    ],
  },
  {
    section: 'layout.nav.portefeuille',
    items: [
      { to: '/initiatives', cle: 'layout.nav.initiatives', Icone: IconProject },
      { to: '/roadmap', cle: 'layout.nav.roadmap', Icone: IconRoadmap },
      { to: '/demandes', cle: 'layout.nav.demandes', Icone: IconInbox, perms: ['DEMANDE_SOUMETTRE', 'DEMANDE_EVALUER'] },
      { to: '/objectifs', cle: 'layout.nav.objectifs', Icone: IconTarget, perms: ['INITIATIVE_CONSULTER'] },
    ],
  },
  {
    section: 'layout.nav.suivi',
    items: [
      { to: '/risques', cle: 'layout.nav.risques', Icone: IconAlert, perms: ['INITIATIVE_CONSULTER'] },
      { to: '/depenses', cle: 'layout.nav.depenses', Icone: IconWallet, perms: ['BUDGET_CONSULTER', 'DEPENSE_SAISIR'] },
    ],
  },
  {
    section: 'layout.nav.activite',
    items: [
      { to: '/activite', cle: 'layout.nav.activiteSecurite', Icone: IconClock, perms: ['INITIATIVE_CONSULTER', 'UTILISATEUR_GERER'] },
    ],
  },
  {
    section: 'layout.nav.administration',
    items: [
      { to: '/utilisateurs', cle: 'layout.nav.utilisateurs', Icone: IconUsers, perms: ['UTILISATEUR_GERER'] },
      { to: '/roles', cle: 'layout.nav.roles', Icone: IconShield, perms: ['ROLE_GERER', 'PERMISSION_GERER'] },
    ],
  },
];

/** Destinations de la barre mobile en bas : 4 liens + « Plus » (tiroir). */
const NAV_MOBILE = [
  { to: '/dashboard', cle: 'layout.nav.dashboard', Icone: IconDashboard },
  { to: '/initiatives', cle: 'layout.nav.initiatives', Icone: IconProject },
  { to: '/demandes', cle: 'layout.nav.demandes', Icone: IconInbox, perms: ['DEMANDE_SOUMETTRE', 'DEMANDE_EVALUER'] },
  { to: '/activite', cle: 'layout.nav.activite', Icone: IconClock, perms: ['INITIATIVE_CONSULTER', 'UTILISATEUR_GERER'] },
];

/** Fil d'Ariane (clés de traduction) déduit de la route courante. */
function filAriane(pathname, search) {
  if (pathname === '/dashboard') return ['layout.fil.portefeuille', 'layout.nav.dashboard'];
  if (pathname === '/initiatives/nouvelle') return ['layout.fil.portefeuille', 'layout.nav.initiatives', 'layout.fil.nouvelleInitiative'];
  if (/^\/initiatives\/\d+\/modifier$/.test(pathname)) return ['layout.fil.portefeuille', 'layout.nav.initiatives', 'layout.fil.modifier'];
  if (/^\/initiatives\/\d+$/.test(pathname)) return ['layout.fil.portefeuille', 'layout.nav.initiatives', 'layout.fil.detail'];
  if (pathname === '/initiatives') {
    return search.includes('archive=1')
      ? ['layout.fil.portefeuille', 'layout.nav.initiatives', 'layout.nav.archives']
      : ['layout.fil.portefeuille', 'layout.nav.initiatives'];
  }
  if (pathname.startsWith('/roadmap')) return ['layout.fil.portefeuille', 'layout.nav.roadmap'];
  if (pathname.startsWith('/demandes')) return ['layout.fil.portefeuille', 'layout.nav.demandes'];
  if (pathname.startsWith('/objectifs')) return ['layout.fil.portefeuille', 'layout.fil.objectifs'];
  if (pathname.startsWith('/mon-espace')) return ['layout.fil.portefeuille', 'layout.nav.monEspace'];
  if (pathname.startsWith('/risques')) return ['layout.fil.suivi', 'layout.nav.risques'];
  if (pathname.startsWith('/depenses')) return ['layout.fil.suivi', 'layout.nav.depenses'];
  if (pathname.startsWith('/activite')) return ['layout.fil.activite', 'layout.nav.activiteSecurite'];
  if (pathname.startsWith('/audit')) return ['layout.fil.activite', 'layout.nav.audit'];
  if (pathname.startsWith('/historique-connexions')) return ['layout.fil.activite', 'layout.nav.connexions'];
  if (pathname.startsWith('/historique')) return ['layout.fil.activite', 'layout.nav.historique'];
  if (pathname.startsWith('/utilisateurs')) return ['layout.fil.administration', 'layout.nav.utilisateurs'];
  if (pathname.startsWith('/roles')) return ['layout.fil.administration', 'layout.nav.roles'];
  if (pathname.startsWith('/assistant')) return ['layout.fil.portefeuille', 'layout.nav.assistant'];
  if (pathname.startsWith('/mot-de-passe')) return ['layout.fil.portefeuille', 'layout.menu.motDePasse'];
  return ['layout.fil.portefeuille'];
}

/**
 * Gabarit de l'application : barre latérale de navigation (desktop),
 * barre de navigation en bas (mobile), barre supérieure (fil d'Ariane,
 * recherche, langue, thème, notifications, profil) et zone de contenu.
 */
export default function Layout() {
  const { user, logout, hasPermission } = useAuth();
  const { t } = useI18n();
  const ref = useReferentiels();
  const navigate = useNavigate();
  const location = useLocation();

  const [menuProfil, setMenuProfil] = useState(false);
  const [tiroir, setTiroir] = useState(false);
  const [recherche, setRecherche] = useState('');
  const refMenu = useRef(null);

  // Ferme le menu profil au clic à l'extérieur.
  useEffect(() => {
    const fermer = (e) => {
      if (refMenu.current && !refMenu.current.contains(e.target)) setMenuProfil(false);
    };
    document.addEventListener('mousedown', fermer);
    return () => document.removeEventListener('mousedown', fermer);
  }, []);

  // Ferme le tiroir mobile à chaque changement de page.
  useEffect(() => {
    setTiroir(false);
    setMenuProfil(false);
  }, [location.pathname, location.search]);

  const estMobile = typeof window !== 'undefined' && window.matchMedia('(max-width: 1024px)').matches;

  const basculerMenu = () => setTiroir((v) => !v);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const soumettreRecherche = (e) => {
    e.preventDefault();
    const q = recherche.trim();
    navigate(q ? `/initiatives?recherche=${encodeURIComponent(q)}` : '/initiatives');
  };

  const { pathname, search } = location;

  const initiales = `${user?.prenom?.[0] ?? ''}${user?.nom?.[0] ?? ''}`.toUpperCase();
  const libelleRole = ref.role(user?.role?.code, user?.role?.libelle);

  const miettes = filAriane(pathname, search);

  // Détermine si une route est active (pour surlignage).
  const estActive = (to) => {
    // L'entrée « Activité et sécurité » englobe les anciennes routes
    // /historique, /historique-connexions et /audit (page fusionnée).
    if (to === '/activite') {
      return pathname.startsWith('/activite')
        || ['/historique', '/historique-connexions', '/audit'].includes(pathname);
    }
    return pathname === to || pathname.startsWith(to + '/');
  };

  // Droits d'accès explicites par entrée de menu (aucune heuristique d'icône).
  const peutVoir = (lien) =>
    !lien.perms || lien.perms.some((permission) => hasPermission(permission));

  return (
    <div className="min-h-screen bg-background text-foreground">
      {/* Barre latérale desktop (tiroir mobile ouvert par le burger / « Plus ») */}
      <aside
        className={cn(
          'fixed left-0 top-0 z-sheet h-screen w-64 border-r border-border bg-card transition-transform duration-300 lg:translate-x-0',
          tiroir ? 'translate-x-0' : '-translate-x-full'
        )}
        aria-label="Navigation principale"
      >
        <div className="h-full flex flex-col">
          <div className="p-4 border-b border-border">
            <div className="flex items-center gap-2">
              <img
                className="h-8 w-8 rounded-md"
                src="/images/logo-it6.png"
                alt="IT6 Academy"
              />
              <span className="font-semibold text-sm">{t('layout.brand.nom')}</span>
            </div>
          </div>

          <nav className="flex-1 overflow-y-auto px-3 py-3 space-y-2">
            {LINKS.filter((section) => section.items.some(peutVoir)).map((section) => (
              <div key={section.section}>
                <h3 className="px-3 mb-1 text-[10px] uppercase tracking-wider text-muted-foreground">
                  {t(section.section)}
                </h3>
                {section.items.map((lien) => {
                  const active = estActive(lien.to);
                  if (!peutVoir(lien)) return null;
                  return (
                    <Link
                      key={lien.to}
                      to={lien.to}
                      className={cn(
                        'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                        active ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
                      )}
                    >
                      <lien.Icone size={18} />
                      <span>{t(lien.cle)}</span>
                    </Link>
                  );
                })}
              </div>
            ))}
          </nav>

          <div className="p-3 border-t border-border">
            <p className="text-xs text-muted-foreground">{user?.prenom} {user?.nom}</p>
            <p className="text-xs font-medium">{libelleRole}</p>
            <button
              onClick={handleLogout}
              className="w-full flex items-center gap-2 rounded-md px-3 py-2 text-sm text-destructive hover:bg-destructive/10"
            >
              <IconLogout size={16} />
              {t('layout.deconnexion')}
            </button>
          </div>
        </div>
      </aside>

      {/* Voile du tiroir mobile */}
      {tiroir && (
        <div
          className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm lg:hidden"
          onClick={() => setTiroir(false)}
        />
      )}

      {/* Zone de contenu principale */}
      <div className={`lg:pl-64 min-h-screen`}>
        {/* Topbar */}
        <header className="sticky top-0 z-10 flex h-16 items-center gap-2 px-4 border-b border-border bg-background/95 backdrop-blur">
          <button
            onClick={basculerMenu}
            className="lg:hidden rounded-md p-1.5 text-muted-foreground ring-offset-background transition-colors hover:bg-accent focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
            aria-label={t('layout.burger.aria')}
            aria-expanded={tiroir}
          >
            <IconMenu size={20} />
          </button>

          <nav className="flex-1" aria-label="Fil d'Ariane">
            {miettes.map((cle, idx) => (
              <span key={`${cle}-${idx}`} className={idx === miettes.length - 1 ? 'font-medium' : 'text-muted-foreground'}>
                {idx > 0 && <span className="mx-1 text-muted-foreground">/</span>}
                {t(cle)}
              </span>
            ))}
          </nav>

          <div className="flex-1 flex justify-end">
            <form
              onSubmit={soumettreRecherche}
              className="hidden sm:flex items-center gap-2"
              role="search"
            >
              <IconSearch size={16} className="text-muted-foreground" />
              <input
                type="search"
                placeholder={t('layout.recherche.placeholder')}
                value={recherche}
                onChange={(e) => setRecherche(e.target.value)}
                aria-label={t('layout.recherche.aria')}
                className="flex-1 rounded-md border border-input bg-background px-3 py-1.5 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </form>

            <LanguageSwitcher className="hidden sm:flex" />
            <ThemeToggle className="hidden sm:flex" />
            <NotificationsBell />

            <div className="ml-3 flex items-center" ref={refMenu}>
              <button
                className="rounded-md p-1.5 text-muted-foreground ring-offset-background transition-colors hover:bg-accent focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
                onClick={() => setMenuProfil((v) => !v)}
                aria-haspopup="menu"
                aria-expanded={menuProfil}
              >
                <span className="relative inline-block">
                  <span className="flex h-7 w-7 items-center justify-center rounded-full bg-primary text-primary-foreground text-xs font-medium">
                    {initiales}
                  </span>
                </span>
              </button>

              {menuProfil && (
                <div className="absolute right-0 mt-2 w-48 rounded-md border border-input bg-card py-1 text-base shadow-lg ring-1 ring-black/5 focus:outline-none sm:origin-top-right">
                  <div className="px-3 py-1.5 text-xs font-medium text-muted-foreground">
                    {user?.prenom} {user?.nom}
                  </div>
                  <div className="px-3 py-1 text-xs text-muted-foreground">{libelleRole}</div>
                  <Separator className="my-1" />
                  <Link
                    to="/mot-de-passe"
                    className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-foreground hover:bg-accent"
                    onClick={() => setMenuProfil(false)}
                  >
                    <IconSettings size={16} />
                    {t('layout.menu.motDePasse')}
                  </Link>
                  <button
                    onClick={handleLogout}
                    className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-destructive hover:bg-destructive/10"
                  >
                    <IconLogout size={16} />
                    {t('layout.deconnexion')}
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>

        <main className="flex-1 p-4 pb-24 lg:p-6 lg:pb-6">
          <Outlet />
        </main>
      </div>

      <CommandPalette />

      <Toaster />

      {/* Barre de navigation mobile en bas : 4 destinations + « Plus » */}
      <nav className="lg:hidden fixed bottom-0 left-0 right-0 z-bottom-nav border-t border-border bg-background">
        <div className="flex items-stretch justify-around px-1 py-1">
          {NAV_MOBILE.filter(peutVoir).map((lien) => {
            const active = estActive(lien.to);
            return (
              <Link
                key={lien.to}
                to={lien.to}
                className={cn(
                  'flex min-w-0 flex-1 flex-col items-center gap-0.5 rounded-md px-1 py-1.5 text-[11px] font-medium transition-colors',
                  active ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
                )}
              >
                <lien.Icone size={20} />
                <span className="w-full truncate text-center">{t(lien.cle)}</span>
              </Link>
            );
          })}
          <button
            onClick={basculerMenu}
            aria-label={t('layout.burger.aria')}
            aria-expanded={tiroir}
            className="flex min-w-0 flex-1 flex-col items-center gap-0.5 rounded-md px-1 py-1.5 text-[11px] font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
          >
            <IconMenu size={20} />
            <span className="w-full truncate text-center">{t('layout.nav.plus')}</span>
          </button>
        </div>
      </nav>
    </div>
  );
}
