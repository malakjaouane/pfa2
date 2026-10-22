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
 * Client HTTP du service d'IA Anthropic (API {@code /v1/messages}).
 *
 * <p>Premier appel réseau sortant du projet : la configuration est donc
 * entièrement externalisée (clé, modèle, URL de base, timeouts). La clé
 * n'est <strong>jamais</strong> journalisée ni renvoyée au client — seule
 * l'absence de clé est signalée, en clair, à l'utilisateur.</p>
 *
 * <p>Les erreurs du réseau ou du service sont traduites en messages
 * français actionnables ({@link IaIndisponibleException}) plutôt qu'en
 * stack traces : l'interface doit toujours pouvoir expliquer pourquoi
 * l'assistant ne répond pas.</p>
 */
@Service
public class ClientIa {

    private static final Logger log = LoggerFactory.getLogger(ClientIa.class);

    private final RestClient restClient;
    private final String modele;
    private final int maxTokens;

    public ClientIa(@Value("${app.ia.url-api:https://api.anthropic.com}") String urlApi,
                    @Value("${app.ia.cle-api:}") String cleApi,
                    @Value("${app.ia.modele:claude-haiku-4-5}") String modele,
                    @Value("${app.ia.max-tokens:1024}") int maxTokens,
                    @Value("${app.ia.timeout-ms:60000}") long timeoutMs) {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(Duration.ofSeconds(10));
        fabrique.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(urlApi)
                .defaultHeader("x-api-key", cleApi)
                .defaultHeader("anthropic-version", "2023-06-01")
                .requestFactory(fabrique)
                .build();
        this.modele = modele;
        this.maxTokens = maxTokens;
    }

    /** Modèle configuré (traçabilité dans le journal et les réponses). */
    public String modele() {
        return modele;
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
        Map<String, Object> corps = Map.of(
                "model", modele,
                "max_tokens", maxTokens,
                "system", systeme,
                "messages", List.of(Map.of("role", "user", "content", instruction)));

        JsonNode reponse;
        try {
            reponse = restClient.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corps)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.warn("Service d'IA : réponse HTTP {} ({})", e.getStatusCode().value(), e.getStatusText());
            throw new IaIndisponibleException(traduireStatut(e.getStatusCode().value()));
        } catch (RestClientException e) {
            log.warn("Service d'IA injoignable : {}", e.getMessage());
            throw new IaIndisponibleException(
                    "Impossible de joindre le service d'IA (réseau ou délai dépassé). Réessayez plus tard.");
        }

        if (reponse == null) {
            throw new IaIndisponibleException("Le service d'IA a renvoyé une réponse vide.");
        }
        StringBuilder texte = new StringBuilder();
        for (JsonNode bloc : reponse.path("content")) {
            if ("text".equals(bloc.path("type").asText())) {
                texte.append(bloc.path("text").asText());
            }
        }
        if (texte.isEmpty()) {
            throw new IaIndisponibleException("Le service d'IA n'a produit aucun texte (réponse vide).");
        }
        return texte.toString().trim();
    }

    /** Traduit un statut HTTP du service d'IA en message actionnable. */
    private static String traduireStatut(int statut) {
        return switch (statut) {
            case 401, 403 -> "Clé API d'IA refusée : vérifiez la valeur ANTHROPIC_API_KEY côté administrateur.";
            case 429 -> "Quota du service d'IA atteint : réessayez dans quelques minutes.";
            case 400, 404, 413, 422 ->
                    "Requête refusée par le service d'IA : vérifiez le modèle configuré (ANTHROPIC_MODELE).";
            default -> "Service d'IA indisponible (HTTP " + statut + "). Réessayez plus tard.";
        };
    }
}
