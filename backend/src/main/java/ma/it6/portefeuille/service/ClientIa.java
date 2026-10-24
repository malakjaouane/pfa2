package ma.it6.portefeuille.service;

import com.fasterxml.jackson.databind.JsonNode;
import ma.it6.portefeuille.exception.IaIndisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client HTTP du service d'IA — <strong>deux formats</strong> :
 *
 * <ul>
 *   <li>{@code anthropic} (défaut) — API officielle {@code /v1/messages}
 *       (API key obligatoire) ;</li>
 *   <li>{@code openai} — chat/completions compatible OpenAI : <b>Ollama
 *       local</b> (sans clé, hors-ligne), LM Studio, Groq, Mistral,
 *       OpenRouter… La clé, si fournie, part en {@code Authorization:
 *       Bearer}.</li>
 * </ul>
 *
 * <p>Premier appel réseau sortant du projet : la configuration est donc
 * entièrement externalisée (format, clé, modèle, URL de base, timeouts).
 * La clé n'est <strong>jamais</strong> journalisée ni renvoyée au client —
 * seule son absence de configuration est signalée, en clair, à
 * l'utilisateur.</p>
 *
 * <p>Les erreurs du réseau ou du service sont traduites en messages
 * français actionnables ({@link IaIndisponibleException}) plutôt qu'en
 * stack traces : l'interface doit toujours pouvoir expliquer pourquoi
 * l'assistant ne répond pas.</p>
 */
@Service
public class ClientIa {

    private static final Logger log = LoggerFactory.getLogger(ClientIa.class);

    /** Format d'appel : API Anthropic (défaut). */
    public static final String FORMAT_ANTHROPIC = "anthropic";
    /** Format d'appel : compatible OpenAI (Ollama, Groq, Mistral…). */
    public static final String FORMAT_OPENAI = "openai";

    private final RestClient restClient;
    private final String format;
    private final String cleApi;
    private final String modele;
    private final int maxTokens;

    public ClientIa(@Value("${app.ia.url-api:https://api.anthropic.com}") String urlApi,
                    @Value("${app.ia.cle-api:}") String cleApi,
                    @Value("${app.ia.format:anthropic}") String format,
                    @Value("${app.ia.modele:claude-haiku-4-5}") String modele,
                    @Value("${app.ia.max-tokens:1024}") int maxTokens,
                    @Value("${app.ia.timeout-ms:60000}") long timeoutMs) {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(Duration.ofSeconds(10));
        fabrique.setReadTimeout(Duration.ofMillis(timeoutMs));
        RestClient.Builder constructeur = RestClient.builder()
                .baseUrl(urlApi)
                .requestFactory(fabrique);
        if (FORMAT_ANTHROPIC.equalsIgnoreCase(format)) {
            constructeur.defaultHeader("anthropic-version", "2023-06-01");
        }
        this.restClient = constructeur.build();
        this.format = format == null ? FORMAT_ANTHROPIC : format;
        this.cleApi = cleApi == null ? "" : cleApi;
        this.modele = modele;
        this.maxTokens = maxTokens;
    }

    /** Modèle configuré (traçabilité dans le journal et les réponses). */
    public String modele() {
        return modele;
    }

    /** Format d'appel configuré ({@code anthropic} ou {@code openai}). */
    public String format() {
        return format;
    }

    /**
     * Pose {@code instruction} au modèle, encadrée par le prompt système
     * {@code systeme} (contexte du portefeuille + règles de réponse).
     *
     * @return le texte de la réponse (jamais {@code null})
     * @throws IaIndisponibleException si le service est injoignable,
     *         refuse la clé, ou renvoie une réponse vide
     */
    public String generer(String systeme, String instruction) {
        boolean ouvert = FORMAT_OPENAI.equalsIgnoreCase(format);
        RestClient.RequestBodySpec requete = restClient.post()
                .uri(ouvert ? "/v1/chat/completions" : "/v1/messages")
                .contentType(MediaType.APPLICATION_JSON);
        if (!cleApi.isBlank()) {
            requete = requete.header(ouvert ? "Authorization" : "x-api-key",
                    ouvert ? "Bearer " + cleApi : cleApi);
        }
        Map<String, Object> corps = ouvert
                ? Map.of(
                        "model", modele,
                        "max_tokens", maxTokens,
                        "messages", List.of(
                                Map.of("role", "system", "content", systeme),
                                Map.of("role", "user", "content", instruction)))
                : Map.of(
                        "model", modele,
                        "max_tokens", maxTokens,
                        "system", systeme,
                        "messages", List.of(Map.of("role", "user", "content", instruction)));

        JsonNode reponse;
        try {
            reponse = requete
                    .body(corps)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.warn("Service d'IA : réponse HTTP {} ({})", e.getStatusCode().value(), e.getStatusText());
            throw new IaIndisponibleException(traduireStatut(e.getStatusCode().value()));
        } catch (RestClientException e) {
            log.warn("Service d'IA injoignable : {}", e.getMessage());
            throw new IaIndisponibleException(
                    "Impossible de joindre le service d'IA (réseau ou délai dépassé). Vérifiez que le service tourne, puis réessayez.");
        }

        if (reponse == null) {
            throw new IaIndisponibleException("Le service d'IA a renvoyé une réponse vide.");
        }
        StringBuilder texte = new StringBuilder();
        if (ouvert) {
            texte.append(reponse.path("choices").path(0).path("message").path("content").asText(""));
        } else {
            for (JsonNode bloc : reponse.path("content")) {
                if ("text".equals(bloc.path("type").asText())) {
                    texte.append(bloc.path("text").asText());
                }
            }
        }
        if (texte.isEmpty()) {
            throw new IaIndisponibleException("Le service d'IA n'a produit aucun texte (réponse vide).");
        }
        return texte.toString().trim();
    }

    /** Traduit un statut HTTP du service d'IA en message actionnable. */
    private String traduireStatut(int statut) {
        boolean ouvert = FORMAT_OPENAI.equalsIgnoreCase(format);
        return switch (statut) {
            case 401, 403 -> ouvert
                    ? "Clé API d'IA refusée : vérifiez la clé configurée côté administrateur."
                    : "Clé API d'IA refusée : vérifiez la valeur ANTHROPIC_API_KEY côté administrateur.";
            case 404 -> ouvert
                    ? "Service d'IA introuvable : vérifiez l'adresse IA_BASE_URL et le modèle configuré (IA_MODELE)."
                    : "Requête refusée par le service d'IA : vérifiez le modèle configuré (ANTHROPIC_MODELE).";
            case 429 -> "Quota du service d'IA atteint : réessayez dans quelques minutes.";
            case 400, 413, 422 ->
                    "Requête refusée par le service d'IA : vérifiez le modèle configuré (IA_MODELE).";
            default -> "Service d'IA indisponible (HTTP " + statut + "). Réessayez plus tard.";
        };
    }
}
