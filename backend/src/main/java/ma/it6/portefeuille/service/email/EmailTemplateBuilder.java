package ma.it6.portefeuille.service.email;

/**
 * Modeles d'e-mails transactionnels en quatre langues (fr, en, ar, es).
 *
 * <p>Chaque methode de fabrique retourne un {@link Modele} dont les
 * placeholders {@code {{nom}}}, {{lien}}, {{duree}}, {{nomInviteur}},
 * {{organisation}} sont remplacés par l'appelant via
 * {@link #personnaliser(Modele, String, String)} avant l'envoi.</p>
 *
 * <p>Le corps HTML porte l'attribut {@code dir="rtl"} pour l'arabe.</p>
 */
public final class EmailTemplateBuilder {

    private EmailTemplateBuilder() {
    }

    /** Modèle construit : sujet + corps HTML + corps texte. */
    public record Modele(String sujet, String html, String texte) {}

    /** Codes de langue supportés. */
    public static final String LANGUE_FR = "fr";
    public static final String LANGUE_EN = "en";
    public static final String LANGUE_AR = "ar";
    public static final String LANGUE_ES = "es";

    /** Remplace les placeholders {{nom}} et {{lien}} dans un modèle. */
    public static Modele personnaliser(Modele modele, String nom, String lien) {
        if (modele == null) return null;
        return new Modele(
                remplace(modele.sujet(), "{{nom}}", nom).replace("{{lien}}", lien),
                remplace(modele.html(), "{{nom}}", nom).replace("{{lien}}", lien),
                remplace(modele.texte(), "{{nom}}", nom).replace("{{lien}}", lien));
    }

    private static String remplace(String texte, String placeholder, String valeur) {
        return texte == null ? null : texte.replace(placeholder, valeur == null ? "" : valeur);
    }

    // ==================================================================
    // Verification de l'adresse e-mail (après inscription)
    // ==================================================================

    public static Modele verification(String langue) {
        String d = duree(24);
        switch (langue) {
            case LANGUE_AR:
                return modele(
                        "تأكيد عنوان البريد الإلكتروني",
                        htmlAr("تأكيد بريدك الإلكتروني",
                                "مرحباً {{nom}},",
                                "لتأكيد عنوان البريد الإلكتروني الخاص بك، اضغط على الزر أدناه:",
                                "تأكيد البريد الإلكتروني",
                                "إذا لم تطلب إنشاء حساب، يمكنك تجاهل هذه الرسالة.",
                        "هذا الرابط صالح لمدة {{duree}} ثم ينتهي."),
                        texteAr("تأكيد بريدك الإلكتروني",
                                "مرحبا {{nom}}،",
                                "للتأكيد، افتح الرابط التالي: {{lien}}",
                        "هذا الرابط صالح لمدة {{duree}}.",
                                "إذا لم تطلب إنشاء حساب، تجاهل هذه الرسالة."),
                        d);
            case LANGUE_EN:
                return modele(
                        "Confirm your email address",
                        html("Confirm your email address",
                                "Hello {{nom}},",
                                "To confirm your email address, click the button below:",
                                "Confirm my email",
                                "If you did not create an account, you can ignore this message.",
                        "This link is valid for {{duree}} and then expires."),
                        texte("Confirm your email address",
                                "Hello {{nom}},",
                                "To confirm, open this link: {{lien}}",
                        "This link is valid for {{duree}}.",
                                "If you did not create an account, ignore this message."),
                        d);
            case LANGUE_ES:
                return modele(
                        "Confirma tu dirección de correo electrónico",
                        html("Confirma tu dirección de correo electrónico",
                                "Hola {{nom}},",
                                "Para confirmar tu dirección de correo electrónico, haz clic en el botón de abajo:",
                                "Confirmar mi correo electrónico",
                                "Si no creaste una cuenta, puedes ignorar este mensaje.",
                        "Este enlace es válido durante {{duree}} y luego expira."),
                        texte("Confirma tu dirección de correo electrónico",
                                "Hola {{nom}},",
                                "Para confirmar, abre este enlace: {{lien}}",
                        "Este enlace es válido durante {{duree}}.",
                                "Si no creaste una cuenta, ignora este mensaje."),
                        d);
            default: // fr
                return modele(
                        "Confirmez votre adresse e-mail",
                        html("Confirmez votre adresse e-mail",
                                "Bonjour {{nom}},",
                                "Pour confirmer votre adresse e-mail, cliquez sur le bouton ci-dessous :",
                                "Confirmer mon e-mail",
                                "Si vous n'avez pas créé de compte, vous pouvez ignorer ce message.",
                        "Ce lien est valable {{duree}} puis expire."),
                        texte("Confirmez votre adresse e-mail",
                                "Bonjour {{nom}},",
                                "Pour confirmer, ouvrez ce lien : {{lien}}",
                        "Ce lien est valable {{duree}} puis expire.",
                                "Si vous n'avez pas créé de compte, ignorez ce message."),
                        d);
        }
    }

