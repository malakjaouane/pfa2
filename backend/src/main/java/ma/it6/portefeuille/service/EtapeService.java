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
                .build();
        Harmonisation harmonisation = harmoniser(request.avancement(), statut);
        etape.setStatutEtape(harmonisation.statut());
        etape.setAvancement(harmonisation.avancement());

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

        StatutEtape statutChoisi = etape.getStatutEtape();
        if (request.idStatutEtape() != null) {
            statutChoisi = statutEtapeRepository.findById(request.idStatutEtape())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Statut d'étape introuvable : " + request.idStatutEtape()));
        }

        etape.setNom(request.nom().trim());
        etape.setDescription(request.description());
        etape.setOrdre(request.ordre() != null ? request.ordre() : etape.getOrdre());
        etape.setDateDebut(request.dateDebut());
        etape.setDateFinPrevue(request.dateFinPrevue());
        Harmonisation harmonisation = harmoniser(request.avancement(), statutChoisi);
        etape.setStatutEtape(harmonisation.statut());
        etape.setAvancement(harmonisation.avancement());

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

        // RM07 : une initiative « Terminée » affiche toujours 100 % — et le
        // reste tant que toutes ses étapes sont terminées. En revanche, si
        // une étape est rouverte (ou ajoutée) et que le recalcul descend sous
        // 100 %, la clôture n'est plus justifiée : l'initiative est
        // réouverte automatiquement, avec trace au journal. « Terminée »
        // n'est donc pas un état figé. La cible suit l'avancement recalculé :
        // 0 % = rien n'a commencé -> « À planifier », sinon -> « En cours ».
        if (InitiativeService.STATUT_TERMINEE.equals(statutActuel)) {
            if (calcule >= 100) {
                // Toujours complet : l'invariant RM07 « Terminée = 100 % » tient.
                initiative.setAvancement(100);
                initiativeRepository.save(initiative);
                return;
            }
            String cible = calcule == 0
                    ? InitiativeService.STATUT_A_PLANIFIER
                    : InitiativeService.STATUT_EN_COURS;
            boolean reouverte = statutInitiativeRepository
                    .findByLibelle(cible)
                    .map(statut -> {
                        initiative.setStatut(statut);
                        return true;
                    })
                    .orElse(false);
            if (!reouverte) {
                // Référentiel incomplet : on ne touche ni au statut ni à 100 %.
                initiative.setAvancement(100);
                initiativeRepository.save(initiative);
                return;
            }
            initiative.setAvancement(calcule);
            initiativeRepository.save(initiative);
            historiqueStatutService.enregistrer(
                    initiative, InitiativeService.STATUT_TERMINEE, cible,
                    "Réouverture automatique : les étapes ne sont plus toutes "
                            + "terminées (avancement recalculé à " + calcule + " %)",
                    utilisateurCourant(), true);
            return;
        }
        // Une initiative « Annulée » garde son avancement figé.
        if (InitiativeService.STATUT_ANNULEE.equals(statutActuel)) {
            return;
        }

        initiative.setAvancement(calcule);

        // RM07-auto : 100 % atteint -> passage à « Terminée », quel que soit
        // l'état de départ (« À planifier », « En cours » ou « En pause »).
        // Inversement, un retour à 0 % signifie que rien n'a commencé :
        // « En cours » redevient « À planifier » (« En pause » est laissé
        // tel quel : c'est un arrêt délibéré). Statuts finaux traités plus haut.
        boolean passeATerminee = false;
        boolean repasseAPlanifier = false;
        if (calcule == 100) {
            passeATerminee = statutInitiativeRepository
                    .findByLibelle(InitiativeService.STATUT_TERMINEE)
                    .map(statut -> {
                        initiative.setStatut(statut);
                        return true;
                    })
                    .orElse(false);
        } else if (calcule == 0 && InitiativeService.STATUT_EN_COURS.equals(statutActuel)) {
            repasseAPlanifier = statutInitiativeRepository
                    .findByLibelle(InitiativeService.STATUT_A_PLANIFIER)
                    .map(statut -> {
                        initiative.setStatut(statut);
                        return true;
                    })
                    .orElse(false);
        }
        initiativeRepository.save(initiative);

        // Chaque changement de statut est journalisé (CDC §7.2 : tout
        // changement de statut est inscrit dans l'historique), y compris
        // les passages automatiques déclenchés par les étapes. L'origine
        // (true) alimente la colonne « automatique » de la page
        // « Historique des statuts ».
        if (passeATerminee) {
            historiqueStatutService.enregistrer(initiative, statutActuel,
                    InitiativeService.STATUT_TERMINEE,
                    "Passage automatique : toutes les étapes sont terminées",
                    utilisateurCourant(), true);
        }
        if (repasseAPlanifier) {
            historiqueStatutService.enregistrer(initiative, statutActuel,
                    InitiativeService.STATUT_A_PLANIFIER,
                    "Retour automatique : avancement à 0 %, l'initiative "
                            + "n'a pas commencé",
                    utilisateurCourant(), true);
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
     * RM18 (double sens) : le statut choisit sa valeur par défaut
     * (« À faire » 0 %, « En cours » 50 %, « Terminée » 100 %) lorsque le
     * champ n'est pas renseigné (absent ou 0) ; inversement, un nombre
     * clairement saisi prime sur le statut : 1-99 -> « En cours »,
     * 100 -> « Terminée ». La paire retournée est toujours cohérente.
     */
    private Harmonisation harmoniser(Integer avancement, StatutEtape statutChoisi) {
        int valeur = avancement == null ? -1 : Math.max(0, Math.min(100, avancement));
        if (valeur >= 1 && valeur <= 99) {
            return new Harmonisation(
                    statutOuReferentiel(statutChoisi, CalculeurAvancement.ETAPE_EN_COURS), valeur);
        }
        if (valeur == 100) {
            return new Harmonisation(
                    statutOuReferentiel(statutChoisi, CalculeurAvancement.ETAPE_TERMINEE), 100);
        }
        // Champ non renseigné (absent ou 0) : la valeur découle du statut choisi.
        String libelle = statutChoisi == null ? null : statutChoisi.getLibelle();
        if (CalculeurAvancement.ETAPE_TERMINEE.equals(libelle)) {
            return new Harmonisation(statutChoisi, 100);
        }
        if (CalculeurAvancement.ETAPE_EN_COURS.equals(libelle)) {
            return new Harmonisation(statutChoisi, 50);
        }
        return new Harmonisation(statutChoisi, 0);
    }

    /** Statut demandé s'il porte déjà le bon libellé, sinon chargement du référentiel. */
    private StatutEtape statutOuReferentiel(StatutEtape choisi, String libelle) {
        if (choisi != null && libelle.equals(choisi.getLibelle())) {
            return choisi;
        }
        return statutEtapeRepository.findAllByOrderByOrdreAsc().stream()
                .filter(s -> libelle.equals(s.getLibelle()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Statut d'étape introuvable : " + libelle));
    }

    /** Paire (statut, avancement) harmonisée selon la règle RM18. */
    private record Harmonisation(StatutEtape statut, Integer avancement) {
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
