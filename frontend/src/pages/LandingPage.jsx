import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import LanguageSwitcher from '../components/LanguageSwitcher';
import MotifZellige from '../components/MotifZellige';
import HeroPortefeuille from '../components/vitrine/HeroPortefeuille';
import DemoKanban from '../components/vitrine/DemoKanban';
import DemoIA from '../components/vitrine/DemoIA';
import ChoixProfil from '../components/vitrine/ChoixProfil';
import AvantApres from '../components/vitrine/AvantApres';
import useReducedMotion from '../components/vitrine/useReducedMotion';
import {
  IconKanban,
  IconRoadmap,
  IconInbox,
  IconTarget,
  IconShield,
  IconArrowRight,
  IconArrowUp,
  IconCheck,
  IconClock,
  IconLayers,
  IconSearch,
  IconMenu,
  IconClose
} from '../components/Icons';

/* ------------------------------------------------------------------ */
/* Contenus statiques (libellés fournis par i18n/locales/landing.js)   */
/* ------------------------------------------------------------------ */

const CHIFFRES = [
  { valeur: '06', cleLibelle: 'landing.chiffre.modules' },
  { valeur: '05', cleLibelle: 'landing.chiffre.statuts' },
  { valeur: '03', cleLibelle: 'landing.chiffre.roles' },
  { valeur: '01', cleLibelle: 'landing.chiffre.source' }
];

/**
 * Cartes de fonctionnalités. `sujet` indique la page /decouvrir/:sujet
 * ouverte quand on clique sur la carte.
 */
const FONCTIONNALITES = [
  { Icone: IconSearch, cleTitre: 'landing.fonction.recherche.titre', cleTexte: 'landing.fonction.recherche.texte', sujet: 'initiatives' },
  { Icone: IconKanban, cleTitre: 'landing.fonction.kanban.titre', cleTexte: 'landing.fonction.kanban.texte', sujet: 'initiatives' },
  { Icone: IconRoadmap, cleTitre: 'landing.fonction.roadmap.titre', cleTexte: 'landing.fonction.roadmap.texte', sujet: 'etapes-jalons' },
  { Icone: IconInbox, cleTitre: 'landing.fonction.demandes.titre', cleTexte: 'landing.fonction.demandes.texte', sujet: 'initiatives' },
  { Icone: IconTarget, cleTitre: 'landing.fonction.objectifs.titre', cleTexte: 'landing.fonction.objectifs.texte', sujet: 'dashboard' },
  { Icone: IconShield, cleTitre: 'landing.fonction.roles.titre', cleTexte: 'landing.fonction.roles.texte', sujet: 'roles-securite' },
  { Icone: IconClock, cleTitre: 'landing.fonction.alertes.titre', cleTexte: 'landing.fonction.alertes.texte', sujet: 'etapes-jalons' },
  { Icone: IconLayers, cleTitre: 'landing.fonction.archives.titre', cleTexte: 'landing.fonction.archives.texte', sujet: 'initiatives' }
];

/**
 * Mots importants soulignés en pointillés dans les textes : chaque entrée
 * relie la clé i18n d'un texte à la liste des mots à rendre cliquables
 * (clé i18n du mot → sujet /decouvrir/:sujet).
 */
const LIENS_TEXTES = {
  'landing.hero.lead': [
    { motCle: 'landing.mot.initiatives', sujet: 'initiatives' },
    { motCle: 'landing.mot.roadmap', sujet: 'etapes-jalons' }
  ],
  'landing.contraste.solution.i3': [
    { motCle: 'landing.mot.kpi', sujet: 'dashboard' }
  ],
  'landing.etape.piloter.texte': [
    { motCle: 'landing.mot.avancement', sujet: 'avancement' }
  ]
};

const ETAPES = [
  { n: '01', cleTitre: 'landing.etape.soumettre.titre', cleTexte: 'landing.etape.soumettre.texte' },
  { n: '02', cleTitre: 'landing.etape.evaluer.titre', cleTexte: 'landing.etape.evaluer.texte' },
  { n: '03', cleTitre: 'landing.etape.transformer.titre', cleTexte: 'landing.etape.transformer.texte' },
  { n: '04', cleTitre: 'landing.etape.piloter.titre', cleTexte: 'landing.etape.piloter.texte' }
];

