import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';

const formVide = { code: '', libelle: '', description: '' };

/**
 * Objectifs stratégiques : consultation du
 * référentiel, création / modification, et initiatives contribuant à
 * chaque objectif (décompte calculé depuis la base réelle).
 */
export default function ObjectifsPage() {
  const { hasPermission } = useAuth();
  const { t } = useI18n();
  const peutGerer = hasPermission('INITIATIVE_MODIFIER');

  const [objectifs, setObjectifs] = useState([]);
  const [initiatives, setInitiatives] = useState([]);
  const [form, setForm] = useState(formVide);
  const [nouveau, setNouveau] = useState(false);
  const [edition, setEdition] = useState(null); // idObjectif
  const [chargement, setChargement] = useState(true);
  const [soumission, setSoumission] = useState(false);
  const [erreur, setErreur] = useState('');
  const [info, setInfo] = useState('');

  const charger = () => {
    Promise.all([api.get('/objectifs'), api.get('/initiatives')])
      .then(([obj, init]) => {
        setObjectifs(obj.data);
        setInitiatives(init.data);
      })
      .catch((err) => setErreur(messageErreur(err)))
      .finally(() => setChargement(false));
  };

  useEffect(() => {
    charger();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const rattachees = useMemo(() => {
    const m = new Map();
    initiatives.forEach((i) => {
      if (i.objectif) {
        const liste = m.get(i.objectif.idObjectif) ?? [];
        liste.push(i);
        m.set(i.objectif.idObjectif, liste);
      }
    });
    return m;
  }, [initiatives]);

  const enregistrer = async (e) => {
    e.preventDefault();
    setSoumission(true);
    setErreur('');
    try {
      if (edition) {
        await api.put(`/objectifs/${edition}`, form);
        setInfo({ cle: 'objectifs.info.misAJour' });
      } else {
        await api.post('/objectifs', form);
        setInfo({ cle: 'objectifs.info.cree' });
      }
      setForm(formVide);
      setNouveau(false);
      setEdition(null);
      charger();
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setSoumission(false);
    }
  };

  return (
    <div className="page">
      <header className="page-entete">
        <div>
          <h2>{t('objectifs.titre')}</h2>
          <p>{t('objectifs.sousTitre')}</p>
        </div>
        {peutGerer && (
          <button className="btn btn-primaire" onClick={() => { setNouveau(!nouveau); setEdition(null); setForm(formVide); }}>
            {nouveau ? t('objectifs.fermer') : t('objectifs.nouveau')}
          </button>
        )}
      </header>

      {info && <div className="alerte alerte-succes">{afficherMessage(t, info)}</div>}
      {erreur && <div className="alerte alerte-erreur">{afficherMessage(t, erreur)}</div>}

      {nouveau && peutGerer && (
        <form className="carte formulaire" onSubmit={enregistrer}>
          <h3>{t('objectifs.form.titreNouveau')}</h3>
          <div className="form-grille">
            <div className="champ">
              <label>{t('objectifs.form.code')}</label>
              <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} required placeholder={t('objectifs.form.codePlaceholder')} />
            </div>
            <div className="champ">
              <label>{t('objectifs.form.libelle')}</label>
              <input value={form.libelle} onChange={(e) => setForm({ ...form, libelle: e.target.value })} required placeholder={t('objectifs.form.libellePlaceholder')} />
            </div>
            <div className="champ champ-plein">
              <label>{t('objectifs.form.description')}</label>
              <textarea rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </div>
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn-secondaire" onClick={() => setNouveau(false)}>{t('objectifs.annuler')}</button>
            <button type="submit" className="btn btn-primaire" disabled={soumission}>{soumission ? t('objectifs.enregistrement') : t('objectifs.creer')}</button>
          </div>
        </form>
      )}

      <div className="roles-grille">
        {chargement ? (
          <div className="center-page">{t('objectifs.chargement')}</div>
        ) : objectifs.length === 0 ? (
          <p className="texte-muet">{t('objectifs.aucun')}</p>
        ) : (
          objectifs.map((o) => {
            const liste = rattachees.get(o.idObjectif) ?? [];
            const enEdition = edition === o.idObjectif;
            return (
              <div key={o.idObjectif} className="carte">
                {enEdition && peutGerer ? (
                  <form onSubmit={enregistrer}>
                    <h3>{t('objectifs.form.titreModifier')}</h3>
                    <div className="champ">
                      <label>{t('objectifs.form.code')}</label>
                      <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} required />
                    </div>
                    <div className="champ">
                      <label>{t('objectifs.form.libelle')}</label>
                      <input value={form.libelle} onChange={(e) => setForm({ ...form, libelle: e.target.value })} required />
                    </div>
                    <div className="champ">
                      <label>{t('objectifs.form.description')}</label>
                      <textarea rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
                    </div>
                    <div className="actions" style={{ marginTop: 10 }}>
                      <button type="submit" className="btn btn-primaire btn-petit" disabled={soumission}>{t('objectifs.enregistrer')}</button>
                      <button type="button" className="btn btn-secondaire btn-petit" onClick={() => setEdition(null)}>{t('objectifs.annuler')}</button>
                    </div>
                  </form>
                ) : (
                  <>
                    <div className="role-entete">
                      <div>
                        <span className="badge badge-gris">{o.code}</span>
                        <h3 style={{ margin: '8px 0 4px' }}>{o.libelle}</h3>
                      </div>
                      {peutGerer && (
                        <button
                          className="btn btn-petit btn-secondaire"
                          onClick={() => { setEdition(o.idObjectif); setForm({ code: o.code, libelle: o.libelle, description: o.description ?? '' }); setNouveau(false); }}
                        >
                          {t('objectifs.modifier')}
                        </button>
                      )}
                    </div>
                    {o.description && <p className="texte-muet">{o.description}</p>}
                    <div className="kpi-contenu" style={{ margin: '10px 0' }}>
                      <span className="kpi-valeur">{liste.length}</span>
                      <span className="kpi-libelle">{t(liste.length > 1 ? 'objectifs.kpi.initiativesRattachees' : 'objectifs.kpi.initiativeRattachee')}</span>
                    </div>
                    <ul style={{ margin: 0, paddingLeft: 18 }}>
                      {liste.length === 0 && <li className="texte-muet">{t('objectifs.aucuneInitiative')}</li>}
                      {liste.map((i) => (
                        <li key={i.idInitiative}>
                          <Link to={`/initiatives/${i.idInitiative}`}>{i.nom}</Link>
                          <span className="texte-muet"> — {i.statut?.libelle}</span>
                        </li>
                      ))}
                    </ul>
                  </>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
