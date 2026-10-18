# Diagrammes de référence

Onze diagrammes décrivent le système (cahier des charges, section 10). Ils sont
cohérents avec le cahier des charges, `database/schema.sql` et l'API (`docs/API.md`).

| N° | Diagramme | Image |
|---|---|---|
| 01 | Cas d'utilisation | `images/01-cas-utilisation.png` |
| 02 | Classes | `images/02-classes.png` |
| 03 | BPMN du processus de gestion d'une initiative | `images/03-bpmn-processus.png` |
| 04 | MCD | `images/04-mcd.png` |
| 05 | MLD | `images/05-mld.png` |
| 06 | MPD (MySQL 8) | `images/06-mpd.png` |
| 07 | Séquence : authentification | `images/07-sequence-authentification.png` |
| 08 | Séquence : création d'une initiative | `images/08-sequence-creer-initiative.png` |
| 09 | Séquence : consultation de la liste | `images/09-sequence-liste-initiatives.png` |
| 10 | Séquence : changement de statut | `images/10-sequence-changer-statut.png` |
| 11 | Séquence : mise à jour d'une étape et recalcul de l'avancement | `images/11-sequence-etape-avancement.png` |

Les fichiers `diagrammes-mermaid.md` et `plantuml/*.puml` sont des diagrammes
complémentaires (états, activité, composants, déploiement, KPI). Le cycle de vie
des statuts est décrit au paragraphe 7.2 du cahier des charges.
