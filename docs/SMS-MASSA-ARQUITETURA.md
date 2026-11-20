# SMS Massa — arquitetura de integração futura

## Escopo desta etapa

O SMS Massa é um aplicativo Android independente do SMS Vivo. Nesta etapa não há painel desktop, servidor remoto nem endpoint de envio. O app inicia com banco Room vazio e executa campanhas somente após ação explícita do usuário.

## Identidade e isolamento

- Application ID e namespace: `br.com.smsmassa.app`.
- Banco privado: `sms-massa.db`, no sandbox do aplicativo.
- Authorities e ações internas derivadas do application ID.
- Permissões, notificações, listas, contatos, campanhas, fila e histórico são próprios do app.
- A instalação não copia dados do SMS Vivo e não atualiza o pacote `br.com.smsvivo.app`.
- A assinatura de release usa Secrets exclusivos `SMS_MASSA_*` no workflow `android-sms-massa-apk.yml`.

## Arquitetura planejada

O painel local futuro será composto por React + Vite na interface, Fastify na API local e SQLite como armazenamento do computador Windows. A comunicação ocorrerá pela rede Wi-Fi local, com pareamento explícito, autenticação por dispositivo e autorização por operação.

O Android continuará sendo a autoridade para telefonia: recebe uma campanha pareada, valida os destinatários, persiste contatos/campanhas/itens no Room e executa a fila pelo SIM usando o motor nativo existente. O painel deverá tratar o Android como dispositivo potencialmente offline e reconciliar estados por identificadores idempotentes.

## Domínios do painel

1. Contatos e listas, incluindo importações XLSX/CSV e deduplicação.
2. Modelos de mensagem e variáveis.
3. Campanhas, lotes e intervalos.
4. Dispositivos Android pareados e estado de conectividade.
5. Histórico, resultados, falhas e reprocessamentos autorizados.

## Contrato futuro (não implementar agora)

O contrato deverá usar IDs estáveis, versão de payload e `idempotencyKey` por campanha/ação. Cada comando deve declarar tenant/contexto, dispositivo alvo, destinatários já normalizados e política de lote. O Android deve validar novamente autorização, limites e formato antes de persistir ou enviar.

Webhooks ou sincronizações podem ser duplicados, atrasados ou fora de ordem. O processamento futuro deve ser idempotente, ter timeout, retry com backoff limitado, estados terminais e logs sem conteúdo de mensagem ou números completos. Nenhum segredo deve ser enviado ao frontend ou armazenado em texto puro.

## Segurança e operação

- O painel deve escutar apenas na interface local necessária e exigir pareamento autenticado.
- Toda operação deve validar ownership do dispositivo e isolamento de dados no servidor local.
- Não confiar em permissões ou validações feitas apenas na UI.
- Aplicar limites de taxa e confirmar ações de envio em massa.
- Registrar métricas de fila, latência, retries e erros sem PII desnecessária.
- Planejar migrações compatíveis e exportação/backup antes de alterar o schema.

## Fora do escopo

Não criar endpoints de envio, serviço pago, gateway SMS, servidor online, sincronização automática ou execução de campanhas após instalação nesta etapa.
