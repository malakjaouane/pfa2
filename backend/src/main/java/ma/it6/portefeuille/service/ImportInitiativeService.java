package ma.it6.portefeuille.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import ma.it6.portefeuille.dto.ImportResultatDTO;
import ma.it6.portefeuille.dto.ImportResultatDTO.ErreurLigneDTO;
import ma.it6.portefeuille.entity.Entite;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.ObjectifStrategique;
import ma.it6.portefeuille.entity.Organisation;
import ma.it6.portefeuille.entity.Plan;
import ma.it6.portefeuille.entity.Priorite;
import ma.it6.portefeuille.entity.StatutInitiative;
import ma.it6.portefeuille.entity.Utilisateur;
import ma.it6.portefeuille.repository.EntiteRepository;
import ma.it6.portefeuille.repository.InitiativeRepository;
import ma.it6.portefeuille.repository.ObjectifStrategiqueRepository;
import ma.it6.portefeuille.repository.OrganisationRepository;
import ma.it6.portefeuille.repository.PlanRepository;
import ma.it6.portefeuille.repository.PrioriteRepository;
import ma.it6.portefeuille.repository.StatutInitiativeRepository;
import ma.it6.portefeuille.repository.UtilisateurRepository;
import ma.it6.portefeuille.security.OrganisationContext;

import static org.springframework.http.HttpStatus.*;

