package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.EtapeRequest;
import ma.it6.portefeuille.dto.EtapeResponse;
import ma.it6.portefeuille.entity.Etape;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.StatutEtape;
import ma.it6.portefeuille.exception.BadRequestException;
import ma.it6.portefeuille.exception.ResourceNotFoundException;
import ma.it6.portefeuille.mapper.EtapeMapper;
import ma.it6.portefeuille.repository.EtapeRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.StatutEtapeRepository;
import ma.it6.portefeuille.repository.StatutInitiativeRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestion des étapes d'une initiative (RM08 : une étape appartient à une
 * seule initiative).
 *
 * <p>Toute création / modification / suppression d'étape :</p>
 * <ul>
 *   <li>contrôle que les dates restent dans l'intervalle de l'initiative ;</li>
 *   <li>recalcule l'avancement de l'initiative à partir des étapes
 *       ({@link CalculeurAvancement}).</li>
 * </ul>
 */
@Service
public class EtapeService {

    private final EtapeRepository etapeRepository;
    private final InitiativeRepository initiativeRepository;
    private final StatutEtapeRepository statutEtapeRepository;
    private final StatutInitiativeRepository statutInitiativeRepository;
    private final HistoriqueStatutService historiqueStatutService;

    public EtapeService(EtapeRepository etapeRepository,
                        InitiativeRepository initiativeRepository,
                        StatutEtapeRepository statutEtapeRepository,
                        StatutInitiativeRepository statutInitiativeRepository,
                        HistoriqueStatutService historiqueStatutService) {
        this.etapeRepository = etapeRepository;
        this.initiativeRepository = initiativeRepository;
        this.statutEtapeRepository = statutEtapeRepository;
        this.statutInitiativeRepository = statutInitiativeRepository;
        this.historiqueStatutService = historiqueStatutService;
    }

    @Transactional(readOnly = true)
    public List<EtapeResponse> lister(Long idInitiative) {
        verifierInitiative(idInitiative);
        return etapes(idInitiative).stream()
                .map(EtapeMapper::toResponse)
                .toList();
    }

    /**
     * Création d'une étape. L'ordre est automatique (max + 1) s'il n'est pas
     * fourni ; le statut par défaut est « À faire ».
     */
    @Transactional
    public EtapeResponse creer(Long idInitiative, EtapeRequest request) {
        Initiative initiative = verifierInitiative(idInitiative);
        if (Boolean.TRUE.equals(initiative.getArchive())) {
            throw new BadRequestException("L'initiative est archivée : ajout d'étape refusé.");
        }

        int ordre = request.ordre() != null ? request.ordre() : prochainOrdre(idInitiative);
        StatutEtape statut = request.idStatutEtape() != null
                ? statutEtapeRepository.findById(request.idStatutEtape())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Statut d'étape introuvable : " + request.idStatutEtape()))
                : statutEtapeRepository.findAllByOrderByOrdreAsc().stream().findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Aucun statut d'étape disponible."));

        Etape etape = Etape.builder()
                .nom(request.nom().trim())
                .description(request.description())
                .ordre(ordre)
                .dateDebut(request.dateDebut())
                .dateFinPrevue(request.dateFinPrevue())
                .initiative(initiative)
                .statutEtape(statut)
                .build();
        etape.setAvancement(cohererAvancement(request.avancement(), statut));

        validerEtape(etape, initiative);
        Etape sauvegardee = etapeRepository.save(etape);
        recalculerAvancement(initiative);
        return EtapeMapper.toResponse(sauvegardee);
    }

