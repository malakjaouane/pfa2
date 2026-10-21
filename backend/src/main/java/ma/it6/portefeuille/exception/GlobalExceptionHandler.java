package ma.it6.portefeuille.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralise la gestion des exceptions API REST afin de renvoyer des
 * erreurs JSON propres au frontend.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
    }

    /**
     * Route ou ressource inexistante : renvoie un 404 JSON plutôt que la
     * page d'erreur par défaut de Spring Boot.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND,
                "Ressource introuvable : " + ex.getResourcePath(), List.of());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    /**
     * Limite de tentatives atteinte (connexion en force brute) : 429 et non
     * 401, afin que le front puisse distinguer « mauvais mot de passe » de
     * « trop de tentatives ».
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException ex) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.add(fe.getField() + " : " + fe.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "Données invalides", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> details = new ArrayList<>();
        ex.getConstraintViolations().forEach(cv -> details.add(cv.getPropertyPath() + " : " + cv.getMessage()));
        return build(HttpStatus.BAD_REQUEST, "Données invalides", details);
    }

    /**
     * Verbe HTTP inconnu pour une route existante (ex. {@code DELETE} sur une
     * route {@code GET}) : 405 et non 500 — c'est la méthode qui est refusée,
     * pas le serveur qui échoue.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "Méthode « " + ex.getMethod() + " » non prise en charge pour cette route.", List.of());
    }

    /**
     * Corps envoyé dans un format non pris en charge (ex. {@code text/plain}
     * là où l'API attend du JSON) : 415.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Type de contenu non pris en charge : l'API attend « application/json ».", List.of());
    }

    /**
     * Corps illisible (JSON malformé, fin de flux coupée) : 400 avec un message
     * exploitable, et non une erreur interne.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST,
                "Corps de requête illisible : JSON invalide ou mal formé.", List.of());
    }

    /**
     * Paramètre de chemin ou de requête de type invalide
     * ({@code GET /initiatives/abc}, {@code ?page=abc}) : 400 en nommant le
     * paramètre et le type attendu.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String attendu = ex.getRequiredType() == null ? "le type attendu" : ex.getRequiredType().getSimpleName();
        return build(HttpStatus.BAD_REQUEST,
                "Valeur « " + ex.getValue() + " » invalide pour le paramètre « " + ex.getName()
                        + " » (type attendu : " + attendu + ").", List.of());
    }

    /**
     * Le client n'accepte aucun format que l'API produit (ex.
     * {@code Accept: application/xml} sur une réponse JSON). On renvoie un 406
     * <strong>sans corps</strong> : écrire du JSON serait impossible avec cet
     * {@code Accept}, et la tentative provoquerait à son tour une erreur.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "Contrainte d'intégrité violée (valeur dupliquée ou référence invalide).", List.of());
    }

    /**
     * Verrou optimiste (@Version) : quelqu'un d'autre a modifié la même ligne
     * entre le chargement de l'écran et l'enregistrement. On renvoie 409 et non
     * 500 : le front sait alors proposer un rechargement plutôt que d'afficher
     * une erreur d'application.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT,
                "Cette fiche a été modifiée par quelqu'un d'autre pendant votre saisie. "
                        + "Rechargez-la avant de retenter.", List.of());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect.", List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Authentification requise.", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "Accès refusé : droits insuffisants.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Erreur non gérée", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur.", List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), message, details));
    }
}