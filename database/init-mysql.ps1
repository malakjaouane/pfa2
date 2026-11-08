# Initialisation de l'instance MySQL locale (une seule fois)
# - initialise un datadir vierge dans .mysql/data (mot de passe root vide)
# - crée la base portefeuille_initiatives et l'utilisateur pfa_user
$base = Split-Path -Parent $PSScriptRoot
$mysqlbin = 'C:\Program Files\MySQL\MySQL Server 8.4\bin'
$datadir = Join-Path $base '.mysql\data'
if (Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue) {
    Write-Error 'Un serveur MySQL écoute déjà sur le port 3306. Arrêtez-le avant l''initialisation.'
    exit 1
}
New-Item -ItemType Directory -Force -Path $datadir | Out-Null
& (Join-Path $mysqlbin 'mysqld.exe') --initialize-insecure --basedir='C:\Program Files\MySQL\MySQL Server 8.4' --datadir=$datadir
if ($LASTEXITCODE -ne 0) { Write-Error "Échec de l'initialisation"; exit 1 }
& (Join-Path $mysqlbin 'mysqld.exe') --basedir='C:\Program Files\MySQL\MySQL Server 8.4' --datadir=$datadir --port=3306 --bind-address=127.0.0.1 --console --daemonize
Start-Sleep -Seconds 6
$sql = @"
CREATE DATABASE IF NOT EXISTS portefeuille_initiatives CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'pfa_user'@'localhost' IDENTIFIED BY 'pfa2026';
CREATE USER IF NOT EXISTS 'pfa_user'@'127.0.0.1' IDENTIFIED BY 'pfa2026';
GRANT ALL PRIVILEGES ON portefeuille_initiatives.* TO 'pfa_user'@'localhost';
GRANT ALL PRIVILEGES ON portefeuille_initiatives.* TO 'pfa_user'@'127.0.0.1';
FLUSH PRIVILEGES;
"@
$sql | & (Join-Path $mysqlbin 'mysql.exe') -u root --protocol=TCP -h 127.0.0.1 -P 3306
Write-Output 'Base portefeuille_initiatives et utilisateur pfa_user créés.'
Write-Output ''
Write-Output 'Application du schéma et des données : lancez simplement l''application.'
Write-Output 'Flyway applique database\migration\V1__baseline.sql puis V2__referentiels.sql au démarrage, en UTF-8.'
Write-Output ''
Write-Warning ('Ne chargez JAMAIS ces fichiers via un pipeline PowerShell du type' +
    ' « Get-Content V1__baseline.sql | mysql » : PowerShell 5.1 lit un UTF-8 sans BOM' +
    ' en ANSI, les accents sont détruits (« À planifier » devient « ?? planifier »),' +
    ' et l''application refuse ensuite de démarrer — car elle cherche les libellés' +
    ' exacts. Si vous devez charger le SQL à la main, passez par une redirection' +
    ' système, qui est sans conversion d''encodage :')
Write-Output '    cmd /c "mysql -u root --default-character-set=utf8mb4 portefeuille_initiatives < database\migration\V1__baseline.sql"'