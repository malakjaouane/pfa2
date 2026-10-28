/**
 * Textes de la page d'accueil publique (vitrine).
 * Namespace : landing.*
 *
 * Sept sections : navigation, hero, chiffres, problème/solution,
 * fonctionnalités, aperçu (captures d'écran), étapes, appel à l'action,
 * plus le pied de page.
 *
 * Règle : chaque langue doit contenir TOUTES les clés. Si une clé manque,
 * la page affiche le texte français (repli) puis la clé brute.
 */
export default {
  fr: {
    // Marque & navigation
    'landing.brand.sousTitre': "Portefeuille d'initiatives",
    'landing.nav.fonctionnalites': 'Fonctionnalités',
    'landing.nav.etapes': 'Comment ça marche',
    'landing.nav.contact': 'Contact',
    'landing.menu.ouvrir': 'Ouvrir le menu',
    'landing.menu.fermer': 'Fermer le menu',
    'landing.menu.navigationPrincipale': 'Navigation principale',

    // Liens « Découvrir » : badge au survol des images + mots soulignés
    'landing.enSavoirPlus': 'En savoir plus',
    'landing.mot.initiatives': 'initiatives',
    'landing.mot.roadmap': 'roadmap',
    'landing.mot.kpi': 'KPI',
    'landing.mot.avancement': 'avancement',
    'landing.mot.budget': 'budget',
    'landing.mot.jalons': 'jalons',

    // Appels à l'action
    'landing.cta.seConnecter': 'Se connecter',
    'landing.cta.ouvrirApp': "Ouvrir l'application",

    // Hero
    'landing.hero.eyebrow': 'Plateforme de pilotage · IT6 Consulting',
    'landing.hero.titre1': 'Pilotez votre portefeuille',
    'landing.hero.titre2Prefix': "d'",
    'landing.hero.titre2Highlight': 'initiatives digitales',
    'landing.hero.lead':
      'Une plateforme unique pour suivre les initiatives, qualifier les demandes, visualiser la roadmap et aligner les projets sur les objectifs stratégiques.',
    'landing.accesReserve': "Accès réservé aux collaborateurs : votre compte est créé par votre administrateur.",

    'landing.contact.titre': 'Contactez-nous',
    'landing.contact.texte': "L'accès est réservé aux collaborateurs : les comptes sont créés par l'administrateur. Pour obtenir un accès ou poser une question, écrivez-nous.",
    'landing.contact.ecrire': 'Écrire un email',
    'landing.contact.projet': 'IT6 Consulting — Direction des systèmes d’information.',
    'landing.avantage.pilotage': 'Pilotage unifié du portefeuille',
    'landing.avantage.workflow': 'Workflow de demandes traçable',
    'landing.avantage.alignement': 'Alignement stratégique mesuré',
    'landing.avantage.decisions': 'Décisions fondées sur des données réelles',

    // Chiffres clés
    'landing.stats.aria': 'La plateforme en chiffres',
    'landing.chiffre.modules': 'Modules intégrés',
    'landing.chiffre.statuts': 'Statuts métier suivis',
    'landing.chiffre.roles': 'Rôles & permissions',
    'landing.chiffre.source': 'Source de vérité',

    // Problème / solution
    'landing.contraste.eyebrow': 'Problème & solution',
    'landing.contraste.titre': 'Fini le suivi éclaté',
    'landing.contraste.texte':
      "Tableurs partagés, fils d'e-mails, statuts obsolètes : le pilotage d'un portefeuille se perd vite. La plateforme IT6 centralise tout, du besoin métier jusqu'à la décision.",
    'landing.contraste.probleme.i1': 'Des tableurs partagés qui divergent',
    'landing.contraste.probleme.i2': 'Des demandes dispersées dans les e-mails',
    'landing.contraste.probleme.i3': 'Des indicateurs recalculés à la main',
    'landing.contraste.solution.i1': 'Une seule source de vérité pour tout le portefeuille',
    'landing.contraste.solution.i2': 'Un workflow de demandes tracé de bout en bout',
    'landing.contraste.solution.i3': 'Des KPI calculés côté serveur, en temps réel',

    // Fonctionnalités
    'landing.fonctionnalites.eyebrow': 'Fonctionnalités',
    'landing.fonctionnalites.titre': 'Les fonctions du quotidien',
    'landing.fonctionnalites.texte': 'Des outils concrets, pensés pour le suivi opérationnel.',
    'landing.fonction.recherche.titre': 'Recherche globale',
    'landing.fonction.recherche.texte': 'Retrouvez une initiative par code ou par nom depuis la barre supérieure.',
    'landing.fonction.kanban.titre': 'Vue Kanban',
    'landing.fonction.kanban.texte': 'Le portefeuille organisé en colonnes, une par statut.',
    'landing.fonction.roadmap.titre': 'Roadmap mensuelle',
    'landing.fonction.roadmap.texte': 'Une frise pour anticiper les échéances sur plusieurs mois.',
    'landing.fonction.demandes.titre': 'Workflow de demandes',
    'landing.fonction.demandes.texte': 'Soumission, évaluation, acceptation ou refus, puis transformation.',
    'landing.fonction.objectifs.titre': 'Objectifs stratégiques',
    'landing.fonction.objectifs.texte': 'Rattachez chaque initiative à un objectif métier.',
    'landing.fonction.roles.titre': 'Rôles & permissions',
    'landing.fonction.roles.texte': 'Des droits fins appliqués côté serveur, pour chaque profil.',
    'landing.fonction.alertes.titre': 'Alertes de retard',
    'landing.fonction.alertes.texte': 'Détection automatique et comptage des jours de retard.',
    'landing.fonction.archives.titre': 'Archives',
    'landing.fonction.archives.texte': 'Conservez les initiatives clôturées sans encombrer les vues.',

    // Aperçu (captures réelles — libellés repris par les pages /decouvrir)
    'landing.apercu.dashboard.alt': "Capture de l'écran Tableau de bord",
    'landing.apercu.kanban.alt': 'Capture de la vue Kanban des initiatives',
    'landing.apercu.fiche.alt': "Capture d'une fiche d'initiative",

    // Comment ça marche
    'landing.etapes.eyebrow': 'Comment ça marche',
    'landing.etapes.titre': "Du besoin à l'initiative, en quatre étapes",
    'landing.etapes.texte': 'Un parcours simple et traçable, du premier besoin jusqu’au pilotage.',
    'landing.etapes.photoAlt': 'Équipe en atelier de travail',
    'landing.etape.soumettre.titre': 'Soumettre',
    'landing.etape.soumettre.texte': 'Un besoin métier arrive via le module Demandes, avec sa description et sa priorité.',
    'landing.etape.evaluer.titre': 'Évaluer',
    'landing.etape.evaluer.texte': 'Le responsable qualifie la demande : faisabilité, priorité, budget et échéance.',
    'landing.etape.transformer.titre': 'Transformer',
    'landing.etape.transformer.texte': 'La demande acceptée devient une initiative suivie et rattachée à un objectif.',
    'landing.etape.piloter.titre': 'Piloter',
    'landing.etape.piloter.texte': 'Statut, avancement et échéances sont suivis jusqu’à la clôture de l’initiative.',

    // Appel à l'action
    'landing.band.eyebrow': 'Prêt à démarrer',
    'landing.band.titre': 'Prêt à piloter votre portefeuille ?',
    'landing.band.texte': "Connectez-vous avec les identifiants fournis par votre administrateur et explorez l'application.",

    // Profils (utilisés dans le pied de page)
    'landing.role.administrateur.nom': 'Administrateur',
    'landing.role.responsable.nom': 'Responsable',
    'landing.role.visiteur.nom': 'Visiteur',

    // Pied de page
    'landing.footer.description':
      "Outil de gestion du portefeuille d'initiatives digitales : initiatives, demandes, roadmap et objectifs stratégiques.",
    'landing.footer.colPlateforme': 'Plateforme',
    'landing.footer.colProfils': 'Profils',
    'landing.footer.colAcces': 'Accès',
    'landing.footer.hautPage': 'Haut de page',
    // Navigation (sections de la refonte)
    'landing.nav.demo': 'Démo',
    'landing.nav.profils': 'Profils',
    'landing.nav.confiance': 'Confiance',

    // Démo vivante du hero (composant HeroPortefeuille)
    'landing.demo.hero.aria':
      "Démo animée du portefeuille : bulles d'initiatives, anneaux de santé et jalons de la semaine",
    'landing.demo.hero.titre': 'portefeuille',
    'landing.demo.hero.avancement': 'avancement',
    'landing.demo.hero.jalons': 'Jalons de la semaine',
    'landing.demo.nom.1': 'Portail client',
    'landing.demo.nom.2': 'CRM commercial',
    'landing.demo.nom.3': 'Data lake',
    'landing.demo.nom.4': 'Site institutionnel',
    'landing.demo.jalon.1': 'Recette — Portail client',
    'landing.demo.jalon.2': 'Bascule CRM',
    'landing.demo.jalon.3': 'Formation utilisateurs',

    // Démo Kanban interactive (composant DemoKanban)
    'landing.demo.eyebrow': 'Démo interactive',
    'landing.demo.titre': 'Le Kanban, manipulable ici même',
    'landing.demo.texte':
      "Cinq initiatives fictives à déplacer d'une colonne à l'autre : la même logique que dans l'application, sans compte ni donnée réelle.",
    'landing.demo.indice': 'Utilisez les flèches d’une carte pour changer son statut — rien n’est enregistré.',
    'landing.demo.boardAria': 'Tableau Kanban de démonstration',
    'landing.demo.colonneVide': 'Colonne vide',
    'landing.demo.deplace': '« {nom} » déplacée vers {statut}.',
    'landing.demo.recule': 'Déplacer {nom} vers la colonne précédente',
    'landing.demo.avance': 'Déplacer {nom} vers la colonne suivante',
    'landing.demo.aucunDeplacement': 'Aucun déplacement pour l’instant : à vous de jouer.',
    'landing.demo.moyenne': 'Avancement moyen de la démo : {pct} %',
    'landing.demo.reinitialiser': 'Réinitialiser',
    'landing.demo.carte.1': 'Portail client unifié',
    'landing.demo.carte.2': 'Migration cloud',
    'landing.demo.carte.3': 'Campagne e-recrutement',
    'landing.demo.carte.4': 'Facturation électronique',
    'landing.demo.carte.5': 'Portail fournisseurs',

    // Assistant IA — aperçu animé (composant DemoIA)
    'landing.ia.eyebrow': 'Assistant IA',
    'landing.ia.cta': 'Essayer l\'assistant',
    'landing.ia.titre': 'Demandez à votre portefeuille',
    'landing.ia.texte':
      "Un assistant conversationnel interroge vos données pour préparer résumés et analyses : des réponses sourcées sur le portefeuille réel, jamais inventées.",
    'landing.ia.legal':
      "L’assistant répond uniquement à partir des données de votre portefeuille ; chaque action sensible reste soumise aux permissions et à votre validation (loi 09-08 / CNDP).",
    'landing.ia.url': 'assistant.it6.ma',
    'landing.ia.q1': 'Quelles initiatives risquent de dépasser leur budget ?',
    'landing.ia.q1.r1': 'Trois initiatives dépassent 90 % de leur budget pour moins de 60 % d’avancement :',
    'landing.ia.q1.r2': '· Data lake — 94 % consommé, 41 % d’avancement',
    'landing.ia.q1.r3': '· Bascule CRM — 91 % consommé, 55 % d’avancement',
    'landing.ia.q2': 'Résume la santé du portefeuille cette semaine.',
    'landing.ia.q2.r1': '6 initiatives en cours : 4 au vert, 1 sous surveillance, 1 à risque (date butoir dépassée).',
    'landing.ia.q2.r2': '3 jalons arrivent à échéance d’ici dimanche.',
    'landing.ia.note': 'Aperçu animé — aucune IA réelle ni requête réseau',

    // Avant / après (composant AvantApres)
    'landing.avantApres.avant': 'Aujourd’hui',
    'landing.avantApres.apres': 'Avec la plateforme',
    'landing.avantApres.kpiInitiatives': 'initiatives',
    'landing.avantApres.kpiAvancement': 'avancement moyen',
    'landing.avantApres.kpiBudget': 'MAD de budget',
    'landing.avantApres.curseurAria':
      'Curseur avant / après : faites glisser ou utilisez les flèches du clavier',

    // Profils (composant ChoixProfil)
    'landing.profils.eyebrow': 'Profils',
    'landing.profils.titre': 'Une lecture adaptée à chaque rôle',
    'landing.profils.texte':
      "Trois profils, trois périmètres : choisissez le vôtre pour découvrir ce que l'application vous montre.",
    'landing.profils.question': 'Je suis…',
    'landing.profils.droits': ' permissions appliquées côté serveur',
    'landing.profils.admin.nom': 'Directeur',
    'landing.profils.admin.vision':
      "L'administrateur pilote tout le portefeuille : KPI consolidés, graphiques, budget, décisions et administration de la plateforme.",
    'landing.profils.admin.m1': 'Tableau de bord et surveillance de toutes les initiatives',
    'landing.profils.admin.m2': 'Gestion des utilisateurs, rôles et permissions',
    'landing.profils.admin.m3': 'Paramétrage (statuts, priorités, entités) et audit complet',
    'landing.profils.resp.nom': 'Responsable',
    'landing.profils.resp.vision':
      "Le responsable qualifie les demandes et anime ses initiatives : jalons, étapes, avancement et budget jusqu'à la clôture.",
    'landing.profils.resp.m1': 'Créer et suivre ses initiatives, jalons et étapes',
    'landing.profils.resp.m2': 'Évaluer les demandes et les transformer en initiatives',
    'landing.profils.resp.m3': 'Suivre budget et écarts de son périmètre',
    'landing.profils.visiteur.nom': 'Visiteur',
    'landing.profils.visiteur.vision':
      "Le visiteur consulte et s'informe : dashboard, liste filtrée, roadmap et objectifs — sans possibilité de modification.",
    'landing.profils.visiteur.m1': 'Consulter le tableau de bord et les indicateurs',
    'landing.profils.visiteur.m2': 'Parcourir les initiatives et la roadmap avec filtres',
    'landing.profils.visiteur.m3': 'Aucune écriture : les droits sont vérifiés côté serveur',

    // Confiance
    'landing.confiance.eyebrow': 'Confiance',
    'landing.confiance.titre': 'Conçu pour l’entreprise',
    'landing.confiance.texte': 'Droits fins, traçabilité complète et hébergement maîtrisé.',
    'landing.confiance.roles.titre': 'Rôles & permissions',
    'landing.confiance.roles.texte':
      "17 permissions pour l'administrateur, 14 pour le responsable, 3 pour le visiteur — appliquées sur chaque requête, pas seulement masquées à l'écran.",
    'landing.confiance.audit.titre': "Journal d'audit",
    'landing.confiance.audit.texte':
      "Chaque modification est horodatée, tracée champ à champ et classée (manuel ou automatique) ; le journal est immuable, aucune suppression n'y est exposée.",
    'landing.confiance.hebergement.titre': 'Hébergement local',
    'landing.confiance.hebergement.texte':
      "Base de données, backend et frontend tournent chez vous (Docker), derrière HTTPS et une politique de sécurité stricte — aucune donnée ne quitte votre réseau.",

    'landing.footer.copyright': '© {annee} IT6 Consulting — Tous droits réservés.'
  },

  en: {
    'landing.brand.sousTitre': 'Initiatives portfolio',
    'landing.nav.fonctionnalites': 'Features',
    'landing.nav.etapes': 'How it works',
    'landing.nav.contact': 'Contact',
    'landing.menu.ouvrir': 'Open menu',
    'landing.menu.fermer': 'Close menu',
    'landing.menu.navigationPrincipale': 'Main navigation',

    // « Découvrir » links: image hover badge + dotted-underlined words
    'landing.enSavoirPlus': 'Learn more',
    'landing.mot.initiatives': 'initiatives',
    'landing.mot.roadmap': 'roadmap',
    'landing.mot.kpi': 'KPI',
    'landing.mot.avancement': 'progress',
    'landing.mot.budget': 'budget',
    'landing.mot.jalons': 'milestones',

    'landing.cta.seConnecter': 'Sign in',
    'landing.cta.ouvrirApp': 'Open the app',

    'landing.hero.eyebrow': 'Portfolio management platform · IT6 Consulting',
    'landing.hero.titre1': 'Drive your portfolio',
    'landing.hero.titre2Prefix': 'of ',
    'landing.hero.titre2Highlight': 'digital initiatives',
    'landing.hero.lead':
      'A single platform to track initiatives, qualify requests, visualise the roadmap and align projects with strategic objectives.',
    'landing.accesReserve': 'Access reserved for collaborators: your account is created by your administrator.',

    'landing.contact.titre': 'Contact us',
    'landing.contact.texte': 'Access is restricted to staff: accounts are created by the administrator. To request access or ask a question, write to us.',
    'landing.contact.ecrire': 'Send an email',
    'landing.contact.projet': 'IT6 Consulting — Information Systems Department.',
    'landing.avantage.pilotage': 'Unified portfolio management',
    'landing.avantage.workflow': 'Traceable request workflow',
    'landing.avantage.alignement': 'Measured strategic alignment',
    'landing.avantage.decisions': 'Decisions based on real data',

    'landing.stats.aria': 'The platform in figures',
    'landing.chiffre.modules': 'Integrated modules',
    'landing.chiffre.statuts': 'Business statuses tracked',
    'landing.chiffre.roles': 'Roles & permissions',
    'landing.chiffre.source': 'Single source of truth',

    'landing.contraste.eyebrow': 'Problem & solution',
    'landing.contraste.titre': 'No more scattered tracking',
    'landing.contraste.texte':
      'Shared spreadsheets, email threads, stale statuses: portfolio steering quickly falls apart. The IT6 platform centralises everything, from business need to decision.',
    'landing.contraste.probleme.i1': 'Shared spreadsheets that drift apart',
    'landing.contraste.probleme.i2': 'Requests scattered across emails',
    'landing.contraste.probleme.i3': 'Indicators recalculated by hand',
    'landing.contraste.solution.i1': 'A single source of truth for the whole portfolio',
    'landing.contraste.solution.i2': 'A request workflow traced end to end',
    'landing.contraste.solution.i3': 'KPIs computed server-side, in real time',

    'landing.fonctionnalites.eyebrow': 'Features',
    'landing.fonctionnalites.titre': 'Everyday functions',
    'landing.fonctionnalites.texte': 'Concrete tools, designed for operational tracking.',
    'landing.fonction.recherche.titre': 'Global search',
    'landing.fonction.recherche.texte': 'Find an initiative by code or name from the top bar.',
    'landing.fonction.kanban.titre': 'Kanban view',
    'landing.fonction.kanban.texte': 'The portfolio organised into columns, one per status.',
    'landing.fonction.roadmap.titre': 'Monthly roadmap',
    'landing.fonction.roadmap.texte': 'A timeline to anticipate deadlines over several months.',
    'landing.fonction.demandes.titre': 'Request workflow',
    'landing.fonction.demandes.texte': 'Submission, assessment, acceptance or rejection, then conversion.',
    'landing.fonction.objectifs.titre': 'Strategic objectives',
    'landing.fonction.objectifs.texte': 'Attach each initiative to a business objective.',
    'landing.fonction.roles.titre': 'Roles & permissions',
    'landing.fonction.roles.texte': 'Fine-grained rights enforced server-side, for every profile.',
    'landing.fonction.alertes.titre': 'Overdue alerts',
    'landing.fonction.alertes.texte': 'Automatic detection and day counting of delays.',
    'landing.fonction.archives.titre': 'Archive',
    'landing.fonction.archives.texte': 'Keep closed initiatives without cluttering your views.',

    'landing.apercu.dashboard.alt': 'Screenshot of the dashboard screen',
    'landing.apercu.kanban.alt': 'Screenshot of the initiatives Kanban view',
    'landing.apercu.fiche.alt': 'Screenshot of an initiative record',

    'landing.etapes.eyebrow': 'How it works',
    'landing.etapes.titre': 'From need to initiative, in four steps',
    'landing.etapes.texte': 'A simple, traceable journey, from the first need through to steering.',
    'landing.etapes.photoAlt': 'Team in a working session',
    'landing.etape.soumettre.titre': 'Submit',
    'landing.etape.soumettre.texte': 'A business need comes in through the Requests module, with its description and priority.',
    'landing.etape.evaluer.titre': 'Assess',
    'landing.etape.evaluer.texte': 'The manager qualifies the request: feasibility, priority, budget and deadline.',
    'landing.etape.transformer.titre': 'Convert',
    'landing.etape.transformer.texte': 'The accepted request becomes a tracked initiative attached to an objective.',
    'landing.etape.piloter.titre': 'Steer',
    'landing.etape.piloter.texte': 'Status, progress and deadlines are tracked until the initiative is closed.',

    'landing.band.eyebrow': 'Ready to start',
    'landing.band.titre': 'Ready to steer your portfolio?',
    'landing.band.texte': 'Sign in with the credentials provided by your administrator and explore the application.',

    'landing.role.administrateur.nom': 'Administrator',
    'landing.role.responsable.nom': 'Manager',
    'landing.role.visiteur.nom': 'Viewer',

    'landing.footer.description':
      'Digital initiatives portfolio management tool: initiatives, requests, roadmap and strategic objectives.',
    'landing.footer.colPlateforme': 'Platform',
    'landing.footer.colProfils': 'Profiles',
    'landing.footer.colAcces': 'Access',
    'landing.footer.hautPage': 'Back to top',
    // Navigation (refonte sections)
    'landing.nav.demo': 'Live demo',
    'landing.nav.profils': 'Profiles',
    'landing.nav.confiance': 'Trust',

    // Hero live demo (HeroPortefeuille)
    'landing.demo.hero.aria':
      'Animated portfolio demo: initiative bubbles, health rings and weekly milestones',
    'landing.demo.hero.titre': 'portfolio',
    'landing.demo.hero.avancement': 'progress',
    'landing.demo.hero.jalons': 'Milestones this week',
    'landing.demo.nom.1': 'Customer portal',
    'landing.demo.nom.2': 'Sales CRM',
    'landing.demo.nom.3': 'Data lake',
    'landing.demo.nom.4': 'Corporate site',
    'landing.demo.jalon.1': 'UAT — Customer portal',
    'landing.demo.jalon.2': 'CRM cutover',
    'landing.demo.jalon.3': 'User training',

    // Interactive Kanban demo (DemoKanban)
    'landing.demo.eyebrow': 'Interactive demo',
    'landing.demo.titre': 'A Kanban you can play with right here',
    'landing.demo.texte':
      "Five fictional initiatives to move across columns: the same logic as the app, with no account and no real data.",
    'landing.demo.indice': 'Use a card’s arrows to change its status — nothing is saved.',
    'landing.demo.boardAria': 'Demo Kanban board',
    'landing.demo.colonneVide': 'Empty column',
    'landing.demo.deplace': '“{nom}” moved to {statut}.',
    'landing.demo.recule': 'Move {nom} to the previous column',
    'landing.demo.avance': 'Move {nom} to the next column',
    'landing.demo.aucunDeplacement': 'No moves yet: your turn to play.',
    'landing.demo.moyenne': 'Demo average progress: {pct} %',
    'landing.demo.reinitialiser': 'Reset',
    'landing.demo.carte.1': 'Unified customer portal',
    'landing.demo.carte.2': 'Cloud migration',
    'landing.demo.carte.3': 'E-recruitment campaign',
    'landing.demo.carte.4': 'E-invoicing',
    'landing.demo.carte.5': 'Supplier portal',

    // AI assistant — animated preview (DemoIA)
    'landing.ia.eyebrow': 'AI assistant',
    'landing.ia.cta': 'Try the assistant',
    'landing.ia.titre': 'Ask your portfolio',
    'landing.ia.texte':
      "A conversational assistant queries your data to draft summaries and analysis: answers grounded in the real portfolio, never invented.",
    'landing.ia.legal':
      "The assistant only answers from your portfolio's data; every sensitive action stays subject to permissions and your validation (law 09-08 / CNDP).",
    'landing.ia.url': 'assistant.it6.ma',
    'landing.ia.q1': 'Which initiatives are at risk of exceeding their budget?',
    'landing.ia.q1.r1': 'Three initiatives have consumed over 90 % of budget for under 60 % progress:',
    'landing.ia.q1.r2': '· Data lake — 94 % spent, 41 % progress',
    'landing.ia.q1.r3': '· CRM cutover — 91 % spent, 55 % progress',
    'landing.ia.q2': 'Summarize portfolio health for this week.',
    'landing.ia.q2.r1': '6 initiatives in progress: 4 on track, 1 to watch, 1 at risk (due date passed).',
    'landing.ia.q2.r2': '3 milestones are due by Sunday.',
    'landing.ia.note': 'Animated preview — no real AI, no network request',

    // Before / after (AvantApres)
    'landing.avantApres.avant': 'Today',
    'landing.avantApres.apres': 'With the platform',
    'landing.avantApres.kpiInitiatives': 'initiatives',
    'landing.avantApres.kpiAvancement': 'average progress',
    'landing.avantApres.kpiBudget': 'MAD budget',
    'landing.avantApres.curseurAria':
      'Before/after slider: drag it or use the keyboard arrows',

    // Profiles (ChoixProfil)
    'landing.profils.eyebrow': 'Profiles',
    'landing.profils.titre': 'A view tailored to each role',
    'landing.profils.texte':
      'Three roles, three scopes: pick yours to see what the app shows you.',
    'landing.profils.question': 'I am…',
    'landing.profils.droits': ' server-side permissions enforced',
    'landing.profils.admin.nom': 'Director',
    'landing.profils.admin.vision':
      'The administrator steers the whole portfolio: consolidated KPIs, charts, budget, decisions and platform administration.',
    'landing.profils.admin.m1': 'Dashboard and monitoring of every initiative',
    'landing.profils.admin.m2': 'Users, roles and permissions management',
    'landing.profils.admin.m3': 'Settings (statuses, priorities, entities) and full audit',
    'landing.profils.resp.nom': 'Manager',
    'landing.profils.resp.vision':
      "The manager qualifies requests and drives their initiatives: milestones, steps, progress and budget up to closure.",
    'landing.profils.resp.m1': 'Create and follow initiatives, milestones and steps',
    'landing.profils.resp.m2': 'Evaluate requests and turn them into initiatives',
    'landing.profils.resp.m3': 'Track budget and variances within their scope',
    'landing.profils.visiteur.nom': 'Viewer',
    'landing.profils.visiteur.vision':
      'The viewer reads and stays informed: dashboard, filtered list, roadmap and objectives — with no edit rights.',
    'landing.profils.visiteur.m1': 'View the dashboard and indicators',
    'landing.profils.visiteur.m2': 'Browse initiatives and roadmap with filters',
    'landing.profils.visiteur.m3': 'No writes: rights are enforced server-side',

    // Trust
    'landing.confiance.eyebrow': 'Trust',
    'landing.confiance.titre': 'Built for the enterprise',
    'landing.confiance.texte': 'Fine-grained rights, full traceability and controlled hosting.',
    'landing.confiance.roles.titre': 'Roles & permissions',
    'landing.confiance.roles.texte':
      "17 permissions for the administrator, 14 for the manager, 3 for the viewer — enforced on every request, not just hidden on screen.",
    'landing.confiance.audit.titre': 'Audit log',
    'landing.confiance.audit.texte':
      'Every change is timestamped, traced field by field and classified (manual or automatic); the log is immutable, no deletion is exposed.',
    'landing.confiance.hebergement.titre': 'On-premises hosting',
    'landing.confiance.hebergement.texte':
      'Database, backend and frontend run in-house (Docker), behind HTTPS and a strict security policy — data never leaves your network.',

    'landing.footer.copyright': '© {annee} IT6 Consulting — All rights reserved.'
  },

  ar: {
    'landing.brand.sousTitre': 'محفظة المبادرات',
    'landing.nav.fonctionnalites': 'الميزات',
    'landing.nav.etapes': 'كيف يعمل',
    'landing.nav.contact': 'اتصل بنا',
    'landing.menu.ouvrir': 'فتح القائمة',
    'landing.menu.fermer': 'إغلاق القائمة',
    'landing.menu.navigationPrincipale': 'التنقل الرئيسي',

    // روابط « الاكتشاف »: شارة عند المرور على الصور وكلمات مسطّرة
    'landing.enSavoirPlus': 'اعرف المزيد',
    'landing.mot.initiatives': 'المبادرات',
    'landing.mot.roadmap': 'خارطة الطريق',
    'landing.mot.kpi': 'مؤشرات',
    'landing.mot.avancement': 'التقدم',
    'landing.mot.budget': 'الميزانية',
    'landing.mot.jalons': 'المراحل',

    'landing.cta.seConnecter': 'تسجيل الدخول',
    'landing.cta.ouvrirApp': 'فتح التطبيق',

    'landing.hero.eyebrow': 'منصة إدارة المبادرات · IT6 Consulting',
    'landing.hero.titre1': 'قُد محفظتك',
    'landing.hero.titre2Prefix': 'من المبادرات',
    'landing.hero.titre2Highlight': 'الرقمية',
    'landing.hero.lead':
      'منصة واحدة لتتبع المبادرات، وتأهيل الطلبات، وعرض خارطة الطريق، ومواءمة المشاريع مع الأهداف الاستراتيجية.',
    'landing.accesReserve': 'الوصول مقصور على الموظفين: يُنشئ حسابك مسؤولك.',

    'landing.contact.titre': 'اتصل بنا',
    'landing.contact.texte': 'الوصول مقصور على الموظفين: يتم إنشاء الحسابات من طرف المسؤول. للحصول على وصول أو لطرح سؤال، راسلنا.',
    'landing.contact.ecrire': 'أرسل بريداً إلكترونياً',
    'landing.contact.projet': 'IT6 Consulting — مديرية نظم المعلومات.',
    'landing.avantage.pilotage': 'إدارة موحدة للمحفظة',
    'landing.avantage.workflow': 'مسار عمل قابل للتتبع للطلبات',
    'landing.avantage.alignement': 'مواءمة استراتيجية قابلة للقياس',
    'landing.avantage.decisions': 'قرارات مبنية على بيانات حقيقية',

    'landing.stats.aria': 'المنصة بالأرقام',
    'landing.chiffre.modules': 'وحدات مدمجة',
    'landing.chiffre.statuts': 'حالات متابَعة',
    'landing.chiffre.roles': 'الأدوار والصلاحيات',
    'landing.chiffre.source': 'مصدر واحد للحقيقة',

    'landing.contraste.eyebrow': 'المشكلة والحل',
    'landing.contraste.titre': 'نهاية التتبع المتناثر',
    'landing.contraste.texte':
      'جداول مشتركة ورسائل بريد وحالات قديمة: يضيع تدقيق المحفظة بسرعة. منصة IT6 تُوحّد كل شيء، من الحاجة المهنية إلى القرار.',
    'landing.contraste.probleme.i1': 'جداول مشتركة تتشتت فيها البيانات',
    'landing.contraste.probleme.i2': 'طلبات موزعة على رسائل البريد',
    'landing.contraste.probleme.i3': 'مؤشرات تُعاد حسابها يدويًا',
    'landing.contraste.solution.i1': 'مصدر واحد للحقيقة لكل المحفظة',
    'landing.contraste.solution.i2': 'مسار عمل للطلبات مُتتبَّع من البداية إلى النهاية',
    'landing.contraste.solution.i3': 'مؤشرات أداء تُحسب على الخادم في الوقت الفعلي',

    'landing.fonctionnalites.eyebrow': 'الميزات',
    'landing.fonctionnalites.titre': 'وظائف الحياة اليومية',
    'landing.fonctionnalites.texte': 'أدوات عملية مصممة للمتابعة التشغيلية.',
    'landing.fonction.recherche.titre': 'بحث شامل',
    'landing.fonction.recherche.texte': 'اعثر على أي مبادرة بالرمز أو الاسم من الشريط العلوي.',
    'landing.fonction.kanban.titre': 'عرض كانبان',
    'landing.fonction.kanban.texte': 'المحفظة موزعة على أعمدة، عمود لكل حالة.',
    'landing.fonction.roadmap.titre': 'خارطة طريق شهرية',
    'landing.fonction.roadmap.texte': 'خط زمني لاستباق الاستحقاقات على عدة أشهر.',
    'landing.fonction.demandes.titre': 'مسار عمل الطلبات',
    'landing.fonction.demandes.texte': 'الإرسال والتقييم والقبول أو الرفض ثم التحويل إلى مبادرة.',
    'landing.fonction.objectifs.titre': 'الأهداف الاستراتيجية',
    'landing.fonction.objectifs.texte': 'اربط كل مبادرة بهدف عمل.',
    'landing.fonction.roles.titre': 'الأدوار والصلاحيات',
    'landing.fonction.roles.texte': 'صلاحيات دقيقة تُطبَّق على الخادم لكل ملف مستخدم.',
    'landing.fonction.alertes.titre': 'تنبيهات التأخر',
    'landing.fonction.alertes.texte': 'كشف آلي للتأخر وعدّ الأيام المنقضية.',
    'landing.fonction.archives.titre': 'الأرشيف',
    'landing.fonction.archives.texte': 'احتفظ بالمبادرات المغلقة دون إثقال العروض.',

    'landing.apercu.dashboard.alt': 'لقطة شاشة لوحة المعلومات',
    'landing.apercu.kanban.alt': 'لقطة شاشة عرض كانبان للمبادرات',
    'landing.apercu.fiche.alt': 'لقطة شاشة بطاقة مبادرة',

    'landing.etapes.eyebrow': 'كيف يعمل',
    'landing.etapes.titre': 'من الحاجة إلى المبادرة في أربع خطوات',
    'landing.etapes.texte': 'مسار بسيط وقابل للتتبع، من أول حاجة إلى التدقيق.',
    'landing.etapes.photoAlt': 'فريق في ورشة عمل',
    'landing.etape.soumettre.titre': 'الإرسال',
    'landing.etape.soumettre.texte': 'تصل حاجة مهنية عبر وحدة الطلبات مع وصفها وأولويتها.',
    'landing.etape.evaluer.titre': 'التقييم',
    'landing.etape.evaluer.texte': 'يؤهّل المسؤول الطلب: الإمكانية والأولوية والميزانية والاستحقاق.',
    'landing.etape.transformer.titre': 'التحويل',
    'landing.etape.transformer.texte': 'يصبح الطلب المقبول مبادرة متابَعة مرتبطة بهدف.',
    'landing.etape.piloter.titre': 'التدقيق',
    'landing.etape.piloter.texte': 'تُتابع الحالة والتقدم والاستحقاقات حتى إغلاق المبادرة.',

    'landing.band.eyebrow': 'جاهز للانطلاق',
    'landing.band.titre': 'هل أنت جاهز لتدقيق محفظتك؟',
    'landing.band.texte': 'سجّل الدخول ببيانات الاعتماد التي يزوّدك بها المسؤول واستكشف التطبيق.',

    'landing.role.administrateur.nom': 'المشرف',
    'landing.role.responsable.nom': 'المسؤول',
    'landing.role.visiteur.nom': 'الزائر',

    'landing.footer.description':
      'أداة لإدارة محفظة المبادرات الرقمية: المبادرات والطلبات وخارطة الطريق والأهداف الاستراتيجية.',
    'landing.footer.colPlateforme': 'المنصة',
    'landing.footer.colProfils': 'الملفات',
    'landing.footer.colAcces': 'الدخول',
    'landing.footer.hautPage': 'أعلى الصفحة',
    // التنقل (أقسام الواجهة الجديدة)
    'landing.nav.demo': 'عرض توضيحي',
    'landing.nav.profils': 'الملفات',
    'landing.nav.confiance': 'الثقة',

    // العرض الحي في الواجهة (HeroPortefeuille)
    'landing.demo.hero.aria': 'عرض متحرك للمحفظة: دوائر المبادرات وحلقات الصحة ومراحل هذا الأسبوع',
    'landing.demo.hero.titre': 'المحفظة',
    'landing.demo.hero.avancement': 'التقدم',
    'landing.demo.hero.jalons': 'مراحل هذا الأسبوع',
    'landing.demo.nom.1': 'بوابة الزبناء',
    'landing.demo.nom.2': 'نظام CRM',
    'landing.demo.nom.3': 'بحيرة البيانات',
    'landing.demo.nom.4': 'الموقع المؤسسي',
    'landing.demo.jalon.1': 'القبول — بوابة الزبناء',
    'landing.demo.jalon.2': 'ترحيل CRM',
    'landing.demo.jalon.3': 'تكوين المستخدمين',

    // كانبان تفاعلي (DemoKanban)
    'landing.demo.eyebrow': 'عرض تفاعلي',
    'landing.demo.titre': 'كانبان يمكنك تجربته هنا',
    'landing.demo.texte': 'خمس مبادرات وهمية لنقلها بين الأعمدة: نفس منطق التطبيق، دون حساب ودون بيانات حقيقية.',
    'landing.demo.indice': 'استعمل أسهم البطاقة لتغيير حالتها — لا يُحفظ شيء.',
    'landing.demo.boardAria': 'لوحة كانبان تجريبية',
    'landing.demo.colonneVide': 'عمود فارغ',
    'landing.demo.deplace': '« {nom} » نُقلت إلى {statut}.',
    'landing.demo.recule': 'نقل {nom} إلى العمود السابق',
    'landing.demo.avance': 'نقل {nom} إلى العمود التالي',
    'landing.demo.aucunDeplacement': 'لا نقل بعد — الدور عليك.',
    'landing.demo.moyenne': 'متوسط التقدم في العرض: {pct} %',
    'landing.demo.reinitialiser': 'إعادة الضبط',
    'landing.demo.carte.1': 'بوابة زبناء موحدة',
    'landing.demo.carte.2': 'الترحيل إلى السحابة',
    'landing.demo.carte.3': 'حملة التوظيف الإلكترونية',
    'landing.demo.carte.4': 'الفوترة الإلكترونية',
    'landing.demo.carte.5': 'بوابة الموردين',

    // مساعد الذكاء الاصطناعي — معاينة متحركة (DemoIA)
    'landing.ia.eyebrow': 'مساعد الذكاء الاصطناعي',
    'landing.ia.cta': 'جرّب المساعد',
    'landing.ia.titre': 'اسأل محفظتك',
    'landing.ia.texte': 'مساعد حواري يستعلم عن بياناتك لتحضير ملخصات وتحليلات: إجابات مستندة إلى المحفظة الحقيقية وليست مخترعة.',
    'landing.ia.legal': 'يجيب المساعد انطلاقًا من بيانات محفظتك فقط؛ وكل إجراء حساس يبقى خاضعًا للأذونات وموافقتك الخاصة (قانون 09-08 / CNDC).',
    'landing.ia.url': 'assistant.it6.ma',
    'landing.ia.q1': 'ما المبادرات المعرّضة لتجاوز الميزانية؟',
    'landing.ia.q1.r1': 'ثلاث مبادرات تجاوزت 90 % من الميزانية بتقدم أقل من 60 %:',
    'landing.ia.q1.r2': '· بحيرة البيانات — 94 % مستهلكة، 41 % تقدم',
    'landing.ia.q1.r3': '· ترحيل CRM — 91 % مستهلكة، 55 % تقدم',
    'landing.ia.q2': 'لخّص صحة المحفظة لهذا الأسبوع.',
    'landing.ia.q2.r1': '6 مبادرات جارية: 4 آمنة، 1 تحت المراقبة، 1 معرّضة للخطر (تجاوز الأجل).',
    'landing.ia.q2.r2': '3 مراحل تستحق قبل يوم الأحد.',
    'landing.ia.note': 'معاينة متحركة — لا ذكاء اصطناعي حقيقي ولا طلب شبكة',

    // قبل / بعد (AvantApres)
    'landing.avantApres.avant': 'اليوم',
    'landing.avantApres.apres': 'مع المنصة',
    'landing.avantApres.kpiInitiatives': 'مبادرة',
    'landing.avantApres.kpiAvancement': 'متوسط التقدم',
    'landing.avantApres.kpiBudget': 'مليون درهم ميزانية',
    'landing.avantApres.curseurAria': 'منزلق قبل/بعد: اسحبه أو استعمل أسهم لوحة المفاتيح',

    // الملفات (ChoixProfil)
    'landing.profils.eyebrow': 'الملفات',
    'landing.profils.titre': 'قراءة مخصصة لكل دور',
    'landing.profils.texte': 'ثلاثة أدوار وثلاثة نطاقات: اختر ملفك لترى ما يعرضه لك التطبيق.',
    'landing.profils.question': 'أنا…',
    'landing.profils.droits': ' إذن مفروض على مستوى الخادم',
    'landing.profils.admin.nom': 'مدير',
    'landing.profils.admin.vision': 'يدير المدير المحفظة كاملة: مؤشرات مجمّعة ومخططات وميزانية وقرارات وإدارة المنصة.',
    'landing.profils.admin.m1': 'لوحة المعلومات ومراقبة جميع المبادرات',
    'landing.profils.admin.m2': 'تدبير المستخدمين والأدوار والأذونات',
    'landing.profils.admin.m3': 'الإعدادات (الحالات الأولويات الكيانات) وتدقيق كامل',
    'landing.profils.resp.nom': 'مسؤول',
    'landing.profils.resp.vision': 'يؤهّل المسؤول الطلبات ويقود مبادراته: المراحل والخطوات والتقدم والميزانية حتى الإغلاق.',
    'landing.profils.resp.m1': 'إنشاء وتدبير المبادرات والمراحل والخطوات',
    'landing.profils.resp.m2': 'تقييم الطلبات وتحويلها إلى مبادرات',
    'landing.profils.resp.m3': 'تتبع الميزانية والفوارق في نطاقه',
    'landing.profils.visiteur.nom': 'زائر',
    'landing.profils.visiteur.vision': 'يطّلع الزائر ويستعلم: لوحة المعلومات والقائمة المفلترة وخارطة الطريق والأهداف — دون أي تعديل.',
    'landing.profils.visiteur.m1': 'الاطلاع على لوحة المعلومات والمؤشرات',
    'landing.profils.visiteur.m2': 'تصفح المبادرات وخارطة الطريق مع الفلاتر',
    'landing.profils.visiteur.m3': 'لا كتابة: الأذونات مفروضة على مستوى الخادم',

    // الثقة
    'landing.confiance.eyebrow': 'الثقة',
    'landing.confiance.titre': 'مصمّمة للمؤسسة',
    'landing.confiance.texte': 'أذونات دقيقة وتتبّع كامل واستضافة محكومة.',
    'landing.confiance.roles.titre': 'الأدوار والأذونات',
    'landing.confiance.roles.texte': '17 إذنًا للمدير، و14 للمسؤول، و3 للزائر — مفروضة في كل طلب، لا مجرّد إخفاء في الشاشة.',
    'landing.confiance.audit.titre': 'سجل التدقيق',
    'landing.confiance.audit.texte': 'كل تعديل موثّق بالتوقيت ومسجّل حقلًا بحقل ومصنّف (يدوي أو آلي)؛ والسجل غير قابل للحذف، لا واجهة حذف معرَّضة.',
    'landing.confiance.hebergement.titre': 'استضافة محلية',
    'landing.confiance.hebergement.texte': 'قاعدة البيانات والواجهة الخلفية والمقدمة تعمل عندكم (Docker)، خلف HTTPS وسياسة أمنية صارمة — لا تغادر بياناتكم شبكتكم.',

    'landing.footer.copyright': '© {annee} IT6 Consulting — جميع الحقوق محفوظة.'
  },

  es: {
    'landing.brand.sousTitre': 'Cartera de iniciativas',
    'landing.nav.fonctionnalites': 'Funcionalidades',
    'landing.nav.etapes': 'Cómo funciona',
    'landing.nav.contact': 'Contacto',
    'landing.menu.ouvrir': 'Abrir el menú',
    'landing.menu.fermer': 'Cerrar el menú',
    'landing.menu.navigationPrincipale': 'Navegación principal',

    // Enlaces « Descubrir »: insignia al pasar sobre las imágenes y palabras subrayadas
    'landing.enSavoirPlus': 'Saber más',
    'landing.mot.initiatives': 'iniciativas',
    'landing.mot.roadmap': 'hoja de ruta',
    'landing.mot.kpi': 'KPI',
    'landing.mot.avancement': 'avance',
    'landing.mot.budget': 'presupuesto',
    'landing.mot.jalons': 'hitos',

    'landing.cta.seConnecter': 'Iniciar sesión',
    'landing.cta.ouvrirApp': 'Abrir la aplicación',

    'landing.hero.eyebrow': 'Plataforma de gestión del portafolio · IT6 Consulting',
    'landing.hero.titre1': 'Dirige tu cartera',
    'landing.hero.titre2Prefix': 'de iniciativas ',
    'landing.hero.titre2Highlight': 'digitales',
    'landing.hero.lead':
      'Una plataforma única para seguir las iniciativas, calificar las solicitudes, visualizar la hoja de ruta y alinear los proyectos con los objetivos estratégicos.',
    'landing.accesReserve': 'Acceso reservado a colaboradores: su cuenta la crea su administrador.',

    'landing.contact.titre': 'Contáctenos',
    'landing.contact.texte': 'El acceso está reservado a los colaboradores: las cuentas las crea el administrador. Para solicitar un acceso o hacer una pregunta, escríbanos.',
    'landing.contact.ecrire': 'Enviar un email',
    'landing.contact.projet': 'IT6 Consulting — Dirección de Sistemas de Información.',
    'landing.avantage.pilotage': 'Gestión unificada de la cartera',
    'landing.avantage.workflow': 'Flujo de solicitudes trazable',
    'landing.avantage.alignement': 'Alineación estratégica medible',
    'landing.avantage.decisions': 'Decisiones basadas en datos reales',

    'landing.stats.aria': 'La plataforma en cifras',
    'landing.chiffre.modules': 'Módulos integrados',
    'landing.chiffre.statuts': 'Estados de negocio controlados',
    'landing.chiffre.roles': 'Roles y permisos',
    'landing.chiffre.source': 'Fuente única de verdad',

    'landing.contraste.eyebrow': 'Problema y solución',
    'landing.contraste.titre': 'Se acabó el seguimiento disperso',
    'landing.contraste.texte':
      'Hojas de cálculo compartidas, correos electrónicos, estados obsoletos: el seguimiento de la cartera se pierde fácilmente. La plataforma IT6 lo centraliza todo, desde la necesidad de negocio hasta la decisión.',
    'landing.contraste.probleme.i1': 'Hojas de cálculo compartidas que divergen',
    'landing.contraste.probleme.i2': 'Solicitudes repartidas por los correos',
    'landing.contraste.probleme.i3': 'Indicadores recalculados a mano',
    'landing.contraste.solution.i1': 'Una sola fuente de verdad para toda la cartera',
    'landing.contraste.solution.i2': 'Un flujo de solicitudes trazable de principio a fin',
    'landing.contraste.solution.i3': 'KPI calculados en el servidor, en tiempo real',

    'landing.fonctionnalites.eyebrow': 'Funcionalidades',
    'landing.fonctionnalites.titre': 'Las funciones del día a día',
    'landing.fonctionnalites.texte': 'Herramientas concretas, pensadas para el seguimiento operativo.',
    'landing.fonction.recherche.titre': 'Búsqueda global',
    'landing.fonction.recherche.texte': 'Encuentra una iniciativa por código o por nombre desde la barra superior.',
    'landing.fonction.kanban.titre': 'Vista Kanban',
    'landing.fonction.kanban.texte': 'La cartera organizada en columnas, una por estado.',
    'landing.fonction.roadmap.titre': 'Hoja de ruta mensual',
    'landing.fonction.roadmap.texte': 'Una línea de tiempo para anticipar vencimientos durante varios meses.',
    'landing.fonction.demandes.titre': 'Flujo de solicitudes',
    'landing.fonction.demandes.texte': 'Envío, evaluación, aceptación o rechazo y conversión.',
    'landing.fonction.objectifs.titre': 'Objetivos estratégicos',
    'landing.fonction.objectifs.texte': 'Relaciona cada iniciativa con un objetivo de negocio.',
    'landing.fonction.roles.titre': 'Roles y permisos',
    'landing.fonction.roles.texte': 'Permisos detallados aplicados en el servidor para cada perfil.',
    'landing.fonction.alertes.titre': 'Alertas de retraso',
    'landing.fonction.alertes.texte': 'Detección automática y conteo de los días de retraso.',
    'landing.fonction.archives.titre': 'Archivo',
    'landing.fonction.archives.texte': 'Conserva las iniciativas cerradas sin saturar las vistas.',

    'landing.apercu.dashboard.alt': 'Captura de la pantalla de cuadro de mando',
    'landing.apercu.kanban.alt': 'Captura de la vista Kanban de iniciativas',
    'landing.apercu.fiche.alt': 'Captura de una ficha de iniciativa',

    'landing.etapes.eyebrow': 'Cómo funciona',
    'landing.etapes.titre': 'De la necesidad a la iniciativa, en cuatro pasos',
    'landing.etapes.texte': 'Un recorrido sencillo y trazable, desde la primera necesidad hasta la dirección.',
    'landing.etapes.photoAlt': 'Equipo en una sesión de trabajo',
    'landing.etape.soumettre.titre': 'Enviar',
    'landing.etape.soumettre.texte': 'Una necesidad de negocio llega por el módulo de solicitudes, con su descripción y prioridad.',
    'landing.etape.evaluer.titre': 'Evaluar',
    'landing.etape.evaluer.texte': 'El responsable califica la solicitud: viabilidad, prioridad, presupuesto y vencimiento.',
    'landing.etape.transformer.titre': 'Convertir',
    'landing.etape.transformer.texte': 'La solicitud aceptada se convierte en una iniciativa vinculada a un objetivo.',
    'landing.etape.piloter.titre': 'Dirigir',
    'landing.etape.piloter.texte': 'Estado, avance y vencimientos se siguen hasta el cierre de la iniciativa.',

    'landing.band.eyebrow': 'Listo para empezar',
    'landing.band.titre': '¿Listo para dirigir tu cartera?',
    'landing.band.texte': 'Inicia sesión con las credenciales facilitadas por tu administrador y explora la aplicación.',

    'landing.role.administrateur.nom': 'Administrador',
    'landing.role.responsable.nom': 'Responsable',
    'landing.role.visiteur.nom': 'Visitante',

    'landing.footer.description':
      'Herramienta de gestión de la cartera de iniciativas digitales: iniciativas, solicitudes, hoja de ruta y objetivos estratégicos.',
    'landing.footer.colPlateforme': 'Plataforma',
    'landing.footer.colProfils': 'Perfiles',
    'landing.footer.colAcces': 'Acceso',
    'landing.footer.hautPage': 'Volver arriba',
    // Navegación (secciones de la renovación)
    'landing.nav.demo': 'Demo',
    'landing.nav.profils': 'Perfiles',
    'landing.nav.confiance': 'Confianza',

    // Demo en vivo del hero (HeroPortefeuille)
    'landing.demo.hero.aria':
      'Demo animada del portafolio: burbujas de iniciativas, anillos de salud y hitos de la semana',
    'landing.demo.hero.titre': 'portafolio',
    'landing.demo.hero.avancement': 'avance',
    'landing.demo.hero.jalons': 'Hitos de la semana',
    'landing.demo.nom.1': 'Portal de clientes',
    'landing.demo.nom.2': 'CRM comercial',
    'landing.demo.nom.3': 'Data lake',
    'landing.demo.nom.4': 'Sitio corporativo',
    'landing.demo.jalon.1': 'Pruebas — Portal de clientes',
    'landing.demo.jalon.2': 'Migración del CRM',
    'landing.demo.jalon.3': 'Formación de usuarios',

    // Demo Kanban interactiva (DemoKanban)
    'landing.demo.eyebrow': 'Demo interactiva',
    'landing.demo.titre': 'Un Kanban que puedes manipular aquí mismo',
    'landing.demo.texte':
      "Cinco iniciativas ficticias para mover entre columnas: la misma lógica que la aplicación, sin cuenta ni datos reales.",
    'landing.demo.indice': 'Usa las flechas de una tarjeta para cambiar su estado — no se guarda nada.',
    'landing.demo.boardAria': 'Tablero Kanban de demostración',
    'landing.demo.colonneVide': 'Columna vacía',
    'landing.demo.deplace': '«{nom}» movida a {statut}.',
    'landing.demo.recule': 'Mover {nom} a la columna anterior',
    'landing.demo.avance': 'Mover {nom} a la columna siguiente',
    'landing.demo.aucunDeplacement': 'Aún no hay movimientos: te toca.',
    'landing.demo.moyenne': 'Avance medio de la demo: {pct} %',
    'landing.demo.reinitialiser': 'Reiniciar',
    'landing.demo.carte.1': 'Portal unificado de clientes',
    'landing.demo.carte.2': 'Migración a la nube',
    'landing.demo.carte.3': 'Campaña de e-reclutamiento',
    'landing.demo.carte.4': 'Facturación electrónica',
    'landing.demo.carte.5': 'Portal de proveedores',

    // Asistente de IA — vista animada (DemoIA)
    'landing.ia.eyebrow': 'Asistente de IA',
    'landing.ia.cta': 'Probar el asistente',
    'landing.ia.titre': 'Pregúntale a tu portafolio',
    'landing.ia.texte':
      'Un asistente conversacional consulta tus datos para preparar resúmenes y análisis: respuestas basadas en el portafolio real, nunca inventadas.',
    'landing.ia.legal':
      'El asistente responde solo con los datos de tu portafolio; toda acción sensible sigue sometida a los permisos y a tu validación (ley 09-08 / CNDP).',
    'landing.ia.url': 'assistant.it6.ma',
    'landing.ia.q1': '¿Qué iniciativas corren riesgo de superar su presupuesto?',
    'landing.ia.q1.r1': 'Tres iniciativas han consumido más del 90 % del presupuesto con menos del 60 % de avance:',
    'landing.ia.q1.r2': '· Data lake — 94 % consumido, 41 % de avance',
    'landing.ia.q1.r3': '· Migración del CRM — 91 % consumido, 55 % de avance',
    'landing.ia.q2': 'Resume la salud del portafolio de esta semana.',
    'landing.ia.q2.r1': '6 iniciativas en curso: 4 en verde, 1 en observación, 1 en riesgo (fecha vencida).',
    'landing.ia.q2.r2': '3 hitos vencen antes del domingo.',
    'landing.ia.note': 'Vista animada — sin IA real ni peticiones de red',

    // Antes / después (AvantApres)
    'landing.avantApres.avant': 'Hoy',
    'landing.avantApres.apres': 'Con la plataforma',
    'landing.avantApres.kpiInitiatives': 'iniciativas',
    'landing.avantApres.kpiAvancement': 'avance medio',
    'landing.avantApres.kpiBudget': 'MAD de presupuesto',
    'landing.avantApres.curseurAria':
      'Deslizador antes/después: arrástralo o usa las flechas del teclado',

    // Perfiles (ChoixProfil)
    'landing.profils.eyebrow': 'Perfiles',
    'landing.profils.titre': 'Una lectura adaptada a cada rol',
    'landing.profils.texte':
      'Tres roles, tres ámbitos: elige el tuyo para descubrir lo que la aplicación te muestra.',
    'landing.profils.question': 'Soy…',
    'landing.profils.droits': ' permisos aplicados en el servidor',
    'landing.profils.admin.nom': 'Director',
    'landing.profils.admin.vision':
      'El administrador dirige todo el portafolio: KPI consolidados, gráficos, presupuesto, decisiones y administración de la plataforma.',
    'landing.profils.admin.m1': 'Cuadro de mando y supervisión de todas las iniciativas',
    'landing.profils.admin.m2': 'Gestión de usuarios, roles y permisos',
    'landing.profils.admin.m3': 'Configuración (estados, prioridades, entidades) y auditoría completa',
    'landing.profils.resp.nom': 'Responsable',
    'landing.profils.resp.vision':
      "El responsable califica las solicitudes e impulsa sus iniciativas: hitos, pasos, avance y presupuesto hasta el cierre.",
    'landing.profils.resp.m1': 'Crear y seguir sus iniciativas, hitos y pasos',
    'landing.profils.resp.m2': 'Evaluar solicitudes y convertirlas en iniciativas',
    'landing.profils.resp.m3': 'Seguir presupuesto y desviaciones de su ámbito',
    'landing.profils.visiteur.nom': 'Visitante',
    'landing.profils.visiteur.vision':
      'El visitante consulta y se informa: cuadro de mando, lista filtrada, hoja de ruta y objetivos — sin posibilidad de modificar.',
    'landing.profils.visiteur.m1': 'Consultar el cuadro de mando y los indicadores',
    'landing.profils.visiteur.m2': 'Recorrer iniciativas y hoja de ruta con filtros',
    'landing.profils.visiteur.m3': 'Sin escritura: los derechos se aplican en el servidor',

    // Confianza
    'landing.confiance.eyebrow': 'Confianza',
    'landing.confiance.titre': 'Pensada para la empresa',
    'landing.confiance.texte': 'Permisos finos, trazabilidad completa y alojamiento controlado.',
    'landing.confiance.roles.titre': 'Roles y permisos',
    'landing.confiance.roles.texte':
      "17 permisos para el administrador, 14 para el responsable, 3 para el visitante — aplicados en cada petición, no solo ocultos en pantalla.",
    'landing.confiance.audit.titre': 'Registro de auditoría',
    'landing.confiance.audit.texte':
      "Cada modificación se registra con hora, campo por campo y clasificada (manual o automática); el registro es inmutable, no expone ninguna supresión.",
    'landing.confiance.hebergement.titre': 'Alojamiento on-premises',
    'landing.confiance.hebergement.texte':
      'Base de datos, backend y frontend corren en su infraestructura (Docker), detrás de HTTPS y una política de seguridad estricta — los datos no salen de su red.',

    'landing.footer.copyright': '© {annee} IT6 Consulting — Todos los derechos reservados.'
  }
};
