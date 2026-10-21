package ma.it6.portefeuille.service;

import ma.it6.portefeuille.dto.SanteDto;
import ma.it6.portefeuille.entity.Initiative;
import ma.it6.portefeuille.entity.Jalon;
import ma.it6.portefeuille.entity.StatutInitiative;
import ma.it6.portefeuille.entity.StatutJalon;
import ma.it6.portefeuille.repository.JalonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * Score de santé automatique : règles déterministes (feu vert / orange /
 * rouge) et chargement en lot des jalons (aucun N+1).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CalculeurSante — feu vert, orange ou rouge, avec motif")
class CalculeurSanteTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);

    @Mock
    private JalonRepository jalonRepository;

    @InjectMocks
    private CalculeurSante calculeur;

    // ------------------------------------------------------------------
    // Fabriques
    // ------------------------------------------------------------------

    private static Initiative initiative(String libelleStatut, int avancement,
                                         LocalDate debut, LocalDate fin) {
        return Initiative.builder()
                .idInitiative(1L).code("TST-001").nom("Initiative test")
                .archive(false)
                .statut(libelleStatut == null ? null
                        : StatutInitiative.builder().libelle(libelleStatut).build())
                .avancement(avancement)
                .dateDebut(debut).dateFinPrevue(fin)
                .build();
    }

    private static Jalon jalon(String nom, LocalDate datePrevue, String statutJalon) {
        return Jalon.builder()
                .nom(nom).datePrevue(datePrevue)
                .statutJalon(statutJalon == null ? null
                        : StatutJalon.builder().libelle(statutJalon).build())
                .build();
    }

    private static SanteDto evaluer(Initiative i, Jalon... jalons) {
        return CalculeurSante.evaluer(i, List.of(jalons), JOUR);
    }

    // ------------------------------------------------------------------
    // Règles
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Règles — premier signal négatif qui gagne")
    class Regles {

        @Test
        @DisplayName("archivée : sans objet (null), aucun calcul")
        void archivee() {
            Initiative i = initiative("En cours", 50, JOUR.minusDays(10), JOUR.plusDays(10));
            i.setArchive(true);

            assertThat(evaluer(i)).isNull();
        }

        @Test
        @DisplayName("terminée : feu vert « initiative terminée », même avec d'anciens retards")
        void terminee() {
            Initiative i = initiative("Terminée", 100, JOUR.minusDays(90), JOUR.minusDays(1));

            SanteDto s = evaluer(i, jalon("Ancien", JOUR.minusDays(10), "À atteindre"));

            assertThat(s.niveau()).isEqualTo(SanteDto.VERTE);
            assertThat(s.raison()).isEqualTo("terminee");
        }

        @Test
        @DisplayName("date butoir dépassée : rouge, motif finDepassee avec le nombre de jours")
        void finDepassee() {
            Initiative i = initiative("En cours", 60, JOUR.minusDays(90), JOUR.minusDays(3));

            SanteDto s = evaluer(i);

            assertThat(s.niveau()).isEqualTo(SanteDto.ROUGE);
            assertThat(s.raison()).isEqualTo("finDepassee");
            assertThat(s.parametres()).containsEntry("n", 3L);
        }

        @Test
        @DisplayName("deux jalons en retard : rouge, motif jalonsRetard")
        void deuxJalonsEnRetard() {
            Initiative i = initiative("En cours", 40, JOUR.minusDays(30), JOUR.plusDays(30));

            SanteDto s = evaluer(i,
                    jalon("A", JOUR.minusDays(5), "À atteindre"),
                    jalon("B", JOUR.minusDays(2), "À atteindre"),
                    jalon("C", JOUR.plusDays(5), "À atteindre"));

            assertThat(s.niveau()).isEqualTo(SanteDto.ROUGE);
            assertThat(s.raison()).isEqualTo("jalonsRetard");
            assertThat(s.parametres()).containsEntry("n", 2L);
        }

        @Test
        @DisplayName("un seul jalon en retard : orange, avec son nom")
        void unJalonEnRetard() {
            Initiative i = initiative("En cours", 40, JOUR.minusDays(30), JOUR.plusDays(30));

            SanteDto s = evaluer(i, jalon("Livraison SI", JOUR.minusDays(1), "À atteindre"));

            assertThat(s.niveau()).isEqualTo(SanteDto.ORANGE);
            assertThat(s.raison()).isEqualTo("jalonRetard");
            assertThat(s.parametres()).containsEntry("nom", "Livraison SI");
        }

        @Test
        @DisplayName("jalon passé mais atteint : n'est pas en retard")
        void jalonAtteintIgnore() {
            Initiative i = initiative("En cours", 80, JOUR.minusDays(30), JOUR.plusDays(30));

            SanteDto s = evaluer(i, jalon("A", JOUR.minusDays(10), "Atteint"));

            assertThat(s.niveau()).isEqualTo(SanteDto.VERTE);
            assertThat(s.raison()).isEqualTo("conforme");
        }

        @Test
        @DisplayName("écart planning/avancement ≥ 30 points : rouge, motif ecartPlanning")
        void ecartRouge() {
            // 120 jours de durée, 90 écoulés = 75 % du temps, 30 % d'avancement
            Initiative i = initiative("En cours", 30, JOUR.minusDays(90), JOUR.plusDays(30));

            SanteDto s = evaluer(i);

            assertThat(s.niveau()).isEqualTo(SanteDto.ROUGE);
            assertThat(s.raison()).isEqualTo("ecartPlanning");
            assertThat(s.parametres()).containsEntry("temps", 75).containsEntry("avancement", 30);
        }

        @Test
        @DisplayName("écart ≥ 15 points : orange, mêmes paramètres expliquables")
        void ecartOrange() {
            // 100 jours de durée, 60 écoulés = 60 %, 40 % d'avancement → 20 pts
            Initiative i = initiative("En cours", 40, JOUR.minusDays(60), JOUR.plusDays(40));

            SanteDto s = evaluer(i);

            assertThat(s.niveau()).isEqualTo(SanteDto.ORANGE);
            assertThat(s.raison()).isEqualTo("ecartPlanning");
            assertThat(s.parametres()).containsEntry("temps", 60).containsEntry("avancement", 40);
        }

        @Test
        @DisplayName("conforme ou en avance : feu vert")
        void conforme() {
            Initiative i = initiative("En cours", 70, JOUR.minusDays(70), JOUR.plusDays(30));

            assertThat(evaluer(i).niveau()).isEqualTo(SanteDto.VERTE);
            assertThat(evaluer(i).raison()).isEqualTo("conforme");
        }

        @Test
        @DisplayName("sans dates exploitables : feu vert « pas encore de planning »")
        void sansPlanning() {
            Initiative i = initiative("À planifier", 0, null, null);

            SanteDto s = evaluer(i);

            assertThat(s.niveau()).isEqualTo(SanteDto.VERTE);
            assertThat(s.raison()).isEqualTo("sansPlanning");
        }

        @Test
        @DisplayName("le rouge prélude sur l'orange : fin dépassée + jalon → finDepassee")
        void prioriteRouge() {
            Initiative i = initiative("En cours", 50, JOUR.minusDays(60), JOUR.minusDays(1));

            SanteDto s = evaluer(i, jalon("A", JOUR.minusDays(4), "À atteindre"));

            assertThat(s.raison()).isEqualTo("finDepassee");
        }
    }

    // ------------------------------------------------------------------
    // Chargement en lot
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Calcul en lot — une seule requête de jalons")
    class Lot {

        @Test
        @DisplayName("les jalons de toute la page sont groupés par initiative")
        void calculerTous() {
            // calculerTous travaille avec LocalDate.now() : dates relatives
            LocalDate jour = LocalDate.now();
            Initiative a = initiative("En cours", 50, jour.minusDays(30), jour.plusDays(30));
            Initiative b = initiative("En cours", 90, jour.minusDays(30), jour.plusDays(30));
            b.setIdInitiative(2L);
            Jalon ja = jalon("Retard A", jour.minusDays(3), "À atteindre");
            ja.setInitiative(a);
            Jalon jb = jalon("Bien", jour.plusDays(10), "À atteindre");
            jb.setInitiative(b);
            when(jalonRepository.chercherParInitiatives(anyCollection())).thenReturn(List.of(ja, jb));

            Map<Long, SanteDto> santes = calculeur.calculerTous(List.of(a, b));

            assertThat(santes).hasSize(2);
            assertThat(santes.get(1L).niveau()).isEqualTo(SanteDto.ORANGE);
            assertThat(santes.get(1L).raison()).isEqualTo("jalonRetard");
            assertThat(santes.get(2L).niveau()).isEqualTo(SanteDto.VERTE);
            assertThat(santes.get(2L).raison()).isEqualTo("conforme");
        }

        @Test
        @DisplayName("liste vide : aucun appel au dépôt")
        void listeVide() {
            assertThat(calculeur.calculerTous(List.of())).isEmpty();
        }
    }
}
