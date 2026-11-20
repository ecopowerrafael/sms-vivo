import Fastify from 'fastify';
import cors from '@fastify/cors';

type DeviceStatus = 'WAITING_FOR_PAIRING' | 'PAIRED' | 'OFFLINE';

type Device = {
  id: string;
  name: string;
  status: DeviceStatus;
};

const app = Fastify({ logger: true });
const port = Number(process.env.DESKTOP_PORT ?? 8787);

await app.register(cors, { origin: ['http://localhost:5173', 'http://127.0.0.1:5173'] });

app.get('/api/health', async () => ({ service: 'sms-massa-desktop', status: 'ok' }));

app.get<{ Reply: { devices: Device[] } }>('/api/devices', async () => ({ devices: [] }));

await app.listen({ host: '127.0.0.1', port });
