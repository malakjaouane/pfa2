package ma.it6.portefeuille;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que Testcontainers peut démarrer une base MySQL 8.4 et qu'on peut
 * s'y connecter : validation de l'infrastructure de tests d'intégration
 * (Phase 1 E3), sans polluer la base de développement.
 */
@Testcontainers
class TestcontainersInfrastructureTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("portefeuille_test")
            .withUsername("pfa_user")
            .withPassword("pfa2026");

    @Test
    void mysqlConteneurDemarreEtRepond() throws Exception {
        try (Connection c = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
            assertThat(c).isNotNull();
            assertThat(c.isValid(5)).isTrue();
            try (var s = c.createStatement(); var rs = s.executeQuery("SELECT 1 AS ok")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("ok")).isEqualTo(1);
            }
        }
    }
}
