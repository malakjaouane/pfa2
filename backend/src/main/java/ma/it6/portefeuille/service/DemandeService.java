package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.*;
import ma.it6.portefeuille.entity.*;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.mapper.InitiativeMapper;
import ma.it6.portefeuille.mapper.UtilisateurMapper;
import ma.it6.portefeuille.repository.DemandeInitiativeRepository;
import ma.it6.portefeuille.repository.EntiteRepository;
import ma.it6.portefeuille.repository.PrioriteRepository;
import ma.it6.portefeuille.repository.StatutDemandeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Workflow des demandes d'initiatives (inspiration ServiceNow SPM) :
 *   Brouillon -> Soumise -> En évaluation -> Acceptée / Refusée.
 * Une demande « Acceptée » peut être transformée en initiative du
 * portefeuille ; la liaison (initiative_liee) est unique, ce qui
 * empêche toute duplication lors d'une seconde transformation.
 */
@Service
@Transactional(readOnly = true)
public class DemandeService {

    public static final String STATUT_BROUILLON = "Brouillon";
    public static final String STATUT_SOUMISE = "Soumise";
    public static final String STATUT_EN_EVALUATION = "En évaluation";
    public static final String STATUT_ACCEPTEE = "Acceptée";
    public static final String STATUT_REFUSEE = "Refusée";

    private final DemandeInitiativeRepository demandeRepository;
    private final StatutDemandeRepository statutDemandeRepository;
    private final EntiteRepository entiteRepository;
    private final PrioriteRepository prioriteRepository;
    private final InitiativeService initiativeService;

    public DemandeService(DemandeInitiativeRepository demandeRepository,
                          StatutDemandeRepository statutDemandeRepository,
                          EntiteRepository entiteRepository,
                          PrioriteRepository prioriteRepository,
                          InitiativeService initiativeService) {
        this.demandeRepository = demandeRepository;
        this.statutDemandeRepository = statutDemandeRepository;
        this.entiteRepository = entiteRepository;
        this.prioriteRepository = prioriteRepository;
        this.initiativeService = initiativeService;
    }

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    /**
     * Liste des demandes, avec recherche libre facultative sur le titre,
     * la description du besoin ou la justification (joker construit ici).
     * Un mot vide ou absent = liste complète.
     */
    public List<DemandeResponse> lister(String recherche) {
        String joker = (recherche == null || recherche.isBlank())
                ? null : "%" + recherche.trim() + "%";
        return demandeRepository.findAllFetch(joker).stream()
                .map(DemandeService::toResponse)
                .toList();
    }

    public DemandeResponse trouver(Long idDemande) {
        return toResponse(getDemande(idDemande));
    }

    // ------------------------------------------------------------------
    // Suppression
    // ------------------------------------------------------------------

    /**
     * Suppression définitive d'une demande — réservée aux titulaires de
     * {@code DEMANDE_EVALUER} (contrôlé par la couche sécurité). Une
     * demande <strong>transformée en initiative</strong> ne peut pas être
     * supprimée : elle a engendré des étapes, un budget et un historique
     * dans le portefeuille.
     */
    @Transactional
    public void supprimer(Long idDemande) {
        DemandeInitiative demande = getDemande(idDemande);
        if (demande.getInitiativeLiee() != null) {
            throw new BadRequestException(
                    "Demande déjà transformée en initiative ("
                            + demande.getInitiativeLiee().getCode()
                            + ") : suppression impossible.");
        }
        demandeRepository.delete(demande);
    }

    // ------------------------------------------------------------------
    // Création / modification (Brouillon)
    // ------------------------------------------------------------------

    /**
     * Création d'une demande au statut « Brouillon ». Le demandeur est
     * l'utilisateur connecté, la date de soumission est la date du jour.
     */
    @Transactional
    public DemandeResponse creer(DemandeRequest request, Utilisateur demandeur) {
        Entite entite = entiteRepository.findById(request.idEntite())
                .orElseThrow(() -> new ResourceNotFoundException("Entité demandeuse introuvable : " + request.idEntite()));
        Priorite priorite = prioriteRepository.findById(request.idPriorite())
                .orElseThrow(() -> new ResourceNotFoundException("Priorité introuvable : " + request.idPriorite()));

        DemandeInitiative demande = DemandeInitiative.builder()
                .titre(request.titre().trim())
                .descriptionBesoin(request.descriptionBesoin())
                .justificationMetier(request.justificationMetier())
                .dateSoumission(LocalDate.now())
                .entite(entite)
                .demandeur(demandeur)
                .priorite(priorite)
                .statut(statut(STATUT_BROUILLON))
                .build();

        return toResponse(demandeRepository.save(demande));
    }

