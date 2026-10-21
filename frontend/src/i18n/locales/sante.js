/**
 * Score de santé automatique (niveaux et motifs, calculés par le backend).
 * Les motifs reprennent les codes renvoyés par l'API : ne pas les renommer
 * sans changer CalculeurSante en même temps.
 */
export default {
  fr: {
    'sante.colonne': 'Santé',
    'sante.verte': 'Feu vert',
    'sante.orange': 'Vigilance',
    'sante.rouge': 'À risque',
    'sante.raison.terminee': 'Initiative terminée',
    'sante.raison.finDepassee': 'Date butoir dépassée de {n} jours',
    'sante.raison.jalonsRetard': '{n} jalons en retard',
    'sante.raison.jalonRetard': 'Jalon « {nom} » en retard',
    'sante.raison.ecartPlanning': "{temps} % du temps écoulé pour {avancement} % d'avancement",
    'sante.raison.conforme': 'Conforme au planning',
    'sante.raison.sansPlanning': 'Pas encore de planning ni de jalons',
  },
  en: {
    'sante.colonne': 'Health',
    'sante.verte': 'On track',
    'sante.orange': 'Watch',
    'sante.rouge': 'At risk',
    'sante.raison.terminee': 'Initiative completed',
    'sante.raison.finDepassee': 'Due date passed by {n} days',
    'sante.raison.jalonsRetard': '{n} milestones overdue',
    'sante.raison.jalonRetard': 'Milestone "{nom}" overdue',
    'sante.raison.ecartPlanning': '{temps} % of time elapsed for {avancement} % progress',
    'sante.raison.conforme': 'On schedule',
    'sante.raison.sansPlanning': 'No schedule or milestones yet',
  },
  ar: {
    'sante.colonne': 'الحالة',
    'sante.verte': 'آمن',
    'sante.orange': 'مراقبة',
    'sante.rouge': 'معرّض للخطر',
    'sante.raison.terminee': 'مبادرة منجزة',
    'sante.raison.finDepassee': 'تجاوز الأجل المحدد بـ {n} يوم',
    'sante.raison.jalonsRetard': '{n} مراحل متأخرة',
    'sante.raison.jalonRetard': 'المرحلة « {nom} » متأخرة',
    'sante.raison.ecartPlanning': 'انقضى {temps} % من المدة مقابل {avancement} % من التقدم',
    'sante.raison.conforme': 'مطابق للبرنامج',
    'sante.raison.sansPlanning': 'لا يوجد برنامج أو مراحل بعد',
  },
  es: {
    'sante.colonne': 'Salud',
    'sante.verte': 'En buen estado',
    'sante.orange': 'Vigilancia',
    'sante.rouge': 'En riesgo',
    'sante.raison.terminee': 'Iniciativa finalizada',
    'sante.raison.finDepassee': 'Fecha límite superada en {n} días',
    'sante.raison.jalonsRetard': '{n} hitos con retraso',
    'sante.raison.jalonRetard': 'Hito "{nom}" con retraso',
    'sante.raison.ecartPlanning': '{temps} % del tiempo transcurrido para {avancement} % de avance',
    'sante.raison.conforme': 'Conforme a la planificación',
    'sante.raison.sansPlanning': 'Sin planificación ni hitos todavía',
  },
};
