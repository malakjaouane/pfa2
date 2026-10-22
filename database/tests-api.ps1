# ============================================================
# Tests fonctionnels de l'API REST (PHASE 10)
# Portefeuille d'initiatives digitales - IT6 Consulting
# Usage : powershell -NoProfile -ExecutionPolicy Bypass -File tests-api.ps1
#   Backend local (profil dev) : valeur par défaut, 127.0.0.1:8080
#   Backend Docker : -Base http://127.0.0.1:8081/api
# La suite est idempotente : codes/emails uniques à chaque exécution,
# l'initiative de test termine « Terminée » + archivée (exclue des KPI).
# Fichier en UTF-8 (BOM) pour les comparaisons accentuées.
# ============================================================
param(
    [string]$Base = 'http://127.0.0.1:8080/api'
)
$suffixe = Get-Date -Format 'HHmmssfff'
$codeTest = "TEST-$suffixe"
$emailTest = "test$suffixe@it6.ma"
$script:Pass = 0
$script:Fail = 0

function Test-Check {
    param([string]$Nom, [bool]$Ok, [string]$Detail = '')
    if ($Ok) { $script:Pass++; Write-Output "  [OK] $Nom" }
    else { $script:Fail++; Write-Output "  [FAIL] $Nom $Detail" }
}