    // ==================================================================
    // Réinitialisation du mot de passe
    // ==================================================================

    public static Modele reinitialisation(String langue) {
        String d = duree(1);
        switch (langue) {
            case LANGUE_AR:
                return modele(
                        "إعادة تعيين كلمة المرور",
                        htmlAr("إعادة تعيين كلمة المرور",
                                "مرحباً {{nom}},",
                                "لقد طلبت إعادة تعيين كلمة المرور الخاصة بك. اضغط على الزر أدناه لاختيار كلمة مرور جديدة:",
                                "اختيار كلمة مرور جديدة",
                                "إذا لم تطلب هذا التغيير، تجاهل هذه الرسالة ولا تضغط على الرابط.",
                        "هذا الرابط صالح لمدة {{duree}} ثم ينتهي."),
                        texteAr("إعادة تعيين كلمة المرور",
                                "مرحبا {{nom}}،",
                                "لإعادة التعيين، افتح الرابط التالي: {{lien}}",
                        "هذا الرابط صالح لمدة {{duree}}.",
                                "إذا لم تطلب هذا التغيير، تجاهل هذه الرسالة."),
                        d);
            case LANGUE_EN:
                return modele(
                        "Reset your password",
                        html("Reset your password",
                                "Hello {{nom}},",
                                "You requested a password reset. Click the button below to choose a new password:",
                                "Reset my password",
                                "If you did not request this, you can ignore this message and not click the link.",
                        "This link is valid for {{duree}} and then expires."),
                        texte("Reset your password",
                                "Hello {{nom}},",
                                "To reset, open this link: {{lien}}",
                        "This link is valid for {{duree}}.",
                                "If you did not request this, ignore this message."),
                        d);
            case LANGUE_ES:
                return modele(
                        "Restablece tu contraseña",
                        html("Restablece tu contraseña",
                                "Hola {{nom}},",
                                "Has solicitado restablecer tu contraseña. Haz clic en el botón de abajo para elegir una nueva:",
                                "Restablecer mi contraseña",
                                "Si no solicitaste este cambio, puedes ignorar este mensaje y no hacer clic en el enlace.",
                        "Este enlace es válido durante {{duree}} y luego expira."),
                        texte("Restablece tu contraseña",
                                "Hola {{nom}},",
                                "Para restablecer, abre este enlace: {{lien}}",
                        "Este enlace es válido durante {{duree}}.",
                                "Si no solicitaste esto, ignora este mensaje."),
                        d);
            default: // fr
                return modele(
                        "Réinitialisez votre mot de passe",
                        html("Réinitialisez votre mot de passe",
                                "Bonjour {{nom}},",
                                "Vous avez demandé la réinitialisation de votre mot de passe. Cliquez sur le bouton ci-dessous pour en choisir un nouveau :",
                                "Réinitialiser mon mot de passe",
                                "Si vous n'êtes pas à l'origine de cette demande, vous pouvez ignorer ce message sans cliquer sur le lien.",
                        "Ce lien est valable {{duree}} puis expire."),
                        texte("Réinitialisez votre mot de passe",
                                "Bonjour {{nom}},",
                                "Pour réinitialiser, ouvrez ce lien : {{lien}}",
                        "Ce lien est valable {{duree}} puis expire.",
                                "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message."),
                        d);
        }
    }

