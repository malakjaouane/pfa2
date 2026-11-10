import { useEffect, useRef, useState } from 'react';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import api, { messageErreur } from '../api';
import { IconAlert, IconAssistant } from '../components/Icons';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Badge } from '../components/ui/badge';

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
    <div className="mx-auto w-full max-w-[1240px] ia-page">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('assistant.titre')}
          </h2>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('assistant.sousTitre')}
          </p>
        </div>
        {statut && (
          <Badge variant={statut.disponible ? 'success' : 'secondary'}>
            {statut.disponible ? t('assistant.etatActif') : t('assistant.etatIndispo')}
          </Badge>
        )}
      </header>

      {indisponible && (
        <div className="mb-4 rounded-md border border-destructive/50 bg-destructive/50 p-3 text-sm text-destructive" role="status">
          <IconAlert size={18} />
          <div className="ml-2 flex flex-col">
            <strong>{t('assistant.indispoTitre')}</strong>
            <p>{statut.actif ? t('assistant.indispoCle') : t('assistant.indispoDesactive')}</p>
          </div>
        </div>
      )}
      {erreur && (
        <div className="mb-4 rounded-md border border-destructive/50 bg-destructive/50 p-3 text-sm text-destructive">
          {afficherMessage(t, erreur)}
        </div>
      )}

      <Card className="mb-6 p-[18px] shadow-sm ia-conversation" role="log" aria-live="polite" aria-label={t('assistant.ariaConversation')}>
        <CardContent className="pt-0">
          {messages.length === 0 && !envoi && (
            <p className="text-sm text-muted-foreground ia-vide">{t('assistant.bienvenue')}</p>
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
        </CardContent>
      </Card>

      <div className="ia-suggestions mb-6">
        <span className="text-xs text-muted-foreground">{t('assistant.suggestions')}</span>
        <div className="flex flex-wrap gap-2 mt-2">
          {[1, 2, 3].map((n) => (
            <Button
              key={n}
              type="button"
              variant="outline"
              size="sm"
              disabled={envoi || indisponible}
              onClick={() => poser(t(`assistant.suggestion${n}`))}
            >
              {t(`assistant.suggestion${n}`)}
            </Button>
          ))}
        </div>
      </div>

      <form className="ia-saisie" onSubmit={(e) => { e.preventDefault(); poser(); }}>
        <div className="flex gap-2">
          <Input
            type="text"
            maxLength={1000}
            value={question}
            placeholder={t('assistant.placeholder')}
            aria-label={t('assistant.placeholder')}
            onChange={(e) => setQuestion(e.target.value)}
            disabled={envoi || indisponible}
            className="flex-1"
          />
          <Button
            type="submit"
            disabled={envoi || indisponible || !question.trim()}
          >
            {envoi ? t('assistant.enCours') : t('assistant.envoyer')}
          </Button>
          {messages.length > 0 && (
            <Button
              type="button"
              variant="secondary"
              onClick={() => { setMessages([]); setErreur(''); }}
            >
              {t('assistant.effacer')}
            </Button>
          )}
        </div>
      </form>

      <p className="text-xs text-muted-foreground ia-legal">{t('assistant.legal')}</p>
    </div>
  );
}
