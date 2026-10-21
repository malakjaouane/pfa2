import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './AuthContext';
import Layout from './components/Layout';
import RequireAuth from './components/RequireAuth';
import LandingPage from './pages/LandingPage';
import DecouvrirPage from './pages/DecouvrirPage';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import InitiativesPage from './pages/InitiativesPage';
import InitiativeFormPage from './pages/InitiativeFormPage';
import InitiativeDetailPage from './pages/InitiativeDetailPage';
import UsersPage from './pages/UsersPage';
import RolesPage from './pages/RolesPage';
import RoadmapPage from './pages/RoadmapPage';
import DemandesPage from './pages/DemandesPage';
import HistoriquePage from './pages/HistoriquePage';
import ObjectifsPage from './pages/ObjectifsPage';
import MotDePassePage from './pages/MotDePassePage';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/decouvrir/:sujet" element={<DecouvrirPage />} />
        <Route path="/login" element={<LoginPage />} />

        <Route element={<RequireAuth><Layout /></RequireAuth>}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/initiatives" element={<InitiativesPage />} />
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
          <Route path="/initiatives/:id/modifier" element={<InitiativeFormPage />} />
          <Route path="/initiatives/:id" element={<InitiativeDetailPage />} />
          <Route
            path="/historique"
            element={
              <RequireAuth permissions={['INITIATIVE_CONSULTER']}>
                <HistoriquePage />
              </RequireAuth>
            }
          />
          <Route path="/mot-de-passe" element={<MotDePassePage />} />
          <Route
            path="/utilisateurs"
            element={
              <RequireAuth permissions={['UTILISATEUR_GERER']}>
                <UsersPage />
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