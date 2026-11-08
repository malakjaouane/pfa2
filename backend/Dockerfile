# ==================================================================
# Image du backend Spring Boot (build multi-étapes).
#
# Le build context DOIT être la racine du dépôt (cf. docker-compose.yml)
# car pom.xml embarque database/migration (V1__baseline.sql +
# V2__referentiels.sql) dans le
# jar : cette source unique versionnée reste ainsi unique pour l'exécution
# locale (base MySQL existante) et pour l'exécution conteneurisée.
# ==================================================================

# ---- Étape 1 : compilation Maven -------------------------------
FROM maven:3.9-eclipse-temurin-17 AS construction
WORKDIR /build

# D'abord le seul fichier de dépendances : la couche Docker reste en
# cache tant que pom.xml ne change pas, ce qui accélère les rebuilds.
COPY backend/pom.xml ./backend/pom.xml
COPY database ./database
WORKDIR /build/backend
RUN mvn -B -q dependency:go-offline

COPY backend/src ./src
# -DskipTests : les tests tournent en CI (mvn test), pas dans l'image.
RUN mvn -B -q package -DskipTests

# ---- Étape 2 : exécution (JRE seule, sans SDK ni sources) -------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Compte non-root : l'image n'a pas besoin des privilèges root.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin portefeuille
USER portefeuille

COPY --from=construction /build/backend/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
