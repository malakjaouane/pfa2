/**
 * Clés de la page « Import d'initiatives » (4B).
 * Chaque entrée exporte { fr, en, ar, es } — les clés sont préfixées
 * `importation.` (l'ancien fichier était un objet plat sans langues :
 * aucune clé n'était fusionnée, la page affichait les clés brutes).
 */
export const fr = {
  'importation.titre': 'Import d’initiatives',
  'importation.sousTitre':
    'Importez plusieurs initiatives depuis un fichier CSV ou Excel',
  'importation.importerFichier': 'Importer un fichier',
  'importation.enCours': 'Importation en cours…',
  'importation.importer': 'Importer',
  'importation.formats': 'Formats acceptés : .csv, .xlsx, .xls',
  'importation.modele': 'Télécharger le modèle CSV',
  'importation.modeleAide':
    'Le modèle contient les colonnes requises (code, nom, statut, priorité, responsable, entité) et une ligne d’exemple à adapter à vos référentiels.',
  'importation.refuse':
    'Import refusé (mode strict) : corrigez les lignes en erreur, puis réessayez.',
  'importation.resultat': 'Résultat de l’importation',
  'importation.total': 'Total',
  'importation.importees': 'Importées ({n})',
  'importation.ignorees': 'Ignorées ({n})',
  'importation.erreurs': 'Erreurs ({n})',
  'importation.succes': 'Importation terminée : {n} ligne(s) importée(s)',
  'importation.erreur': 'Erreur lors de l’importation',
};

export const en = {
  'importation.titre': 'Initiative import',
  'importation.sousTitre':
    'Import multiple initiatives from a CSV or Excel file',
  'importation.importerFichier': 'Upload a file',
  'importation.enCours': 'Importing…',
  'importation.importer': 'Import',
  'importation.formats': 'Accepted formats: .csv, .xlsx, .xls',
  'importation.modele': 'Download CSV template',
  'importation.modeleAide':
    'The template contains the required columns (code, name, status, priority, owner, entity) and a sample row to adapt to your reference data.',
  'importation.refuse':
    'Import rejected (strict mode): fix the erroneous lines, then try again.',
  'importation.resultat': 'Import result',
  'importation.total': 'Total',
  'importation.importees': 'Imported ({n})',
  'importation.ignorees': 'Skipped ({n})',
  'importation.erreurs': 'Errors ({n})',
  'importation.succes': 'Import finished: {n} line(s) imported',
  'importation.erreur': 'Import failed',
};

export const ar = {
  'importation.titre': 'استيراد المبادرات',
  'importation.sousTitre':
    'استورد عدة مبادرات من ملف CSV أو Excel',
  'importation.importerFichier': 'رفع ملف',
  'importation.enCours': 'جارٍ الاستيراد…',
  'importation.importer': 'استيراد',
  'importation.formats': 'الصيغ المقبولة: .csv، .xlsx، .xls',
  'importation.modele': 'تحميل نموذج CSV',
  'importation.modeleAide':
    'يحتوي النموذج على الأعمدة المطلوبة (الرمز، الاسم، الحالة، الأولوية، المسؤول، الكيان) وصف مثال يجدر تكييفه مع معجم مؤسستك.',
  'importation.refuse':
    'رُفض الاستيراد (الوضع الصارم): صحح الأسطر الخاطئة ثم أعد المحاولة.',
  'importation.resultat': 'نتيجة الاستيراد',
  'importation.total': 'المجموع',
  'importation.importees': 'مستوردة ({n})',
  'importation.ignorees': 'متخطاة ({n})',
  'importation.erreurs': 'أخطاء ({n})',
  'importation.succes': 'اكتمل الاستيراد: {n} سطر',
  'importation.erreur': 'خطأ أثناء الاستيراد',
};

export const es = {
  'importation.titre': 'Importación de iniciativas',
  'importation.sousTitre':
    'Importe varias iniciativas desde un archivo CSV o Excel',
  'importation.importerFichier': 'Subir un archivo',
  'importation.enCours': 'Importando…',
  'importation.importer': 'Importar',
  'importation.formats': 'Formatos aceptados: .csv, .xlsx, .xls',
  'importation.modele': 'Descargar plantilla CSV',
  'importation.modeleAide':
    'La plantilla contiene las columnas obligatorias (código, nombre, estado, prioridad, responsable, entidad) y una fila de ejemplo para adaptar a sus referentes.',
  'importation.refuse':
    'Importación rechazada (modo estricto): corrija las líneas con errores y vuelva a intentarlo.',
  'importation.resultat': 'Resultado de la importación',
  'importation.total': 'Total',
  'importation.importees': 'Importadas ({n})',
  'importation.ignorees': 'Omitidas ({n})',
  'importation.erreurs': 'Errores ({n})',
  'importation.succes': 'Importación finalizada: {n} línea(s) importada(s)',
  'importation.erreur': 'Error al importar',
};

export default { fr, en, ar, es };
