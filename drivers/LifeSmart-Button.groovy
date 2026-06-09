/**
 * LifeSmart Button
 *
 * TecnoSimples Tecnologia LTDA
 * contato@tecnosimples.com.br | (14) 99760-6885
 * (c) 2026 TecnoSimples - Todos os direitos reservados.
 *
 * Produto licenciado. Distribuido via Hubitat Package Manager.
 * Versao do pacote: 1.1.1
 */
 
metadata {
definition(
name: "LifeSmart Button",
namespace: "tecnosimples",
author: "TecnoSimples"
) {
capability "Sensor"
capability "PushableButton"
capability "HoldableButton"
capability "Battery"
capability "Refresh"
attribute "deviceType", "string"
attribute "deviceCls", "string"
}
preferences {
section("Configurações") {
input name: "logEnable", type: "bool", title: "Ativar logs de debug", defaultValue: false
}
section("<small>Configurações Internas</small>") {
input name: "devid", type: "text", title: "ID Interno", required: true
input name: "channel", type: "text", title: "Canal", required: true
input name: "deviceCls", type: "text", title: "Classe", required: false
}
}
}
def installed() { initialize() }
def updated() { initialize() }
def initialize() {
sendEvent(name: "deviceType", value: "button")
sendEvent(name: "numberOfButtons", value: 2)
}
def refresh() {
if (parent) parent.refresh()
}
 
def handleEvent(Object val, Object cmdType, Map extraData = [:]) {
if (logEnable) log.debug "[EVENT] Button: val=${val} type=${cmdType}"
if (val == null) return
int ct = cmdType instanceof Number ? cmdType.intValue() : 0
if (ct != 79) return 
int v = val instanceof Number ? val.intValue() : Integer.parseInt(val.toString())
switch (v) {
case 1:
log.info "${device.displayName} — 1 clique"
sendEvent(name: "pushed", value: 1, isStateChange: true,
descriptionText: "${device.displayName} button 1 pushed")
break
case 2:
log.info "${device.displayName} — 2 cliques"
sendEvent(name: "pushed", value: 2, isStateChange: true,
descriptionText: "${device.displayName} button 2 pushed")
break
case 255:
log.info "${device.displayName} — manter pressionado"
sendEvent(name: "held", value: 1, isStateChange: true,
descriptionText: "${device.displayName} button 1 held")
break
default:
if (logEnable) log.warn "[BUTTON] val desconhecido: ${v}"
}
}
def getDevid() { return settings.devid ?: "" }
def getChannel() { return settings.channel ?: "" }