    /**
     * Modification d'une demande — uniquement tant qu'elle est « Brouillon ».
     */
    @Transactional
    public DemandeResponse modifier(Long idDemande, DemandeRequest request) {
        DemandeInitiative demande = getDemande(idDemande);
        exigerStatut(demande, STATUT_BROUILLON, "modifiée");

        demande.setTitre(request.titre().trim());
        demande.setDescriptionBesoin(request.descriptionBesoin());
        demande.setJustificationMetier(request.justificationMetier());
        demande.setEntite(entiteRepository.findById(request.idEntite())
                .orElseThrow(() -> new ResourceNotFoundException("Entité demandeuse introuvable : " + request.idEntite())));
        demande.setPriorite(prioriteRepository.findById(request.idPriorite())
                .orElseThrow(() -> new ResourceNotFoundException("Priorité introuvable : " + request.idPriorite())));

        return toResponse(demandeRepository.save(demande));
    }

    // ------------------------------------------------------------------
    // Workflow
    // ------------------------------------------------------------------

    @Transactional
    public DemandeResponse soumettre(Long idDemande) {
        DemandeInitiative demande = getDemande(idDemande);
        exigerStatut(demande, STATUT_BROUILLON, "soumise");
        demande.setStatut(statut(STATUT_SOUMISE));
        return toResponse(demandeRepository.save(demande));
    }

    @Transactional
    public DemandeResponse evaluer(Long idDemande) {
        DemandeInitiative demande = getDemande(idDemande);
        exigerStatut(demande, STATUT_SOUMISE, "évaluée");
        demande.setStatut(statut(STATUT_EN_EVALUATION));
        return toResponse(demandeRepository.save(demande));
    }

    @Transactional
    public DemandeResponse accepter(Long idDemande) {
        DemandeInitiative demande = getDemande(idDemande);
        exigerStatut(demande, STATUT_EN_EVALUATION, "acceptée");
        demande.setStatut(statut(STATUT_ACCEPTEE));
        return toResponse(demandeRepository.save(demande));
    }

    @Transactional
    public DemandeResponse refuser(Long idDemande, String motifRefus) {
        DemandeInitiative demande = getDemande(idDemande);
        exigerStatut(demande, STATUT_EN_EVALUATION, "refusée");
        demande.setMotifRefus(motifRefus);
        demande.setStatut(statut(STATUT_REFUSEE));
        return toResponse(demandeRepository.save(demande));
    }

    // ------------------------------------------------------------------
    // Transformation en initiative (ServiceNow : « Acceptée -> portefeuille »)
    // ------------------------------------------------------------------

    /**
     * Transforme une demande « Acceptée » en initiative du portefeuille.
     * Le code de l'initiative est généré depuis l'identifiant de la demande
     * (DEM-xxxxxx), garantissant l'unicité. Une demande déjà liée à une
     * initiative ne peut pas être transformée une seconde fois (aucun doublon).
     */
    @Transactional
    public DemandeResponse transformer(Long idDemande) {
        DemandeInitiative demande = getDemande(idDemande);
        exigerStatut(demande, STATUT_ACCEPTEE, "transformée");
        if (demande.getInitiativeLiee() != null) {
            throw new BadRequestException("Cette demande a déjà été transformée en initiative.");
        }

        String code = "DEM-" + String.format("%06d", demande.getIdDemande());
        InitiativeRequest request = new InitiativeRequest(
                code,
                demande.getTitre(),
                demande.getDescriptionBesoin(),
                null, null,
                BigDecimal.ZERO, 0,
                demande.getEntite().getIdEntite(),
                null,
                1L, // statut ignoré : le domaine positionne « À planifier »
                demande.getPriorite().getIdPriorite(),
                null);

        Initiative initiative = initiativeService.creerEnEntite(request);
        demande.setInitiativeLiee(initiative);
        return toResponse(demandeRepository.save(demande));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private StatutDemande statut(String libelle) {
        return statutDemandeRepository.findByLibelle(libelle)
                .orElseThrow(() -> new ResourceNotFoundException("Statut de demande introuvable : " + libelle));
    }

    private DemandeInitiative getDemande(Long idDemande) {
        return demandeRepository.findById(idDemande)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable : " + idDemande));
    }

    private void exigerStatut(DemandeInitiative demande, String attendu, String action) {
        String actuel = demande.getStatut().getLibelle();
        if (!actuel.equals(attendu)) {
            throw new BadRequestException("Une demande « " + actuel
                    + " » ne peut pas être " + action + " (statut requis : « " + attendu + " »).");
        }
    }

    static DemandeResponse toResponse(DemandeInitiative d) {
        if (d == null) {
            return null;
        }
        InitiativeLieeDto liee = d.getInitiativeLiee() == null ? null
                : new InitiativeLieeDto(
                        d.getInitiativeLiee().getIdInitiative(),
                        d.getInitiativeLiee().getCode(),
                        d.getInitiativeLiee().getNom());
        return new DemandeResponse(
                d.getIdDemande(),
                d.getTitre(),
                d.getDescriptionBesoin(),
                d.getJustificationMetier(),
                d.getMotifRefus(),
                d.getDateSoumission(),
                UtilisateurMapper.toEntiteDto(d.getEntite()),
                UtilisateurMapper.toSummary(d.getDemandeur()),
                InitiativeMapper.toPrioriteDto(d.getPriorite()),
                new StatutDemandeDto(
                        d.getStatut().getIdStatutDemande(),
                        d.getStatut().getLibelle(),
                        d.getStatut().getOrdre()),
                liee);
    }
}