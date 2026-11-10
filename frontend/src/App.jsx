import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './AuthContext';
import Layout from './components/Layout';
import RequireAuth from './components/RequireAuth';
import LandingPage from './pages/LandingPage';
import DecouvrirPage from './pages/DecouvrirPage';
import LoginPage from './pages/LoginPage';
import InscriptionPage from './pages/InscriptionPage';
import DashboardPage from './pages/DashboardPage';
import InitiativesPage from './pages/InitiativesPage';
import InitiativeFormPage from './pages/InitiativeFormPage';
import InitiativeDetailPage from './pages/InitiativeDetailPage';
import PriorisationPage from './pages/PriorisationPage';
import ImportInitiativesPage from './pages/ImportInitiativesPage';
import OnboardingPage from './pages/OnboardingPage';
import UsersPage from './pages/UsersPage';
import RolesPage from './pages/RolesPage';
import RoadmapPage from './pages/RoadmapPage';
import DemandesPage from './pages/DemandesPage';
import ActiviteSecuritePage from './pages/ActiviteSecuritePage';
import ObjectifsPage from './pages/ObjectifsPage';
import MonEspacePage from './pages/MonEspacePage';
import RisquesPage from './pages/RisquesPage';
import DepensesPage from './pages/DepensesPage';
import MotDePassePage from './pages/MotDePassePage';
import AssistantPage from './pages/AssistantPage';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/decouvrir/:sujet" element={<DecouvrirPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/inscription" element={<InscriptionPage />} />

        <Route element={<RequireAuth><Layout /></RequireAuth>}>
          <Route path="/onboarding" element={<OnboardingPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/mon-espace" element={<MonEspacePage />} />
          <Route path="/initiatives" element={<InitiativesPage />} />
          <Route
            path="/priorisation"
            element={
              <RequireAuth permissions={['INITIATIVE_CONSULTER']}>
                <PriorisationPage />
              </RequireAuth>
            }
          />
          <Route path="/initiatives/import"
            element={
              <RequireAuth permissions={['INITIATIVE_CREER']}>
                <ImportInitiativesPage />
              </RequireAuth>
            }
          />
          <Route path="/initiatives/nouvelle" element={<InitiativeFormPage />} />
          <Route path="/roadmap" element={<RoadmapPage />} />
          <Route
            path="/demandes"
            element={
              <RequireAuth permissions={['DEMANDE_SOUMETTRE', 'DEMANDE_EVALUER']}>
                <DemandesPage />
              </RequireAuth>
            }
          />
          <Route
            path="/objectifs"
            element={
              <RequireAuth permissions={['INITIATIVE_CONSULTER']}>
                <ObjectifsPage />
              </RequireAuth>
            }
          />
          {/* Registre des risques : lecture ouverte à qui consulte les
              initiatives, écriture (suppression) refusée par le serveur
              sans RISQUE_GERER. */}
          <Route
            path="/risques"
            element={
              <RequireAuth permissions={['INITIATIVE_CONSULTER']}>
                <RisquesPage />
              </RequireAuth>
            }
          />
          {/* Dépenses : même règle que le menu (BUDGET_CONSULTER ou
              DEPENSE_SAISIR) ; l'API exige DEPENSE_SAISIR sur chaque appel. */}
          <Route
            path="/depenses"
            element={
              <RequireAuth permissions={['BUDGET_CONSULTER', 'DEPENSE_SAISIR']}>
                <DepensesPage />
              </RequireAuth>
            }
          />
          {/* Journal d'audit : lecture seule, réservée à UTILISATEUR_GERER. */}
          <Route
            path="/audit"
            element={
              <RequireAuth permissions={['UTILISATEUR_GERER']}>
                <ActiviteSecuritePage ongletInitial="audit" />
              </RequireAuth>
            }
          />
          <Route path="/initiatives/:id/modifier" element={<InitiativeFormPage />} />
          <Route path="/initiatives/:id" element={<InitiativeDetailPage />} />
          {/* Page fusionnée « Activité et sécurité » (4 registres en onglets) ;
              les anciennes routes restent valides et ouvrent l'onglet dédié. */}
          <Route
            path="/activite"
            element={
              <RequireAuth>
                <ActiviteSecuritePage />
              </RequireAuth>
            }
          />
          <Route
            path="/historique"
            element={
              <RequireAuth permissions={['INITIATIVE_CONSULTER']}>
                <ActiviteSecuritePage ongletInitial="historique" />
              </RequireAuth>
            }
          />
          <Route path="/mot-de-passe" element={<MotDePassePage />} />
          <Route
            path="/assistant"
            element={
              <RequireAuth permissions={['IA_UTILISER']}>
                <AssistantPage />
              </RequireAuth>
            }
          />
          <Route
            path="/utilisateurs"
            element={
              <RequireAuth permissions={['UTILISATEUR_GERER']}>
                <UsersPage />
              </RequireAuth>
            }
          />
          <Route
            path="/historique-connexions"
            element={
              <RequireAuth permissions={['UTILISATEUR_GERER']}>
                <ActiviteSecuritePage ongletInitial="connexions" />
              </RequireAuth>
            }
          />
          <Route
            path="/roles"
            element={
              <RequireAuth permissions={['ROLE_GERER', 'PERMISSION_GERER']}>
                <RolesPage />
              </RequireAuth>
            }
          />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}