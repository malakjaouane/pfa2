/**
 * Textes de la rubrique publique « Découvrir » (/decouvrir/:sujet).
 * Namespace : decouvrir.*
 *
 * Un sujet = 5 clés (titre, intro, p1, p2, p3) + une clé alt pour la
 * capture. Les textes communs (retour, CTA, points clés, cadre de
 * remplacement) sont en tête de chaque langue.
 *
 * Pour AJOUTER un sujet : ajouter les clés decouvrir.<id>.* ci-dessous
 * (fr + en minimum ; ar et es pour la cohérence de la vitrine) et, si
 * possible, déposer la capture dans frontend/public/images/screens/.
 * Voir aussi frontend/src/data/decouvrir.js (id + image).
 *
 * Les explications décrivent le comportement réel de l'application,
 * vérifié dans le code backend :
 *  - avancement = moyenne pondérée par la durée des étapes
 *    (CalculeurAvancement) ;
 *  - jalons « À atteindre / Atteint », en retard si la date prévue est
 *    passée et le statut n'est pas « Atteint » (JalonMapper) ;
 *  - dashboard filtrable par statut, entité, responsable, période
 *    (InitiativeFilterRequest) ;
 *  - trois profils, comptes créés par l'administrateur (V2__referentiels.sql).
 */