    @Transactional
    public EtapeResponse modifier(Long idEtape, EtapeRequest request) {
        Etape etape = etapeRepository.findById(idEtape)
                .orElseThrow(() -> new ResourceNotFoundException("Étape introuvable : " + idEtape));
        Initiative initiative = etape.getInitiative();
        if (Boolean.TRUE.equals(initiative.getArchive())) {
            throw new BadRequestException("L'initiative est archivée : modification d'étape refusée.");
        }

        StatutEtape statut = etape.getStatutEtape();
        if (request.idStatutEtape() != null) {
            statut = statutEtapeRepository.findById(request.idStatutEtape())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Statut d'étape introuvable : " + request.idStatutEtape()));
        }
        etape.setStatutEtape(statut);

        etape.setNom(request.nom().trim());
        etape.setDescription(request.description());
        etape.setOrdre(request.ordre() != null ? request.ordre() : etape.getOrdre());
        etape.setDateDebut(request.dateDebut());
        etape.setDateFinPrevue(request.dateFinPrevue());
        etape.setAvancement(cohererAvancement(request.avancement(), statut));

        validerEtape(etape, initiative);
        Etape sauvegardee = etapeRepository.save(etape);
        recalculerAvancement(initiative);
        return EtapeMapper.toResponse(sauvegardee);
    }

    /**
     * Suppression d'une étape (RM : une erreur de saisie ne doit pas rester
     * définitive). Les étapes restantes sont renumérotées de 1 à n pour
     * éviter un trou dans l'ordre, puis l'avancement est recalculé.
     */
    @Transactional
    public void supprimer(Long idEtape) {
        Etape etape = etapeRepository.findById(idEtape)
                .orElseThrow(() -> new ResourceNotFoundException("Étape introuvable : " + idEtape));
        Initiative initiative = etape.getInitiative();
        if (Boolean.TRUE.equals(initiative.getArchive())) {
            throw new BadRequestException("L'initiative est archivée : suppression d'étape refusée.");
        }

        etapeRepository.delete(etape);
        renumeroter(initiative.getIdInitiative());
        recalculerAvancement(initiative);
    }

    /**
     * Réordonnancement des étapes : fournit les identifiants dans l'ordre
     * souhaité ; l'ordre est réaffecté de 1 à n. Les étapes absentes de la
     * liste sont placées en fin.
     */
    @Transactional
    public List<EtapeResponse> reordonner(Long idInitiative, List<Long> idsOrdonnees) {
        verifierInitiative(idInitiative);
        if (idsOrdonnees == null || idsOrdonnees.isEmpty()) {
            throw new BadRequestException("La nouvelle liste d'étapes est obligatoire.");
        }
        List<Etape> existantes = etapes(idInitiative);
        Map<Long, Etape> parId = new HashMap<>();
        existantes.forEach(e -> parId.put(e.getIdEtape(), e));

        for (Long id : idsOrdonnees) {
            if (!parId.containsKey(id)) {
                throw new BadRequestException("Étape " + id + " : identifiant inconnu pour cette initiative.");
            }
        }

        List<Etape> ordonnees = new ArrayList<>();
        for (Long id : idsOrdonnees) {
            ordonnees.add(parId.get(id));
        }
        existantes.stream()
                .filter(e -> !idsOrdonnees.contains(e.getIdEtape()))
                .forEach(ordonnees::add);

        int position = 1;
        for (Etape etape : ordonnees) {
            etape.setOrdre(position++);
            etapeRepository.save(etape);
        }
        return ordonnees.stream().map(EtapeMapper::toResponse).toList();
    }

    // ------------------------------------------------------------------
    // Avancement de l'initiative
    // ------------------------------------------------------------------

    /**
     * Recalcule l'avancement de l'initiative à partir de ses étapes.
     * S'il n'y a aucune étape, l'avancement saisi est conservé.
     */
    private void recalculerAvancement(Initiative initiative) {
        int calcule = CalculeurAvancement.calculer(etapes(initiative.getIdInitiative()));
        if (calcule < 0) {
            return;
        }
        String statutActuel = initiative.getStatut() == null
                ? "" : initiative.getStatut().getLibelle();

        // RM07 : une initiative « Terminée » affiche toujours 100 %.
        if (InitiativeService.STATUT_TERMINEE.equals(statutActuel)) {
            initiative.setAvancement(100);
            initiativeRepository.save(initiative);
            return;
        }
        // Une initiative « Annulée » garde son avancement figé.
        if (InitiativeService.STATUT_ANNULEE.equals(statutActuel)) {
            return;
        }

        initiative.setAvancement(calcule);

        // RM07-auto : 100 % atteint -> passage à « Terminée » (transition autorisée).
        boolean passeATerminee = false;
        if (calcule == 100 && InitiativeService.STATUT_EN_COURS.equals(statutActuel)) {
            passeATerminee = statutInitiativeRepository
                    .findByLibelle(InitiativeService.STATUT_TERMINEE)
                    .map(statut -> {
                        initiative.setStatut(statut);
                        return true;
                    })
                    .orElse(false);
        }
        initiativeRepository.save(initiative);

        // Chaque changement de statut est journalisé (CDC §7.2 : tout
        // changement de statut est inscrit dans l'historique), y compris
        // le passage automatique à « Terminée » déclenché par les étapes.
        if (passeATerminee) {
            historiqueStatutService.enregistrer(initiative, statutActuel,
                    InitiativeService.STATUT_TERMINEE,
                    "Passage automatique : toutes les étapes sont terminées",
                    utilisateurCourant());
        }
    }

