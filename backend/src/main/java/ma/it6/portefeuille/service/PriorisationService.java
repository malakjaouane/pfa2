package ma.it6.portefeuille.service;

import java.util.Optional;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.security.OrganisationContext;

import static org.springframework.http.HttpStatus.*;

/**
 * Service de priorisation des initiatives (score valeur/effort).
 *
 * <p>Le score est calcule comme suit : {@code score = valeur * 25 / effort}
 * (arrondi a l'entier inferieur), donne un score compris entre 1 et 25.
 * Un score eleve indique une initiative a forte valeur pour un faible effort
 * (prioritaire).</p>
 *
 * <p>L'isolation par organisation est garantie par le filtre Hibernate
 * {@code filtreParOrganisation} (contexte {@link OrganisationContext}).</p>
 */
@Service
@Transactional(readOnly = true)
public class PriorisationService {

    private static final int VALEUR_MIN = 1;
    private static final int VALEUR_MAX = 5;
    private static final int SCORE_MAX = 25;

    private final InitiativeRepository initiativeRepository;

    public PriorisationService(InitiativeRepository initiativeRepository) {
        this.initiativeRepository = initiativeRepository;
    }

    /**
     * Calcule le score de priorisation a partir d'une valeur et d'un effort
     * comprises entre 1 et 5.
     *
     * @param valeur valeur metier (1-5)
     * @param effort effort estime (1-5)
     * @return score compris entre 1 et 25
     */
    public int calculerScore(int valeur, int effort) {
        validerValeur(valeur);
        validerEffort(effort);
        return Math.max(1, Math.min(SCORE_MAX, (valeur * SCORE_MAX) / effort));
    }

    /**
     * Met a jour la valeur, l'effort et le score d'une initiative.
     *
     * @param idInitiative identifiant de l'initiative
     * @param valeur       nouvelle valeur (1-5)
     * @param effort       nouvel effort (1-5)
     * @return l'initiative mise a jour
     */
    @Transactional
    public Initiative mettreAJourPriorisation(Long idInitiative, Integer valeur, Integer effort) {
        if (valeur == null || effort == null) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "valeur et effort sont obligatoires (1-5).");
        }
        validerValeur(valeur);
        validerEffort(effort);

        Optional<Initiative> initiativeOpt = initiativeRepository.findByIdInitiative(idInitiative);
        if (initiativeOpt.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Initiative introuvable : " + idInitiative);
        }

        Initiative initiative = initiativeOpt.get();

        initiative.setValeur(valeur);
        initiative.setEffort(effort);
        initiative.setScorePriorisation(calculerScore(valeur, effort));

        return initiativeRepository.save(initiative);
    }

    /**
     * Recalcule le score de toutes les initiatives de l'organisation courante
     * a partir de leur valeur et effort actuels (les initiatives sans valeur
     * ou effort ne sont pas modifiees).
     */
    @Transactional
    public void recalculerTousLesScores() {
        Long idOrganisation = OrganisationContext.getId();
        if (idOrganisation == null) {
            return; // hors contexte (tests, batch)
        }
        for (Initiative initiative : initiativeRepository.findByIdOrganisation(idOrganisation)) {
            if (initiative.getValeur() != null && initiative.getEffort() != null) {
                initiative.setScorePriorisation(calculerScore(initiative.getValeur(), initiative.getEffort()));
                initiativeRepository.save(initiative);
            }
        }
    }

    private void validerValeur(Integer valeur) {
        if (valeur < VALEUR_MIN || valeur > VALEUR_MAX) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "valeur doit etre comprise entre " + VALEUR_MIN + " et " + VALEUR_MAX + " (recu : " + valeur + ").");
        }
    }

    private void validerEffort(Integer effort) {
        if (effort < VALEUR_MIN || effort > VALEUR_MAX) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "effort doit etre compris entre " + VALEUR_MIN + " et " + VALEUR_MAX + " (recu : " + effort + ").");
        }
    }
}
