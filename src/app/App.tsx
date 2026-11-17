import { useEffect } from 'react';
import { useHashRouter } from '../hooks/useHashRouter';
import { SmsAppProvider, useSmsApp } from '../contexts/SmsAppContext';
import { AppShell } from '../components/layout/AppShell';
import { DashboardPage } from '../pages/DashboardPage';
import { ContactsPage } from '../pages/ContactsPage';
import { CampaignsPage } from '../pages/CampaignsPage';
import { QueuePage } from '../pages/QueuePage';
import { SendSmsPage } from '../pages/SendSmsPage';
import { ListsPage } from '../pages/ListsPage';
import { TemplatesPage } from '../pages/TemplatesPage';
import { HistoryPage } from '../pages/HistoryPage';
import { LaboratoryPage } from '../pages/LaboratoryPage';
import { SettingsPage } from '../pages/SettingsPage';
import type { AppRoute } from './routes';

export function App() {
  return <SmsAppProvider><RoutedApp /></SmsAppProvider>;
}

function RoutedApp() {
  const { route, navigate } = useHashRouter();
  const { setNotice } = useSmsApp();
  useEffect(() => { setNotice(null); }, [route, setNotice]);
  return <AppShell route={route} onNavigate={navigate}>{renderPage(route, navigate)}</AppShell>;
}

function renderPage(route: AppRoute, navigate: (route: AppRoute) => void) {
  switch (route) {
    case 'contacts': return <ContactsPage onNavigate={navigate} />;
    case 'campaigns': return <CampaignsPage />;
    case 'queue': return <QueuePage />;
    case 'send': return <SendSmsPage />;
    case 'lists': return <ListsPage onNavigate={navigate} />;
    case 'templates': return <TemplatesPage />;
    case 'history': return <HistoryPage />;
    case 'laboratory': return <LaboratoryPage />;
    case 'settings': return <SettingsPage />;
    case 'home': return <DashboardPage onNavigate={navigate} />;
  }
}