/** Cartes de la section « Confiance ». */
const CONFIANCE = [
  { Icone: IconShield, cleTitre: 'landing.confiance.roles.titre', cleTexte: 'landing.confiance.roles.texte' },
  { Icone: IconClock, cleTitre: 'landing.confiance.audit.titre', cleTexte: 'landing.confiance.audit.texte' },
  { Icone: IconLayers, cleTitre: 'landing.confiance.hebergement.titre', cleTexte: 'landing.confiance.hebergement.texte' }
];

/** Ancres suivies pour le lien « en cours » de la barre de navigation. */
const SECTIONS_NAV = ['demo', 'profils', 'confiance', 'fonctionnalites', 'contact'];

/**
 * Durée (ms) de l'animation des chiffres clés, une fois la bande visible.
 * Volontairement courte : la page reste sobre, l'effet est un accent.
 */
const DUREE_COMPTEUR = 900;

/* ------------------------------------------------------------------ */
/* Page                                                                */
/* ------------------------------------------------------------------ */

/**
 * Page d'accueil publique (vitrine), présentée avant la connexion.
 *
 * Sections : navigation, hero avec démo vivante du portefeuille, chiffres,
 * avant/après (curseur), démo Kanban manipulable, assistant IA (aperçu
 * animé), choix de profil, fonctionnalités, confiance, étapes, appel à
 * l'action, contact, pied de page.
 *
 * Toutes les animations respectent `prefers-reduced-motion` (règle CSS
 * globale + garde JS via useReducedMotion).
 */
