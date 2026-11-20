import { useEffect, useState } from 'react';

type DeviceStatus = 'WAITING_FOR_PAIRING' | 'PAIRED' | 'OFFLINE';

type Device = {
  id: string;
  name: string;
  status: DeviceStatus;
};

type Panel = 'overview' | 'devices' | 'contacts' | 'campaigns';

const panels: Array<{ id: Panel; label: string; icon: string; disabled?: boolean }> = [
  { id: 'overview', label: 'Visão geral', icon: '⌂' },
  { id: 'devices', label: 'Dispositivos', icon: '▣' },
  { id: 'contacts', label: 'Contatos', icon: '◎', disabled: true },
  { id: 'campaigns', label: 'Campanhas', icon: '✦', disabled: true },
];

const statusLabels: Record<DeviceStatus, string> = {
  WAITING_FOR_PAIRING: 'Aguardando pareamento',
  PAIRED: 'Pareado',
  OFFLINE: 'Offline',
};

function App() {
  const [panel, setPanel] = useState<Panel>('devices');
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);
  const [serverOnline, setServerOnline] = useState(false);

  async function loadDevices() {
    setLoading(true);
    try {
      const response = await fetch('/api/devices');
      if (!response.ok) throw new Error('Resposta inválida do painel local');
      const data = (await response.json()) as { devices: Device[] };
      setDevices(data.devices);
      setServerOnline(true);
    } catch {
      setServerOnline(false);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadDevices();
  }, []);

  const activePanel = panels.find((item) => item.id === panel);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">SM</div>
          <div>
            <strong>SMS Massa</strong>
            <span>Painel local</span>
          </div>
        </div>

        <nav className="nav-list" aria-label="Navegação principal">
          <p className="nav-caption">Workspace</p>
          {panels.map((item) => (
            <button
              className={`nav-item ${panel === item.id ? 'active' : ''}`}
              disabled={item.disabled}
              key={item.id}
              onClick={() => setPanel(item.id)}
              type="button"
            >
              <span aria-hidden="true">{item.icon}</span>
              {item.label}
              {item.disabled && <small>Em breve</small>}
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <span className={`connection-dot ${serverOnline ? 'online' : ''}`} />
          <div>
            <strong>{serverOnline ? 'Painel conectado' : 'Servidor offline'}</strong>
            <span>Somente rede local</span>
          </div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div>
            <span className="eyebrow">SMS MASSA / LOCAL</span>
            <h1>{activePanel?.label ?? 'Painel'}</h1>
          </div>
          <div className="topbar-status">
            <span className="status-pill"><i /> Ambiente de homologação</span>
            <button className="avatar" type="button" title="Perfil local">CM</button>
          </div>
        </header>

        {panel === 'devices' && (
          <DevicesPage devices={devices} loading={loading} onRefresh={() => void loadDevices()} />
        )}
        {panel !== 'devices' && <ComingSoonPage label={activePanel?.label ?? 'Esta área'} />}
      </main>
    </div>
  );
}

function DevicesPage({ devices, loading, onRefresh }: { devices: Device[]; loading: boolean; onRefresh: () => void }) {
  return (
    <div className="page-content">
      <section className="hero-card">
        <div>
          <span className="hero-kicker">PRIMEIRO PASSO</span>
          <h2>Conecte um dispositivo Android</h2>
          <p>O painel local usará o celular como executor. Faça o pareamento quando essa etapa estiver disponível.</p>
        </div>
        <div className="hero-orbit" aria-hidden="true"><span>⌁</span><b>+</b></div>
      </section>

      <div className="section-heading">
        <div>
          <span className="eyebrow">INVENTÁRIO LOCAL</span>
          <h2>Dispositivos</h2>
        </div>
        <button className="outline-button" onClick={onRefresh} type="button">↻ Atualizar</button>
      </div>

      <section className="device-panel">
        {loading ? <div className="empty-state"><div className="spinner" /><p>Consultando o servidor local…</p></div> : devices.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">⌁</div>
            <h3>Nenhum dispositivo pareado</h3>
            <p>Quando um Android for pareado, ele aparecerá aqui com status, versão e disponibilidade.</p>
            <span className="muted-note">Pareamento remoto ainda não está habilitado nesta versão.</span>
          </div>
        ) : devices.map((device) => (
          <article className="device-row" key={device.id}>
            <div className="device-icon">▣</div>
            <div><strong>{device.name}</strong><span>{device.id}</span></div>
            <span className={`device-status ${device.status.toLowerCase()}`}>{statusLabels[device.status]}</span>
          </article>
        ))}
      </section>

      <p className="scope-note"><strong>Escopo atual:</strong> estrutura visual e leitura do inventário local. Nenhum comando de envio remoto está implementado.</p>
    </div>
  );
}

function ComingSoonPage({ label }: { label: string }) {
  return <div className="coming-soon"><span className="empty-icon">✦</span><h2>{label} em preparação</h2><p>Esta área será adicionada depois do pareamento seguro do dispositivo.</p></div>;
}

export default App;
