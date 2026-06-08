# LifeSmart Local — Hubitat (TecnoSimples)

Integração local (zero-cloud) entre Hubitat e LifeSmart Smart Station.
**Produto licenciado da TecnoSimples Tecnologia LTDA.**

## Instalação (via Hubitat Package Manager)

1. No HPM, escolha **Install** → **Import a custom repository** e cole:
   `https://raw.githubusercontent.com/tecnosimples/hubitat_lifesmart/main/repository.json`
2. Instale o pacote **LifeSmart Local** (o HPM baixa todos os drivers).
3. Em **Devices**, crie um **Virtual Device** com o driver **LifeSmart Smart Station Local**.
4. Configure IP, usuário e senha da central. Copie o **HubUID** exibido na página do device
   e envie para **contato@tecnosimples.com.br** para receber sua **chave de licença**.
5. Cole a chave no campo `licenseKey` e use o comando **Ativar Licença**.
6. Use **Descobrir Dispositivos** — os demais dispositivos aparecem automaticamente.

> Sem uma chave de licença válida, os comandos ficam bloqueados.

## Suporte
TecnoSimples Tecnologia LTDA · contato@tecnosimples.com.br · (14) 99760-6885