function Invoke-Api {
    param([string]$Method, [string]$Url, $Body, [string]$Token)
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $params = @{ Uri = $Url; Method = $Method; Headers = $headers; UseBasicParsing = $true; ErrorAction = 'Stop' }
    if ($null -ne $Body) {
        # Encodage UTF-8 explicite : sous Windows PowerShell 5.1,
        # Invoke-WebRequest encode sinon les corps en Latin-1 (le « é »
        # devient 0xE9), que Spring rejette (« Invalid UTF-8 middle byte »).
        $params.ContentType = 'application/json; charset=utf-8'
        if ($Body -is [string]) { $json = $Body } else { $json = ($Body | ConvertTo-Json -Depth 5) }
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    try {
        $resp = Invoke-WebRequest @params
        # Headers conservés (ex. X-Total-Count : compte total hors pagination).
        return @{ OK = $true; Status = [int]$resp.StatusCode; Body = (Convertir-Reponse $resp); Headers = $resp.Headers }
    } catch {
        # Le corps d'erreur porte souvent la règle métier violée : on le relit
        # également en UTF-8 pour pouvoir l'afficher sans mojibake.
        $body = $null
        if ($_.Exception.Response) {
            try {
                $flux = $_.Exception.Response.GetResponseStream()
                $lecteur = New-Object System.IO.StreamReader($flux, [System.Text.Encoding]::UTF8)
                $texte = $lecteur.ReadToEnd()
                if (-not [string]::IsNullOrWhiteSpace($texte)) { $body = $texte | ConvertFrom-Json }
            } catch { $body = $null }
        }
        return @{ OK = $false; Status = [int]$_.Exception.Response.StatusCode; Body = $body }
    }
}

# L'API renvoie « application/json » sans charset ; Windows PowerShell 5.1
# décode alors le corps en ANSI et abîme les accents (« Terminée » ->
# « TerminÃ©e »). On relit donc les octets bruts en UTF-8 avant de
# désérialiser le JSON : les comparaisons accentuées deviennent fiables.
function Convertir-Reponse {
    param($Reponse)
    $octets = $Reponse.RawContentStream.ToArray()
    $texte = [System.Text.Encoding]::UTF8.GetString($octets)
    if ([string]::IsNullOrWhiteSpace($texte)) { return $null }
    return ($texte | ConvertFrom-Json)
}

Write-Output "=== 1. Authentification ($emailTest) ==="

$login = Invoke-Api 'Post' "$Base/auth/login" @{ email = 'admin@it6.ma'; motDePasse = 'Admin@123' }
Test-Check 'Login admin (token présent)' ($login.OK -and $null -ne $login.Body.token)
# 16 = les 15 permissions du cahier des charges + BUDGET_CONSULTER (cf. data.sql).
Test-Check 'Permissions administrateur = 16 (dont ROLE_GERER + BUDGET_CONSULTER)' ($login.OK -and $login.Body.permissions.Count -eq 16) "($($login.Body.permissions.Count))"
Test-Check 'Permission ROLE_GERER présente' ($login.OK -and @($login.Body.permissions | Where-Object { $_ -eq 'ROLE_GERER' }).Count -eq 1)
$adminToken = $login.Body.token

$bad = Invoke-Api 'Post' "$Base/auth/login" @{ email = 'admin@it6.ma'; motDePasse = 'FauxMdp' }
Test-Check 'Mauvais mot de passe -> 401' ($bad.OK -eq $false -and $bad.Status -eq 401) "($($bad.Status))"

$loginV = Invoke-Api 'Post' "$Base/auth/login" @{ email = 'visiteur@it6.ma'; motDePasse = 'Visi@123' }
Test-Check 'Login visiteur, permissions = 3' ($loginV.OK -and $loginV.Body.permissions.Count -eq 3) "($($loginV.Body.permissions.Count))"
$visiteurToken = $loginV.Body.token

$loginR = Invoke-Api 'Post' "$Base/auth/login" @{ email = 'responsable@it6.ma'; motDePasse = 'Resp@123' }
Test-Check 'Login responsable, permissions = 13' ($loginR.OK -and $loginR.Body.permissions.Count -eq 13) "($($loginR.Body.permissions.Count))"
$responsableToken = $loginR.Body.token
Test-Check 'Permissions DEMANDE_* présentes (admin + responsable)' (
    (($login.Body.permissions | Where-Object { $_ -eq 'DEMANDE_SOUMETTRE' -or $_ -eq 'DEMANDE_EVALUER' }).Count -eq 2) -and
    (($loginR.Body.permissions | Where-Object { $_ -eq 'DEMANDE_SOUMETTRE' -or $_ -eq 'DEMANDE_EVALUER' }).Count -eq 2))

Write-Output '=== 2. Dashboard / KPI ==='
$dash = Invoke-Api 'Get' "$Base/dashboard" $null $adminToken
$listeRef = Invoke-Api 'Get' "$Base/initiatives" $null $adminToken
if ($dash.OK -and $listeRef.OK) {
    # Les KPI sont comparés à un recalcul indépendant effectué à partir de
    # la liste réelle des initiatives non archivées : aucune valeur n'est
    # codée en dur, donc le test reste valide quelle que soit la baseline
    # de la base (initiatives de démonstration ou données résiduelles).
    $ref = @($listeRef.Body)
    $expTotal     = $ref.Count
    $expEnCours   = @($ref | Where-Object { $_.statut.libelle -eq 'En cours' }).Count
    $expTerminees = @($ref | Where-Object { $_.statut.libelle -eq 'Terminée' }).Count
    $expEnPause   = @($ref | Where-Object { $_.statut.libelle -eq 'En pause' }).Count
    $expEnRetard  = @($ref | Where-Object { $_.enRetard -eq $true }).Count
    $expBudget    = ($ref | Measure-Object -Property budget -Sum).Sum
    # DashboardService.arrondir() publie l'avancement moyen à 2 décimales.
    $expAva       = [Math]::Round((($ref | Measure-Object -Property avancement -Average).Average), 2)
    $expStatuts   = @($ref | ForEach-Object { $_.statut.libelle } | Sort-Object -Unique).Count

    Test-Check 'Total initiatives = liste non archivée' ($dash.Body.totalInitiatives -eq $expTotal) "($($dash.Body.totalInitiatives) / $expTotal)"
    Test-Check 'En cours cohérent' ($dash.Body.enCours -eq $expEnCours) "($($dash.Body.enCours) / $expEnCours)"
    Test-Check 'Terminées cohérent' ($dash.Body.terminees -eq $expTerminees) "($($dash.Body.terminees) / $expTerminees)"
    Test-Check 'En pause cohérent' ($dash.Body.enPause -eq $expEnPause) "($($dash.Body.enPause) / $expEnPause)"
    Test-Check 'En retard cohérent' ($dash.Body.enRetard -eq $expEnRetard) "($($dash.Body.enRetard) / $expEnRetard)"
    Test-Check 'Budget total cohérent' ([Math]::Abs([double]$dash.Body.budgetTotal - [double]$expBudget) -lt 1) "($($dash.Body.budgetTotal) / $expBudget)"
    Test-Check 'Avancement moyen cohérent' ([Math]::Abs($dash.Body.avancementMoyen - $expAva) -lt 0.01) "($($dash.Body.avancementMoyen) / $expAva)"
    Test-Check 'Graphique par statut = nb de statuts présents' ($dash.Body.parStatut.Count -eq $expStatuts) "($($dash.Body.parStatut.Count) / $expStatuts)"
    Test-Check 'Répartition par entité = total' ((($dash.Body.parEntite | Measure-Object -Property count -Sum).Sum) -eq $expTotal) "($($dash.Body.parEntite.Count) groupes)"
    Test-Check 'Répartition par responsable = total' ((($dash.Body.parResponsable | Measure-Object -Property count -Sum).Sum) -eq $expTotal) "($($dash.Body.parResponsable.Count) groupes)"
    Test-Check 'Liste en retard = KPI en retard' ($dash.Body.initiativesEnRetard.Count -eq $expEnRetard) "($($dash.Body.initiativesEnRetard.Count) / $expEnRetard)"
    Test-Check 'MOB-SUI-003 figure parmi les initiatives en retard' (@($dash.Body.initiativesEnRetard | Where-Object { $_.code -eq 'MOB-SUI-003' }).Count -eq 1)
    Test-Check 'Répartition par objectif = total' ((($dash.Body.parObjectif | Measure-Object -Property count -Sum).Sum) -eq $expTotal) "($($dash.Body.parObjectif.Count) groupes)"

    # Message d'accueil : « N initiatives à risque » = lignes ORANGE ou ROUGE,
    # recalculé sur la même liste ; « N jalons cette semaine » = jalons non
    # atteints du jour courant au dimanche (fenêtre recalculée via l'API des
    # jalons). La fenêtre est calculée à la fois sur la date locale et sur la
    # date UTC du serveur : le test accepte l'une ou l'autre pour rester
    # robuste au décalage horaire autour de minuit.
    $expRisque = @($ref | Where-Object { $_.sante -and ($_.sante.niveau -eq 'ORANGE' -or $_.sante.niveau -eq 'ROUGE') }).Count
    Test-Check 'Initiatives à risque = nb de lignes ORANGE/ROUGE' ($dash.Body.initiativesARisque -eq $expRisque) "($($dash.Body.initiativesARisque) / $expRisque)"

    function Get-FenetreSemaine {
        param([datetime]$Debut)
        $Dimanche = $Debut.Date.AddDays(7 - [int]$Debut.DayOfWeek)
        if ([int]$Debut.DayOfWeek -eq 0) { $Dimanche = $Debut.Date }
        return @($Debut.Date.ToString('yyyy-MM-dd'), $Dimanche.ToString('yyyy-MM-dd'))
    }
    $expJalons = @{ }
    foreach ($fen in @((Get-FenetreSemaine ([DateTime]::UtcNow)), (Get-FenetreSemaine (Get-Date)))) {
        $n = 0
        foreach ($i in $ref) {
            $jal = Invoke-Api 'Get' "$Base/initiatives/$($i.idInitiative)/jalons" $null $adminToken
            if ($jal.OK) {
                $n += @($jal.Body | Where-Object {
                    $_.datePrevue -and $_.datePrevue -ge $fen[0] -and $_.datePrevue -le $fen[1] -and
                    ($null -eq $_.statutJalon -or $_.statutJalon.libelle -ne 'Atteint')
                }).Count
            }
        }
        $expJalons[$fen[0] + '/' + $fen[1]] = $n
    }
    Test-Check 'Jalons de la semaine = recalcul (fenêtre locale ou UTC)' (
        @($expJalons.Values | Where-Object { $_ -eq $dash.Body.jalonsSemaine }).Count -ge 1) "($($dash.Body.jalonsSemaine) / $($expJalons.Values -join ' ou '))"
} else {
    Test-Check 'Dashboard -> 200' $false "($($dash.Status))"
}

Write-Output '=== 3. Liste et filtres combinés ==='
$liste = Invoke-Api 'Get' "$Base/initiatives" $null $adminToken
Test-Check 'Liste non vide (hors archivées)' ($liste.OK -and $liste.Body.Count -ge 4) "($($liste.Body.Count))"
$filtreArchive = Invoke-Api 'Get' "$Base/initiatives?archive=true" $null $adminToken
Test-Check 'Filtre archive=true accepté (200)' $filtreArchive.OK
$filtre = Invoke-Api 'Get' "$Base/initiatives?statut=2&archive=false" $null $adminToken
Test-Check 'Filtre statut=En cours cohérent' ($filtre.OK -and @($filtre.Body | Where-Object { $_.statut.libelle -ne 'En cours' }).Count -eq 0) "($($filtre.Body.Count))"
$recherche = Invoke-Api 'Get' "$Base/initiatives?recherche=MOB" $null $adminToken
Test-Check 'Recherche texte « MOB » trouve MOB-SUI-003' ($recherche.OK -and @($recherche.Body | Where-Object { $_.code -eq 'MOB-SUI-003' }).Count -eq 1)
$filtreCombo = Invoke-Api 'Get' "$Base/initiatives?responsable=2&priorite=3&statut=2" $null $adminToken
Test-Check 'Combinaison de filtres acceptée (200)' $filtreCombo.OK

# Score de santé automatique : chaque ligne de la liste en porte un, avec un
# niveau de l'énumération et un motif ; MOB-SUI-003 (date butoir dépassée)
# doit ressortir ROUGE « finDepassee ». La fiche et la surveillance du
# dashboard sont enrichies par les mêmes règles.
$niveaux = @('VERTE', 'ORANGE', 'ROUGE')
Test-Check 'Santé présente sur chaque initiative (niveau + motif)' (
    $liste.OK -and @($liste.Body | Where-Object {
        $null -eq $_.sante -or $niveaux -notcontains $_.sante.niveau -or [string]::IsNullOrEmpty($_.sante.raison)
    }).Count -eq 0) "($($liste.Body.Count) lignes)"
$mob = @($liste.Body | Where-Object { $_.code -eq 'MOB-SUI-003' })
Test-Check 'MOB-SUI-003 (en retard) -> santé ROUGE « finDepassee »' (
    $mob.Count -eq 1 -and $mob[0].sante.niveau -eq 'ROUGE' -and $mob[0].sante.raison -eq 'finDepassee') "($($mob[0].sante.niveau)/$($mob[0].sante.raison))"
$idMob = 0; if ($mob.Count -eq 1) { $idMob = $mob[0].idInitiative }
$ficheMob = Invoke-Api 'Get' "$Base/initiatives/$idMob" $null $adminToken
Test-Check 'Fiche détaillée : santé renvoyée avec niveau valide' (
    $ficheMob.OK -and $niveaux -contains $ficheMob.Body.sante.niveau -and
    -not [string]::IsNullOrEmpty($ficheMob.Body.sante.raison)) "($($ficheMob.Body.sante.niveau)/$($ficheMob.Body.sante.raison))"
Test-Check 'Surveillance du dashboard : santé enrichie sur chaque ligne' (
    $dash.OK -and @($dash.Body.initiativesEnRetard | Where-Object {
        $null -eq $_.sante -or $niveaux -notcontains $_.sante.niveau
    }).Count -eq 0) "($($dash.Body.initiativesEnRetard.Count) lignes)"

Write-Output "=== 4. Création et règles métier ($codeTest) ==="
$corps = @{
    code = $codeTest; nom = 'Initiative de test'; description = 'Créée par les tests';
    dateDebut = '2026-09-01'; dateFinPrevue = '2026-12-31'; budget = 100000;
    avancement = 0; idEntite = 1; idResponsable = 2; idStatut = 1; idPriorite = 3
}
$cree = Invoke-Api 'Post' "$Base/initiatives" $corps $adminToken
Test-Check 'Création valide -> 201' ($cree.OK -and $cree.Body.idInitiative -gt 0)
$idTest = $cree.Body.idInitiative
Test-Check 'Création : santé non calculée sur les écritures (null)' ($null -eq $cree.Body.sante)
Test-Check 'Statut créé = À planifier' ($cree.Body.statut.libelle -eq 'À planifier') "($($cree.Body.statut.libelle))"
Test-Check 'Responsable affecté' ($cree.Body.responsable.nomComplet -eq 'Karim Alaoui') "($($cree.Body.responsable.nomComplet))"

$dup = Invoke-Api 'Post' "$Base/initiatives" $corps $adminToken
Test-Check 'Code dupliqué -> 400 (RM11)' ($dup.OK -eq $false -and $dup.Status -eq 400) "($($dup.Status))"

$negatif = Invoke-Api 'Post' "$Base/initiatives" @{ code = "TESTNEG-$suffixe"; nom = 'Test budget'; budget = -5; idEntite = 1; idStatut = 1; idPriorite = 1 } $adminToken
Test-Check 'Budget négatif -> 400 (RM05)' ($negatif.OK -eq $false -and $negatif.Status -eq 400) "($($negatif.Status))"

$dates = Invoke-Api 'Post' "$Base/initiatives" @{ code = "TESTDAT-$suffixe"; nom = 'Dates'; dateDebut = '2026-12-31'; dateFinPrevue = '2026-01-01'; idEntite = 1; idStatut = 1; idPriorite = 1 } $adminToken
Test-Check 'Fin < début -> 400 (RM04)' ($dates.OK -eq $false -and $dates.Status -eq 400) "($($dates.Status))"

$avancement = Invoke-Api 'Post' "$Base/initiatives" @{ code = "TESTAV-$suffixe"; nom = 'Avancement'; avancement = 150; idEntite = 1; idStatut = 1; idPriorite = 1 } $adminToken
Test-Check 'Avancement > 100 -> 400 (RM06)' ($avancement.OK -eq $false -and $avancement.Status -eq 400) "($($avancement.Status))"

$sansNom = Invoke-Api 'Post' "$Base/initiatives" @{ code = "TESTNM-$suffixe"; nom = ''; idEntite = 1; idStatut = 1; idPriorite = 1 } $adminToken
Test-Check 'Nom vide -> 400 (RM01)' ($sansNom.OK -eq $false -and $sansNom.Status -eq 400) "($($sansNom.Status))"

$statutForce = Invoke-Api 'Post' "$Base/initiatives" @{ code = "TESTST-$suffixe"; nom = 'Statut forcé'; avancement = 0; idEntite = 1; idStatut = 2; idPriorite = 1 } $adminToken
Test-Check 'Statut fourni ignoré en création -> « À planifier »' ($statutForce.OK -and $statutForce.Body.statut.libelle -eq 'À planifier') "($($statutForce.Body.statut.libelle))"
# Nettoyage : l'initiative « À planifier » est annulée puis archivée (cycle
# de vie À planifier -> Annulée -> archivage) afin de ne pas subsister dans
# les KPI des exécutions suivantes.
$corpsForce = @{ code = "TESTST-$suffixe"; nom = 'Statut forcé'; description = ''; dateDebut = $null; dateFinPrevue = $null; budget = 0; avancement = 0; idEntite = 1; idResponsable = $null; idStatut = 5; idPriorite = 1 }
$annuleForce = Invoke-Api 'Put' "$Base/initiatives/$($statutForce.Body.idInitiative)" $corpsForce $adminToken
$archForce = Invoke-Api 'Patch' "$Base/initiatives/$($statutForce.Body.idInitiative)/archive" $null $adminToken
Test-Check 'Nettoyage : statut forcé annulé puis archivé' ($annuleForce.OK -and $archForce.OK -and $archForce.Body.archive -eq $true)

$creation100 = Invoke-Api 'Post' "$Base/initiatives" @{ code = "TEST100-$suffixe"; nom = 'Créée à 100 %'; avancement = 100; idEntite = 1; idStatut = 1; idPriorite = 1 } $adminToken
Test-Check 'Création à 100 % -> directement « Terminée » (RM07)' ($creation100.OK -and $creation100.Body.statut.libelle -eq 'Terminée') "($($creation100.Body.statut.libelle))"
# Nettoyage : cette initiative reste « Terminée » (donc non archivée) et
# fausserait les KPI des exécutions suivantes ; on l'archive aussitôt.
$arch100 = Invoke-Api 'Patch' "$Base/initiatives/$($creation100.Body.idInitiative)/archive" $null $adminToken
Test-Check 'Nettoyage : initiative 100 % archivée' ($arch100.OK -and $arch100.Body.archive -eq $true)

Write-Output '=== 5. Étapes et jalons (avant archivage) ==='
# L'étape est créée à 0 % : RM07-auto clôturerait l'initiative dès qu'elle
# atteint 100 % (cf. section 6 et les checks RM07-auto), ce qui interdirait
# ensuite toute transition du diagramme d'état.
$etape = Invoke-Api 'Post' "$Base/initiatives/$idTest/etapes" @{ nom = 'Analyse'; ordre = 1; avancement = 0; idStatutEtape = 2 } $adminToken
Test-Check 'Création étape -> 201' ($etape.OK -and $etape.Body.idEtape -gt 0)
$idEtape = $etape.Body.idEtape
$modEtape = Invoke-Api 'Put' "$Base/etapes/$idEtape" @{ nom = 'Analyse approfondie'; ordre = 1; avancement = 0; idStatutEtape = 2 } $adminToken
Test-Check 'Modification étape -> 200' ($modEtape.OK -and $modEtape.Body.nom -eq 'Analyse approfondie')
$etapeDefaut = Invoke-Api 'Post' "$Base/initiatives/$idTest/etapes" @{ nom = 'Conception'; avancement = 0 } $adminToken
Test-Check 'Étape ordre auto (max+1) + statut par défaut' ($etapeDefaut.OK -and $etapeDefaut.Body.ordre -eq 2 -and $etapeDefaut.Body.statutEtape.libelle -eq 'À faire') "($($etapeDefaut.Body.ordre))"

$jalon = Invoke-Api 'Post' "$Base/initiatives/$idTest/jalons" @{ nom = 'Recette'; datePrevue = '2026-11-30'; idStatutJalon = 2 } $adminToken
Test-Check 'Jalon « Atteint » -> date réalisation auto' ($jalon.OK -and $null -ne $jalon.Body.dateRealisation)
$idJalon = $jalon.Body.idJalon
$modJalon = Invoke-Api 'Put' "$Base/jalons/$idJalon" @{ nom = 'Recette finale'; datePrevue = '2026-11-30'; idStatutJalon = 1 } $adminToken
Test-Check 'Jalon non atteint -> date réalisation vide (RM15)' ($modJalon.OK -and $null -eq $modJalon.Body.dateRealisation)
$jalonDefaut = Invoke-Api 'Post' "$Base/initiatives/$idTest/jalons" @{ nom = 'Mise en production'; datePrevue = '2026-12-15' } $adminToken
Test-Check 'Jalon statut par défaut « À atteindre »' ($jalonDefaut.OK -and $jalonDefaut.Body.statutJalon.libelle -eq 'À atteindre')

$etapesListees = Invoke-Api 'Get' "$Base/initiatives/$idTest/etapes" $null $adminToken
Test-Check 'Liste étapes = 2' ($etapesListees.OK -and $etapesListees.Body.Count -eq 2)
$jalonsListes = Invoke-Api 'Get' "$Base/initiatives/$idTest/jalons" $null $adminToken
Test-Check 'Liste jalons = 2' ($jalonsListes.OK -and $jalonsListes.Body.Count -eq 2)

# Autorisation vérifiée ici (initiative encore active) : le Responsable
# dispose de ETAPE_GERER, contrairement au Visiteur.
$respEtape = Invoke-Api 'Post' "$Base/initiatives/$idTest/etapes" @{ nom = 'Validation responsable'; avancement = 0 } $responsableToken
Test-Check 'Responsable : création étape autorisée -> 200' ($respEtape.OK -and $null -ne $respEtape.Body.idEtape) "($($respEtape.Status))"

Write-Output '=== 6. Cycle de vie (diagramme d''état) ==='
$baseCorps = @{ code = $codeTest; nom = 'Initiative de test'; description = ''; dateDebut = '2026-09-01'; dateFinPrevue = '2026-12-31'; budget = 100000; idEntite = 1; idResponsable = 2; idPriorite = 3 }

$txPause = Invoke-Api 'Put' "$Base/initiatives/$idTest" (@{ idStatut = 3; avancement = 0 } + $baseCorps) $adminToken
Test-Check 'À planifier -> En pause interdit -> 400' ($txPause.OK -eq $false -and $txPause.Status -eq 400) "($($txPause.Status))"

$txEnCours = Invoke-Api 'Put' "$Base/initiatives/$idTest" (@{ idStatut = 2; avancement = 10 } + $baseCorps) $adminToken
Test-Check 'À planifier -> En cours (200)' ($txEnCours.OK -and $txEnCours.Body.statut.libelle -eq 'En cours')

$txTerminee = Invoke-Api 'Put' "$Base/initiatives/$idTest" (@{ idStatut = 4; avancement = 40 } + $baseCorps) $adminToken
Test-Check 'Terminée sans 100 % refusé -> 400 (RM07)' ($txTerminee.OK -eq $false -and $txTerminee.Status -eq 400) "($($txTerminee.Status))"

$txPause2 = Invoke-Api 'Put' "$Base/initiatives/$idTest" (@{ idStatut = 3; avancement = 10 } + $baseCorps) $adminToken
Test-Check 'En cours -> En pause (200)' ($txPause2.OK -and $txPause2.Body.statut.libelle -eq 'En pause')

$txReprise = Invoke-Api 'Put' "$Base/initiatives/$idTest" (@{ idStatut = 2; avancement = 10 } + $baseCorps) $adminToken
Test-Check 'En pause -> En cours (200)' ($txReprise.OK -and $txReprise.Body.statut.libelle -eq 'En cours')

# L'avancement est piloté par les étapes dès qu'il en existe :
# InitiativeService.modifier remplace la valeur envoyée dans le corps par le
# calcul (moyenne des étapes). On porte donc les étapes à « Terminée » :
# atteindre 100 % par les étapes est ce qui déclenche le passage automatique
# à « Terminée » — même règle RM07-auto, déclenchée par le calcul au lieu
# d'une saisie directe.
$etapesRM07 = Invoke-Api 'Get' "$Base/initiatives/$idTest/etapes" $null $adminToken
foreach ($etapeRM07 in @($etapesRM07.Body)) {
    $null = Invoke-Api 'Put' "$Base/etapes/$($etapeRM07.idEtape)" @{ nom = $etapeRM07.nom; ordre = $etapeRM07.ordre; avancement = 100; idStatutEtape = 3 } $adminToken
}
$txAuto = Invoke-Api 'Get' "$Base/initiatives/$idTest" $null $adminToken
Test-Check 'Avancement 100 (étapes terminées) -> auto « Terminée »' ($txAuto.OK -and $txAuto.Body.statut.libelle -eq 'Terminée' -and $txAuto.Body.avancement -eq 100)

# RM07-auto doit s'appliquer depuis TOUS les états actifs (« À planifier »,
# « En cours », « En pause »), et pas seulement depuis « En cours ».
# 1) Avancement saisi à la main (initiative sans étapes) depuis « À planifier ».
$rm07A = Invoke-Api 'Post' "$Base/initiatives" @{ code = "TESTRM07A-$suffixe"; nom = 'RM07 depuis À planifier'; avancement = 0; idEntite = 1; idPriorite = 1 } $adminToken
$rm07AMaj = Invoke-Api 'Put' "$Base/initiatives/$($rm07A.Body.idInitiative)" @{ code = "TESTRM07A-$suffixe"; nom = 'RM07 depuis À planifier'; avancement = 100; idEntite = 1; idStatut = 1; idPriorite = 1 } $adminToken
Test-Check 'RM07-auto : 100 % saisi depuis « À planifier » -> « Terminée »' ($rm07A.OK -and $rm07AMaj.OK -and $rm07AMaj.Body.statut.libelle -eq 'Terminée' -and $rm07AMaj.Body.avancement -eq 100) "($($rm07AMaj.Status))"
# 2) Même règle depuis « En pause ».
$rm07B = Invoke-Api 'Post' "$Base/initiatives" @{ code = "TESTRM07B-$suffixe"; nom = 'RM07 depuis En pause'; avancement = 0; idEntite = 1; idPriorite = 1 } $adminToken
$idRm07B = $rm07B.Body.idInitiative
$null = Invoke-Api 'Put' "$Base/initiatives/$idRm07B" @{ code = "TESTRM07B-$suffixe"; nom = 'RM07 depuis En pause'; avancement = 0; idEntite = 1; idStatut = 2; idPriorite = 1 } $adminToken
$null = Invoke-Api 'Put' "$Base/initiatives/$idRm07B" @{ code = "TESTRM07B-$suffixe"; nom = 'RM07 depuis En pause'; avancement = 0; idEntite = 1; idStatut = 3; idPriorite = 1 } $adminToken
$rm07BMaj = Invoke-Api 'Put' "$Base/initiatives/$idRm07B" @{ code = "TESTRM07B-$suffixe"; nom = 'RM07 depuis En pause'; avancement = 100; idEntite = 1; idStatut = 3; idPriorite = 1 } $adminToken
Test-Check 'RM07-auto : 100 % saisi depuis « En pause » -> « Terminée »' ($rm07B.OK -and $rm07BMaj.OK -and $rm07BMaj.Body.statut.libelle -eq 'Terminée') "($($rm07BMaj.Status))"
# 3) Déclenché par les ÉTAPES depuis « À planifier » (parcours d'origine du
#    correctif : toutes les étapes passées à « Terminée »).
$rm07C = Invoke-Api 'Post' "$Base/initiatives" @{ code = "TESTRM07C-$suffixe"; nom = 'RM07 par les étapes'; avancement = 0; idEntite = 1; idPriorite = 1 } $adminToken
$idRm07C = $rm07C.Body.idInitiative
$etapeRm07C = Invoke-Api 'Post' "$Base/initiatives/$idRm07C/etapes" @{ nom = 'Étape RM07'; ordre = 1; avancement = 100; idStatutEtape = 3 } $adminToken
$rm07CApres = Invoke-Api 'Get' "$Base/initiatives/$idRm07C" $null $adminToken
Test-Check 'RM07-auto : étapes terminées depuis « À planifier » -> « Terminée »' ($etapeRm07C.OK -and $rm07CApres.OK -and $rm07CApres.Body.statut.libelle -eq 'Terminée' -and $rm07CApres.Body.avancement -eq 100)
# La clôture n'est pas un état figé : rouverrir l'étape fait revenir
# l'initiative à « En cours » avec l'avancement recalculé.
$null = Invoke-Api 'Put' "$Base/etapes/$($etapeRm07C.Body.idEtape)" @{ nom = 'Étape RM07'; ordre = 1; avancement = 0; idStatutEtape = 1 } $adminToken
$rm07CReouverte = Invoke-Api 'Get' "$Base/initiatives/$idRm07C" $null $adminToken
Test-Check 'RM07 : étape rouverte à 0 % -> initiative « À planifier »' ($rm07CReouverte.OK -and $rm07CReouverte.Body.statut.libelle -eq 'À planifier' -and $rm07CReouverte.Body.avancement -eq 0) "($($rm07CReouverte.Body.statut.libelle)/$($rm07CReouverte.Body.avancement))"

# RM17 : « En cours » retombé à 0 % par ses étapes -> retour automatique à
# « À planifier » (l'initiative n'a pas commencé). On fait progresser
# l'étape, on promeut l'initiative à « En cours », puis on la remet à 0 %.
$null = Invoke-Api 'Put' "$Base/etapes/$($etapeRm07C.Body.idEtape)" @{ nom = 'Étape RM07'; ordre = 1; avancement = 50; idStatutEtape = 2 } $adminToken
$null = Invoke-Api 'Put' "$Base/initiatives/$idRm07C" @{ code = "TESTRM07C-$suffixe"; nom = 'RM07 par les étapes'; avancement = 50; idEntite = 1; idStatut = 2; idPriorite = 1 } $adminToken
$null = Invoke-Api 'Put' "$Base/etapes/$($etapeRm07C.Body.idEtape)" @{ nom = 'Étape RM07'; ordre = 1; avancement = 0; idStatutEtape = 1 } $adminToken
$rm17 = Invoke-Api 'Get' "$Base/initiatives/$idRm07C" $null $adminToken
Test-Check 'RM17 : « En cours » à 0 % (étapes réouvertes) -> retour « À planifier »' ($rm17.OK -and $rm17.Body.statut.libelle -eq 'À planifier' -and $rm17.Body.avancement -eq 0) "($($rm17.Body.statut.libelle)/$($rm17.Body.avancement))"

# RM18 : statut et avancement d'une étape se déduisent mutuellement —
# statut -> valeur par défaut (0 / 50 / 100) ; nombre saisi -> statut
# (1-99 -> « En cours », 100 -> « Terminée »). L'initiative suit.
$rm18 = Invoke-Api 'Post' "$Base/initiatives" @{ code = "TESTRM18-$suffixe"; nom = 'RM18 statut/avancement'; avancement = 0; idEntite = 1; idStatut = 1; idPriorite = 1 } $adminToken
$idRm18 = $rm18.Body.idInitiative
$etapeRm18 = Invoke-Api 'Post' "$Base/initiatives/$idRm18/etapes" @{ nom = 'Étape RM18'; ordre = 1; avancement = 80; idStatutEtape = 1 } $adminToken
Test-Check 'RM18 : nombre saisi (80 %) -> statut « En cours » automatique' ($etapeRm18.OK -and $etapeRm18.Body.avancement -eq 80 -and $etapeRm18.Body.statutEtape.libelle -eq 'En cours') "($($etapeRm18.Body.avancement)/$($etapeRm18.Body.statutEtape.libelle))"
$null = Invoke-Api 'Put' "$Base/etapes/$($etapeRm18.Body.idEtape)" @{ nom = 'Étape RM18'; ordre = 1; avancement = 40; idStatutEtape = 2 } $adminToken
$etapeRm18B = Invoke-Api 'Get' "$Base/initiatives/$idRm18/etapes" $null $adminToken
Test-Check 'RM18 : étape « En cours » saisi à 40 % -> conservé (40 %)' ($etapeRm18B.OK -and $etapeRm18B.Body[0].avancement -eq 40) "($($etapeRm18B.Body[0].avancement))"
$etapeRm18D = Invoke-Api 'Post' "$Base/initiatives/$idRm18/etapes" @{ nom = 'Étape RM18 bis'; ordre = 2; idStatutEtape = 2 } $adminToken
Test-Check 'RM18 : étape « En cours » sans valeur -> 50 % par défaut' ($etapeRm18D.OK -and $etapeRm18D.Body.avancement -eq 50) "($($etapeRm18D.Body.avancement))"
$null = Invoke-Api 'Put' "$Base/etapes/$($etapeRm18D.Body.idEtape)" @{ nom = 'Étape RM18 bis'; ordre = 2; avancement = 0; idStatutEtape = 2 } $adminToken
$etapeRm18E = Invoke-Api 'Get' "$Base/initiatives/$idRm18/etapes" $null $adminToken
Test-Check 'RM18 : étape « En cours » champ non touché (0 %) -> 50 % par défaut' ($etapeRm18E.OK -and $etapeRm18E.Body[1].avancement -eq 50) "($($etapeRm18E.Body[1].avancement))"
$etapeRm18F = Invoke-Api 'Put' "$Base/etapes/$($etapeRm18D.Body.idEtape)" @{ nom = 'Étape RM18 bis'; ordre = 2; avancement = 87; idStatutEtape = 1 } $adminToken
Test-Check 'RM18 : 87 % saisi avec statut « À faire » -> « En cours » / 87 %' ($etapeRm18F.OK -and $etapeRm18F.Body.avancement -eq 87 -and $etapeRm18F.Body.statutEtape.libelle -eq 'En cours') "($($etapeRm18F.Body.avancement)/$($etapeRm18F.Body.statutEtape.libelle))"
$etapeRm18G = Invoke-Api 'Put' "$Base/etapes/$($etapeRm18D.Body.idEtape)" @{ nom = 'Étape RM18 bis'; ordre = 2; avancement = 40; idStatutEtape = 3 } $adminToken
Test-Check 'RM18 : 40 % saisi avec statut « Terminée » -> « En cours » / 40 %' ($etapeRm18G.OK -and $etapeRm18G.Body.avancement -eq 40 -and $etapeRm18G.Body.statutEtape.libelle -eq 'En cours') "($($etapeRm18G.Body.avancement)/$($etapeRm18G.Body.statutEtape.libelle))"
$null = Invoke-Api 'Delete' "$Base/etapes/$($etapeRm18D.Body.idEtape)" $null $adminToken
$etapeRm18C = Invoke-Api 'Put' "$Base/etapes/$($etapeRm18.Body.idEtape)" @{ nom = 'Étape RM18'; ordre = 1; avancement = 100; idStatutEtape = 3 } $adminToken
Test-Check 'RM18 : étape « Terminée » avec 100 % -> « Terminée » / 100 %' ($etapeRm18C.OK -and $etapeRm18C.Body.avancement -eq 100 -and $etapeRm18C.Body.statutEtape.libelle -eq 'Terminée') "($($etapeRm18C.Body.avancement)/$($etapeRm18C.Body.statutEtape.libelle))"
$rm18Init = Invoke-Api 'Get' "$Base/initiatives/$idRm18" $null $adminToken
Test-Check 'RM18 : initiative pilotée suivie (100 % -> « Terminée » RM07-auto)' ($rm18Init.OK -and $rm18Init.Body.avancement -eq 100 -and $rm18Init.Body.statut.libelle -eq 'Terminée') "($($rm18Init.Body.avancement)/$($rm18Init.Body.statut.libelle))"

$arch = Invoke-Api 'Patch' "$Base/initiatives/$idTest/archive" $null $adminToken
Test-Check 'Archivage OK (archive = true)' ($arch.OK -and $arch.Body.archive -eq $true)
$arch2 = Invoke-Api 'Patch' "$Base/initiatives/$idTest/archive" $null $adminToken
Test-Check 'Ré-archivage idempotent' ($arch2.OK -and $arch2.Body.archive -eq $true)

$txPostArch = Invoke-Api 'Put' "$Base/initiatives/$idTest" (@{ idStatut = 4; avancement = 100 } + $baseCorps) $adminToken
Test-Check 'Modification après archivage refusée -> 400' ($txPostArch.OK -eq $false -and $txPostArch.Status -eq 400) "($($txPostArch.Status))"
$etapePostArch = Invoke-Api 'Post' "$Base/initiatives/$idTest/etapes" @{ nom = 'X'; avancement = 0 } $adminToken
Test-Check 'Ajout étape sur initiative archivée refusé -> 400' ($etapePostArch.OK -eq $false -and $etapePostArch.Status -eq 400) "($($etapePostArch.Status))"
$modEtapePostArch = Invoke-Api 'Put' "$Base/etapes/$idEtape" @{ nom = 'Y'; ordre = 1; avancement = 100; idStatutEtape = 3 } $adminToken
Test-Check 'Modification étape archivée refusée -> 400' ($modEtapePostArch.OK -eq $false -and $modEtapePostArch.Status -eq 400) "($($modEtapePostArch.Status))"
$archEnCours = Invoke-Api 'Patch' "$Base/initiatives/1/archive" $null $adminToken
Test-Check 'Archiver une initiative « En cours » refusé -> 400' ($archEnCours.OK -eq $false -and $archEnCours.Status -eq 400) "($($archEnCours.Status))"

Write-Output '=== 7. Autorisations (Spring Security) ==='
$visiteurCreate = Invoke-Api 'Post' "$Base/initiatives" @{ code = "TESTX-$suffixe"; nom = 'Tentative'; idEntite = 1; idStatut = 1; idPriorite = 1 } $visiteurToken
Test-Check 'Visiteur : création interdite -> 403' ($visiteurCreate.OK -eq $false -and $visiteurCreate.Status -eq 403) "($($visiteurCreate.Status))"
$visiteurListe = Invoke-Api 'Get' "$Base/initiatives" $null $visiteurToken
Test-Check 'Visiteur : consultation autorisée -> 200' ($visiteurListe.OK -and $visiteurListe.Body.Count -ge 1)
$visiteurUsers = Invoke-Api 'Get' "$Base/users" $null $visiteurToken
Test-Check 'Visiteur : /users interdit -> 403' ($visiteurUsers.OK -eq $false -and $visiteurUsers.Status -eq 403) "($($visiteurUsers.Status))"
$anonyme = Invoke-Api 'Get' "$Base/dashboard" $null $null
Test-Check 'Anonyme : /dashboard interdit -> 401' ($anonyme.OK -eq $false -and $anonyme.Status -eq 401) "($($anonyme.Status))"
$respUsers = Invoke-Api 'Post' "$Base/users" @{ nom = 'x'; prenom = 'y'; email = "r$suffixe@it6.ma"; motDePasse = 'Pass@123'; idRole = 2 } $responsableToken
Test-Check 'Responsable : création utilisateur interdite -> 403' ($respUsers.OK -eq $false -and $respUsers.Status -eq 403) "($($respUsers.Status))"

Write-Output "=== 8. Gestion des utilisateurs ($emailTest) ==="
$user = Invoke-Api 'Post' "$Base/users" @{ nom = 'Tester'; prenom = 'Test'; email = $emailTest; motDePasse = 'Pass@123'; telephone = '0600000000'; actif = $true; idRole = 2; idEntite = 1 } $adminToken
Test-Check 'Création utilisateur -> 201' ($user.OK -and $user.Body.idUser -gt 0)
$idUser = $user.Body.idUser
$modUser = Invoke-Api 'Put' "$Base/users/$idUser" @{ nom = 'Tester'; prenom = 'Testée'; email = $emailTest; motDePasse = ''; telephone = '0600000000'; actif = $true; idRole = 2; idEntite = 1 } $adminToken
Test-Check 'Modification utilisateur (prénom) -> 200' ($modUser.OK -and $modUser.Body.prenom -eq 'Testée')
$userDup = Invoke-Api 'Post' "$Base/users" @{ nom = 'x'; prenom = 'y'; email = $emailTest; motDePasse = 'Pass@123'; idRole = 2 } $adminToken
Test-Check 'Email dupliqué -> 400' ($userDup.OK -eq $false -and $userDup.Status -eq 400) "($($userDup.Status))"
$sansRole = Invoke-Api 'Post' "$Base/users" @{ nom = 'x'; prenom = 'y'; email = "norole$suffixe@it6.ma"; motDePasse = 'Pass@123' } $adminToken
Test-Check 'Utilisateur sans rôle refusé -> 400' ($sansRole.OK -eq $false -and $sansRole.Status -eq 400) "($($sansRole.Status))"
$nouveauLogin = Invoke-Api 'Post' "$Base/auth/login" @{ email = $emailTest; motDePasse = 'Pass@123' }
Test-Check 'Le nouvel utilisateur peut se connecter' ($nouveauLogin.OK -and $null -ne $nouveauLogin.Body.token)
$desactive = Invoke-Api 'Put' "$Base/users/$idUser" @{ nom = 'Tester'; prenom = 'Testée'; email = $emailTest; motDePasse = ''; telephone = ''; actif = $false; idRole = 2; idEntite = 1 } $adminToken
Test-Check 'Désactivation du compte' ($desactive.OK -and $desactive.Body.actif -eq $false)
$loginDesactive = Invoke-Api 'Post' "$Base/auth/login" @{ email = $emailTest; motDePasse = 'Pass@123' }
Test-Check 'Compte désactivé : connexion refusée -> 401' ($loginDesactive.OK -eq $false -and $loginDesactive.Status -eq 401) "($($loginDesactive.Status))"

Write-Output '=== 9. Rôles et permissions ==='
$roles = Invoke-Api 'Get' "$Base/roles" $null $adminToken
Test-Check '3 rôles exposés' ($roles.OK -and $roles.Body.Count -eq 3) "($($roles.Body.Count))"
$roleAdmin = Invoke-Api 'Get' "$Base/roles/1" $null $adminToken
Test-Check 'ADMIN : 16 permissions' ($roleAdmin.OK -and $roleAdmin.Body.permissions.Count -eq 16) "($($roleAdmin.Body.permissions.Count))"
$roleVisiteur = Invoke-Api 'Get' "$Base/roles/3" $null $adminToken
$idsOriginaux = @($roleVisiteur.Body.permissions | ForEach-Object { $_.idPermission })
$roleUpdate = Invoke-Api 'Put' "$Base/roles/3/permissions" @{ idsPermissions = @($idsOriginaux[0]) } $adminToken
Test-Check 'Mise à jour permissions VISITEUR -> 1' ($roleUpdate.OK -and $roleUpdate.Body.permissions.Count -eq 1) "($($roleUpdate.Body.permissions.Count))"
$roleRestore = Invoke-Api 'Put' "$Base/roles/3/permissions" @{ idsPermissions = $idsOriginaux } $adminToken
Test-Check 'Restauration permissions VISITEUR' ($roleRestore.OK -and $roleRestore.Body.permissions.Count -eq $idsOriginaux.Count)

Write-Output '=== 10. Objectifs stratégiques ==='
$objetifs = Invoke-Api 'Get' "$Base/objectifs" $null $adminToken
Test-Check 'Objectifs : référentiel non vide' ($objetifs.OK -and $objetifs.Body.Count -ge 3) "($($objetifs.Body.Count))"
$objTest = @{ code = "TEST-OBJ-$suffixe"; libelle = 'Objectif de test'; description = 'Créé par les tests' }
$objCree = Invoke-Api 'Post' "$Base/objectifs" $objTest $adminToken
Test-Check 'Objectifs : création -> 201' ($objCree.OK -and $objCree.Body.idObjectif -gt 0) "($($objCree.Status))"
$idObjTest = $objCree.Body.idObjectif
$dupObj = Invoke-Api 'Post' "$Base/objectifs" $objTest $adminToken
Test-Check 'Objectifs : code dupliqué -> 400' ($dupObj.OK -eq $false -and $dupObj.Status -eq 400) "($($dupObj.Status))"
$modObj = Invoke-Api 'Put' "$Base/objectifs/$idObjTest" @{ code = "TEST-OBJ-$suffixe"; libelle = 'Objectif de test modifié'; description = 'x' } $adminToken
Test-Check 'Objectifs : modification -> 200' ($modObj.OK -and $modObj.Body.libelle -eq 'Objectif de test modifié')
$objVisiteur = Invoke-Api 'Post' "$Base/objectifs" $objTest $visiteurToken
Test-Check 'Objectifs : création par visiteur interdite -> 403' ($objVisiteur.OK -eq $false -and $objVisiteur.Status -eq 403) "($($objVisiteur.Status))"
$filtreObj = Invoke-Api 'Get' "$Base/initiatives?objectif=1" $null $adminToken
Test-Check 'Filtre initiatives par objectif cohérent' ($filtreObj.OK -and @($filtreObj.Body | Where-Object { $_.objectif.idObjectif -ne 1 }).Count -eq 0) "($($filtreObj.Body.Count))"
$initAvecObjectif = Invoke-Api 'Post' "$Base/initiatives" @{ code = "TESTOBJ-$suffixe"; nom = 'Initiative rattachée'; avancement = 0; idEntite = 1; idStatut = 1; idPriorite = 1; idObjectif = $idObjTest } $adminToken
Test-Check 'Initiative rattachée à un objectif (201)' ($initAvecObjectif.OK -and $initAvecObjectif.Body.objectif.idObjectif -eq $idObjTest) "($($initAvecObjectif.Status))"
$idInitObj = $initAvecObjectif.Body.idInitiative
$corpsObjInit = @{ code = "TESTOBJ-$suffixe"; nom = 'Initiative rattachée'; description = ''; dateDebut = $null; dateFinPrevue = $null; budget = 0; avancement = 0; idEntite = 1; idResponsable = $null; idStatut = 5; idPriorite = 1; idObjectif = $idObjTest }
$annuleObjInit = Invoke-Api 'Put' "$Base/initiatives/$idInitObj" $corpsObjInit $adminToken
$archObjInit = Invoke-Api 'Patch' "$Base/initiatives/$idInitObj/archive" $null $adminToken
Test-Check 'Nettoyage : initiative rattachée clôturée' ($annuleObjInit.OK -and $archObjInit.OK -and $archObjInit.Body.archive -eq $true)

Write-Output '=== 11. Demandes d''initiatives (workflow SPM) ==='
$demVisiteur = Invoke-Api 'Get' "$Base/demandes" $null $visiteurToken
Test-Check 'Demandes : visiteur accès interdit -> 403' ($demVisiteur.OK -eq $false -and $demVisiteur.Status -eq 403) "($($demVisiteur.Status))"
$demCorps = @{ titre = "TEST-DEM-$suffixe"; descriptionBesoin = 'Besoin exprimé'; justificationMetier = 'Justification métier'; idEntite = 1; idPriorite = 3 }
$dem1 = Invoke-Api 'Post' "$Base/demandes" $demCorps $adminToken
Test-Check 'Demande : création -> 201 « Brouillon » (demandeur = connecté)' ($dem1.OK -and $dem1.Body.statut.libelle -eq 'Brouillon' -and $null -ne $dem1.Body.demandeur.idUser) "($($dem1.Status))"
$idDem1 = $dem1.Body.idDemande
$demModCorps = $demCorps.Clone()
$demModCorps.titre = "TEST-DEM-$suffixe-MOD"
$demMod = Invoke-Api 'Put' "$Base/demandes/$idDem1" $demModCorps $adminToken
Test-Check 'Demande : modification « Brouillon » -> 200' ($demMod.OK -and $demMod.Body.titre -eq "TEST-DEM-$suffixe-MOD")
$soumettre = Invoke-Api 'Post' "$Base/demandes/$idDem1/soumettre" $null $adminToken
Test-Check 'Demande : Brouillon -> Soumise' ($soumettre.OK -and $soumettre.Body.statut.libelle -eq 'Soumise')
$reSoumettre = Invoke-Api 'Post' "$Base/demandes/$idDem1/soumettre" $null $adminToken
Test-Check 'Demande : re-soumission interdite -> 400' ($reSoumettre.OK -eq $false -and $reSoumettre.Status -eq 400) "($($reSoumettre.Status))"
$modSoumise = Invoke-Api 'Put' "$Base/demandes/$idDem1" $demCorps $adminToken
Test-Check 'Demande : modifier une demande soumise interdit -> 400' ($modSoumise.OK -eq $false -and $modSoumise.Status -eq 400) "($($modSoumise.Status))"
$evaluer = Invoke-Api 'Post' "$Base/demandes/$idDem1/evaluer" $null $responsableToken
Test-Check 'Demande : évaluation par le responsable -> en évaluation' ($evaluer.OK -and $evaluer.Body.statut.libelle -eq 'En évaluation')
$evaluer2 = Invoke-Api 'Post' "$Base/demandes/$idDem1/evaluer" $null $adminToken
Test-Check 'Demande : double évaluation interdite -> 400' ($evaluer2.OK -eq $false -and $evaluer2.Status -eq 400) "($($evaluer2.Status))"
$accepter = Invoke-Api 'Post' "$Base/demandes/$idDem1/accepter" $null $adminToken
Test-Check 'Demande : En évaluation -> Acceptée' ($accepter.OK -and $accepter.Body.statut.libelle -eq 'Acceptée')
$transform = Invoke-Api 'Post' "$Base/demandes/$idDem1/transformer" $null $adminToken
Test-Check 'Demande : transformation -> initiative liée (code DEM-xxxxxx)' ($transform.OK -and $null -ne $transform.Body.initiativeLiee -and $transform.Body.initiativeLiee.code -match '^DEM-') "($($transform.Status))"
$idInitDem = $transform.Body.initiativeLiee.idInitiative
$initDem = Invoke-Api 'Get' "$Base/initiatives/$idInitDem" $null $adminToken
Test-Check 'Initiative issue de la demande : À planifier, budget 0, avancement 0' ($initDem.OK -and $initDem.Body.statut.libelle -eq 'À planifier' -and $initDem.Body.budget -eq 0 -and $initDem.Body.avancement -eq 0)
$reTransform = Invoke-Api 'Post' "$Base/demandes/$idDem1/transformer" $null $adminToken
Test-Check 'Demande : seconde transformation refusée -> 400 (aucun doublon)' ($reTransform.OK -eq $false -and $reTransform.Status -eq 400) "($($reTransform.Status))"
$corpsClot = @{ code = $transform.Body.initiativeLiee.code; nom = $transform.Body.initiativeLiee.nom; description = ''; dateDebut = $null; dateFinPrevue = $null; budget = 0; avancement = 0; idEntite = 1; idResponsable = $null; idStatut = 5; idPriorite = 3 }
$annuleDem = Invoke-Api 'Put' "$Base/initiatives/$idInitDem" $corpsClot $adminToken
$archDem = Invoke-Api 'Patch' "$Base/initiatives/$idInitDem/archive" $null $adminToken
Test-Check 'Nettoyage : initiative transformée clôturée (Annulée + archivée)' ($annuleDem.OK -and $archDem.OK -and $archDem.Body.archive -eq $true)

$dem2Corps = $demCorps.Clone()
$dem2Corps.titre = "TEST-DEM2-$suffixe"
$dem2 = Invoke-Api 'Post' "$Base/demandes" $dem2Corps $adminToken
$idDem2 = $dem2.Body.idDemande
$refusBrouillon = Invoke-Api 'Post' "$Base/demandes/$idDem2/refuser" @{ motifRefus = 'x' } $adminToken
Test-Check 'Demande : refuser en « Brouillon » interdit -> 400' ($refusBrouillon.OK -eq $false -and $refusBrouillon.Status -eq 400) "($($refusBrouillon.Status))"
$ok = Invoke-Api 'Post' "$Base/demandes/$idDem2/soumettre" $null $adminToken
$ok = Invoke-Api 'Post' "$Base/demandes/$idDem2/evaluer" $null $adminToken
$refus2 = Invoke-Api 'Post' "$Base/demandes/$idDem2/refuser" @{ motifRefus = 'Budget non disponible' } $adminToken
Test-Check 'Demande : refus avec motif -> Refusée (motif conservé)' ($refus2.OK -and $refus2.Body.statut.libelle -eq 'Refusée' -and $refus2.Body.motifRefus -eq 'Budget non disponible')
$txNonAcceptee = Invoke-Api 'Post' "$Base/demandes/$idDem2/transformer" $null $adminToken
Test-Check 'Demande : transformation d''une demande refusée interdite -> 400' ($txNonAcceptee.OK -eq $false -and $txNonAcceptee.Status -eq 400) "($($txNonAcceptee.Status))"

$dem3Corps = $demCorps.Clone()
$dem3Corps.titre = "TEST-DEM3-$suffixe"
$dem3 = Invoke-Api 'Post' "$Base/demandes" $dem3Corps $responsableToken
$idDem3 = $dem3.Body.idDemande
Test-Check 'Demande : création par le responsable autorisée' ($dem3.OK -and $dem3.Body.idDemande -gt 0) "($($dem3.Status))"
$ok = Invoke-Api 'Post' "$Base/demandes/$idDem3/soumettre" $null $responsableToken
$ok = Invoke-Api 'Post' "$Base/demandes/$idDem3/evaluer" $null $adminToken
$refusSansMotif = Invoke-Api 'Post' "$Base/demandes/$idDem3/refuser" @{} $adminToken
Test-Check 'Demande : refus sans motif toléré (motif facultatif)' ($refusSansMotif.OK -and $refusSansMotif.Body.statut.libelle -eq 'Refusée' -and $null -eq $refusSansMotif.Body.motifRefus)
$detailDem = Invoke-Api 'Get' "$Base/demandes/$idDem3" $null $adminToken
Test-Check 'Demande : détail OK' ($detailDem.OK -and $detailDem.Body.idDemande -eq $idDem3)
$listeDem = Invoke-Api 'Get' "$Base/demandes" $null $adminToken
Test-Check 'Demandes : liste contient les demandes de test' ($listeDem.OK -and @($listeDem.Body | Where-Object { $_.titre -like 'TEST-DEM*' }).Count -ge 3) "($(@($listeDem.Body | Where-Object { $_.titre -like 'TEST-DEM*' }).Count))"

Write-Output '=== 12. Historique des statuts (journal global, lecture + suppression admin) ==='
# Une initiative dédiée enchaîne les trois origines possibles : création
# (manuelle), transition explicite (manuelle), puis passage RM07-auto
# déclenché par les étapes (automatique). Chaque ligne du journal doit
# porter sa colonne « automatique » — c'est ce qu'affiche la page dédiée.
$histCorps = @{ code = "TESTHIST-$suffixe"; nom = 'Journal des statuts'; idEntite = 1; idStatut = 1; idPriorite = 1 }
$histCree = Invoke-Api 'Post' "$Base/initiatives" $histCorps $adminToken
Test-Check 'Historique : initiative de test créée' ($histCree.OK -and $histCree.Body.idInitiative -gt 0) "($($histCree.Status))"
$idHist = $histCree.Body.idInitiative
$codeHist = $histCree.Body.code

$histBase = @{ code = $codeHist; nom = 'Journal des statuts'; description = ''; dateDebut = $null; dateFinPrevue = $null; budget = 0; idEntite = 1; idResponsable = $null; idPriorite = 1 }
$histManuel = Invoke-Api 'Put' "$Base/initiatives/$idHist" (@{ idStatut = 2; avancement = 10 } + $histBase) $adminToken
Test-Check 'Historique : transition manuelle « À planifier » -> « En cours »' ($histManuel.OK -and $histManuel.Body.statut.libelle -eq 'En cours') "($($histManuel.Body.statut.libelle))"

# L'étape à 100 % déclenche le recalcule : RM07-auto porte l'initiative à
# « Terminée ». La modification qui suit garantit le recalcul même si la
# création ne le déclenchait pas (idempotent : aucun doublon de journal).
$histEtape = Invoke-Api 'Post' "$Base/initiatives/$idHist/etapes" @{ nom = 'Livraison'; ordre = 1; avancement = 100; idStatutEtape = 3 } $adminToken
Test-Check 'Historique : étape créée à 100 %' ($histEtape.OK -and $histEtape.Body.idEtape -gt 0) "($($histEtape.Status))"
$null = Invoke-Api 'Put' "$Base/etapes/$($histEtape.Body.idEtape)" @{ nom = 'Livraison'; ordre = 1; avancement = 100; idStatutEtape = 3 } $adminToken
$histAuto = Invoke-Api 'Get' "$Base/initiatives/$idHist" $null $adminToken
Test-Check 'Historique : passage automatique « En cours » -> « Terminée » (RM07)' ($histAuto.OK -and $histAuto.Body.statut.libelle -eq 'Terminée') "($($histAuto.Body.statut.libelle))"

# Journal de la fiche : origine de chaque ligne + rattachement à l'initiative.
$histDetail = Invoke-Api 'Get' "$Base/initiatives/$idHist/historique-statuts" $null $adminToken
$histLignes = @($histDetail.Body)
$histCreation = @($histLignes | Where-Object { $null -eq $_.ancienStatut }) | Select-Object -First 1
Test-Check 'Journal détail : création présente, origine manuelle' ($histDetail.OK -and $null -ne $histCreation -and $histCreation.automatique -eq $false) "($($histLignes.Count) lignes)"
$histManLigne = @($histLignes | Where-Object { $_.nouveauStatut -eq 'En cours' }) | Select-Object -First 1
Test-Check 'Journal détail : transition choisie, origine manuelle' ($null -ne $histManLigne -and $histManLigne.automatique -eq $false)
$histAutoLigne = @($histLignes | Where-Object { $_.nouveauStatut -eq 'Terminée' }) | Select-Object -First 1
Test-Check 'Journal détail : passage RM07, origine automatique' ($null -ne $histAutoLigne -and $histAutoLigne.automatique -eq $true)
Test-Check 'Journal détail : chaque ligne porte le code de l''initiative' (@($histLignes | Where-Object { $_.initiative.code -eq $codeHist }).Count -eq $histLignes.Count)
$histDetailChamps = @($histLignes[0].PSObject.Properties.Name)
Test-Check 'Journal détail : champs utilisateur + initiative + origine' (($histDetailChamps -contains 'utilisateur') -and ($histDetailChamps -contains 'initiative') -and ($histDetailChamps -contains 'automatique'))

# Page globale : colonnes, compte total (X-Total-Count) et pagination.
$histGlobal = Invoke-Api 'Get' "$Base/historique-statuts" $null $adminToken
$histTotal = 0
if ($histGlobal.OK) { $histTotal = [int](@($histGlobal.Headers['X-Total-Count'])[0]) }
Test-Check 'Page globale : 200 + X-Total-Count ≥ lignes' ($histGlobal.OK -and $histTotal -ge @($histGlobal.Body).Count -and @($histGlobal.Body).Count -gt 0) "($($histGlobal.Status)/$histTotal)"
Test-Check 'Page globale : lignes avec code, nom et origine' (@($histGlobal.Body | Where-Object { $_.initiative.code -and $_.initiative.nom -and $null -ne $_.automatique }).Count -eq @($histGlobal.Body).Count)

$histPage = Invoke-Api 'Get' "$Base/historique-statuts?page=0&size=2" $null $adminToken
$histPageTotal = 0
if ($histPage.OK) { $histPageTotal = [int](@($histPage.Headers['X-Total-Count'])[0]) }
Test-Check 'Pagination : taille 2 respectée, total hors page' ($histPage.OK -and @($histPage.Body).Count -le 2 -and $histPageTotal -gt @($histPage.Body).Count) "($(@($histPage.Body).Count)/$histPageTotal)"

# Recherche par code ou nom, et filtres combinés.
$histRech = Invoke-Api 'Get' "$Base/historique-statuts?recherche=$codeHist" $null $adminToken
Test-Check 'Recherche par code : au moins les 3 lignes du test, rien d''autre' (@($histRech.Body).Count -ge 3 -and @($histRech.Body | Where-Object { $_.initiative.code -like 'TESTHIST-*' }).Count -eq @($histRech.Body).Count) "($(@($histRech.Body).Count))"

$histInit = Invoke-Api 'Get' "$Base/historique-statuts?initiative=$idHist" $null $adminToken
Test-Check 'Filtre initiative (lien « Voir tout ») : 3 événements' (@($histInit.Body).Count -eq 3) "($(@($histInit.Body).Count))"

$histStatut = Invoke-Api 'Get' "$Base/historique-statuts?statut=Terminée" $null $adminToken
Test-Check 'Filtre statut « Terminée » : que des lignes « Terminée »' (@($histStatut.Body).Count -gt 0 -and @($histStatut.Body | Where-Object { $_.nouveauStatut -eq 'Terminée' }).Count -eq @($histStatut.Body).Count) "($(@($histStatut.Body).Count))"

$auteursHist = Invoke-Api 'Get' "$Base/historique-statuts/auteurs" $null $adminToken
Test-Check 'Auteurs du journal : liste non vide' ($auteursHist.OK -and @($auteursHist.Body).Count -ge 1)
$idAuteurHist = 0
if ($auteursHist.OK -and @($auteursHist.Body).Count -ge 1) { $idAuteurHist = @($auteursHist.Body)[0].idUser }
$histParUtil = Invoke-Api 'Get' "$Base/historique-statuts?utilisateur=$idAuteurHist" $null $adminToken
Test-Check 'Filtre utilisateur : lignes toutes du même auteur' (@($histParUtil.Body).Count -gt 0 -and @($histParUtil.Body | Where-Object { $_.utilisateur.idUser -eq $idAuteurHist }).Count -eq @($histParUtil.Body).Count) "($(@($histParUtil.Body).Count))"

$jour = Get-Date -Format 'yyyy-MM-dd'
$hier = (Get-Date).AddDays(-1).ToString('yyyy-MM-dd')
# Les événements sont horodatés en UTC côté serveur : la fenêtre couvre hier
# ET aujourd'hui pour rester valide de part et d'autre de minuit (minuit
# local peut encore être la veille en UTC).
$histJour = Invoke-Api 'Get' "$Base/historique-statuts?du=$hier&au=$jour" $null $adminToken
Test-Check 'Filtre période du/au = aujourd''hui : événements du jour' (
    @($histJour.Body).Count -ge 3 -and
    @($histJour.Body | Where-Object {
        $_.dateChangement -like "$jour*" -or $_.dateChangement -like "$hier*"
    }).Count -eq @($histJour.Body).Count) "($(@($histJour.Body).Count))"
$histFutur = Invoke-Api 'Get' "$Base/historique-statuts?du=2099-01-01&au=2099-01-02" $null $adminToken
Test-Check 'Filtre période future : aucune ligne' ($histFutur.OK -and @($histFutur.Body).Count -eq 0) "($(@($histFutur.Body).Count))"

# Tri et validation des paramètres.
$histAsc = Invoke-Api 'Get' "$Base/historique-statuts?initiative=$idHist&direction=asc" $null $adminToken
$ascPremier = @($histAsc.Body)[0]
$ascDernier = @($histAsc.Body)[-1]
Test-Check 'Tri « plus ancien d''abord » : première ligne ≤ dernière' ($histAsc.OK -and @($histAsc.Body).Count -eq 3 -and ([datetime]$ascPremier.dateChangement) -le ([datetime]$ascDernier.dateChangement))
$histSensBad = Invoke-Api 'Get' "$Base/historique-statuts?direction=sens-interdit" $null $adminToken
Test-Check 'Direction de tri inconnue -> 400' ($histSensBad.OK -eq $false -and $histSensBad.Status -eq 400) "($($histSensBad.Status))"
$histPeriodeBad = Invoke-Api 'Get' "$Base/historique-statuts?du=$jour&au=2020-01-01" $null $adminToken
Test-Check 'Période inversée (du > au) -> 400' ($histPeriodeBad.OK -eq $false -and $histPeriodeBad.Status -eq 400) "($($histPeriodeBad.Status))"

# Permissions : INITIATIVE_CONSULTER suffit, rien n'est ouvert en plus.
$histAnonyme = Invoke-Api 'Get' "$Base/historique-statuts" $null $null
Test-Check 'Anonyme : journal global interdit -> 401' ($histAnonyme.OK -eq $false -and $histAnonyme.Status -eq 401) "($($histAnonyme.Status))"
$histVisiteur = Invoke-Api 'Get' "$Base/historique-statuts" $null $visiteurToken
Test-Check 'Visiteur : lecture du journal autorisée -> 200' ($histVisiteur.OK -and $histVisiteur.Status -eq 200) "($($histVisiteur.Status))"

# Écriture : aucune création ni modification sur le journal (pas de POST/PUT).
# La suppression d'une ligne existe mais reste réservée aux administrateurs
# (UTILISATEUR_GERER) — la consultation seule ne suffit pas.
$histEcriture = Invoke-Api 'Post' "$Base/historique-statuts" @{ nouveauStatut = 'Terminée' } $adminToken
Test-Check 'Écriture sur le journal refusée (POST -> 4xx)' ($histEcriture.OK -eq $false -and $histEcriture.Status -ge 400 -and $histEcriture.Status -lt 500) "($($histEcriture.Status))"

$histLigneASuppr = @($histInit.Body)[0].idHistorique
$histSupprVisiteur = Invoke-Api 'Delete' "$Base/historique-statuts/$histLigneASuppr" $null $visiteurToken
Test-Check 'Suppression journal : visiteur interdit -> 403' ($histSupprVisiteur.OK -eq $false -and $histSupprVisiteur.Status -eq 403) "($($histSupprVisiteur.Status))"
$histSupprAnonyme = Invoke-Api 'Delete' "$Base/historique-statuts/$histLigneASuppr" $null $null
Test-Check 'Suppression journal : anonyme -> 401' ($histSupprAnonyme.OK -eq $false -and $histSupprAnonyme.Status -eq 401) "($($histSupprAnonyme.Status))"
$histSupprInconnue = Invoke-Api 'Delete' "$Base/historique-statuts/999999999" $null $adminToken
Test-Check 'Suppression journal : id inexistant -> 404' ($histSupprInconnue.OK -eq $false -and $histSupprInconnue.Status -eq 404) "($($histSupprInconnue.Status))"

$histAvantSuppr = Invoke-Api 'Get' "$Base/historique-statuts" $null $adminToken
$histTotalAvant = 0
if ($histAvantSuppr.OK) { $histTotalAvant = [int](@($histAvantSuppr.Headers['X-Total-Count'])[0]) }
$histSuppr = Invoke-Api 'Delete' "$Base/historique-statuts/$histLigneASuppr" $null $adminToken
$histApresSuppr = Invoke-Api 'Get' "$Base/historique-statuts" $null $adminToken
$histTotalApres = 0
if ($histApresSuppr.OK) { $histTotalApres = [int](@($histApresSuppr.Headers['X-Total-Count'])[0]) }
Test-Check 'Suppression journal : admin -> 204, total diminué de 1' ($histSuppr.OK -and $histSuppr.Status -eq 204 -and $histTotalApres -eq ($histTotalAvant - 1)) "($($histSuppr.Status)/$histTotalAvant->$histTotalApres)"

# Nettoyage : « Terminée » -> archivée (exclue des KPI des exécutions suivantes).
$histArch = Invoke-Api 'Patch' "$Base/initiatives/$idHist/archive" $null $adminToken
Test-Check 'Nettoyage : initiative du journal archivée' ($histArch.OK -and $histArch.Body.archive -eq $true)

Write-Output ''
Write-Output '------------------------------------------------------------'
Write-Output "RESULTATS : $script:Pass reussis, $script:Fail echoues"
if ($script:Fail -gt 0) { exit 1 }