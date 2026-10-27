package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.RisqueCritiqueDto;
import ma.it6.portefeuille.dto.RisqueRequest;
import ma.it6.portefeuille.dto.RisqueResponse;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Risque;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.mapper.RisqueMapper;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.RisqueRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Registre des risques d'une initiative : description, probabilité et
 * impact notés de 1 à 3, responsable du plan d'action et plan d'action.
 *
 * <p>Garde-fous, identiques aux étapes :</p>
 * <ul>
 *   <li>initiative inexistante → 404 ;</li>
 *   <li>initiative archivée → 400 sur toute écriture ;</li>
 *   <li>notes bornées 1-3 (validation du contrôleur + contrôle ici) ;</li>
 *   <li>responsable inexistant → 404.</li>
 * </ul>
 *
 * <p>La santé pondérée (score 1-9) est recalculée à chaque lecture par
 * {@link RisqueMapper} — jamais stockée.</p>
 */
@Service
public class RisqueService {

    /** Les plus critiques d'abord, puis ordre d'arrivée. */
    static final Comparator<Risque> PAR_CRITICITE =
            Comparator.comparingInt(RisqueMapper::score).reversed()
                    .thenComparing(r -> r.getIdRisque() == null ? 0L : r.getIdRisque());

    private final RisqueRepository risqueRepository;
    private final InitiativeRepository initiativeRepository;
    private final UtilisateurRepository utilisateurRepository;

    public RisqueService(RisqueRepository risqueRepository,
                         InitiativeRepository initiativeRepository,
                         UtilisateurRepository utilisateurRepository) {
        this.risqueRepository = risqueRepository;
        this.initiativeRepository = initiativeRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    /** Registre d'une initiative, du risque le plus critique au moins critique. */
    @Transactional(readOnly = true)
    public List<RisqueResponse> lister(Long idInitiative) {
        Initiative initiative = verifierInitiative(idInitiative);
        return risqueRepository.findByInitiativeIdInitiative(initiative.getIdInitiative()).stream()
                .sorted(PAR_CRITICITE)
                .map(RisqueMapper::toResponse)
                .toList();
    }

    @Transactional
    public RisqueResponse creer(Long idInitiative, RisqueRequest request) {
        Initiative initiative = verifierInitiative(idInitiative);
        verifierActive(initiative, "ajout de risque refusé.");
        validerNotes(request);

        Risque risque = Risque.builder()
                .description(request.description().trim())
                .probabilite(request.probabilite())
                .impact(request.impact())
                .planAction(normaliser(request.planAction()))
                .initiative(initiative)
                .responsable(responsable(request.idResponsable()))
                .build();
        return RisqueMapper.toResponse(risqueRepository.save(risque));
    }

    @Transactional
    public RisqueResponse modifier(Long idRisque, RisqueRequest request) {
        Risque risque = risqueRepository.findById(idRisque)
                .orElseThrow(() -> new ResourceNotFoundException("Risque introuvable : " + idRisque));
        verifierActive(risque.getInitiative(), "modification de risque refusée.");
        validerNotes(request);

        risque.setDescription(request.description().trim());
        risque.setProbabilite(request.probabilite());
        risque.setImpact(request.impact());
        risque.setPlanAction(normaliser(request.planAction()));
        risque.setResponsable(responsable(request.idResponsable()));
        return RisqueMapper.toResponse(risqueRepository.save(risque));
    }

    @Transactional
    public void supprimer(Long idRisque) {
        Risque risque = risqueRepository.findById(idRisque)
                .orElseThrow(() -> new ResourceNotFoundException("Risque introuvable : " + idRisque));
        verifierActive(risque.getInitiative(), "suppression de risque refusée.");
        risqueRepository.delete(risque);
    }

    /**
     * Risques <b>critiques</b> (score ≥ 6) parmi les initiatives fournies —
     * alimente le widget du tableau de bord. Liste vide si aucune initiative
     * (la clause IN SQL n'aime pas les listes vides).
     */
    @Transactional(readOnly = true)
    public List<RisqueCritiqueDto> listerCritiques(List<Long> idsInitiatives) {
        if (idsInitiatives == null || idsInitiatives.isEmpty()) {
            return List.of();
        }
        return risqueRepository.findByInitiatives(idsInitiatives).stream()
                .filter(r -> RisqueMapper.score(r) >= RisqueMapper.SEUIL_CRITIQUE)
                .sorted(PAR_CRITICITE)
                .map(RisqueMapper::toCritique)
                .toList();
    }

    // ------------------------------------------------------------------
    // Garde-fous
    // ------------------------------------------------------------------

    private Initiative verifierInitiative(Long idInitiative) {
        return initiativeRepository.findById(idInitiative)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Initiative introuvable : " + idInitiative));
    }

    private void verifierActive(Initiative initiative, String motifRefus) {
        if (Boolean.TRUE.equals(initiative.getArchive())) {
            throw new BadRequestException("L'initiative est archivée : " + motifRefus);
        }
    }

    /** Notes bornées 1-3 (la validation du contrôleur répond d'abord en 400). */
    private void validerNotes(RisqueRequest request) {
        if (request.probabilite() == null || request.probabilite() < 1 || request.probabilite() > 3
                || request.impact() == null || request.impact() < 1 || request.impact() > 3) {
            throw new BadRequestException(
                    "Probabilité et impact doivent être notés de 1 à 3 (1 = Faible, 3 = Élevé).");
        }
    }

    private Utilisateur responsable(Long idResponsable) {
        if (idResponsable == null) {
            return null;
        }
        return utilisateurRepository.findById(idResponsable)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Responsable introuvable : " + idResponsable));
    }

    /** Le plan d'action peut être vidé : on stocke alors NULL, pas "". */
    private static String normaliser(String texte) {
        if (texte == null) {
            return null;
        }
        String nettoye = texte.trim();
        return nettoye.isEmpty() ? null : nettoye;
    }
}
