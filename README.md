# SMS Vivo

Aplicativo Android offline-first para envio de SMS pelo chip do próprio aparelho, usando `SmsManager` e seleção explícita da assinatura. O núcleo atual inclui diagnóstico, permissão, envio unitário, contatos, importação CSV/XLSX, Room, fila persistente e serviço foreground iniciado pelo usuário.

## Desenvolvimento

```bash
npm ci
npm test
npm run build
npx cap add android   # apenas na primeira configuração
npx cap sync android
cd android && ./gradlew assembleDebug
```

O build Android usa Java 21.

O APK debug fica em `android/app/build/outputs/apk/debug/app-debug.apk`. No GitHub, o workflow `Android APK` publica o mesmo arquivo como artifact em cada push para `main`/`develop` ou execução manual.

Na execução manual do workflow, a opção `build_release` gera também o APK release. O workflow exige os Secrets persistentes `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD`; sem eles, a execução falha antes de produzir um release. A mesma keystore deve ser mantida para que futuras versões atualizem a instalação existente sem apagar o SQLite.

### Conectar a um repositório GitHub e distribuir

O checkout local contém o commit inicial, mas não possui um remoto por padrão. Em um repositório vazio autorizado, execute:

```bash
git remote add origin https://github.com/USUARIO/REPOSITORIO.git
git push -u origin master
```

Depois do push, abra a aba **Actions**, aguarde o workflow `Android APK` e baixe o artifact `sms-vivo-debug-apk`. Para uma distribuição recorrente, use uma GitHub Release e anexe o APK gerado; no WordPress, publique um botão apontando para a URL da Release. Não envie o APK como anexo do Gmail nem desative filtros de upload.

Para um release assinado, execute o workflow manualmente com `build_release=true` depois de configurar os quatro Secrets de keystore. Se uma keystore diferente for usada, o Android não aceitará a atualização sobre a instalação anterior; será necessário desinstalar, perdendo os dados locais.

## Arquitetura

React/TypeScript é a camada de apresentação. A fonte de verdade dos contatos e da fila é o Room nativo (`sms-vivo.db`); a ponte Capacitor apenas chama operações nativas. `SmsResultReceiver` persiste confirmações do Android mesmo quando a WebView não está aberta. A fila usa estados `PENDING`, `PROCESSING`, `SENT`, `DELIVERED`, `FAILED`, `UNKNOWN`, `CANCELLED` e `SKIPPED`. Um único worker processa campanhas em ordem; intervalo e lote da campanha são autoritativos, enquanto os controles da fila servem como fallback para itens sem campanha. Callbacks multipartes são idempotentes por request, parte e fase. Itens em `PROCESSING` são marcados como `UNKNOWN` somente ao detectar encerramento inesperado do serviço ou ao reabrir o app sem serviço ativo, sem reenvio automático.

Importações começam como não autorizadas. O servidor nativo da fila rejeita contatos não autorizados ou descadastrados. A campanha rápida pede confirmação antes de enfileirar e o início da fila é uma ação separada.

## Primeiro teste no Redmi 12

1. Instale o APK e permita notificações quando solicitado.
2. Conceda `SEND_SMS` e `READ_PHONE_STATE` nas configurações do Android, se o sistema oferecer as solicitações.
3. Abra o app, aguarde o diagnóstico e selecione a assinatura Vivo correta.
4. Use um número próprio/autorizado e envie uma mensagem curta.
5. Confirme o resultado no histórico e no app de mensagens do destinatário. `SENT` não significa entrega; em multipartes, o estado final só é emitido após todas as partes.
6. Em HyperOS, permita notificações e remova restrições de bateria/início automático quando o processamento em segundo plano for necessário.

O processamento em segundo plano usa `ForegroundService` iniciado explicitamente, notificação persistente e subtipo `specialUse` declarado no Manifest como `user-initiated offline SMS queue processing`. O sistema/HyperOS ainda pode interromper serviços; o app não promete execução ininterrupta.

## Limitações conhecidas

O envio é real somente no dispositivo com telefonia, SIM ativo, cobertura e permissão concedida. CI valida a lógica e a compilação, nunca o envio. Listas, campanhas rápidas, modelos persistidos, exportação CSV, filtros de histórico e o Laboratório SMS já estão integrados. Ainda falta configurar/verificar os quatro Secrets no repositório e realizar validação física no Redmi 12. Não há backend, Firebase, gateway ou mecanismo de evasão de limites da operadora.

O Laboratório aceita números separados por linha, vírgula ou ponto e vírgula, exige confirmação explícita, mede intervalo entre solicitações e tempo até o retorno final do Android, persiste o resultado e interrompe o teste em falha, resultado desconhecido ou timeout de 30 segundos. Ele não tenta repetir ou contornar limites de taxa. A fila permite limitar a quantidade processada em cada execução; zero significa todos os pendentes.

Os eventos técnicos persistidos podem ser consultados na seção “Diagnóstico técnico” do app. Eles registram categorias, códigos e horários; o histórico de SMS fica separado e mantém os estados `SENT`, `DELIVERED`, `FAILED` e `UNKNOWN`.

`npm audit --omit=dev --audit-level=high` foi executado após a troca do parser de planilhas e retornou zero vulnerabilidades na árvore de produção. O bundle web ainda emite um aviso de tamanho por incluir o parser XLSX; isso é uma otimização pendente, não uma falha funcional.