export default {
  fr: {
    // ---- Textes communs ----
    'decouvrir.retour': "Retour à l'accueil",
    'decouvrir.eyebrow': 'Rubrique Découvrir',
    'decouvrir.pointsCles': 'Points clés',
    'decouvrir.accesApp': "Accéder à l'application",
    'decouvrir.suivant': 'Sujet suivant',
    'decouvrir.imageManquante.titre': 'Capture à venir',
    'decouvrir.imageManquante.texte':
      "Cet écran sera illustré par une capture d'écran de l'application.",

    // ---- Tableau de bord ----
    'decouvrir.dashboard.titre': 'Le tableau de bord',
    'decouvrir.dashboard.intro':
      "La vue d'ensemble du portefeuille : les indicateurs consolidés, calculés côté serveur à chaque affichage.",
    'decouvrir.dashboard.p1':
      'Huit cartes de synthèse résument le portefeuille : total d’initiatives, en cours, terminées, en pause, en retard, jalons en retard, budget total et avancement moyen.',
    'decouvrir.dashboard.p2':
      'Deux graphiques croisent la répartition par statut (camembert) et par priorité (barres) ; la liste des initiatives en retard est affichée en premier.',
    'decouvrir.dashboard.p3':
      'La barre de filtres recalcule les indicateurs et les graphiques sur la sélection, et les valeurs sont lues directement dans la base — rien n’est saisi à la main.',
    'decouvrir.dashboard.alt': "Capture de l'écran Tableau de bord",

    // ---- Initiatives ----
    'decouvrir.initiatives.titre': 'Les initiatives',
    'decouvrir.initiatives.intro':
      'Le cœur du portefeuille : chaque initiative porte un code, un statut, une priorité, un responsable et une échéance.',
    'decouvrir.initiatives.p1':
      'La vue Kanban range les initiatives en colonnes, une par statut : à planifier, en cours, en pause, terminée ou annulée.',
    'decouvrir.initiatives.p2':
      'Une recherche par code ou par nom retrouve une initiative depuis la barre supérieure, et les priorités vont de Basse à Critique.',
    'decouvrir.initiatives.p3':
      'Les initiatives archivées restent consultables sans encombrer les vues courantes.',
    'decouvrir.initiatives.alt': 'Capture de la vue Kanban des initiatives',

    // ---- Étapes et jalons ----
    'decouvrir.etapes-jalons.titre': 'Étapes et jalons',
    'decouvrir.etapes-jalons.intro':
      'Deux outils de suivi au sein de la fiche initiative : les étapes décrivent le travail, les jalons ponctuent les échéances.',
    'decouvrir.etapes-jalons.p1':
      'Une étape porte un statut (à faire, en cours, terminée), des dates de début et de fin prévue, et un avancement saisi entre 0 et 100 %.',
    'decouvrir.etapes-jalons.p2':
      'Un jalon est une date à tenir : il passe de « À atteindre » à « Atteint » lorsqu’il est validé, avec sa date de réalisation.',
    'decouvrir.etapes-jalons.p3':
      'Un jalon non atteint dont la date prévue est passée apparaît en retard, tout comme une initiative échue non terminée.',
    'decouvrir.etapes-jalons.alt': "Capture d'une fiche d'initiative",

    // ---- Avancement ----
    'decouvrir.avancement.titre': 'L’avancement',
    'decouvrir.avancement.intro':
      "L'avancement d'une initiative n'est pas un chiffre arbitraire : il est calculé à partir de ses étapes.",
    'decouvrir.avancement.p1':
      'Une étape terminée vaut 100 %, une étape à faire vaut 0 % ; sinon l’avancement saisi pour cette étape est retenu.',
    'decouvrir.avancement.p2':
      'L’avancement global est la moyenne pondérée par la durée des étapes (en jours, du début à la fin prévue), ou la moyenne simple si les dates manquent.',
    'decouvrir.avancement.p3':
      'Sans aucune étape, le calcul est impossible : la valeur saisie manuellement est alors conservée.',
    'decouvrir.avancement.alt': "Capture de l'avancement calculé d'une initiative",

    // ---- Budget ----
    'decouvrir.budget.titre': 'Le budget',
    'decouvrir.budget.intro':
      'Chaque initiative porte un budget saisi à la création. La consommation est une estimation, pas une comptabilité.',
    'decouvrir.budget.p1':
      'Le budget consommé est calculé au prorata de l’avancement : budget multiplié par l’avancement, divisé par 100.',
    'decouvrir.budget.p2':
      'L’écart entre le budget effectif et le consommé mesure ce qu’il reste à engager, en montant et en pourcentage.',
    'decouvrir.budget.p3':
      'Aucune dépense réelle n’est enregistrée : ces montants sont des indicateurs de pilotage, la définition saisie reste la source de vérité.',
    'decouvrir.budget.alt': "Capture de l'encart budget du tableau de bord",

    // ---- Filtres ----
    'decouvrir.filtres.titre': 'Les filtres',
    'decouvrir.filtres.intro':
      'Une barre de filtres commune au tableau de bord et aux initiatives réduit le périmètre analysé.',
    'decouvrir.filtres.p1':
      'Quatre critères principaux : le statut, l’entité, le responsable et la période, entre deux dates.',
    'decouvrir.filtres.p2':
      'Les indicateurs, la liste et les graphiques suivent la sélection ; « Réinitialiser » revient à la vue complète.',
    'decouvrir.filtres.p3':
      'Les filtres sont écrits dans l’adresse de la page : une vue filtrée se partage par un simple lien.',
    'decouvrir.filtres.alt': 'Capture de la barre de filtres du tableau de bord',

    // ---- Rôles et sécurité ----
    'decouvrir.roles-securite.titre': 'Rôles et sécurité',
    'decouvrir.roles-securite.intro':
      'Trois profils coexistent, et les droits sont appliqués côté serveur pour chaque requête.',
    'decouvrir.roles-securite.p1':
      "L'administrateur gère les utilisateurs, les rôles et les permissions ; le responsable pilote ses initiatives ; le visiteur consulte.",
    'decouvrir.roles-securite.p2':
      "Il n'existe pas d'inscription : les comptes sont créés par l'administrateur, avec un rôle et une entité.",
    'decouvrir.roles-securite.p3':
      'Les mots de passe sont hachés en BCrypt et la connexion peut être temporairement bloquée après plusieurs échecs.',
    'decouvrir.roles-securite.alt': "Capture de l'écran Rôles & permissions"
  },

  en: {
    // ---- Common ----
    'decouvrir.retour': 'Back to home',
    'decouvrir.eyebrow': 'Discover section',
    'decouvrir.pointsCles': 'Key points',
    'decouvrir.accesApp': 'Open the application',
    'decouvrir.suivant': 'Next topic',
    'decouvrir.imageManquante.titre': 'Screenshot coming soon',
    'decouvrir.imageManquante.texte':
      'This screen will be illustrated with a screenshot of the application.',

    // ---- Dashboard ----
    'decouvrir.dashboard.titre': 'The dashboard',
    'decouvrir.dashboard.intro':
      'The portfolio overview: consolidated indicators, computed server-side on every load.',
    'decouvrir.dashboard.p1':
      'Eight summary cards cover the portfolio: total initiatives, in progress, completed, on hold, overdue, overdue milestones, total budget and average progress.',
    'decouvrir.dashboard.p2':
      'Two charts cross the breakdown by status (pie) and by priority (bars); the list of overdue initiatives comes first.',
    'decouvrir.dashboard.p3':
      'The filter bar recomputes the indicators and charts on the selection, and values are read straight from the database — nothing is entered by hand.',
    'decouvrir.dashboard.alt': 'Screenshot of the dashboard screen',

    // ---- Initiatives ----
    'decouvrir.initiatives.titre': 'Initiatives',
    'decouvrir.initiatives.intro':
      'The heart of the portfolio: every initiative carries a code, a status, a priority, an owner and a deadline.',
    'decouvrir.initiatives.p1':
      'The Kanban view lines initiatives up in columns, one per status: to plan, in progress, on hold, completed or cancelled.',
    'decouvrir.initiatives.p2':
      'A search by code or name finds any initiative from the top bar, and priorities run from Low to Critical.',
    'decouvrir.initiatives.p3':
      'Archived initiatives stay readable without cluttering the current views.',
    'decouvrir.initiatives.alt': 'Screenshot of the initiatives Kanban view',

    // ---- Steps and milestones ----
    'decouvrir.etapes-jalons.titre': 'Steps and milestones',
    'decouvrir.etapes-jalons.intro':
      'Two tracking tools inside the initiative record: steps describe the work, milestones mark the deadlines.',
    'decouvrir.etapes-jalons.p1':
      'Each step carries a status (to do, in progress, completed), planned start and end dates and a progress value between 0 and 100.',
    'decouvrir.etapes-jalons.p2':
      'A milestone is a date to hit: it moves from “To reach” to “Reached” once validated, with its actual date.',
    'decouvrir.etapes-jalons.p3':
      'A milestone not reached after its planned date shows as overdue, like an overdue initiative that is not completed.',
    'decouvrir.etapes-jalons.alt': 'Screenshot of an initiative record',

    // ---- Progress ----
    'decouvrir.avancement.titre': 'Progress',
    'decouvrir.avancement.intro':
      'The progress of an initiative is not an arbitrary number: it is computed from its steps.',
    'decouvrir.avancement.p1':
      'A completed step counts as 100 %, a to-do step as 0 %; otherwise the progress entered for that step is used.',
    'decouvrir.avancement.p2':
      'Overall progress is the average weighted by step duration (in days, from start to planned end), or the simple average when dates are missing.',
    'decouvrir.avancement.p3':
      'With no step at all the calculation is impossible: the manually entered value is kept.',
    'decouvrir.avancement.alt': 'Screenshot of a computed initiative progress',

    // ---- Budget ----
    'decouvrir.budget.titre': 'The budget',
    'decouvrir.budget.intro':
      'Every initiative carries a budget entered at creation. Consumption is an estimate, not accounting.',
    'decouvrir.budget.p1':
      'The consumed budget is computed pro rata to progress: budget times progress divided by 100.',
    'decouvrir.budget.p2':
      'The gap between the effective and consumed budgets measures what is left to commit, in amount and percentage.',
    'decouvrir.budget.p3':
      'No real expense is recorded: these amounts are steering indicators, the entered definition remains the source of truth.',
    'decouvrir.budget.alt': 'Screenshot of the dashboard budget card',

    // ---- Filters ----
    'decouvrir.filtres.titre': 'Filters',
    'decouvrir.filtres.intro':
      'A filter bar shared by the dashboard and the initiatives narrows down the analysed scope.',
    'decouvrir.filtres.p1':
      'Four main criteria: status, entity, owner and period, between two dates.',
    'decouvrir.filtres.p2':
      'Indicators, list and charts follow the selection; “Reset” brings back the full view.',
    'decouvrir.filtres.p3':
      'Filters are written into the page address: a filtered view is shared with a single link.',
    'decouvrir.filtres.alt': 'Screenshot of the dashboard filter bar',

    // ---- Roles and security ----
    'decouvrir.roles-securite.titre': 'Roles and security',
    'decouvrir.roles-securite.intro':
      'Three profiles coexist and permissions are enforced server-side on every request.',
    'decouvrir.roles-securite.p1':
      'The administrator manages users, roles and permissions; the owner steers their initiatives; the visitor only reads.',
    'decouvrir.roles-securite.p2':
      'There is no self sign-up: accounts are created by the administrator, with a role and an entity.',
    'decouvrir.roles-securite.p3':
      'Passwords are hashed with BCrypt and sign-in can be temporarily blocked after several failures.',
    'decouvrir.roles-securite.alt': 'Screenshot of the roles and permissions screen'
  },

  ar: {
    // ---- نصوص مشتركة ----
    'decouvrir.retour': 'العودة إلى الصفحة الرئيسية',
    'decouvrir.eyebrow': 'قسم الاكتشاف',
    'decouvrir.pointsCles': 'نقاط أساسية',
    'decouvrir.accesApp': 'الدخول إلى التطبيق',
    'decouvrir.suivant': 'الموضوع التالي',
    'decouvrir.imageManquante.titre': 'لقطة شاشة قادمة',
    'decouvrir.imageManquante.texte': 'سيُوضَح هذا الشاشة بلقطة من التطبيق.',

    // ---- لوحة المعلومات ----
    'decouvrir.dashboard.titre': 'لوحة المعلومات',
    'decouvrir.dashboard.intro': 'نظرة عامة على المحفظة: مؤشرات موحّدة تُحسب على الخادم في كل تحميل.',
    'decouvrir.dashboard.p1':
      'ثماني بطاقات تلخّص المحفظة: إجمالي المبادرات،قيد الإنجاز،منجزة،متوقفة،متأخرة،معالم متأخرة،ميزانية إجمالية،متوسط التقدم.',
    'decouvrir.dashboard.p2':
      'رسمان يتقاطعان: التوزيع حسب الحالة (دائري) وحسب الأولوية (أعمدة)، وتظهر قائمة المبادرات المتأخرة أولًا.',
    'decouvrir.dashboard.p3':
      'شريط الفلاتر يعيد حساب المؤشرات والرسوم على الاختيار، والقيم تُقرأ مباشرة من قاعدة البيانات — دون إدخال يدوي.',
    'decouvrir.dashboard.alt': 'لقطة شاشة لوحة المعلومات',

    // ---- المبادرات ----
    'decouvrir.initiatives.titre': 'المبادرات',
    'decouvrir.initiatives.intro': 'قلب المحفظة: لكل مبادرة رمز وحالة وأولوية ومسؤول واستحقاق.',
    'decouvrir.initiatives.p1':
      'عرض كانبان يرتّب المبادرات في أعمدة، عمود لكل حالة: للتخطيط،قيد الإنجاز،متوقفة،منجزة،ملغاة.',
    'decouvrir.initiatives.p2':
      'البحث بالرمز أو بالاسم يعثر على أي مبادرة من الشريط العلوي، والأولويات تتراوح من منخفضة إلى حرجة.',
    'decouvrir.initiatives.p3': 'المبادرات المؤرشفة تبقى قابلة للاطلاع دون إثقال العروض الحالية.',
    'decouvrir.initiatives.alt': 'لقطة شاشة عرض كانبان للمبادرات',

    // ---- المراحل والمعالم ----
    'decouvrir.etapes-jalons.titre': 'المراحل والمعالم',
    'decouvrir.etapes-jalons.intro':
      'أداتان للتدقيق داخل بطاقة المبادرة: المراحل تصف العمل، والمعالم تحدّد الاستحقاقات.',
    'decouvrir.etapes-jalons.p1':
      'لكل مرحلة حالة (للتنفيذ،قيد التنفيذ،منجزة) وتواريخ بدء وانتهاء متوقعة وتقدّماً بين 0 و100٪.',
    'decouvrir.etapes-jalons.p2':
      'المعلم موعد يجب الالتزام به: ينتقل من «مُراد بلوغه» إلى «تم بلوغه» عند التحقق منه، مع تاريخ الإنجاز.',
    'decouvrir.etapes-jalons.p3':
      'معلم لم يُبلَغ وموعد توقعه انقضى يظهر متأخراً، وكذلك مبادرة متأخرة وغير منجزة.',
    'decouvrir.etapes-jalons.alt': 'لقطة شاشة بطاقة مبادرة',

    // ---- التقدم ----
    'decouvrir.avancement.titre': 'التقدم',
    'decouvrir.avancement.intro': 'تقدم المبادرة ليس رقماً اعتباطياً: يُحسب من مراحلها.',
    'decouvrir.avancement.p1':
      'المرحلة المنجزة تساوي 100٪، ومرحلة «للتنفيذ» تساوي 0٪؛ وإلا يُعتمد التقدّم المُدخل لها.',
    'decouvrir.avancement.p2':
      'التقدم العام هو المتوسط المرجّح بمدة المراحل (بالأيام، من البداية إلى الانتهاء المتوقعة)، أو المتوسط البسيط عند غياب التواريخ.',
    'decouvrir.avancement.p3': 'بدون أي مرحلة يتعذّر الحساب: تُحتفظ بالقيمة المُدخلة يدوياً.',
    'decouvrir.avancement.alt': 'لقطة شاشة تقدم مبادرة محسوب',

    // ---- الميزانية ----
    'decouvrir.budget.titre': 'الميزانية',
    'decouvrir.budget.intro': 'لكل مبادرة ميزانية تُدخل عند إنشائها. الاستهلاك تقدير وليس محاسبة.',
    'decouvrir.budget.p1': 'الميزانية المستهلكة تُحسب بالنسبة من التقدم: الميزانية × التقدم ÷ 100.',
    'decouvrir.budget.p2': 'الفارق بين الميزانية الفعلية والمستهلكة يقيس ما تبقّى، بالقيمة وبالنسبة المئوية.',
    'decouvrir.budget.p3':
      'لا تُسجَّل أي مصروفات فعلية: هذه المبالغ مؤشرات تدقيق، والإدخال يبقى مصدر الحقيقة.',
    'decouvrir.budget.alt': 'لقطة شاشة بطاقة الميزانية في لوحة المعلومات',

    // ---- الفلاتر ----
    'decouvrir.filtres.titre': 'الفلاتر',
    'decouvrir.filtres.intro': 'شريط فلاتر مشترك بين لوحة المعلومات والمبادرات يضيّق نطاق التحليل.',
    'decouvrir.filtres.p1': 'أربعة معايير أساسية: الحالة،الكيان،المسؤول،الفترة بين تاريخين.',
    'decouvrir.filtres.p2': 'المؤشرات والقائمة والرسوم تتبع الاختيار، و«إعادة الضبط» تعود إلى العرض الكامل.',
    'decouvrir.filtres.p3': 'الفلاتر تُكتب في عنوان الصفحة: العرض المفلتر يُشارك برابط واحد.',
    'decouvrir.filtres.alt': 'لقطة شاشة شريط فلاتر لوحة المعلومات',

    // ---- الأدوار والأمان ----
    'decouvrir.roles-securite.titre': 'الأدوار والأمان',
    'decouvrir.roles-securite.intro': 'ثلاثة ملفات شخصية تتعايش، والصلاحيات تُطبَّق على الخادم في كل طلب.',
    'decouvrir.roles-securite.p1':
      'المشرف يدير المستخدمين والأدوار والصلاحيات؛ والمسؤول يقود مبادراته؛ والزائر يطّلع فقط.',
    'decouvrir.roles-securite.p2': 'لا يوجد تسجيل ذاتي: الحسابات ينشئها المشرف، مع دور وكيان.',
    'decouvrir.roles-securite.p3':
      'كلمات المرور مشفّرة بـ BCrypt، ويمكن حظر الدخول مؤقتاً بعد عدة محاولات فاشلة.',
    'decouvrir.roles-securite.alt': 'لقطة شاشة شاشة الأدوار والصلاحيات'
  },

  es: {
    // ---- Textos comunes ----
    'decouvrir.retour': 'Volver a la portada',
    'decouvrir.eyebrow': 'Sección Descubrir',
    'decouvrir.pointsCles': 'Puntos clave',
    'decouvrir.accesApp': 'Acceder a la aplicación',
    'decouvrir.suivant': 'Tema siguiente',
    'decouvrir.imageManquante.titre': 'Captura próximamente',
    'decouvrir.imageManquante.texte':
      'Esta pantalla se ilustrará con una captura de la aplicación.',

    // ---- Cuadro de mando ----
    'decouvrir.dashboard.titre': 'El cuadro de mando',
    'decouvrir.dashboard.intro':
      'La vista general de la cartera: indicadores consolidados, calculados en el servidor en cada carga.',
    'decouvrir.dashboard.p1':
      'Ocho tarjetas resumen la cartera: total de iniciativas, en curso, terminadas, en pausa, con retraso, hitos con retraso, presupuesto total y avance medio.',
    'decouvrir.dashboard.p2':
      'Dos gráficos cruzan la repartición por estado (tarta) y por prioridad (barras); la lista de iniciativas con retraso aparece primero.',
    'decouvrir.dashboard.p3':
      'La barra de filtros recalcula los indicadores y los gráficos sobre la selección, y los valores se leen directamente de la base de datos — nada se introduce a mano.',
    'decouvrir.dashboard.alt': 'Captura de la pantalla de cuadro de mando',

    // ---- Iniciativas ----
    'decouvrir.initiatives.titre': 'Las iniciativas',
    'decouvrir.initiatives.intro':
      'El corazón de la cartera: cada iniciativa lleva un código, un estado, una prioridad, un responsable y un vencimiento.',
    'decouvrir.initiatives.p1':
      'La vista Kanban ordena las iniciativas en columnas, una por estado: por planificar, en curso, en pausa, terminada o anulada.',
    'decouvrir.initiatives.p2':
      'Una búsqueda por código o por nombre encuentra cualquier iniciativa desde la barra superior, y las prioridades van de Baja a Crítica.',
    'decouvrir.initiatives.p3':
      'Las iniciativas archivadas siguen consultables sin saturar las vistas actuales.',
    'decouvrir.initiatives.alt': 'Captura de la vista Kanban de iniciativas',

    // ---- Etapas y hitos ----
    'decouvrir.etapes-jalons.titre': 'Etapas y hitos',
    'decouvrir.etapes-jalons.intro':
      'Dos herramientas de seguimiento dentro de la ficha: las etapas describen el trabajo y los hitos marcan los vencimientos.',
    'decouvrir.etapes-jalons.p1':
      'Cada etapa lleva un estado (por hacer, en curso, terminada), fechas de inicio y fin previstas y un avance introducido entre 0 y 100 %.',
    'decouvrir.etapes-jalons.p2':
      'Un hito es una fecha que se debe cumplir: pasa de «Por alcanzar» a «Alcanzado» al validarse, con su fecha de realización.',
    'decouvrir.etapes-jalons.p3':
      'Un hito no alcanzado cuya fecha previa ya pasó aparece con retraso, igual que una iniciativa vencida sin terminar.',
    'decouvrir.etapes-jalons.alt': 'Captura de una ficha de iniciativa',

    // ---- Avance ----
    'decouvrir.avancement.titre': 'El avance',
    'decouvrir.avancement.intro':
      'El avance de una iniciativa no es un número arbitrario: se calcula a partir de sus etapas.',
    'decouvrir.avancement.p1':
      'Una etapa terminada vale 100 %, una etapa por hacer vale 0 %; si no, se toma el avance introducido en la etapa.',
    'decouvrir.avancement.p2':
      'El avance global es la media ponderada por la duración de las etapas (en días, del inicio a la fin prevista), o la media simple si faltan fechas.',
    'decouvrir.avancement.p3':
      'Sin ninguna etapa el cálculo es imposible: se conserva el valor introducido manualmente.',
    'decouvrir.avancement.alt': 'Captura del avance calculado de una iniciativa',

    // ---- Presupuesto ----
    'decouvrir.budget.titre': 'El presupuesto',
    'decouvrir.budget.intro':
      'Cada iniciativa lleva un presupuesto introducido al crearse. El consumo es una estimación, no una contabilidad.',
    'decouvrir.budget.p1':
      'El presupuesto consumido se calcula al prorrateo del avance: presupuesto por avance entre 100.',
    'decouvrir.budget.p2':
      'La diferencia entre el presupuesto efectivo y el consumido mide lo que queda por comprometer, en importe y en porcentaje.',
    'decouvrir.budget.p3':
      'No se registra ningún gasto real: estas cantidades son indicadores de dirección, la definición introducida sigue siendo la fuente de verdad.',
    'decouvrir.budget.alt': 'Captura de la tarjeta de presupuesto del cuadro de mando',

    // ---- Filtros ----
    'decouvrir.filtres.titre': 'Los filtros',
    'decouvrir.filtres.intro':
      'Una barra de filtros común al cuadro de mando y a las iniciativas acota el ámbito analizado.',
    'decouvrir.filtres.p1':
      'Cuatro criterios principales: estado, entidad, responsable y periodo entre dos fechas.',
    'decouvrir.filtres.p2':
      'Los indicadores, la lista y los gráficos siguen la selección; «Reiniciar» vuelve a la vista completa.',
    'decouvrir.filtres.p3':
      'Los filtros se escriben en la dirección de la página: una vista filtrada se comparte con un simple enlace.',
    'decouvrir.filtres.alt': 'Captura de la barra de filtros del cuadro de mando',

    // ---- Roles y seguridad ----
    'decouvrir.roles-securite.titre': 'Roles y seguridad',
    'decouvrir.roles-securite.intro':
      'Tres perfiles conviven y los permisos se aplican en el servidor en cada petición.',
    'decouvrir.roles-securite.p1':
      'El administrador gestiona usuarios, roles y permisos; el responsable dirige sus iniciativas; el visitante solo consulta.',
    'decouvrir.roles-securite.p2':
      'No existe autoinscripción: las cuentas las crea el administrador, con un rol y una entidad.',
    'decouvrir.roles-securite.p3':
      'Las contraseñas se cifran con BCrypt y el acceso puede bloquearse temporalmente tras varios fallos.',
    'decouvrir.roles-securite.alt': 'Captura de la pantalla de roles y permisos'
  }
};
