# Painel local SMS Massa

Estrutura inicial do futuro painel Windows do SMS Massa. Esta etapa contém apenas o shell visual, a navegação, a tela de dispositivos e um servidor Fastify local com leitura de saúde/inventário vazio.

Não há envio remoto, pareamento real, persistência SQLite, autenticação ou comandos que alterem o Android. O serviço de SMS nativo permanece exclusivamente no projeto Android.

## Requisitos

- Windows 10/11
- Node.js 20 ou superior
- npm

## Executar no Windows

Abra dois terminais PowerShell na pasta `desktop/`:

```powershell
npm install
```

Terminal 1 — API local:

```powershell
npm run dev:server
```

Terminal 2 — interface:

```powershell
npm run dev
```

Abra `http://localhost:5173`. A API fica restrita a `127.0.0.1:8787`; o Vite encaminha `/api` para ela.

## Verificar compilação

```powershell
npm run typecheck
npm run build
```

O build do painel é independente do build Android. Na raiz do repositório, o app móvel continua usando `npm test`, `npm run build`, `npx cap sync android` e Gradle.

## Próximas fronteiras arquiteturais

Antes de implementar pareamento ou envio remoto, definir autenticação mútua, IDs idempotentes, isolamento de dados, reconciliação offline, limites de lote e validação autoritativa no Android. Essas decisões não fazem parte desta etapa.
