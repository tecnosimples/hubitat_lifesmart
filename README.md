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

## Configuração

1. Em **Devices**, crie um **Virtual Device** com o driver **LifeSmart Smart Station Local**.
2. Copie o **HubUID** exibido na página do device e envie para **contato@tecnosimples.com.br**
   para receber sua **chave de licença** (a chave é única por hub).
3. Cole a chave no campo `licenseKey` e clique no comando **Ativar Licença**.
4. Preencha **IP**, **usuário** e **senha** da central e clique em **Save Preferences** —
   o driver conecta automaticamente (e reconecta sozinho após reinício do hub).
5. Clique em **Descobrir Dispositivos** — os demais dispositivos aparecem automaticamente.

> Sem uma chave de licença válida, o driver não conecta nem controla nada.

## Comandos do dispositivo (Parent)

| Comando | O que faz |
|---|---|
| **Ativar Licença** | Valida a chave colada em `licenseKey`. Necessário antes de tudo. |
| **Refresh** | Reconecta à central e relê o estado de todos os dispositivos. |
| **Descobrir Dispositivos** | Cria/atualiza os child devices a partir do que foi lido (pode rodar quantas vezes quiser — não duplica). |
| **Limpar Cache IR** | Remove os controles remotos IR memorizados (dispositivos SPOT/IR). Use ao reconfigurar IR. |

> **Save Preferences** e o reinício do hub já reconectam sozinhos — o **Refresh** é só para forçar uma releitura manual.

## Suporte
TecnoSimples Tecnologia LTDA · contato@tecnosimples.com.br · (14) 99760-6885