    // ==================================================================
    // Invitation d'un collègue
    // ==================================================================

    public static Modele invitation(String langue, String nomInviteur, String organisation) {
        String d = duree(48);
        switch (langue) {
            case LANGUE_AR:
                return modele(
                        "دعوة للانضمام إلى " + escapeHtml(organisation),
                        htmlAr("دعوة للانضمام إلى " + escapeHtml(organisation),
                                "مرحباً {{nom}},",
                                "{{nomInviteur}} يدعوك للانضمام إلى منظمة " + escapeHtml(organisation) + " على Portefeuille IT6.",
                                "قبول الدعوة",
                                "إذا لم تتوقع هذه الدعوة، يمكنك تجاهل هذه الرسالة.",
                        "هذا الرابط صالح لمدة {{duree}} ثم ينتهي."),
                        texteAr("دعوة للانضمام إلى " + escapeHtml(organisation),
                                "مرحبا {{nom}}،",
                                "{{nomInviteur}} يدعوك للانضمام إلى " + escapeHtml(organisation) + ". رابط القبول: {{lien}}",
                        "هذا الرابط صالح لمدة {{duree}}.",
                                "إذا لم تتوقع هذه الدعوة، تجاهل هذه الرسالة."),
                        d,
                        nomInviteur);
            case LANGUE_EN:
                return modele(
                        "Invitation to join " + escapeHtml(organisation),
                        html("Invitation to join " + escapeHtml(organisation),
                                "Hello {{nom}},",
                                "{{nomInviteur}} invites you to join the organisation " + escapeHtml(organisation) + " on Portefeuille IT6.",
                                "Accept the invitation",
                                "If you were not expecting this invitation, you can ignore this message.",
                        "This link is valid for {{duree}} and then expires."),
                        texte("Invitation to join " + escapeHtml(organisation),
                                "Hello {{nom}},",
                                "{{nomInviteur}} invites you to join " + escapeHtml(organisation) + ". Acceptance link: {{lien}}",
                        "This link is valid for {{duree}}.",
                                "If you were not expecting this invitation, ignore this message."),
                        d,
                        nomInviteur);
            case LANGUE_ES:
                return modele(
                        "Invitación a unirse a " + escapeHtml(organisation),
                        html("Invitación a unirse a " + escapeHtml(organisation),
                                "Hola {{nom}},",
                                "{{nomInviteur}} te invita a unirte a la organización " + escapeHtml(organisation) + " en Portefeuille IT6.",
                                "Aceptar la invitación",
                                "Si no esperabas esta invitación, puedes ignorar este mensaje.",
                        "Este enlace es válido durante {{duree}} y luego expira."),
                        texte("Invitación a unirse a " + escapeHtml(organisation),
                                "Hola {{nom}},",
                                "{{nomInviteur}} te invita a unirte a " + escapeHtml(organisation) + ". Enlace de aceptación: {{lien}}",
                        "Este enlace es válido durante {{duree}}.",
                                "Si no esperabas esta invitación, ignora este mensaje."),
                        d,
                        nomInviteur);
            default: // fr
                return modele(
                        "Invitation à rejoindre " + escapeHtml(organisation),
                        html("Invitation à rejoindre " + escapeHtml(organisation),
                                "Bonjour {{nom}},",
                                "{{nomInviteur}} vous invite à rejoindre l'organisation " + escapeHtml(organisation) + " sur Portefeuille IT6.",
                                "Accepter l'invitation",
                                "Si vous n'attendiez pas cette invitation, vous pouvez ignorer ce message.",
                        "Ce lien est valable {{duree}} puis expire."),
                        texte("Invitation à rejoindre " + escapeHtml(organisation),
                                "Bonjour {{nom}},",
                                "{{nomInviteur}} vous invite à rejoindre " + escapeHtml(organisation) + ". Lien d'acceptation : {{lien}}",
                        "Ce lien est valable {{duree}} puis expire.",
                                "Si vous n'attendiez pas cette invitation, ignorez ce message."),
                        d,
                        nomInviteur);
        }
    }

