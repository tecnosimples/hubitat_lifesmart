# LifeSmart Local — Hubitat (TecnoSimples)

Integração local (zero-cloud) entre Hubitat e LifeSmart Smart Station.
**Produto licenciado da TecnoSimples Tecnologia LTDA.**

## Instalação (via Hubitat Package Manager)

**Forma mais simples — instalar direto pelo manifesto:**

1. No HPM, escolha **Install** → **From a URL**.
2. Cole a URL do **packageManifest.json** (atenção: é o manifesto, NÃO o repository.json):
   `https://raw.githubusercontent.com/tecnosimples/hubitat_lifesmart/main/packageManifest.json`
3. Avance — deve aparecer **"You are about to install LifeSmart Local"**. Conclua (o HPM baixa todos os drivers).

> Se aparecer **"install null"**, você colou a URL errada na opção errada. A opção **"From a URL"** espera o **`packageManifest.json`**. O `repository.json` só serve para a opção **"Add a Custom Repository" → "Browse by Tags"**.

4. Em **Devices**, crie um **Virtual Device** com o driver **LifeSmart Smart Station Local**.
4. Configure IP, usuário e senha da central. Copie o **HubUID** exibido na página do device
   e envie para **contato@tecnosimples.com.br** para receber sua **chave de licença**.
5. Cole a chave no campo `licenseKey` e use o comando **Ativar Licença**.
6. Use **Descobrir Dispositivos** — os demais dispositivos aparecem automaticamente.

> Sem uma chave de licença válida, os comandos ficam bloqueados.

## Suporte
TecnoSimples Tecnologia LTDA · contato@tecnosimples.com.br · (14) 99760-6885
