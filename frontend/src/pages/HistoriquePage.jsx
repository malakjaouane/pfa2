import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api, { messageErreur, messageErreurBlob, telechargerFichier } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { badgeTypeJournal, libelleChamp, libelleType } from '../i18n/journal';
import StatutBadge from '../components/StatutBadge';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Badge } from '../components/ui/badge';
import { useToast } from '../components/ui/use-toast';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '../components/ui/table';

/** Taille de page fixe : un tableau de lecture se feuillette, il ne s'épuise pas. */
const TAILLE_PAGE = 25;

/** Types d'objet journalisés — doit suivre TYPES côté backend (400 sinon). */
const TYPES = ['initiative', 'etape', 'jalon', 'demande', 'utilisateur', 'role'];

const vide = { recherche: '', type: '', utilisateur: '', du: '', au: '' };

/** Lit l'ensemble des critères depuis l'URL (vue partageable, fil du navigateur). */
const lireDepuisUrl = (params) => {
  const page = parseInt(params.get('page') ?? '0', 10);
  const type = params.get('type') ?? '';
  return {
    recherche: params.get('recherche') ?? '',
    type: TYPES.includes(type) ? type : '',
    utilisateur: params.get('utilisateur') ?? '',
    du: params.get('du') ?? '',
    au: params.get('au') ?? '',
    initiative: params.get('initiative') ?? '',
    // Bascule « Actives | Archives » portée par l'URL (?archive=1) :
    // lien partagé, retour navigateur et rechargement automatique.
    archives: params.get('archive') === '1' || params.get('archive') === 'true',
    direction: params.get('direction') === 'asc' ? 'asc' : 'desc',
    page: Number.isNaN(page) || page < 0 ? 0 : page
  };
};

/** Date et heure dans la langue active (l'ISO de l'API est locale : sans « Z »). */
const formaterDate = (valeur, langue) =>
  new Date(valeur).toLocaleString(langue, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });

/**
 * Normalisation insensible à la casse et aux accents : « terminee » trouve
 * « Terminée ». Sert au surlignage des cellules de résultat.
 */
const normaliser = (texte) =>
  (texte ?? '').toString().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

/** Surligne les occurrences de la requête dans un texte d'origine. */
function Surligner({ texte, requete }) {
  const source = texte ?? '';
  const aiguille = normaliser((requete ?? '').trim());
  if (!source || !aiguille) return <>{source}</>;

  // NFD peut allonger la chaîne : on garde la table index normalisé -> index
  // d'origine pour découper sur les bords exacts du texte affiché.
  const base = [];
  const carte = [];
  for (let i = 0; i < source.length; i += 1) {
    const morceau = source[i].normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
    for (const c of morceau) {
      base.push(c);
      carte.push(i);
    }
  }
  const normalisee = base.join('');
  const morceaux = [];
  let curseur = 0;
  let pos = normalisee.indexOf(aiguille);
  while (pos !== -1) {
    const debut = carte[pos];
    const fin = carte[pos + aiguille.length - 1] + 1;
    if (debut > curseur) morceaux.push(source.slice(curseur, debut));
    morceaux.push(<mark key={`m${debut}`}>{source.slice(debut, fin)}</mark>);
    curseur = fin;
    pos = normalisee.indexOf(aiguille, pos + aiguille.length);
  }
  if (curseur < source.length) morceaux.push(source.slice(curseur));
  return <>{morceaux}</>;
}

/**
 * Page « Historique » : journal global immuable de toutes les
 * modifications — initiatives, étapes, jalons, demandes, comptes et
 * rôles. Lecture ouverte à tous les consultants (INITIATIVE_CONSULTER) et
 * rien d'autre : il n'existe AUCUNE écriture (aucun bouton de
 * suppression ni de purge — la preuve d'audit ne se retouche pas).
 *
 * Recherche (code ou nom), filtres type / utilisateur / période, bascule
 * « Actives | Archives » (lignes de plus de 12 mois, conservées telles
 * quelles), tri, pagination et export CSV complet côté serveur (toutes
 * les lignes filtrées, pas seulement l'affichage courant). L'URL porte
 * les critères : la vue filtrée se partage, c'est ce que fait le bouton
 * « Voir tout l'historique » de la fiche initiative.
 */
