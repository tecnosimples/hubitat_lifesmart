/**
 * LifeSmart Dimmer
 *
 * TecnoSimples Tecnologia LTDA
 * contato@tecnosimples.com.br | (14) 99760-6885
 * (c) 2026 TecnoSimples - Todos os direitos reservados.
 *
 * Produto licenciado. Distribuido via Hubitat Package Manager.
 * Versao do pacote: 1.2.0
 */
 
metadata {
definition(
name: "LifeSmart Dimmer",
namespace: "tecnosimples",
author: "TecnoSimples",
importUrl: "https://raw.githubusercontent.com/tecnosimples/hubitat-lifesmart/main/LifeSmart-Dimmer.groovy"
) {
capability "Switch"
capability "SwitchLevel"
capability "ChangeLevel"
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
input name: "cmdTypeOn", type: "number", title: "Override ON Type", defaultValue: 0
input name: "cmdTypeLevel", type: "number", title: "Override Level Type", defaultValue: 0
}
}
}



def installed() {
log.info "[DIMMER] Instalado: ${device.displayName}"
initialize()
}
def updated() {
if (logEnable) log.debug "[DIMMER] Preferências salvas"
initialize()
}
def initialize() {
sendEvent(name: "deviceCls", value: settings.deviceCls ?: "")
if (device.currentValue("switch") == null) sendEvent(name: "switch", value: "off")
if (device.currentValue("level") == null) sendEvent(name: "level", value: 0)
}
def refresh() {
if (logEnable) log.debug "[DIMMER] Refresh — estado sincronizado via Parent/notify"
}



def on() {
if (logEnable) log.debug "[CMD] on() — ${device.displayName}"
int onType = (settings.cmdTypeOn != null) ? (settings.cmdTypeOn as Integer) : 0

sendCommand(onType, 255)
sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} ligado")
}
def off() {
if (logEnable) log.debug "[CMD] off() — ${device.displayName}"
int onType = (settings.cmdTypeOn != null) ? (settings.cmdTypeOn as Integer) : 0
sendCommand(onType, 0)
sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} desligado")
}



def setLevel(BigDecimal level) { setLevel(level, 0) }
def setLevel(BigDecimal level, BigDecimal duration) {

int lvl = Math.max(0, Math.min(100, level.intValue()))
if (logEnable) log.debug "[CMD] setLevel(${lvl}) — ${device.displayName}"
int levelType = (settings.cmdTypeLevel != null && settings.cmdTypeLevel != 0) ? (settings.cmdTypeLevel as Integer) : 207
int onType = (settings.cmdTypeOn != null && settings.cmdTypeOn != 0) ? (settings.cmdTypeOn as Integer) : 129

if (lvl > 0 && device.currentValue("switch") == "off") {
if (logEnable) log.debug "[CMD] Dimmer apagado — ligando antes de ajustar nível"
sendCommand(onType, 1)
}
sendCommand(levelType, lvl)
sendEvent(name: "level", value: lvl, unit: "%")
sendEvent(name: "switch", value: lvl > 0 ? "on" : "off")
}
def startLevelChange(String direction) {
int current = (device.currentValue("level") as Integer) ?: 50
int newLevel = direction == "up" ? Math.min(100, current + 10) : Math.max(0, current - 10)
setLevel(newLevel)
}
def stopLevelChange() {
if (logEnable) log.debug "[CMD] stopLevelChange — sem suporte nativo"
}



def handleEvent(Object val, Object cmdType, Map extraData = [:]) {
if (logEnable) log.debug "[EVENT] val=${val} type=${cmdType} extra=${extraData}"
int v = 0
if (val != null) {
try { v = val instanceof Number ? ((Number)val).intValue() : Integer.parseInt(val.toString()) }
catch (Exception ignored) {}
}
int ct = 0
try { ct = cmdType instanceof Number ? ((Number)cmdType).intValue() : Integer.parseInt(cmdType.toString()) }
catch (Exception ignored) {}
if (ct == 128 || ct == 206) { 
sendEvent(name: "switch", value: "off")
if (logEnable) log.debug "[EVENT] Dimmer desligado (nível preservado no Hubitat)"
} else if (ct == 129) { 
sendEvent(name: "switch", value: "on")
if (val != null && v > 1) {
sendEvent(name: "level", value: Math.max(1, Math.min(100, v)), unit: "%")
} else if (val == null || v <= 1) {
if (logEnable) log.debug "[EVENT] Ligado via ON binário — sincronizando nível..."
runIn(2, "requestSync")
}
} else if (ct == 207) { 
sendEvent(name: "switch", value: "on")
if (val != null && v > 0) {
sendEvent(name: "level", value: Math.max(1, Math.min(100, v)), unit: "%")
}
} else if (ct == 0) { 
if (val == null) {
if (logEnable) log.debug "[EVENT] Dimmer: notificação sem valor — ignorada"
} else if (v > 0) {
sendEvent(name: "level", value: Math.max(1, Math.min(100, v)), unit: "%")
sendEvent(name: "switch", value: "on")
} else { 
sendEvent(name: "switch", value: "off")
}
}
}
 
def requestSync() {
if (parent) {
try { parent.doPoll() } catch (Exception e) { log.error "[SYNC] Falha: ${e.message}" }
}
}



 
def getDevid() {
return settings.devid ?: ""
}
 
def getChannel() {
return settings.channel ?: ""
}
 
private void sendCommand(int cmdType, int val) {
if (!parent) {
log.error "[CMD] Parent não disponível"
return
}
String devid = settings.devid ?: ""
String channel = settings.channel ?: "P1"
if (!devid) { log.error "[CMD] devid não configurado!"; return }

if (channel.startsWith("AI_IR_")) {
if (logEnable) log.debug "[CMD] Canal virtual IR '${channel}' — ignorado no dimmer"
return
}
try {
parent.sendDeviceCommand(devid, channel, cmdType, val)
} catch (Exception e) {
log.error "[CMD] Falha ao chamar parent: ${e.message}"
}
}