export default function LandingPage() {
  const { user } = useAuth();
  const { t } = useI18n();
  const reduit = useReducedMotion();
  const [menuOuvert, setMenuOuvert] = useState(false);
  // Effets de défilement : barre compacte, section en cours, retour en haut.
  const [navCompacte, setNavCompacte] = useState(false);
  const [sectionActive, setSectionActive] = useState('');
  const [hautPageVisible, setHautPageVisible] = useState(false);
  // Chiffres clés : valeurs affichées, animées de 0 à la valeur réelle.
  const [compteurs, setCompteurs] = useState(() => CHIFFRES.map((c) => c.valeur));
  const cible = user ? '/dashboard' : '/login';
  const libelleCta = user ? t('landing.cta.ouvrirApp') : t('landing.cta.seConnecter');

  /* Effets de défilement (CSS + IntersectionObserver, sans librairie).
     Progressif : sans JS — ou avec animations réduites demandées — la page
     reste entièrement lisible (les animations sont des compléments). */
  useEffect(() => {
    const page = document.querySelector('.sn-page');
    if (!page) return undefined;
    page.classList.add('sn-defilement');

    let rafScroll = 0;
    let rafChiffres = 0;
    const majScroll = () => {
      const y = window.scrollY;
      setNavCompacte(y > 16);
      setHautPageVisible(y > 640);
    };
    const surScroll = () => {
      if (!rafScroll) rafScroll = requestAnimationFrame(majScroll);
    };
    window.addEventListener('scroll', surScroll, { passive: true });
    majScroll();

    // Apparitions au défilement.
    const obsApparait = new IntersectionObserver(
      (entrees) => {
        entrees.forEach((e) => {
          if (e.isIntersecting) {
            e.target.classList.add('est-visible');
            obsApparait.unobserve(e.target);
          }
        });
      },
      { threshold: 0.12 }
    );
    document.querySelectorAll('[data-apparait]').forEach((el) => obsApparait.observe(el));

    // Compteurs animés (uniquement si l'utilisateur accepte les animations).
    const obsChiffres = new IntersectionObserver(
      (entrees) => {
        if (!entrees.some((e) => e.isIntersecting) || reduit) return;
        const cible2 = CHIFFRES.map((c) => parseInt(c.valeur, 10));
        const debut = performance.now();
        const animer = (now) => {
          const p = Math.min(1, (now - debut) / DUREE_COMPTEUR);
          const doux = 1 - Math.pow(1 - p, 3);
          setCompteurs(cible2.map((v) => String(Math.round(v * doux)).padStart(2, '0')));
          if (p < 1) rafChiffres = requestAnimationFrame(animer);
        };
        rafChiffres = requestAnimationFrame(animer);
        obsChiffres.disconnect();
      },
      { threshold: 0.4 }
    );
    const bande = document.querySelector('.sn-stats');
    if (bande) obsChiffres.observe(bande);

    // Section courante (barre de navigation).
    const obsSections = new IntersectionObserver(
      (entrees) => {
        entrees.forEach((e) => {
          if (e.isIntersecting) setSectionActive(e.target.id);
        });
      },
      { rootMargin: '-45% 0px -50% 0px' }
    );
    SECTIONS_NAV.forEach((id) => {
      const el = document.getElementById(id);
      if (el) obsSections.observe(el);
    });

    return () => {
      window.removeEventListener('scroll', surScroll);
      cancelAnimationFrame(rafScroll);
      cancelAnimationFrame(rafChiffres);
      obsApparait?.disconnect();
      obsChiffres?.disconnect();
      obsSections?.disconnect();
      page?.classList.remove('sn-defilement');
    };
  }, [reduit]);

  const allerEnHaut = () => {
    window.scrollTo({ top: 0, behavior: reduit ? 'auto' : 'smooth' });
  };

  /**
   * Texte traduit avec liens « Découvrir » : entoure chaque mot clé
   * (première occurrence, insensible à la casse) d'un lien vers la page
   * /decouvrir/:sujet correspondant. Sans mot présent, renvoie le texte.
   */
  const echapperMot = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const avecLiens = (cleTexte) => {
    const texte = t(cleTexte);
    const mots = (LIENS_TEXTES[cleTexte] ?? [])
      .map((l) => ({ ...l, mot: t(l.motCle) }))
      .filter((l) => l.mot);
    if (!mots.length) return texte;
    const motif = new RegExp(`(${mots.map((l) => echapperMot(l.mot)).join('|')})`, 'gi');
    return texte.split(motif).map((morceau, i) => {
      // split avec un groupe de capture : indices pairs = texte, impairs = mot
      if (i % 2 === 0) return morceau;
      const lien = mots.find((l) => l.mot.toLowerCase() === morceau.toLowerCase());
      if (!lien) return morceau;
      return (
        <Link key={`${lien.sujet}-${i}`} to={`/decouvrir/${lien.sujet}`} className="sn-mot-cle">
          {morceau}
        </Link>
      );
    });
  };

  /** Libellés de navigation (même liste dans la barre et le menu mobile). */
  const liensNav = [
    { ancre: 'demo', cle: 'landing.nav.demo' },
    { ancre: 'profils', cle: 'landing.nav.profils' },
    { ancre: 'confiance', cle: 'landing.nav.confiance' },
    { ancre: 'fonctionnalites', cle: 'landing.nav.fonctionnalites' },
    { ancre: 'contact', cle: 'landing.nav.contact' }
  ];

  return (
    <div className="sn-page">
      {/* ---------------------------------------------------------- */}
      {/* 1. Navigation                                              */}
      {/* ---------------------------------------------------------- */}
      <header className={`sn-nav${navCompacte ? ' sn-nav-compacte' : ''}`}>
        <div className="sn-nav-inner">
          <a href="#top" className="sn-logo">
            <img
              className="sn-logo-img"
              src="/images/logo-it6.png"
              alt="IT6 Academy"
              width="132"
              height="138"
            />
            <span className="sn-logo-text">
              <strong>IT6 Consulting</strong>
              <small>{t('landing.brand.sousTitre')}</small>
            </span>
          </a>
          <nav className="sn-nav-links">
            {liensNav.map(({ ancre, cle }) => (
              <a
                key={ancre}
                href={`#${ancre}`}
                className={sectionActive === ancre ? 'est-courant' : undefined}
              >
                {t(cle)}
              </a>
            ))}
          </nav>
          <div className="sn-nav-actions">
            {/* Bascule de langue visible : un clic et la page part en RTL. */}
            <LanguageSwitcher variante="segmente" />
            <Link to={cible} className="sn-btn sn-btn-green sn-btn-sm sn-nav-cta">
              {libelleCta}
              <IconArrowRight size={15} />
            </Link>
            <button
              type="button"
              className="sn-nav-burger"
              onClick={() => setMenuOuvert((v) => !v)}
              aria-label={menuOuvert ? t('landing.menu.fermer') : t('landing.menu.ouvrir')}
              aria-expanded={menuOuvert}
              aria-controls="sn-menu-mobile"
            >
              {menuOuvert ? <IconClose size={22} /> : <IconMenu size={22} />}
            </button>
          </div>
        </div>

        {menuOuvert && (
          <nav className="sn-menu-mobile" id="sn-menu-mobile" aria-label={t('landing.menu.navigationPrincipale')}>
            {liensNav.map(({ ancre, cle }) => (
              <a
                key={ancre}
                href={`#${ancre}`}
                className={sectionActive === ancre ? 'est-courant' : undefined}
                onClick={() => setMenuOuvert(false)}
              >
                {t(cle)}
              </a>
            ))}
            <LanguageSwitcher variante="segmente" className="sn-menu-langue" />
            <Link to={cible} className="sn-menu-cta" onClick={() => setMenuOuvert(false)}>
              {libelleCta}
              <IconArrowRight size={16} />
            </Link>
          </nav>
        )}
      </header>

      {/* ---------------------------------------------------------- */}
      {/* 2. Hero + démo vivante du portefeuille                      */}
      {/* ---------------------------------------------------------- */}
      <section className="sn-hero" id="top">
        <MotifZellige className="sn-motif-hero" />
        <span className="sn-hero-glow sn-hero-glow-a" aria-hidden="true" />
        <span className="sn-hero-glow sn-hero-glow-b" aria-hidden="true" />
        <div className="sn-hero-inner">
          <div>
            <span className="sn-eyebrow">
              <span className="sn-eyebrow-dot" />
              {t('landing.hero.eyebrow')}
            </span>
            <h1>
              {t('landing.hero.titre1')}
              <br />
              {t('landing.hero.titre2Prefix')}
              <span className="sn-highlight">{t('landing.hero.titre2Highlight')}</span>
            </h1>
            <p className="sn-hero-lead">{avecLiens('landing.hero.lead')}</p>
            <div className="sn-cta">
              <Link to={cible} className="sn-btn sn-btn-green sn-btn-lg">
                {libelleCta}
                <IconArrowRight size={18} />
              </Link>
            </div>
            <p className="sn-hero-note">{t('landing.accesReserve')}</p>
            <ul className="sn-avantages">
              <li><span className="sn-check"><IconCheck size={12} /></span>{t('landing.avantage.pilotage')}</li>
              <li><span className="sn-check"><IconCheck size={12} /></span>{t('landing.avantage.workflow')}</li>
              <li><span className="sn-check"><IconCheck size={12} /></span>{t('landing.avantage.alignement')}</li>
              <li><span className="sn-check"><IconCheck size={12} /></span>{t('landing.avantage.decisions')}</li>
            </ul>
          </div>

          <div className="sn-hero-visual">
            <span className="sn-shot-glow" aria-hidden="true" />
            <HeroPortefeuille />
          </div>
        </div>
      </section>

      {/* ---------------------------------------------------------- */}
      {/* 3. Chiffres clés                                           */}
      {/* ---------------------------------------------------------- */}
      <section className="sn-stats" aria-label={t('landing.stats.aria')}>
        <div className="sn-stats-inner">
          {CHIFFRES.map((c, i) => (
            <div key={c.valeur} className="sn-stat">
              <span className="sn-stat-valeur">{compteurs[i]}</span>
              <span className="sn-stat-libelle">{t(c.cleLibelle)}</span>
            </div>
          ))}
        </div>
      </section>

      {/* ---------------------------------------------------------- */}
      {/* 4. Avant / après : le curseur raconte le constat           */}
      {/* ---------------------------------------------------------- */}
      <section className="sn-section" id="probleme">
        <div className="sn-section-head" data-apparait>
          <span className="sn-eyebrow sn-eyebrow-dark">{t('landing.contraste.eyebrow')}</span>
          <h2>{t('landing.contraste.titre')}</h2>
          <p>{t('landing.contraste.texte')}</p>
        </div>
        <div data-apparait>
          <AvantApres />
        </div>
      </section>

      {/* ---------------------------------------------------------- */}
      {/* 5. Démo Kanban interactive                                 */}
      {/* ---------------------------------------------------------- */}
      <section className="sn-section sn-section-calme" id="demo">
        <div className="sn-section-head" data-apparait>
          <span className="sn-eyebrow sn-eyebrow-dark">{t('landing.demo.eyebrow')}</span>
          <h2>{t('landing.demo.titre')}</h2>
          <p>{t('landing.demo.texte')}</p>
        </div>
        <div data-apparait>
          <DemoKanban />
        </div>
      </section>

      {/* ---------------------------------------------------------- */}
      {/* 6. Assistant IA (aperçu animé)                             */}
      {/* ---------------------------------------------------------- */}
      <section className="sn-section" id="ia">
        <div className="sn-ia-grille" data-apparait>
          <div className="sn-section-head sn-ia-texte">
            <span className="sn-eyebrow sn-eyebrow-dark">{t('landing.ia.eyebrow')}</span>
            <h2>{t('landing.ia.titre')}</h2>
            <p>{avecLiens('landing.ia.texte')}</p>
            <p className="sn-ia-legal">{t('landing.ia.legal')}</p>
            {/* L'aperçu ci-contre est une animation ; ce bouton ouvre le
                VRAI assistant de l'application (permission IA_UTILISER). */}
            <p>
              <Link to="/assistant" className="btn btn-primaire sn-ia-cta">
                {t('landing.ia.cta')} →
              </Link>
            </p>
          </div>
          <DemoIA />
        </div>
      </section>

      {/* ---------------------------------------------------------- */}
      {/* 7. Choix de profil                                          */}
      {/* ---------------------------------------------------------- */}
      <section className="sn-section sn-section-calme" id="profils">
        <div className="sn-section-head" data-apparait>
          <span className="sn-eyebrow sn-eyebrow-dark">{t('landing.profils.eyebrow')}</span>
          <h2>{t('landing.profils.titre')}</h2>
          <p>{t('landing.profils.texte')}</p>
        </div>
        <div data-apparait>
          <ChoixProfil />
        </div>
      </section>

      {/* ---------------------------------------------------------- */}
      {/* 8. Fonctionnalités                                         */}
      {/* ---------------------------------------------------------- */}
      <section className="sn-section" id="fonctionnalites">
        <div className="sn-section-head" data-apparait>
          <span className="sn-eyebrow sn-eyebrow-dark">{t('landing.fonctionnalites.eyebrow')}</span>
          <h2>{t('landing.fonctionnalites.titre')}</h2>
          <p>{t('landing.fonctionnalites.texte')}</p>
        </div>
        <div className="sn-fonctionnalites">
          {FONCTIONNALITES.map(({ Icone, cleTitre, cleTexte, sujet }) => (
            <Link
              key={cleTitre}
              to={`/decouvrir/${sujet}`}
              className="sn-fonctionnalite sn-fonctionnalite-lien"
              data-apparait
            >
              <span className="sn-fonctionnalite-ic"><Icone size={19} /></span>
              <div>
                <h3>{t(cleTitre)}</h3>
                <p>{t(cleTexte)}</p>
                <span className="sn-lien-badge">{t('landing.enSavoirPlus')}</span>
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* ---------------------------------------------------------- */}
      {/* 9. Confiance : rôles, audit, hébergement                    */}
      {/* ---------------------------------------------------------- */}
      <section className="sn-section sn-section-calme" id="confiance">
        <div className="sn-section-head" data-apparait>
          <span className="sn-eyebrow sn-eyebrow-dark">{t('landing.confiance.eyebrow')}</span>
          <h2>{t('landing.confiance.titre')}</h2>
          <p>{t('landing.confiance.texte')}</p>
        </div>
        <div className="sn-confiance">
          {CONFIANCE.map(({ Icone, cleTitre, cleTexte }) => (
            <article key={cleTitre} className="sn-confiance-carte" data-apparait>
              <span className="sn-confiance-ic"><Icone size={19} /></span>
              <h3>{t(cleTitre)}</h3>
              <p>{t(cleTexte)}</p>
            </article>
          ))}
        </div>
      </section>

      {/* ---------------------------------------------------------- */}
      {/* 10. Comment ça marche                                      */}
      {/* ---------------------------------------------------------- */}
      <section className="sn-section" id="etapes">
        <div className="sn-etapes-boite">
          <div className="sn-etapes-photo" data-apparait>
            <img
              src="/images/etapes.webp"
              alt={t('landing.etapes.photoAlt')}
              width="1200"
              height="1500"
              loading="lazy"
            />
          </div>
          <div>
            <div className="sn-etapes-tete" data-apparait>
              <span className="sn-eyebrow sn-eyebrow-dark">{t('landing.etapes.eyebrow')}</span>
              <h2>{t('landing.etapes.titre')}</h2>
              <p>{t('landing.etapes.texte')}</p>
            </div>
            <ol className="sn-etapes">
              {ETAPES.map((e) => (
                <li key={e.n} className="sn-etape" data-apparait>
                  <span className="sn-etape-num">{e.n}</span>
                  <div>
                    <h3>{t(e.cleTitre)}</h3>
                    <p>{avecLiens(e.cleTexte)}</p>
                  </div>
                </li>
              ))}
            </ol>
          </div>
        </div>
      </section>

      {/* ---------------------------------------------------------- */}
      {/* 11. Appel à l'action                                       */}
      {/* ---------------------------------------------------------- */}
      <section className="sn-band" id="demarrer">
        <img
          className="sn-band-fond"
          src="/images/cta.webp"
          alt=""
          aria-hidden="true"
          width="1600"
          height="900"
          loading="lazy"
        />
        <span className="sn-band-voile" aria-hidden="true" />
        <div className="sn-band-inner" data-apparait>
          <span className="sn-eyebrow sn-eyebrow-on-dark">
            <span className="sn-eyebrow-dot" />
            {t('landing.band.eyebrow')}
          </span>
          <h2>{t('landing.band.titre')}</h2>
          <p>{t('landing.band.texte')}</p>
          <Link to={cible} className="sn-btn sn-btn-green sn-btn-lg">
            {libelleCta}
            <IconArrowRight size={18} />
          </Link>
          <p className="sn-band-note">{t('landing.accesReserve')}</p>
        </div>
      </section>

      {/* ---------------------------------------------------------- */}
      {/* 12. Contact                                                */}
      {/* ---------------------------------------------------------- */}
      <section className="sn-section sn-section-calme" id="contact">
        <div className="sn-contact" data-apparait>
          <h2>{t('landing.contact.titre')}</h2>
          <p>{t('landing.contact.texte')}</p>
          <div className="sn-contact-liens">
            <a
              className="sn-btn sn-btn-green sn-btn-lg"
              href="mailto:malakjaouane2@gmail.com?subject=Portefeuille%20d%27initiatives"
            >
              {t('landing.contact.ecrire')}
            </a>
            <a className="sn-btn sn-btn-ghost sn-btn-lg" href="tel:+212682074679" dir="ltr">
              +212682074679
            </a>
          </div>
          <small>{t('landing.contact.projet')}</small>
        </div>
      </section>

      {/* ---------------------------------------------------------- */}
      {/* Pied de page                                                */}
      {/* ---------------------------------------------------------- */}
      <footer className="sn-footer">
        <div className="sn-footer-inner">
          <div className="sn-footer-brand">
            <div className="sn-logo">
              <img
                className="sn-logo-img"
                src="/images/logo-it6.png"
                alt="IT6 Academy"
                width="132"
                height="138"
                loading="lazy"
              />
              <span className="sn-logo-text">
                <strong>IT6 Consulting</strong>
                <small>{t('landing.brand.sousTitre')}</small>
              </span>
            </div>
            <p>{t('landing.footer.description')}</p>
          </div>
          <div className="sn-footer-cols">
            <div className="sn-footer-col">
              <h4>{t('landing.footer.colPlateforme')}</h4>
              <a href="#demo">{t('landing.nav.demo')}</a>
              <a href="#profils">{t('landing.nav.profils')}</a>
              <a href="#confiance">{t('landing.nav.confiance')}</a>
              <a href="#etapes">{t('landing.nav.etapes')}</a>
            </div>
            <div className="sn-footer-col">
              <h4>{t('landing.footer.colProfils')}</h4>
              <span>{t('landing.role.administrateur.nom')}</span>
              <span>{t('landing.role.responsable.nom')}</span>
              <span>{t('landing.role.visiteur.nom')}</span>
            </div>
            <div className="sn-footer-col">
              <h4>{t('landing.footer.colAcces')}</h4>
              <Link to={cible}>{libelleCta}</Link>
              <a href="#top">{t('landing.footer.hautPage')}</a>
            </div>
          </div>
        </div>
        <div className="sn-footer-bottom">
          {t('landing.footer.copyright', { annee: new Date().getFullYear() })}
        </div>
      </footer>

      {/* ---------------------------------------------------------- */}
      {/* Retour en haut (apparaît après un certain défilement)       */}
      {/* ---------------------------------------------------------- */}
      <button
        type="button"
        className={`sn-haut-page${hautPageVisible ? ' est-visible' : ''}`}
        onClick={allerEnHaut}
        aria-label={t('landing.footer.hautPage')}
        aria-hidden={!hautPageVisible}
        tabIndex={hautPageVisible ? 0 : -1}
      >
        <IconArrowUp size={20} />
      </button>
    </div>
  );
}
