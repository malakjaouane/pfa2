import { useEffect, useRef, useState } from 'react';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import api, { messageErreur } from '../api';
import { IconAlert, IconAssistant } from '../components/Icons';

/**
 * Page « Assistant IA » — chat « Demande à ton portefeuille ».
 *
 * <p>Chaque question est envoyée à {@code POST /api/ia/assistant} avec la
 * langue courante ; le backend reconstruit le contexte du portefeuille et
 * interdit au modèle d'inventer (loi 09-08 / CNDP). Si l'assistant est
 * indisponible (clé absente ou kill switch), l'état est affiché
 * explicitement et le formulaire est désactivé — jamais d'erreur muette.</p>
 */
export default function AssistantPage() {
  const { t, langue } = useI18n();
  const [statut, setStatut] = useState(null);
  const [messages, setMessages] = useState([]);
  const [question, setQuestion] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState('');
  const refFin = useRef(null);

  useEffect(() => {
    let actif = true;
    api.get('/ia/statut')
      .then(({ data }) => { if (actif) setStatut(data); })
      .catch(() => { /* statut inconnu : on laisse l'envoi révéler l'erreur */ });
    return () => { actif = false; };
  }, []);

  useEffect(() => {
    refFin.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }, [messages, envoi]);

  const poser = async (texte) => {
    const q = (texte ?? question).trim();
    if (!q || envoi) return;
    setErreur('');
    setQuestion('');
    setMessages((m) => [...m, { role: 'utilisateur', texte: q }]);
    setEnvoi(true);
    try {
      const { data } = await api.post('/ia/assistant', { question: q, langue });
      setMessages((m) => [...m, { role: 'assistant', texte: data.reponse, modele: data.modele }]);
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  };

  const indisponible = statut != null && !statut.disponible;

  return (
    <div className="page ia-page">
      <header className="page-entete">
        <div>
          <h2>{t('assistant.titre')}</h2>
          <p>{t('assistant.sousTitre')}</p>
        </div>
        {statut && (
          <span className={statut.disponible ? 'badge badge-vert' : 'badge badge-gris'}>
            {statut.disponible ? t('assistant.etatActif') : t('assistant.etatIndispo')}
          </span>
        )}
      </header>

      {indisponible && (
        <div className="alerte alerte-erreur ia-indispo" role="status">
          <IconAlert size={18} />
          <div>
            <strong>{t('assistant.indispoTitre')}</strong>
            <p>{statut.actif ? t('assistant.indispoCle') : t('assistant.indispoDesactive')}</p>
          </div>
        </div>
      )}
      {erreur && <div className="alerte alerte-erreur">{afficherMessage(t, erreur)}</div>}

      <div className="carte ia-conversation" role="log" aria-live="polite" aria-label={t('assistant.ariaConversation')}>
        {messages.length === 0 && !envoi && (
          <p className="texte-muet ia-vide">{t('assistant.bienvenue')}</p>
        )}
        {messages.map((m, i) => (
          <div key={`${m.role}-${i}`} className={`ia-message ${m.role}`}>
            <div className="ia-bulle">
              {m.role === 'assistant' && <IconAssistant size={16} />}
              <span>{m.texte}</span>
            </div>
          </div>
        ))}
        {envoi && (
          <div className="ia-message assistant">
            <div className="ia-bulle ia-attente">
              <span className="ia-points" aria-hidden="true"><i /><i /><i /></span>
              {t('assistant.enCours')}
            </div>
          </div>
        )}
        <div ref={refFin} />
      </div>

      <div className="ia-suggestions">
        <span className="texte-muet">{t('assistant.suggestions')}</span>
        {[1, 2, 3].map((n) => (
          <button
            key={n}
            type="button"
            className="ia-suggestion"
            disabled={envoi || indisponible}
            onClick={() => poser(t(`assistant.suggestion${n}`))}
          >
            {t(`assistant.suggestion${n}`)}
          </button>
        ))}
      </div>

      <form className="ia-saisie" onSubmit={(e) => { e.preventDefault(); poser(); }}>
        <input
          type="text"
          maxLength={1000}
          value={question}
          placeholder={t('assistant.placeholder')}
          aria-label={t('assistant.placeholder')}
          onChange={(e) => setQuestion(e.target.value)}
          disabled={envoi || indisponible}
        />
        <button
          type="submit"
          className="btn btn-primaire"
          disabled={envoi || indisponible || !question.trim()}
        >
          {envoi ? t('assistant.enCours') : t('assistant.envoyer')}
        </button>
        {messages.length > 0 && (
          <button
            type="button"
            className="btn btn-secondaire"
            onClick={() => { setMessages([]); setErreur(''); }}
          >
            {t('assistant.effacer')}
          </button>
        )}
      </form>

      <p className="texte-muet ia-legal">{t('assistant.legal')}</p>
    </div>
  );
}
