import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';

/**
 * Assistant d'installation en 3 étapes (Phase 2B5) :
 *  1. votre organisation (créee a l'inscription),
 *  2. votre première initiative,
 *  3. invitation d'un collegue.
 *
 * A la fin, le drapeau localStorage onboardingDone est leve et
 * l'utilisateur est redirige vers le tableau de bord.
 */
export default function OnboardingPage() {
  const { t } = useI18n();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [etape, setEtape] = useState(1);
  const [termine, setTermine] = useState(false);
  const [chargement, setChargement] = useState(false);
  const [erreur, setErreur] = useState('');

  // --- Etape 2 : premiere initiative
  const [init, setInit] = useState({ code: '', nom: '', description: '' });
  const [referentiels, setReferentiels] = useState({ statuts: [], entites: [], priorites: [] });

  // --- Etape 3 : invitation
  const [invite, setInvite] = useState({ email: '', idRole: '' });
  const [roles, setRoles] = useState([]);

  const set = (objet, champ) => (e) => setObjet(objet, champ, e.target.value);
  const setObjet = (objet, champ, valeur) => {
    if (objet === 'init') setInit((f) => ({ ...f, [champ]: valeur }));
    else setInvite((f) => ({ ...f, [champ]: valeur }));
  };

  const suivant = () => setEtape((e) => Math.min(e + 1, 3));
  const precedent = () => setEtape((e) => Math.max(e - 1, 1));

  useEffect(() => {
    Promise.all([
      api.get('/referentiels/statuts-initiative'),
      api.get('/referentiels/entites'),
      api.get('/referentiels/priorites'),
      api.get('/roles')
    ])
      .then(([s, e, p, r]) => {
        setReferentiels({
          statuts: s.data,
          entites: e.data,
          priorites: p.data
        });
        setRoles(r.data);
      })
      .catch(() => {});
  }, []);

  const creerInitiative = async (e) => {
    e.preventDefault();
    setErreur('');
    if (!init.code || !init.nom) {
      setErreur(t('onboarding.initiative') + ' et ' + t('onboarding.initiativeDesc') + ' sont obligatoires.');
      return;
    }
    setChargement(true);
    try {
      const statut = referentiels.statuts[0];
      const entite = referentiels.entites[0];
      const priorite = referentiels.priorites[0];
      await api.post('/api/initiatives', {
        code: init.code,
        nom: init.nom,
        description: init.description || null,
        budget: 0,
        avancement: 0,
        dateDebut: null,
        dateFinPrevue: null,
        idEntite: entite ? Number(entite.idEntite) : null,
        idStatut: statut ? Number(statut.idStatut) : null,
        idPriorite: priorite ? Number(priorite.idPriorite) : null,
        idObjectif: null
      });
      setEtape(3);
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setChargement(false);
    }
  };

  const inviter = async (e) => {
    e.preventDefault();
    setErreur('');
    if (!invite.email || !invite.idRole) {
      setErreur(t('onboarding.invite') + ' et ' + t('onboarding.role') + ' sont obligatoires.');
      return;
    }
    setChargement(true);
    try {
      await api.post('/api/auth/inviter', {
        email: invite.email.trim().toLowerCase(),
        idRole: Number(invite.idRole)
      });
      setTermine(true);
      localStorage.setItem('onboardingDone', '1');
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setChargement(false);
    }
  };

  const allerDashboard = () => navigate('/dashboard', { replace: true });

  if (termine) {
    return (
      <div className="page">
        <header className="page-entete">
          <div>
            <h2>{t('onboarding.termine')}</h2>
            <p>{t('onboarding.bravo')}</p>
          </div>
        </header>
        <div className="carte formulaire">
          <p>{t('onboarding.etape3Desc')}</p>
          <button className="btn btn-primaire" onClick={allerDashboard}>
            {t('onboarding.allerDashboard')}
          </button>
        </div>
      </div>
    );
  }

  const etapeCourante = [
    { numero: 1, titre: t('onboarding.etape1'), texte: t('onboarding.etape1Desc') },
    { numero: 2, titre: t('onboarding.etape2'), texte: t('onboarding.etape2Desc') },
    { numero: 3, titre: t('onboarding.etape3'), texte: t('onboarding.etape3Desc') }
  ][etape - 1];

  return (
    <div className="page">
      <header className="page-entete">
        <div>
          <h2>{t('onboarding.titre')}</h2>
          <p>{t('onboarding.sousTitre')}</p>
        </div>
      </header>

      {erreur && <div className="alerte alerte-erreur">{erreur}</div>}

      <div className="onboarding-progres">
        {[1, 2, 3].map((n) => (
          <div key={n} className={`etape ${n === etape ? 'active' : ''} ${n < etape ? 'faite' : ''}`}>
            <span className="etape-numero">{n}</span>
            <span className="etape-libelle">{etapeCourante.numero === n ? etapeCourante.titre : ''}</span>
          </div>
        ))}
      </div>

      <div className="carte formulaire">
        <p className="etape-desc">{etapeCourante.texte}</p>

        {/* Etape 1 : organisation (deja creee a l'inscription) */}
        {etape === 1 && (
          <>
            <p>{t('onboarding.etape1Desc')}</p>
            <p><em>{user?.email}</em></p>
          </>
        )}

        {/* Etape 2 : creer la premiere initiative */}
        {etape === 2 && (
          <form onSubmit={creerInitiative}>
            <div className="form-grille">
              <div className="champ">
                <label>{t('onboarding.initiative')}</label>
                <input value={init.code} onChange={set('init', 'code')} placeholder="DIG-CLI-001" required />
              </div>
              <div className="champ champ-plein">
                <label>{t('initiativeForm.nom')}</label>
                <input value={init.nom} onChange={set('init', 'nom')} placeholder="Ex. Digitalisation client" required />
              </div>
              <div className="champ champ-plein">
                <label>{t('initiativeForm.description')}</label>
                <textarea value={init.description} onChange={set('init', 'description')} rows={3} />
              </div>
            </div>
            <div className="form-actions">
              <button type="button" className="btn btn-secondaire" disabled={chargement} onClick={precedent}>
                {t('initiativeForm.annuler')}
              </button>
              <button type="submit" className="btn btn-primaire" disabled={chargement}>
                {chargement ? t('initiativeForm.enregistrement') : t('onboarding.creerInit')}
              </button>
            </div>
          </form>
        )}

        {/* Etape 3 : inviter un collegue */}
        {etape === 3 && (
          <form onSubmit={inviter}>
            <div className="form-grille">
              <div className="champ">
                <label>{t('onboarding.invite')}</label>
                <input type="email" value={invite.email} onChange={set('invite', 'email')} placeholder="colleague@example.com" required />
              </div>
              <div className="champ">
                <label>{t('onboarding.role')}</label>
                <select value={invite.idRole} onChange={set('invite', 'idRole')} required>
                  <option value="">—</option>
                  {roles.map((r) => (
                    <option key={r.idRole} value={r.idRole}>{r.libelle || r.code}</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="form-actions">
              <button type="button" className="btn btn-secondaire" disabled={chargement} onClick={precedent}>
                {t('initiativeForm.annuler')}
              </button>
              <button type="submit" className="btn btn-primaire" disabled={chargement}>
                {chargement ? t('inscription.enCours') : t('onboarding.inviter')}
              </button>
            </div>
          </form>
        )}
      </div>

      <div className="form-actions" style={{ marginTop: 16 }}>
        {etape > 1 && (
          <button type="button" className="btn btn-secondaire" onClick={precedent}>
            {t('initiativeForm.annuler')}
          </button>
        )}
        {etape < 3 && (
          <button type="button" className="btn btn-primaire" onClick={suivant}>
            {t('onboarding.suivant')}
          </button>
        )}
      </div>
    </div>
  );
}
