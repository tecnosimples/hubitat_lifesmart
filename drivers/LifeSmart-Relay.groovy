/**
 * LifeSmart Relay
 *
 * TecnoSimples Tecnologia LTDA
 * contato@tecnosimples.com.br | (14) 99760-6885
 * (c) 2026 TecnoSimples - Todos os direitos reservados.
 *
 * Produto licenciado. Distribuido via Hubitat Package Manager.
 * Versao do pacote: 1.3.1
 */
 
metadata {
definition(
name: "LifeSmart Relay",
namespace: "tecnosimples",
author: "TecnoSimples",
importUrl: "https://raw.githubusercontent.com/tecnosimples/hubitat-lifesmart/main/LifeSmart-Relay.groovy"
) {
capability "Switch"
capability "Refresh"
capability "Actuator"
attribute "deviceCls", "string"
}
preferences {
section("Configurações do Dispositivo") {
input name: "logEnable", type: "bool", title: "Ativar logs de debug", defaultValue: false
}
section("<small>Configurações Internas (Não alterar)</small>") {
input name: "devid", type: "text", title: "ID Interno", required: true
input name: "channel", type: "text", title: "Canal", required: true
input name: "deviceCls", type: "text", title: "Classe", required: false
}
}
}



def installed() {
log.info "[RELAY] Instalado: ${device.displayName}"
initialize()
}
def updated() {
if (logEnable) log.debug "[RELAY] Preferências salvas"
initialize()
}
def initialize() {
sendEvent(name: "deviceCls", value: settings.deviceCls ?: "")
if (device.currentValue("switch") == null) sendEvent(name: "switch", value: "off")
}
def refresh() {
if (logEnable) log.debug "[RELAY] Refresh — estado sincronizado via Parent/notify"
}



def on() {
if (logEnable) log.debug "[CMD] on() — ${device.displayName}"
sendCommand(0, 255)
sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} ligado")
}
def off() {
if (logEnable) log.debug "[CMD] off() — ${device.displayName}"
sendCommand(0, 0)
sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} desligado")
}



def handleEvent(Object val, Object cmdType, Map extraData = [:]) {
if (logEnable) log.debug "[EVENT] val=${val} type=${cmdType} extra=${extraData}"
int ct = 0
try { ct = cmdType instanceof Number ? ((Number)cmdType).intValue() : Integer.parseInt(cmdType.toString()) }
catch (Exception ignored) {}
if (ct == 206 || ct == 128) {
sendEvent(name: "switch", value: "off")
} else if (ct == 207 && val == null) {
sendEvent(name: "switch", value: "on")
} else if (val != null) {
int v = 0
try { v = val instanceof Number ? ((Number)val).intValue() : Integer.parseInt(val.toString()) }
catch (Exception ignored) {}
sendEvent(name: "switch", value: v > 0 ? "on" : "off")
}

}



 
def getDevid() { return settings.devid ?: "" }
 
def getChannel() { return settings.channel ?: "" }
 
private void sendCommand(int cmdType, int val) {
if (!parent) { log.error "[CMD] Parent não disponível"; return }
String devid = settings.devid ?: ""
String channel = settings.channel ?: "P1"
if (!devid) { log.error "[CMD] devid não configurado!"; return }
if (channel.startsWith("AI_IR_")) {
if (logEnable) log.debug "[CMD] Canal virtual IR '${channel}' — ignorado no relay"
return
}
try {
parent.sendDeviceCommand(devid, channel, cmdType, val)
} catch (Exception e) {
log.error "[CMD] Falha ao chamar parent: ${e.message}"
}
}
