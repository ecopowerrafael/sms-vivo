# Checklist Redmi 12 — Android 14 / HyperOS 1

- [ ] Instalar o APK debug por instalação direta.
- [ ] Confirmar que o app aparece como SMS Vivo e abre sem crash.
- [ ] Conceder ou diagnosticar `SEND_SMS`.
- [ ] Confirmar que a assinatura Vivo aparece e selecionar o slot correto.
- [ ] Testar número nacional normalizado e mensagem GSM-7.
- [ ] Testar mensagem Unicode e multipartes; confirmar que a confirmação final só aparece após todas as partes.
- [ ] Confirmar resultado de solicitação e distinguir `SENT` de entrega.
- [ ] Desativar/reativar restrição de bateria e repetir o teste.
- [ ] Bloquear a tela durante um teste unitário.
- [ ] Registrar código Android, sinal, SIM e horário em caso de falha.
- [ ] Abrir Laboratório SMS, informar apenas números próprios/autorizados e confirmar o teste.
- [ ] Comparar intervalo zero e intervalo configurado; registrar tempo de retorno e código Android.
- [ ] Confirmar interrupção automática ao receber falha, resultado desconhecido ou timeout.
- [ ] Exportar CSV do Laboratório e conferir que não há dados fictícios.
- [ ] Cadastrar um contato não autorizado e confirmar que a fila o rejeita.
- [ ] Autorizar um contato, adicioná-lo à campanha rápida e confirmar explicitamente.
- [ ] Definir quantidade por execução, iniciar a fila e verificar que os demais pendentes permanecem aguardando.
- [ ] Verificar a notificação persistente com ações de pausar/interromper.
- [ ] Pausar, retomar e interromper; confirmar que pendentes cancelados não são reenviados.
- [ ] Encerrar o processo durante `PROCESSING` e confirmar estado `UNKNOWN` ao reabrir.

Automatizado no CI: 13 testes de normalização, importação CSV/XLSX, duplicados, escape de CSV, variáveis, segmentação, fila e filtros de histórico; TypeScript, build web e compilação Android são executados no workflow. Dependente do aparelho: permissões, SIM Vivo, modem, cobertura, confirmação de entrega e comportamento do HyperOS.
