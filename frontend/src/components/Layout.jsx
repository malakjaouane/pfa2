import { useEffect, useRef, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import { useReferentiels } from '../i18n/referentiels';
import LanguageSwitcher from './LanguageSwitcher';
import MotifZellige from './MotifZellige';
import {
  IconDashboard,
  IconProject,
  IconRoadmap,
  IconInbox,
  IconTarget,
  IconUsers,
  IconShield,
  IconLogout,
  IconArchive,
  IconClock,
  IconSearch,
  IconChevronDown,
  IconMenu,
  IconClose,
  IconSettings,
  IconAssistant
} from './Icons';

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
  if (pathname.startsWith('/historique-connexions')) return ['layout.fil.portefeuille', 'layout.nav.connexions'];
  if (pathname.startsWith('/historique')) return ['layout.fil.portefeuille', 'layout.nav.historique'];
  if (pathname.startsWith('/utilisateurs')) return ['layout.fil.portefeuille', 'layout.nav.utilisateurs'];
  if (pathname.startsWith('/roles')) return ['layout.fil.portefeuille', 'layout.nav.roles'];
  if (pathname.startsWith('/assistant')) return ['layout.fil.portefeuille', 'layout.nav.assistant'];
  if (pathname.startsWith('/mot-de-passe')) return ['layout.fil.portefeuille', 'layout.menu.motDePasse'];
  return ['layout.fil.portefeuille'];
}

/**
 * Gabarit de l'application : barre latérale de navigation, barre supérieure
 * (fil d'Ariane, recherche, langue, profil) et zone de contenu.
 */
