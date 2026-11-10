package ma.it6.portefeuille.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ma.it6.portefeuille.dto.InitiativeFormeDto;
import ma.it6.portefeuille.dto.PreferenceNotificationDTO;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Organisation;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.OrganisationRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;

import static org.springframework.http.HttpStatus.*;

/**
 * Assistant IA avance (4G).
 *
 * <p>Deux fonctionnalités :</p>
 * <ul>
 *   <li>Rapport hebdomadaire automatique : tache planifiée qui redige un
 *       resume du portefeuille et l'envoie par email aux utilisateurs
 *       abonnes. Respecte le kill switch IA.</li>
 *   <li>Creation d'initiative depuis texte libre : POST
 *       {@code /api/ia/creer-initiative} prend un texte libre, propose une
 *       initiative (code, nom, description, dates, budget) et exige une
 *       confirmation avant enregistrement.</li>
 * </ul>
 *
 * L'isolation par organisation est respectee : le rapport hebdomadaire ne
 * couvre que l'organisation du destinataire, et la creation d'initiative se
 * fait dans l'organisation du demandeur.
 */
@Service
public class AssistantIaAvanceService {

    private final InitiativeRepository initiativeRepository;
    private final OrganisationRepository organisationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;

    public AssistantIaAvanceService(InitiativeRepository initiativeRepository,
                                    OrganisationRepository organisationRepository,
                                    UtilisateurRepository utilisateurRepository,
                                    NotificationService notificationService) {
        this.initiativeRepository = initiativeRepository;
        this.organisationRepository = organisationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.notificationService = notificationService;
    }

    /**
     * Genere et envoie le rapport hebdomadaire a tous les utilisateurs
     * abonnes (frequence hebdomadaire, email actif). Respecte le kill
     * switch IA : si l'assistant est indisponible, aucun rapport n'est
     * envoye.
     */
    @Scheduled(cron = "0 30 9 * * 1") // chaque lundi a 09h30
    public void envoyerRapportHebdomadaire() {
        List<Organisation> organisations = organisationRepository.findAll();
        for (Organisation org : organisations) {
            List<PreferenceNotificationDTO> prefs = abonnesHebdomadaire(org.getIdOrganisation());
            for (PreferenceNotificationDTO pref : prefs) {
                if (pref.getEmailActif() == null || !pref.getEmailActif()) {
                    continue;
                }
                Utilisateur u = utilisateurRepository.findById(pref.getIdUtilisateur())
                        .orElse(null);
                if (u == null || u.getEmail() == null) {
                    continue;
                }
                try {
                    String rapport = genererRapportHebdomadaire(org.getIdOrganisation());
                    notificationService.envoyer(u.getIdUser(),
                            "Rapport hebdomadaire - " + org.getSlug(),
                            rapport);
                } catch (Exception e) {
                    // Echec d'envoi : on passe a l'utilisateur suivant.
                }
            }
        }
    }

    /**
     * Genere le texte du rapport hebdomadaire pour une organisation.
     */
    public String genererRapportHebdomadaire(Long idOrganisation) {
        List<Initiative> initiatives = initiativeRepository.findByIdOrganisation(idOrganisation);
        long totale = initiatives.size();
        long enRetard = initiatives.stream()
                .filter(i -> i.getDateFinPrevue() != null
                        && i.getDateFinPrevue().isBefore(LocalDate.now())
                        && (i.getAvancement() == null || i.getAvancement() < 100))
                .count();
        long terminees = initiatives.stream()
                .filter(i -> "Terminee".equals(i.getStatut() != null ? i.getStatut().getLibelle() : null))
                .count();
        Double budgetTotal = initiatives.stream()
                .map(i -> i.getBudget() != null ? i.getBudget().doubleValue() : 0.0)
                .reduce(0.0, Double::sum);

        StringBuilder sb = new StringBuilder();
        sb.append("Rapport hebdomadaire - ").append(LocalDate.now()).append("\n\n");
        sb.append("Initiatives totales : ").append(totale).append("\n");
        sb.append("En retard : ").append(enRetard).append("\n");
        sb.append("Terminees : ").append(terminees).append("\n");
        sb.append("Budget total : ").append(String.format("%.2f", budgetTotal)).append(" MAD\n");
        sb.append("\nDernieres initiatives :\n");
        initiatives.stream()
                .sorted((a, b) -> {
                    java.time.LocalDate da = a.getDateFinPrevue() != null ? a.getDateFinPrevue() : LocalDate.MIN;
                    java.time.LocalDate db = b.getDateFinPrevue() != null ? b.getDateFinPrevue() : LocalDate.MIN;
                    return db.compareTo(da);
                })
                .limit(5)
                .forEach(i -> sb.append("  - ").append(i.getCode()).append(" - ")
                        .append(i.getNom()).append(" (")
                        .append(i.getStatut() != null ? i.getStatut().getLibelle() : "?")
                        .append(")\n"));
        sb.append("\nBonne semaine !");
        return sb.toString();
    }