/**
 * Import d'initiatives depuis un fichier CSV (séparateur point-virgule) ou
 * Excel (.xlsx).
 *
 * <p>Règles :</p>
 * <ul>
 *   <li>première ligne = en-têtes (noms de colonnes) ;</li>
 *   <li>colonnes requises : {@code code, nom, statut, priorite, responsable}</li>
 *   <li>colonnes optionnelles : {@code description, dateDebut, dateFinPrevue,
 *       budget, entite, objectif}</li>
 *   <li>mode strict : une seule ligne en erreur rejette l'import entier</li>
 *   <li>mode « ignorer les erreurs » : seules les lignes valides sont
 *       importées, les erreurs sont rapportées ligne par ligne</li>
 *   <li>rien n'est importé si la limite du plan ({@code nb_initiatives_max})
 *       serait dépassée</li>
 *   <li>tout est isolé par organisation (contexte {@link OrganisationContext})</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ImportInitiativeService {

    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Set<String> EXTENSIONS_CSV = Set.of("csv");
    private static final Set<String> EXTENSIONS_EXCEL = Set.of("xlsx", "xls");

    private static final String[] COLONNES_REQUISES = {
        "code", "nom", "statut", "priorite", "responsable"
    };

    private static final String[] COLONNES_OPTIONNELLES = {
        "description", "dateDebut", "dateFinPrevue", "budget", "entite", "objectif"
    };

    private final InitiativeRepository initiativeRepository;
    private final OrganisationRepository organisationRepository;
    private final PlanRepository planRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EntiteRepository entiteRepository;
    private final StatutInitiativeRepository statutRepository;
    private final PrioriteRepository prioriteRepository;
    private final ObjectifStrategiqueRepository objectifRepository;
    private final long tailleMaxOctets;

    public ImportInitiativeService(InitiativeRepository initiativeRepository,
                                   OrganisationRepository organisationRepository,
                                   PlanRepository planRepository,
                                   UtilisateurRepository utilisateurRepository,
                                   EntiteRepository entiteRepository,
                                   StatutInitiativeRepository statutRepository,
                                   PrioriteRepository prioriteRepository,
                                   ObjectifStrategiqueRepository objectifRepository,
                                   @Value("${spring.servlet.multipart.max-file-size:10MB}") String tailleMax) {
        this.initiativeRepository = initiativeRepository;
        this.organisationRepository = organisationRepository;
        this.planRepository = planRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.entiteRepository = entiteRepository;
        this.statutRepository = statutRepository;
        this.prioriteRepository = prioriteRepository;
        this.objectifRepository = objectifRepository;
        this.tailleMaxOctets = parseTaille(tailleMax);
    }

    /**
     * Importe un fichier CSV ou Excel.
     *
     * @param fichier           fichier à importer (non vide)
     * @param ignorerErreurs    si true, les lignes en erreur sont sautées
     *                          et les lignes valides importées ; sinon toute
     *                          erreur rejette l'import entier
     * @return résultat détaillé (lignes importées, erreurs ligne par ligne)
     */
    @Transactional
    public ImportResultatDTO importer(MultipartFile fichier, boolean ignorerErreurs) {
        validerFichier(fichier);

        List<String[]> lignes;
        try {
            lignes = lireLignes(fichier);
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Impossible de lire le fichier : " + e.getMessage());
        }
        if (lignes.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Le fichier ne contient aucune ligne.");
        }

        String[] entete = lignes.get(0);
        Map<String, Integer> index = mapperEntete(entete);
        validerColonnesRequises(index);

        // Référentiels de l'organisation courante (chargés une fois).
        Long idOrganisation = OrganisationContext.getId();
        if (idOrganisation == null) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR,
                    "Aucune organisation dans le contexte de requête.");
        }
        Organisation organisation = organisationRepository.findById(idOrganisation)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Organisation introuvable"));
        Plan plan = planRepository.findByCode(organisation.getPlan());
        if (plan == null) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR,
                    "Plan de l'organisation introuvable : " + organisation.getPlan());
        }

        Map<String, StatutInitiative> statuts = chargerStatuts();
        Map<String, Priorite> priorites = chargerPriorites();
        Map<String, Entite> entites = chargerEntites();
        Map<String, ObjectifStrategique> objectifs = chargerObjectifs();
        Map<String, Utilisateur> utilisateurs = chargerUtilisateurs();

        long countExistant = initiativeRepository.countByIdOrganisation(idOrganisation);
        long nbInitiativesMax = plan.getNbInitiativesMax();

        List<ErreurLigneDTO> erreurs = new ArrayList<>();
        Set<String> codesVus = new HashSet<>();
        List<Initiative> aImporter = new ArrayList<>();
        int lignesImportees = 0;
        int lignesIgnorees = 0;

        for (int i = 1; i < lignes.size(); i++) {
            String[] valeurs = lignes.get(i);
            int numeroLigne = i + 1; // en-tête = ligne 1

            if (ligneVide(valeurs)) {
                continue;
            }

            String code = valeur(index, valeurs, "code");
            String nom = valeur(index, valeurs, "nom");
            String description = valeur(index, valeurs, "description");
            String dateDebutS = valeur(index, valeurs, "dateDebut");
            String dateFinPrevueS = valeur(index, valeurs, "dateFinPrevue");
            String budgetS = valeur(index, valeurs, "budget");
            String responsableEmail = valeur(index, valeurs, "responsable");
            String statutLibelle = valeur(index, valeurs, "statut");
            String prioriteLibelle = valeur(index, valeurs, "priorite");
            String entiteNom = valeur(index, valeurs, "entite");
            String objectifCode = valeur(index, valeurs, "objectif");

            List<String> erreursLigne = validerLigne(numeroLigne, code, nom, description, dateDebutS,
                    dateFinPrevueS, budgetS, responsableEmail, statutLibelle, prioriteLibelle,
                    entiteNom, objectifCode, codesVus, statuts, priorites, entites, objectifs, utilisateurs);

            if (!erreursLigne.isEmpty()) {
                for (String msg : erreursLigne) {
                    erreurs.add(new ErreurLigneDTO(numeroLigne, msg));
                }
                lignesIgnorees++;
                continue; // cette ligne ne sera pas importee, quel que soit le mode
            }

            // Construire l'initiative (non persistée encore).
            Initiative initiative = construireInitiative(code, nom, description, dateDebutS, dateFinPrevueS,
                    budgetS, responsableEmail, statutLibelle, prioriteLibelle, entiteNom, objectifCode,
                    statuts, priorites, entites, objectifs, utilisateurs);
            aImporter.add(initiative);
            lignesImportees++;
        }

        // En mode strict, toute erreur rejette l'import (rien n'est persiste).
        // Le DTO est retourne avec les erreurs ; le code HTTP est geré par
        // le controleur (400 si strict + erreurs, 201 sinon).
        boolean importe = erreurs.isEmpty() || ignorerErreurs;

        // Garde-fou : limite du plan (verifie seulement si on importe).
        if (importe) {
            long nbATransporter = aImporter.size();
            if (countExistant + nbATransporter > nbInitiativesMax) {
                throw new ResponseStatusException(BAD_REQUEST,
                        "Limite du plan dépassee : " + countExistant + " initiatives existantes + "
                                + nbATransporter + " a importer > " + nbInitiativesMax + " autorisees.");
            }

            for (Initiative initiative : aImporter) {
                initiativeRepository.save(initiative);
            }
        }

        return ImportResultatDTO.builder()
                .totalLignes(lignes.size() - 1)
                .lignesImportees(importe ? lignesImportees : 0)
                .lignesIgnorees(lignesIgnorees)
                .erreurs(erreurs)
                .mode(ignorerErreurs ? "ignorer_erreurs" : "strict")
                .build();
    }

    // ------------------------------------------------------------------
    // Lecture du fichier
    // ------------------------------------------------------------------

    private void validerFichier(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Aucun fichier fourni.");
        }
        if (fichier.getSize() > tailleMaxOctets) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Fichier trop lourd (" + fichier.getSize() + " octets, max " + tailleMaxOctets + ").");
        }
        String nom = fichier.getOriginalFilename();
        if (nom == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Nom de fichier manquant.");
        }
        String ext = nom.substring(nom.lastIndexOf('.') + 1).toLowerCase();
        if (!EXTENSIONS_CSV.contains(ext) && !EXTENSIONS_EXCEL.contains(ext)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Extension non supportee : " + ext + " (attendu : csv, xlsx, xls).");
        }
    }

    private List<String[]> lireLignes(MultipartFile fichier) throws IOException {
        String nom = fichier.getOriginalFilename().toLowerCase();
        if (nom.endsWith(".csv")) {
            return lireLignesCSV(fichier);
        }
        return lireLignesExcel(fichier);
    }

    private List<String[]> lireLignesCSV(MultipartFile fichier) throws IOException {
        List<String[]> lignes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(fichier.getInputStream(), StandardCharsets.UTF_8))) {
            String ligne;
            while ((ligne = reader.readLine()) != null) {
                if (ligne.isBlank()) {
                    continue;
                }
                // Supprimer le BOM UTF-8 sur la première ligne si present.
                if (lignes.isEmpty() && ligne.startsWith("\uFEFF")) {
                    ligne = ligne.substring(1);
                }
                lignes.add(ligne.split(";", -1));
            }
        }
        return lignes;
    }

    private List<String[]> lireLignesExcel(MultipartFile fichier) throws IOException {
        List<String[]> lignes = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(fichier.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int maxColonnes = 0;
            for (Row row : sheet) {
                if (row.getLastCellNum() > maxColonnes) {
                    maxColonnes = row.getLastCellNum();
                }
                String[] valeurs = new String[maxColonnes];
                boolean vide = true;
                for (int c = 0; c < maxColonnes; c++) {
                    Row rowFinal = row; // effective final pour lambda
                    org.apache.poi.ss.usermodel.Cell cell = rowFinal.getCell(c, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String valeur = cell == null ? "" : formatter.formatCellValue(cell).trim();
                    valeurs[c] = valeur;
                    if (!valeur.isEmpty()) {
                        vide = false;
                    }
                }
                if (!vide) {
                    lignes.add(valeurs);
                }
            }
        }
        return lignes;
    }

    private boolean ligneVide(String[] valeurs) {
        for (String v : valeurs) {
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Mapping des colonnes
    // ------------------------------------------------------------------

    private Map<String, Integer> mapperEntete(String[] entete) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < entete.length; i++) {
            String nom = entete[i] == null ? "" : entete[i].trim().toLowerCase();
            if (!nom.isEmpty()) {
                index.put(nom, i);
            }
        }
        return index;
    }

    private void validerColonnesRequises(Map<String, Integer> index) {
        for (String colonne : COLONNES_REQUISES) {
            if (!index.containsKey(colonne)) {
                throw new ResponseStatusException(BAD_REQUEST,
                        "Colonne manquante dans l'en-tete : " + colonne);
            }
        }
    }

    private String valeur(Map<String, Integer> index, String[] valeurs, String colonne) {
        Integer idx = index.get(colonne);
        if (idx == null || idx >= valeurs.length) {
            return "";
        }
        return valeurs[idx] == null ? "" : valeurs[idx].trim();
    }

    // ------------------------------------------------------------------
    // Validation ligne par ligne
    // ------------------------------------------------------------------

    private List<String> validerLigne(int numeroLigne, String code, String nom, String description,
                                      String dateDebutS, String dateFinPrevueS, String budgetS,
                                      String responsableEmail, String statutLibelle, String prioriteLibelle,
                                      String entiteNom, String objectifCode,
                                      Set<String> codesVus,
                                      Map<String, StatutInitiative> statuts,
                                      Map<String, Priorite> priorites,
                                      Map<String, Entite> entites,
                                      Map<String, ObjectifStrategique> objectifs,
                                      Map<String, Utilisateur> utilisateurs) {

        List<String> erreurs = new ArrayList<>();

        if (code.isEmpty()) {
            erreurs.add("code est obligatoire.");
        } else {
            if (codesVus.contains(code)) {
                erreurs.add("code duplique dans le fichier : " + code);
            } else {
                codesVus.add(code);
            }
            if (initiativeRepository.existsByCode(code)) {
                erreurs.add("code deja existant : " + code);
            }
        }
        if (nom.isEmpty()) {
            erreurs.add("nom est obligatoire.");
        }

        LocalDate dateDebut = null;
        if (!dateDebutS.isEmpty()) {
            try {
                dateDebut = LocalDate.parse(dateDebutS, FORMAT_DATE);
            } catch (Exception e) {
                erreurs.add("dateDebut invalide (attendu yyyy-MM-dd) : " + dateDebutS);
            }
        }
        LocalDate dateFinPrevue = null;
        if (!dateFinPrevueS.isEmpty()) {
            try {
                dateFinPrevue = LocalDate.parse(dateFinPrevueS, FORMAT_DATE);
            } catch (Exception e) {
                erreurs.add("dateFinPrevue invalide (attendu yyyy-MM-dd) : " + dateFinPrevueS);
            }
        }
        if (dateDebut != null && dateFinPrevue != null && dateFinPrevue.isBefore(dateDebut)) {
            erreurs.add("dateFinPrevue antérieure à dateDebut.");
        }

        BigDecimal budget = BigDecimal.ZERO;
        if (!budgetS.isEmpty()) {
            try {
                budget = new BigDecimal(budgetS);
                if (budget.compareTo(BigDecimal.ZERO) < 0) {
                    erreurs.add("budget ne peut pas être négatif : " + budgetS);
                }
            } catch (NumberFormatException e) {
                erreurs.add("budget invalide (nombre attendu) : " + budgetS);
            }
        }

        Utilisateur responsable = null;
        if (responsableEmail.isEmpty()) {
            erreurs.add("responsable est obligatoire (email).");
        } else {
            responsable = utilisateurs.get(responsableEmail.toLowerCase());
            if (responsable == null) {
                erreurs.add("responsable introuvable dans l'organisation : " + responsableEmail);
            } else if (!responsable.getActif()) {
                erreurs.add("responsable desactive : " + responsableEmail);
            }
        }

        StatutInitiative statut = statuts.get(statutLibelle.toLowerCase());
        if (statutLibelle.isEmpty()) {
            erreurs.add("statut est obligatoire.");
        } else if (statut == null) {
            erreurs.add("statut inconnu : " + statutLibelle);
        }

        Priorite priorite = priorites.get(prioriteLibelle.toLowerCase());
        if (prioriteLibelle.isEmpty()) {
            erreurs.add("priorite est obligatoire.");
        } else if (priorite == null) {
            erreurs.add("priorite inconnue : " + prioriteLibelle);
        }

        Entite entite = null;
        if (!entiteNom.isEmpty()) {
            entite = entites.get(entiteNom.toLowerCase());
            if (entite == null) {
                erreurs.add("entite inconnue : " + entiteNom);
            }
        }

        ObjectifStrategique objectif = null;
        if (!objectifCode.isEmpty()) {
            objectif = objectifs.get(objectifCode.toLowerCase());
            if (objectif == null) {
                erreurs.add("objectif inconnu : " + objectifCode);
            }
        }

        return erreurs;
    }

    // ------------------------------------------------------------------
    // Construction de l'entite
    // ------------------------------------------------------------------

    private Initiative construireInitiative(String code, String nom, String description,
                                            String dateDebutS, String dateFinPrevueS,
                                            String budgetS, String responsableEmail,
                                            String statutLibelle, String prioriteLibelle,
                                            String entiteNom, String objectifCode,
                                            Map<String, StatutInitiative> statuts,
                                            Map<String, Priorite> priorites,
                                            Map<String, Entite> entites,
                                            Map<String, ObjectifStrategique> objectifs,
                                            Map<String, Utilisateur> utilisateurs) {

        LocalDate dateDebut = parseOptionnel(dateDebutS);
        LocalDate dateFinPrevue = parseOptionnel(dateFinPrevueS);
        BigDecimal budget = parseOptionnel(budgetS, BigDecimal.ZERO);

        return Initiative.builder()
                .code(code)
                .nom(nom)
                .description(description.isEmpty() ? null : description)
                .dateDebut(dateDebut)
                .dateFinPrevue(dateFinPrevue)
                .budget(budget)
                .avancement(0)
                .archive(false)
                .statut(statuts.get(statutLibelle.toLowerCase()))
                .priorite(priorites.get(prioriteLibelle.toLowerCase()))
                .responsable(utilisateurs.get(responsableEmail.toLowerCase()))
                .entite(entiteNom.isEmpty() ? null : entites.get(entiteNom.toLowerCase()))
                .objectif(objectifCode.isEmpty() ? null : objectifs.get(objectifCode.toLowerCase()))
                .build();
    }

    private LocalDate parseOptionnel(String valeur) {
        if (valeur == null || valeur.isEmpty()) {
            return null;
        }
        return LocalDate.parse(valeur, FORMAT_DATE);
    }

    private BigDecimal parseOptionnel(String valeur, BigDecimal defaut) {
        if (valeur == null || valeur.isEmpty()) {
            return defaut;
        }
        return new BigDecimal(valeur);
    }

    // ------------------------------------------------------------------
    // Chargement des référentiels (cache en mémoire)
    // ------------------------------------------------------------------

    private Map<String, StatutInitiative> chargerStatuts() {
        Map<String, StatutInitiative> map = new HashMap<>();
        statutRepository.findAll().forEach(s -> map.put(s.getLibelle().toLowerCase(), s));
        return map;
    }

    private Map<String, Priorite> chargerPriorites() {
        Map<String, Priorite> map = new HashMap<>();
        prioriteRepository.findAll().forEach(p -> map.put(p.getLibelle().toLowerCase(), p));
        return map;
    }

    private Map<String, Entite> chargerEntites() {
        Map<String, Entite> map = new HashMap<>();
        entiteRepository.findAll().forEach(e -> map.put(e.getNom().toLowerCase(), e));
        return map;
    }

    private Map<String, ObjectifStrategique> chargerObjectifs() {
        Map<String, ObjectifStrategique> map = new HashMap<>();
        objectifRepository.findAll().forEach(o -> map.put(o.getCode().toLowerCase(), o));
        return map;
    }

    private Map<String, Utilisateur> chargerUtilisateurs() {
        Map<String, Utilisateur> map = new HashMap<>();
        utilisateurRepository.findAll().forEach(u -> map.put(u.getEmail().toLowerCase(), u));
        return map;
    }

    private long parseTaille(String taille) {
        try {
            String s = taille.trim().toUpperCase();
            long multiplicateur = 1;
            if (s.endsWith("KB")) {
                multiplicateur = 1024;
                s = s.substring(0, s.length() - 2);
            } else if (s.endsWith("MB")) {
                multiplicateur = 1024 * 1024;
                s = s.substring(0, s.length() - 2);
            } else if (s.endsWith("GB")) {
                multiplicateur = 1024L * 1024 * 1024;
                s = s.substring(0, s.length() - 2);
            }
            return Long.parseLong(s) * multiplicateur;
        } catch (Exception e) {
            return 10L * 1024 * 1024; // 10 Mo par défaut
        }
    }
}