export default function Layout() {
  const { user, logout, hasPermission } = useAuth();
  const { t } = useI18n();
  const ref = useReferentiels();
  const navigate = useNavigate();
  const location = useLocation();

  const [menuProfil, setMenuProfil] = useState(false);
  const [tiroir, setTiroir] = useState(false);
  const [menuReduit, setMenuReduit] = useState(false);
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

  // Ouvre le tiroir sur mobile, replie/affiche la barre latérale sur grand écran.
  const basculerMenu = () => {
    const estMobile = typeof window !== 'undefined'
      && window.matchMedia('(max-width: 980px)').matches;
    if (estMobile) {
      setTiroir((v) => !v);
    } else {
      setMenuReduit((v) => !v);
    }
  };

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
  const dansArchives = pathname === '/initiatives' && search.includes('archive=1');

  const liens = [
    { to: '/dashboard', cle: 'layout.nav.dashboard', Icone: IconDashboard, actif: pathname === '/dashboard' }
  ];
  if (hasPermission('INITIATIVE_CONSULTER')) {
    liens.push({ to: '/initiatives', cle: 'layout.nav.initiatives', Icone: IconProject, actif: pathname.startsWith('/initiatives') && !dansArchives });
    liens.push({ to: '/roadmap', cle: 'layout.nav.roadmap', Icone: IconRoadmap, actif: pathname.startsWith('/roadmap') });
  }
  if (user?.permissions?.some((p) => ['DEMANDE_SOUMETTRE', 'DEMANDE_EVALUER'].includes(p))) {
    liens.push({ to: '/demandes', cle: 'layout.nav.demandes', Icone: IconInbox, actif: pathname.startsWith('/demandes') });
  }
  if (hasPermission('INITIATIVE_CONSULTER')) {
    liens.push({ to: '/objectifs', cle: 'layout.nav.objectifs', Icone: IconTarget, actif: pathname.startsWith('/objectifs') });
    liens.push({ to: '/initiatives?archive=1', cle: 'layout.nav.archives', Icone: IconArchive, actif: dansArchives });
    // Journal global : même permission que la lecture des initiatives —
    // aucun nouveau droit ouvert par la page. Le chemin est exact (et non
    // « startsWith ») pour ne pas rester actif sur /historique-connexions.
    liens.push({ to: '/historique', cle: 'layout.nav.historique', Icone: IconClock, actif: pathname === '/historique' });
  }
  if (hasPermission('UTILISATEUR_GERER')) {
    liens.push({ to: '/utilisateurs', cle: 'layout.nav.utilisateurs', Icone: IconUsers, actif: pathname.startsWith('/utilisateurs') });
    // Journal des connexions (e-mails + IP) : administrateurs seuls,
    // même droit que la gestion des comptes — aucun nouveau droit créé.
    liens.push({ to: '/historique-connexions', cle: 'layout.nav.connexions', Icone: IconClock, actif: pathname.startsWith('/historique-connexions') });
  }
  if (user?.permissions?.some((p) => ['ROLE_GERER', 'PERMISSION_GERER'].includes(p))) {
    liens.push({ to: '/roles', cle: 'layout.nav.roles', Icone: IconShield, actif: pathname.startsWith('/roles') });
  }
  if (hasPermission('IA_UTILISER')) {
    liens.push({ to: '/assistant', cle: 'layout.nav.assistant', Icone: IconAssistant, actif: pathname.startsWith('/assistant') });
  }

  const miettes = filAriane(pathname, search);
  const initiales = `${user?.prenom?.[0] ?? ''}${user?.nom?.[0] ?? ''}`.toUpperCase();
  const libelleRole = ref.role(user?.role?.code, user?.role?.libelle);

  return (
    <div className="app-shell">
      <aside className={`sidebar${tiroir ? ' ouverte' : ''}${menuReduit ? ' reduite' : ''}`}>
        <div className="sidebar-brand">
          <img
            className="brand-logo"
            src="/images/logo-it6.png"
            alt="IT6 Academy"
            width="132"
            height="138"
          />
          <div className="sidebar-brand-texte">
            <strong>{t('layout.brand.slogan')}</strong>
            <small>IT6 Consulting</small>
          </div>
          <button className="sidebar-fermer" onClick={() => setTiroir(false)} aria-label={t('layout.sidebar.fermer')}>
            <IconClose size={18} />
          </button>
        </div>

        <nav className="sidebar-nav">
          {liens.map((lien) => (
            <Link key={lien.to} to={lien.to} className={lien.actif ? 'nav-link active' : 'nav-link'}>
              <lien.Icone size={18} />
              <span>{t(lien.cle)}</span>
            </Link>
          ))}
        </nav>

        <div className="sidebar-pied">
          <span className="texte-muet">{t('layout.pied')}</span>
        </div>
      </aside>

      {tiroir && <div className="sidebar-voile" onClick={() => setTiroir(false)} />}

      <div className="app-corps">
        <header className="topbar">
          <MotifZellige className="motif-bande" taille={12} />
          <button
            className="topbar-burger"
            onClick={basculerMenu}
            aria-label={t('layout.burger.aria')}
            aria-expanded={tiroir || !menuReduit}
          >
            <IconMenu size={20} />
          </button>

          <nav className="fil-ariane" aria-label="Fil d'Ariane">
            {miettes.map((cle, idx) => (
              <span key={`${cle}-${idx}`} className={idx === miettes.length - 1 ? 'fil-ariane-courant' : ''}>
                {idx > 0 && <span className="fil-ariane-sep">/</span>}
                {t(cle)}
              </span>
            ))}
          </nav>

          <form className="topbar-recherche" onSubmit={soumettreRecherche} role="search">
            <IconSearch size={16} />
            <input
              type="search"
              placeholder={t('layout.recherche.placeholder')}
              value={recherche}
              onChange={(e) => setRecherche(e.target.value)}
              aria-label={t('layout.recherche.aria')}
            />
          </form>

          <LanguageSwitcher />

          <div className="topbar-utilisateur" ref={refMenu}>
            <button className="topbar-profil" onClick={() => setMenuProfil((v) => !v)} aria-haspopup="menu" aria-expanded={menuProfil}>
              <span className="avatar avatar-petit">{initiales}</span>
              <span className="topbar-profil-texte">
                <strong>{user?.prenom} {user?.nom}</strong>
                <small>{libelleRole}</small>
              </span>
              <IconChevronDown size={16} />
            </button>

            {menuProfil && (
              <div className="menu-deroulant" role="menu">
                <div className="menu-deroulant-entete">
                  <strong>{user?.prenom} {user?.nom}</strong>
                  <small>{libelleRole}</small>
                  {user?.email && <small className="texte-muet">{user.email}</small>}
                </div>
                <Link
                  to="/mot-de-passe"
                  className="menu-deroulant-item"
                  role="menuitem"
                  onClick={() => setMenuProfil(false)}
                >
                  <IconSettings size={16} />
                  {t('layout.menu.motDePasse')}
                </Link>
                <button className="menu-deroulant-item" onClick={handleLogout} role="menuitem">
                  <IconLogout size={16} />
                  {t('layout.deconnexion')}
                </button>
              </div>
            )}
          </div>
        </header>

        <main className="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
