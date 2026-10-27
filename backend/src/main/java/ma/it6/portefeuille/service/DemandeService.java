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
    private final HistoriqueModificationService historiqueService;

    public DemandeService(DemandeInitiativeRepository demandeRepository,
                          StatutDemandeRepository statutDemandeRepository,
                          EntiteRepository entiteRepository,
                          PrioriteRepository prioriteRepository,
                          InitiativeService initiativeService,
                          HistoriqueModificationService historiqueService) {
        this.demandeRepository = demandeRepository;
        this.statutDemandeRepository = statutDemandeRepository;
        this.entiteRepository = entiteRepository;
        this.prioriteRepository = prioriteRepository;
        this.initiativeService = initiativeService;
        this.historiqueService = historiqueService;
    }

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    /**
     * Liste des demandes, avec recherche libre facultative sur le titre,
     * la description du besoin ou la justification (joker construit ici).
     * Un mot vide ou absent = liste complète.
     *
     * <p>Les deux vues de la bascule « Actives | Archives » sont
     * exclusives : {@code archive = true} ne renvoie que les demandes
     * archivées, sinon (paramètre absent ou faux) que les actives.</p>
     */
    public List<DemandeResponse> lister(String recherche, Boolean archive) {
        String joker = (recherche == null || recherche.isBlank())
                ? null : "%" + recherche.trim() + "%";
        boolean vueArchives = Boolean.TRUE.equals(archive);
        return demandeRepository.findAllFetch(joker, vueArchives).stream()
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
        journaliser(demande, HistoriqueModificationService.CHAMP_SUPPRESSION,
                demande.getTitre(), null, "Suppression de la demande");
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

        DemandeInitiative enregistree = demandeRepository.save(demande);
        journaliser(enregistree, HistoriqueModificationService.CHAMP_CREATION,
                null, enregistree.getTitre(), "Création de la demande");
        return toResponse(enregistree);
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
        String ancien = demande.getStatut().getLibelle();
        demande.setStatut(statut(STATUT_SOUMISE));
        DemandeInitiative enregistree = demandeRepository.save(demande);
        journaliser(enregistree, HistoriqueModificationService.CHAMP_STATUT,
                ancien, STATUT_SOUMISE, null);
        return toResponse(enregistree);
    }

    @Transactional
    public DemandeResponse evaluer(Long idDemande) {
        DemandeInitiative demande = getDemande(idDemande);
        exigerStatut(demande, STATUT_SOUMISE, "évaluée");
        String ancien = demande.getStatut().getLibelle();
        demande.setStatut(statut(STATUT_EN_EVALUATION));
        DemandeInitiative enregistree = demandeRepository.save(demande);
        journaliser(enregistree, HistoriqueModificationService.CHAMP_STATUT,
                ancien, STATUT_EN_EVALUATION, null);
        return toResponse(enregistree);
    }

    @Transactional
    public DemandeResponse accepter(Long idDemande) {
        DemandeInitiative demande = getDemande(idDemande);
        exigerStatut(demande, STATUT_EN_EVALUATION, "acceptée");
        String ancien = demande.getStatut().getLibelle();
        demande.setStatut(statut(STATUT_ACCEPTEE));
        DemandeInitiative enregistree = demandeRepository.save(demande);
        journaliser(enregistree, HistoriqueModificationService.CHAMP_STATUT,
                ancien, STATUT_ACCEPTEE, null);
        return toResponse(enregistree);
    }

    /**
     * Refus avec motif : le motif part AUSSI au journal — « pourquoi c'était
     * refusé » reste consultable des mois plus tard, quand bien même la
     * demande est archivée.
     */
    @Transactional
    public DemandeResponse refuser(Long idDemande, String motifRefus) {
        DemandeInitiative demande = getDemande(idDemande);
        exigerStatut(demande, STATUT_EN_EVALUATION, "refusée");
        String ancien = demande.getStatut().getLibelle();
        demande.setMotifRefus(motifRefus);
        demande.setStatut(statut(STATUT_REFUSEE));
        DemandeInitiative enregistree = demandeRepository.save(demande);
        journaliser(enregistree, HistoriqueModificationService.CHAMP_STATUT,
                ancien, STATUT_REFUSEE, "Motif du refus : " + motifRefus);
        return toResponse(enregistree);
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
        DemandeInitiative enregistree = demandeRepository.save(demande);
        journaliser(enregistree, HistoriqueModificationService.CHAMP_TRANSFORMATION,
                null, initiative.getCode(),
                "Transformation en initiative « " + initiative.getNom() + " »");
        return toResponse(enregistree);
    }

    // ------------------------------------------------------------------
    // Archivage (bascule « Actives | Archives »)
    // ------------------------------------------------------------------

    /**
     * Archivage d'une demande : elle quitte la liste active pour la vue
     * « Archives ». Comme pour une initiative (RM14), l'archivage n'est
     * PAS un statut de workflow — le dossier ne change pas. Seules les
     * demandes terminées (« Acceptée » ou « Refusée ») peuvent l'être :
     * une demande en cours ne doit pas disparaître des files d'évaluation.
     * Idempotent : déjà archivée = réponse sans écriture.
     */
    @Transactional
    public DemandeResponse archiver(Long idDemande) {
        DemandeInitiative demande = getDemande(idDemande);
        if (demande.isArchive()) {
            return toResponse(demande);
        }
        String statut = demande.getStatut().getLibelle();
        if (!STATUT_ACCEPTEE.equals(statut) && !STATUT_REFUSEE.equals(statut)) {
            throw new BadRequestException("Seules les demandes « " + STATUT_ACCEPTEE
                    + " » ou « " + STATUT_REFUSEE + " » peuvent être archivées (statut actuel : « "
                    + statut + " »).");
        }
        demande.setArchive(true);
        DemandeInitiative enregistree = demandeRepository.save(demande);
        journaliser(enregistree, HistoriqueModificationService.CHAMP_ARCHIVE,
                "Active", "Archivée", null);
        return toResponse(enregistree);
    }

    /**
     * Retour d'une demande archivée vers la liste active. Aucune
     * condition de statut : l'archivage des demandes est réversible à tout
     * moment (contrairement à la purge des initiatives).
     */
    @Transactional
    public DemandeResponse restaurer(Long idDemande) {
        DemandeInitiative demande = getDemande(idDemande);
        demande.setArchive(false);
        DemandeInitiative enregistree = demandeRepository.save(demande);
        journaliser(enregistree, HistoriqueModificationService.CHAMP_ARCHIVE,
                "Archivée", "Active", null);
        return toResponse(enregistree);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Dépose une ligne d'audit de type {@code demande} dans le journal
     * générique — auteur = utilisateur connecté, aucun lien d'initiative
     * (la demande n'en a un qu'une fois transformée, et ce n'est pas le
     * journal de celle-ci). Écrit dans la transaction de la modification.
     */
    private void journaliser(DemandeInitiative demande,
                             String champ,
                             String ancienne,
                             String nouvelle,
                             String commentaire) {
        historiqueService.ecrire(HistoriqueModificationService.TYPE_DEMANDE,
                demande.getIdDemande(), null, champ, ancienne, nouvelle,
                commentaire, HistoriqueModificationService.auteurCourant(), false);
    }

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
                liee,
                d.isArchive());
    }
}