    // ------------------------------------------------------------------
    // Fragments
    // ------------------------------------------------------------------

    private static String duree(int heures) {
        return heures + " heure" + (heures > 1 ? "s" : "");
    }

    private static Modele modele(String sujet, String html, String texte, String duree) {
        sujet = remplace(sujet, "{{duree}}", duree);
        html = remplace(html, "{{duree}}", duree);
        texte = remplace(texte, "{{duree}}", duree);
        return new Modele(sujet, html, texte);
    }

    private static Modele modele(String sujet, String html, String texte, String duree, String nomInviteur) {
        Modele m = modele(sujet, html, texte, duree);
        sujet = remplace(m.sujet(), "{{nomInviteur}}", nomInviteur);
        html = remplace(m.html(), "{{nomInviteur}}", nomInviteur);
        texte = remplace(m.texte(), "{{nomInviteur}}", nomInviteur);
        return new Modele(sujet, html, texte);
    }

    private static String html(String titre, String salut, String phrase, String texteBouton,
                               String alternative, String validite) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"></head>"
                + "<body style=\"font-family:sans-serif;line-height:1.6;color:#222;\">"
                + "<h2>" + escapeHtml(titre) + "</h2>"
                + "<p>" + escapeHtml(salut) + "</p>"
                + "<p>" + escapeHtml(phrase) + "</p>"
                + "<p style=\"text-align:center;\">"
                + "<a href=\"{{lien}}\" style=\"background:#0a6ea2;color:#fff;padding:12px 24px;text-decoration:none;border-radius:6px;\">"
                + escapeHtml(texteBouton) + "</a></p>"
                + "<p>" + escapeHtml(alternative) + "</p>"
                + "<p><small>" + escapeHtml(validite) + "</small></p>"
                + "</body></html>";
    }

    private static String htmlAr(String titre, String salut, String phrase, String texteBouton,
                                 String alternative, String validite) {
        return "<!doctype html><html lang=\"ar\" dir=\"rtl\"><head><meta charset=\"utf-8\"></head>"
                + "<body style=\"font-family:sans-serif;line-height:1.6;color:#222;\">"
                + "<h2>" + escapeHtml(titre) + "</h2>"
                + "<p>" + escapeHtml(salut) + "</p>"
                + "<p>" + escapeHtml(phrase) + "</p>"
                + "<p style=\"text-align:center;\">"
                + "<a href=\"{{lien}}\" style=\"background:#0a6ea2;color:#fff;padding:12px 24px;text-decoration:none;border-radius:6px;\">"
                + escapeHtml(texteBouton) + "</a></p>"
                + "<p>" + escapeHtml(alternative) + "</p>"
                + "<p><small>" + escapeHtml(validite) + "</small></p>"
                + "</body></html>";
    }

    private static String texte(String titre, String salut, String phrase, String validite,
                                String alternative) {
        return titre + "\n\n" + salut + "\n\n" + phrase + "\n{{lien}}\n\n"
                + validite + "\n\n" + alternative;
    }

    private static String texteAr(String titre, String salut, String phrase, String validite,
                                  String alternative) {
        return titre + "\n\n" + salut + "\n\n" + phrase + "\n{{lien}}\n\n"
                + validite + "\n\n" + alternative;
    }

    private static String escapeHtml(String valeur) {
        if (valeur == null) return "";
        return valeur.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