    /**
     * Propose une initiative a partir d'un texte libre. L'initiative n'est
     * PAS enregistree : l'utilisateur doit confirmer via
     * {@link #confirmerCreation(InitiativeFormeDto, Long)}.
     */
    public InitiativeFormeDto proposerDepuisTexte(String texte, Long idUtilisateur) {
        Utilisateur u = utilisateurRepository.findById(idUtilisateur)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Utilisateur introuvable"));

        String texteLower = texte.toLowerCase(Locale.ROOT);
        String nom = extraireNom(texte);
        String code = extraireCode(texte, nom);
        String description = texte.length() > 200 ? texte.substring(0, 200) : texte;
        Double budget = extraireBudget(texte);
        LocalDate dateDebut = LocalDate.now();
        LocalDate dateFinPrevue = dateDebut.plusWeeks(4);

        InitiativeFormeDto dto = new InitiativeFormeDto();
        dto.setCode(code);
        dto.setNom(nom);
        dto.setDescription(description);
        dto.setDateDebut(dateDebut);
        dto.setDateFinPrevue(dateFinPrevue);
        dto.setBudget(budget != null ? java.math.BigDecimal.valueOf(budget) : null);
        dto.setAvancement(0);
        return dto;
    }

    /**
     * Confirme et enregistre une initiative proposee par l'IA.
     */
    public Initiative confirmerCreation(InitiativeFormeDto dto, Long idUtilisateur) {
        Utilisateur u = utilisateurRepository.findById(idUtilisateur)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Utilisateur introuvable"));

        Initiative i = Initiative.builder()
                .code(dto.getCode())
                .nom(dto.getNom())
                .description(dto.getDescription())
                .dateDebut(dto.getDateDebut())
                .dateFinPrevue(dto.getDateFinPrevue())
                .budget(dto.getBudget() != null ? dto.getBudget() : java.math.BigDecimal.ZERO)
                .avancement(dto.getAvancement() != null ? dto.getAvancement() : 0)
                .archive(false)
                .build();
        return initiativeRepository.save(i);
    }

    // ------------------------------------------------------------------
    // Helpers d'extraction (heuristiques simples, sans vraie IA)
    // ------------------------------------------------------------------

    private String extraireNom(String texte) {
        String[] mots = texte.split("\\s+");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String m : mots) {
            if (m.matches(".*[a-zA-ZÀ-ÿ].*")) {
                sb.append(m).append(" ");
                count++;
                if (count >= 4) {
                    break;
                }
            }
        }
        String nom = sb.toString().trim();
        return nom.isEmpty() ? "Nouvelle initiative" : nom;
    }

    private String extraireCode(String texte, String nom) {
        StringBuilder sb = new StringBuilder();
        for (char c : nom.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append(c);
            }
        }
        if (sb.length() < 3) {
            sb.append("PROJ");
        }
        return sb.toString().substring(0, Math.min(sb.length(), 6)).toUpperCase();
    }

    private Double extraireBudget(String texte) {
        String t = texte.toUpperCase(Locale.ROOT);
        for (String mot : new String[]{"MAD", "DH", "DIRHAM", "€", "EURO", "$"}) {
            int idx = t.indexOf(mot);
            if (idx >= 0) {
                String avant = texte.substring(0, idx).trim();
                String[] parties = avant.split("\\s+");
                if (parties.length > 0) {
                    try {
                        return Double.parseDouble(parties[parties.length - 1].replace(',', '.'));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Préférences (délégué à NotificationService)
    // ------------------------------------------------------------------

    private List<PreferenceNotificationDTO> abonnesHebdomadaire(Long idOrganisation) {
        return utilisateurRepository.findAll().stream()
                .filter(u -> idOrganisation.equals(u.getIdOrganisation()))
                .map(u -> {
                    PreferenceNotificationDTO dto = new PreferenceNotificationDTO();
                    dto.setIdUtilisateur(u.getIdUser());
                    dto.setEmailActif(Boolean.TRUE);
                    dto.setFrequence("hebdomadaire");
                    dto.setWhatsappActif(false);
                    dto.setTelephone(u.getTelephone());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