export default function HistoriquePage() {
  const { t, langue } = useI18n();
  const [searchParams, setSearchParams] = useSearchParams();

  const [filtres, setFiltres] = useState(() => lireDepuisUrl(searchParams));
  const [lignes, setLignes] = useState([]);
  const [total, setTotal] = useState(0);
  const [auteurs, setAuteurs] = useState([]);
  const [initiativeFiltree, setInitiativeFiltree] = useState(null);
  const [chargement, setChargement] = useState(true);
  const { toast } = useToast();

  // Auteurs réellement présents dans le journal (une fois).
  useEffect(() => {
    api.get('/historique/auteurs')
      .then((res) => setAuteurs(res.data))
      .catch((err) => toast({ title: messageErreur(err), variant: 'destructive' }));
  }, []);

  // Toute modification de l'URL (appliquer, tri, pagination, bascule
  // d'archives, lien partagé, retour du navigateur) recharge la liste.
  useEffect(() => {
    const depuisUrl = lireDepuisUrl(searchParams);
    setFiltres(depuisUrl);
    charger(depuisUrl);

    // Le pastille « filtré sur cette initiative » montre le code et le nom.
    if (depuisUrl.initiative) {
      api.get(`/initiatives/${depuisUrl.initiative}`)
        .then((res) => setInitiativeFiltree({ id: depuisUrl.initiative, code: res.data.code, nom: res.data.nom }))
        .catch(() => setInitiativeFiltree({ id: depuisUrl.initiative, code: null, nom: null }));
    } else {
      setInitiativeFiltree(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const charger = async (f) => {
    setChargement(true);
    try {
      const params = new URLSearchParams();
      if (f.recherche.trim()) params.set('recherche', f.recherche.trim());
      if (f.type) params.set('type', f.type);
      if (f.utilisateur) params.set('utilisateur', f.utilisateur);
      if (f.du) params.set('du', f.du);
      if (f.au) params.set('au', f.au);
      if (f.initiative) params.set('initiative', f.initiative);
      if (f.archives) params.set('archives', 'true');
      if (f.direction === 'asc') params.set('direction', 'asc');
      params.set('page', String(f.page));
      params.set('size', String(TAILLE_PAGE));
      const res = await api.get(`/historique?${params.toString()}`);
      setLignes(res.data);
      const compte = parseInt(res.headers['x-total-count'] ?? '', 10);
      setTotal(Number.isNaN(compte) ? res.data.length : compte);
    } catch (err) {
      toast({ title: messageErreur(err), variant: 'destructive' });
    } finally {
      setChargement(false);
    }
  };

  const construireParams = (f, options = {}) => {
    const params = new URLSearchParams();
    if (f.recherche.trim()) params.set('recherche', f.recherche.trim());
    if (f.type) params.set('type', f.type);
    if (f.utilisateur) params.set('utilisateur', f.utilisateur);
    if (f.du) params.set('du', f.du);
    if (f.au) params.set('au', f.au);
    if (f.initiative) params.set('initiative', f.initiative);
    if (f.archives) params.set('archive', '1');
    if (f.direction === 'asc') params.set('direction', 'asc');
    const page = options.page ?? 0;
    if (page > 0) params.set('page', String(page));
    return params;
  };

  const appliquerFiltres = (e) => {
    e.preventDefault();
    // Une nouvelle recherche repart à la première page.
    setSearchParams(construireParams(filtres), { replace: true });
  };

  const reinitialiser = () => {
    setFiltres({ ...vide, initiative: '', archives: false, direction: 'desc', page: 0 });
    setSearchParams({}, { replace: true });
  };

  const changerTri = (direction) => {
    setSearchParams(construireParams({ ...filtres, direction }, { page: 0 }), { replace: true });
  };

  /** Bascule vers la vue « Archives » (ou retour aux « Actives »). */
  const basculerVue = (archives) => {
    setSearchParams(construireParams({ ...filtres, archives }, { page: 0 }), { replace: false });
  };

  const allerPage = (page) => {
    setSearchParams(construireParams(filtres, { page }), { replace: true });
  };

  const retirerFiltreInitiative = () => {
    setSearchParams(construireParams({ ...filtres, initiative: '' }, { page: 0 }), { replace: true });
  };

  /**
   * Export CSV complet côté serveur : mêmes filtres que la lecture, mais
   * TOUTES les lignes filtrées (pas seulement l'affichage courant) — c'est
   * le serveur qui assemble et encode le fichier (BOM, « ; »).
   */
  const exporterCsv = async () => {
    try {
      const params = new URLSearchParams();
      if (filtres.recherche.trim()) params.set('recherche', filtres.recherche.trim());
      if (filtres.type) params.set('type', filtres.type);
      if (filtres.utilisateur) params.set('utilisateur', filtres.utilisateur);
      if (filtres.du) params.set('du', filtres.du);
      if (filtres.au) params.set('au', filtres.au);
      if (filtres.initiative) params.set('initiative', filtres.initiative);
      if (filtres.archives) params.set('archives', 'true');
      await telechargerFichier(`/historique/export?${params.toString()}`, 'historique-modifications.csv');
    } catch (err) {
      toast({ title: await messageErreurBlob(err), variant: 'destructive' });
    }
  };

  const pagesTotales = Math.max(1, Math.ceil(total / TAILLE_PAGE));
  const depart = total === 0 ? 0 : filtres.page * TAILLE_PAGE + 1;
  const arrivee = Math.min((filtres.page + 1) * TAILLE_PAGE, total);
  const filtresActifs = Boolean(
    filtres.recherche.trim() || filtres.type || filtres.utilisateur
    || filtres.du || filtres.au || filtres.initiative
  );

  const champRecherche = (e) => setFiltres((f) => ({ ...f, recherche: e.target.value }));
  const champ = (nom) => (e) => setFiltres((f) => ({ ...f, [nom]: e.target.value }));

  /**
   * Ancienne → nouvelle valeur. Les changements de statut gardent les
   * badges colorés du reste de l'application ; les autres champs
   * s'affichent en clair (budget, dates, responsable…).
   */
  const renduValeurs = (ev) => {
    const estStatut = ev.champ === 'statut';
    const avant = ev.ancienneValeur
      ? (estStatut
        ? <StatutBadge libelle={ev.ancienneValeur} />
        : <span className="historique-valeur"><Surligner texte={ev.ancienneValeur} requete={filtres.recherche} /></span>)
      : <span className="texte-muet">—</span>;
    const apres = ev.nouvelleValeur
      ? (estStatut
        ? <StatutBadge libelle={ev.nouvelleValeur} />
        : <span className="historique-valeur"><Surligner texte={ev.nouvelleValeur} requete={filtres.recherche} /></span>)
      : <span className="texte-muet">—</span>;
    return (
      <>
        {avant}
        <span className="historique-fleche" aria-hidden="true"> → </span>
        {apres}
      </>
    );
  };

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('historique.titre')}
          </h2>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('historique.sousTitre')}
          </p>
        </div>
        <div className="flex items-end gap-3">
          {/* Bascule dans la page (aucune route ni lien de menu) :
              l'URL ?archive=1 documente la vue affichée. */}
          <div className="flex items-center gap-2 border border-input rounded-md p-1" role="group" aria-label={t('historique.vueAria')}>
            <button
              type="button"
              className={`px-3 py-1.5 text-sm rounded-md transition-colors ${!filtres.archives ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'}`}
              onClick={() => basculerVue(false)}
            >
              {t('historique.vueActives')}
            </button>
            <button
              type="button"
              className={`px-3 py-1.5 text-sm rounded-md transition-colors ${filtres.archives ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'}`}
              onClick={() => basculerVue(true)}
            >
              {t('historique.vueArchives')}
            </button>
          </div>
          <Button
            type="button"
            variant="secondary"
            onClick={exporterCsv}
            disabled={lignes.length === 0}
            title={t('historique.exportAide')}
          >
            {t('historique.exportCsv')}
          </Button>
        </div>
      </header>

      <Card className="mb-6 p-[18px] shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-semibold text-muted-foreground">
            {t('historique.filtres')}
          </CardTitle>
        </CardHeader>
        <CardContent className="pt-0">
          <form className="flex flex-wrap items-start gap-3" onSubmit={appliquerFiltres}>
            <div className="flex-1 min-w-[200px] space-y-2">
              <Label htmlFor="historiqueRecherche">
                {t('historique.recherchePlaceholder')}
              </Label>
              <Input
                id="historiqueRecherche"
                type="search"
                placeholder={t('historique.recherchePlaceholder')}
                aria-label={t('historique.recherchePlaceholder')}
                value={filtres.recherche}
                onChange={champRecherche}
              />
            </div>
            <div className="flex-1 min-w-[160px] space-y-2">
              <Label className="text-xs font-medium text-muted-foreground">
                {t('historique.ariaType')}
              </Label>
              <select
                aria-label={t('historique.ariaType')}
                value={filtres.type}
                onChange={champ('type')}
                className="min-w-[160px] rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="">{t('historique.tousTypes')}</option>
                {TYPES.map((type) => (
                  <option key={type} value={type}>{libelleType(t, type)}</option>
                ))}
              </select>
            </div>
            <div className="flex-1 min-w-[200px] space-y-2">
              <Label className="text-xs font-medium text-muted-foreground">
                {t('historique.ariaUtilisateur')}
              </Label>
              <select
                aria-label={t('historique.ariaUtilisateur')}
                value={filtres.utilisateur}
                onChange={champ('utilisateur')}
                className="min-w-[200px] rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="">{t('historique.tousUtilisateurs')}</option>
                {auteurs.map((a) => (
                  <option key={a.idUser} value={a.idUser}>{a.nomComplet}</option>
                ))}
              </select>
            </div>
            <div className="flex-none space-y-2">
              <Label className="text-xs font-medium text-muted-foreground whitespace-nowrap">
                {t('historique.periodeTitre')}
              </Label>
              <div className="flex items-center gap-1">
                <input
                  type="date"
                  aria-label={t('historique.periodeDu')}
                  title={t('historique.periodeDu')}
                  value={filtres.du}
                  onChange={champ('du')}
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                />
                <span className="text-muted-foreground whitespace-nowrap" aria-hidden="true">
                  →
                </span>
                <input
                  type="date"
                  aria-label={t('historique.periodeAu')}
                  title={t('historique.periodeAu')}
                  value={filtres.au}
                  onChange={champ('au')}
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                />
              </div>
              <small className="text-xs text-muted-foreground filtre-aide">
                {t('historique.periodeAide')}
              </small>
            </div>
            <div className="flex items-end gap-2">
              <Button type="submit" disabled={chargement}>
                {t('historique.appliquer')}
              </Button>
              <Button type="button" variant="secondary" onClick={reinitialiser}>
                {t('historique.reinitialiser')}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card className="mb-6 p-[18px] shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 border border-input rounded-md p-1" role="group" aria-label={t('historique.triAria')}>
            <button
              type="button"
              className={`px-3 py-1.5 text-sm rounded-md transition-colors ${filtres.direction === 'desc' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'}`}
              onClick={() => changerTri('desc')}
            >
              {t('historique.triRecent')}
            </button>
            <button
              type="button"
              className={`px-3 py-1.5 text-sm rounded-md transition-colors ${filtres.direction === 'asc' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'}`}
              onClick={() => changerTri('asc')}
            >
              {t('historique.triAncien')}
            </button>
          </div>
          <span className="text-sm text-muted-foreground">
            {filtres.archives ? t('historique.vueArchivesAide') : t('historique.compteur', { de: depart, a: arrivee, total })}
            {filtresActifs && (
              <>
                {' · '}
                <button
                  type="button"
                  className="text-primary underline underline-offset-2 hover:text-primary/80"
                  onClick={reinitialiser}
                >
                  {t('historique.reinitialiser')}
                </button>
              </>
            )}
          </span>
        </div>

        {initiativeFiltree && (
          <p className="mb-3 flex items-center gap-2">
            {initiativeFiltree.code ? (
              <>
                <Link to={`/initiatives/${initiativeFiltree.id}`}>
                  {t('historique.pastilleInitiative', { code: initiativeFiltree.code, nom: initiativeFiltree.nom })}
                </Link>
                <button
                  type="button"
                  className="text-destructive hover:underline text-xs ml-2"
                  onClick={retirerFiltreInitiative}
                  aria-label={t('historique.pastilleRetirer')}
                >
                  ×
                </button>
              </>
            ) : (
              <>
                <span>{t('historique.pastilleSansNom', { id: initiativeFiltree.id })}</span>
                <button
                  type="button"
                  className="text-destructive hover:underline text-xs ml-2"
                  onClick={retirerFiltreInitiative}
                  aria-label={t('historique.pastilleRetirer')}
                >
                  ×
                </button>
              </>
            )}
          </p>
        )}

        {chargement ? (
          <div className="flex items-center justify-center py-12 text-sm text-muted-foreground">
            {t('historique.chargement')}
          </div>
        ) : lignes.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-8">
            {filtres.archives ? t('historique.aucuneArchive') : t('historique.aucunResultat')}
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('historique.colType')}</TableHead>
                <TableHead>{t('historique.colInitiative')}</TableHead>
                <TableHead>{t('historique.colChamp')}</TableHead>
                <TableHead>{t('historique.colValeurs')}</TableHead>
                <TableHead>{t('historique.colDetail')}</TableHead>
                <TableHead>{t('historique.colUtilisateur')}</TableHead>
                <TableHead>{t('historique.colDate')}</TableHead>
                <TableHead>{t('historique.colOrigine')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lignes.map((ev) => (
                <TableRow key={ev.id}>
                  <TableCell>
                    <Badge variant="secondary">{libelleType(t, ev.typeObjet)}</Badge>
                    <span className="ml-1 text-xs text-muted-foreground historique-id">
                      #{ev.idObjet}
                    </span>
                  </TableCell>
                  <TableCell>
                    {ev.initiative ? (
                      <>
                        <Link
                          to={`/initiatives/${ev.initiative.idInitiative}`}
                          className="text-primary hover:underline"
                        >
                          <Surligner texte={ev.initiative.code} requete={filtres.recherche} />
                        </Link>
                        <span className="ml-1 text-xs text-muted-foreground historique-nom">
                          <Surligner texte={ev.initiative.nom} requete={filtres.recherche} />
                        </span>
                      </>
                    ) : (
                      <span className="text-muted-foreground">—</span>
                    )}
                  </TableCell>
                  <TableCell>{libelleChamp(t, ev.champ)}</TableCell>
                  <TableCell>{renduValeurs(ev)}</TableCell>
                  <TableCell title={ev.commentaire ?? ''}>
                    {ev.commentaire
                      ? <span className="historique-commentaire">{ev.commentaire}</span>
                      : <span className="text-muted-foreground">—</span>}
                  </TableCell>
                  <TableCell title={ev.utilisateur?.email ?? ''}>
                    {ev.utilisateur?.nomComplet ?? (
                      <span className="text-muted-foreground">{t('initiativeDetail.auteurInconnu')}</span>
                    )}
                  </TableCell>
                  <TableCell>{formaterDate(ev.dateChangement, langue)}</TableCell>
                  <TableCell>
                    <Badge variant={ev.automatique ? 'secondary' : 'outline'}>
                      {ev.automatique ? t('historique.origineAutomatique') : t('historique.origineManuelle')}
                    </Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}

        {total > TAILLE_PAGE && (
          <nav className="mt-4 flex items-center justify-center gap-3" aria-label={t('historique.titre')}>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => allerPage(filtres.page - 1)}
              disabled={filtres.page === 0 || chargement}
            >
              {t('historique.pagePrecedente')}
            </Button>
            <span className="text-sm text-muted-foreground">
              {t('historique.pageInfo', { courante: filtres.page + 1, total: pagesTotales })}
            </span>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => allerPage(filtres.page + 1)}
              disabled={filtres.page + 1 >= pagesTotales || chargement}
            >
              {t('historique.pageSuivante')}
            </Button>
          </nav>
        )}
      </Card>
    </div>
  );
}