    /** E-mail de l'utilisateur connecté, ou {@code null} hors contexte de sécurité. */
    private static String utilisateurCourant() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || authentification.getName() == null
                || authentification.getName().isBlank()) {
            return null;
        }
        return authentification.getName();
    }

    /**
     * Cohérence avancement / statut : « Terminée » vaut 100 %, « À faire » vaut 0 %.
     */
    private Integer cohererAvancement(Integer avancement, StatutEtape statut) {
        int valeur = avancement == null ? 0 : avancement;
        String libelle = statut == null ? null : statut.getLibelle();
        if (CalculeurAvancement.ETAPE_TERMINEE.equals(libelle)) {
            return 100;
        }
        if (CalculeurAvancement.ETAPE_A_FAIRE.equals(libelle)) {
            return 0;
        }
        return Math.max(0, Math.min(100, valeur));
    }

    // ------------------------------------------------------------------
    // Validations
    // ------------------------------------------------------------------

    private void validerEtape(Etape etape, Initiative initiative) {
        if (etape.getAvancement() < 0 || etape.getAvancement() > 100) {
            throw new BadRequestException("L'avancement de l'étape doit être compris entre 0 et 100.");
        }
        if (etape.getDateDebut() != null && etape.getDateFinPrevue() != null
                && etape.getDateFinPrevue().isBefore(etape.getDateDebut())) {
            throw new BadRequestException("La date de fin prévue de l'étape ne peut pas être antérieure "
                    + "à sa date de début.");
        }
        verifierIntervalle(initiative, etape.getDateDebut(), etape.getDateFinPrevue());
    }

    /**
     * RM : étape et jalon doivent rester dans l'intervalle de l'initiative.
     */
    private void verifierIntervalle(Initiative initiative, LocalDate debut, LocalDate fin) {
        if (initiative.getDateDebut() != null && debut != null && debut.isBefore(initiative.getDateDebut())) {
            throw new BadRequestException("La date de début de l'étape (" + debut
                    + ") ne peut pas précéder celle de l'initiative (" + initiative.getDateDebut() + ").");
        }
        if (initiative.getDateFinPrevue() != null && fin != null && fin.isAfter(initiative.getDateFinPrevue())) {
            throw new BadRequestException("La date de fin de l'étape (" + fin
                    + ") ne peut pas dépasser celle de l'initiative (" + initiative.getDateFinPrevue() + ").");
        }
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private void renumeroter(Long idInitiative) {
        int position = 1;
        for (Etape etape : etapes(idInitiative)) {
            if (etape.getOrdre() != position) {
                etape.setOrdre(position);
                etapeRepository.save(etape);
            }
            position++;
        }
    }

    private List<Etape> etapes(Long idInitiative) {
        return etapeRepository.findByInitiativeIdInitiativeOrderByOrdreAsc(idInitiative);
    }

    private int prochainOrdre(Long idInitiative) {
        return etapes(idInitiative).stream()
                .mapToInt(Etape::getOrdre)
                .max()
                .orElse(0) + 1;
    }

    private Initiative verifierInitiative(Long idInitiative) {
        return initiativeRepository.findById(idInitiative)
                .orElseThrow(() -> new ResourceNotFoundException("Initiative introuvable : " + idInitiative));
    }
}
