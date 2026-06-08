/**
 * LifeSmart Device
 *
 * TecnoSimples Tecnologia LTDA
 * contato@tecnosimples.com.br | (14) 99760-6885
 * (c) 2026 TecnoSimples - Todos os direitos reservados.
 *
 * Produto licenciado. Distribuido via Hubitat Package Manager.
 * Versao do pacote: 1.0.1
 */
 
metadata {
definition(
name: "LifeSmart Device",
namespace: "tecnosimples",
author: "TecnoSimples",
importUrl: "https://raw.githubusercontent.com/tecnosimples/hubitat-lifesmart/main/LifeSmart-Child.groovy"
) {
capability "Refresh"
capability "Actuator"
capability "Sensor"
capability "Switch"
capability "SwitchLevel"
capability "WindowShade"
capability "PushableButton"

capability "MotionSensor"
capability "ContactSensor"
capability "TemperatureMeasurement"
capability "RelativeHumidityMeasurement"
attribute "deviceType", "string"
attribute "deviceCls", "string"

command "sendIR", [
[name: "remoteId", type: "STRING", description: "ID do controle remoto (ex: AI_IR_8b11_1780402846)"],
[name: "button", type: "STRING", description: "Nome da tecla (ex: VOLUMEUP)"]
]
command "sendButton", [
[name: "button", type: "STRING", description: "Nome da tecla (usa o controle padrão configurado nas preferências)"]
]
}
preferences {
section("Configurações do Dispositivo") {
input name: "deviceType", type: "enum",
options: ["switch","dimmer","curtain","motion","contact","siren","audio","virtual","temperature"],
title: "Tipo de Dispositivo", required: true, defaultValue: "switch"
input name: "defaultRemoteId", type: "text",
title: "ID do Controle Remoto Padrão (ex: AI_IR_8b11_1780402846)", required: false
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
log.info "[CHILD] Instalado: ${device.displayName}"
initialize()
}
def updated() {
if (logEnable) log.debug "[CHILD] Preferências salvas"
initialize()
}
def initialize() {
String type = settings.deviceType ?: "switch"
sendEvent(name: "deviceType", value: type)
sendEvent(name: "deviceCls", value: settings.deviceCls ?: "")
sendEvent(name: "numberOfButtons", value: 30)

switch (type) {
case "switch": case "dimmer": case "siren": case "audio": case "virtual":
if (device.currentValue("switch") == null) sendEvent(name: "switch", value: "off")
break
}
if (type == "dimmer") {
if (device.currentValue("level") == null) sendEvent(name: "level", value: 0)
} else if (type == "curtain") {
if (device.currentValue("windowShade") == null) sendEvent(name: "windowShade", value: "unknown")
if (device.currentValue("position") == null) sendEvent(name: "position", value: 0)
} else if (type == "motion") {
if (device.currentValue("motion") == null) sendEvent(name: "motion", value: "inactive")
} else if (type == "contact") {
if (device.currentValue("contact") == null) sendEvent(name: "contact", value: "closed")
}
}
def refresh() {
String type = currentDeviceType()
if (isSensorType(type)) {
if (logEnable) log.debug "[CHILD] Refresh (${type}) — aguardando eventos do Parent"
} else {
if (logEnable) log.debug "[CHILD] Refresh (${type}) — estado sincronizado via Parent/notify"
}
}



def on() {
String dType = currentDeviceType()
if (!isTypeAllowed(dType, "switchOnOff")) {
log.warn "[CMD] on() ignorado para deviceType=${dType}"
return
}
if (logEnable) log.debug "[CMD] on() — ${device.displayName}"

if (dType == "curtain") {
int levelType = (settings.cmdTypeLevel as Integer) ?: 0
sendCommand(levelType, 100)
sendEvent(name: "switch", value: "on")
sendEvent(name: "windowShade", value: "open")
sendEvent(name: "position", value: 100, unit: "%")
return
}

int onType = (settings.cmdTypeOn != null) ? (settings.cmdTypeOn as Integer) : 0
sendCommand(onType, 255)
sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} ligado")
}
def off() {
String dType = currentDeviceType()
if (!isTypeAllowed(dType, "switchOnOff")) {
log.warn "[CMD] off() ignorado para deviceType=${dType}"
return
}
if (logEnable) log.debug "[CMD] off() — ${device.displayName}"

if (dType == "curtain") {
int levelType = (settings.cmdTypeLevel as Integer) ?: 0
sendCommand(levelType, 0)
sendEvent(name: "switch", value: "off")
sendEvent(name: "windowShade", value: "closed")
sendEvent(name: "position", value: 0, unit: "%")
return
}

int onType = (settings.cmdTypeOn != null) ? (settings.cmdTypeOn as Integer) : 0
sendCommand(onType, 0)
sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} desligado")
}



def setLevel(BigDecimal level) { setLevel(level, 0) }
def setLevel(BigDecimal level, BigDecimal duration) {
String dType = currentDeviceType()
if (!isTypeAllowed(dType, "level")) {
log.warn "[CMD] setLevel() ignorado para deviceType=${dType}"
return
}
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
String dType = currentDeviceType()
if (!isTypeAllowed(dType, "level")) {
log.warn "[CMD] startLevelChange() ignorado para deviceType=${dType}"
return
}

int current = (device.currentValue("level") as Integer) ?: 50
int newLevel = direction == "up" ? Math.min(100, current + 10) : Math.max(0, current - 10)
setLevel(newLevel)
}
def stopLevelChange() {
String dType = currentDeviceType()
if (!isTypeAllowed(dType, "level")) {
log.warn "[CMD] stopLevelChange() ignorado para deviceType=${dType}"
return
}
if (logEnable) log.debug "[CMD] stopLevelChange — sem suporte nativo"
}



def open() {
String dType = currentDeviceType()
if (!isTypeAllowed(dType, "shade")) {
log.warn "[CMD] open() ignorado para deviceType=${dType}"
return
}
if (logEnable) log.debug "[CMD] open() — ${device.displayName}"
int levelType = (settings.cmdTypeLevel as Integer) ?: 0
sendCommand(levelType, 100)
sendEvent(name: "switch", value: "on")
sendEvent(name: "windowShade", value: "open")
sendEvent(name: "position", value: 100, unit: "%")
}
def close() {
String dType = currentDeviceType()
if (!isTypeAllowed(dType, "shade")) {
log.warn "[CMD] close() ignorado para deviceType=${dType}"
return
}
if (logEnable) log.debug "[CMD] close() — ${device.displayName}"
int levelType = (settings.cmdTypeLevel as Integer) ?: 0
sendCommand(levelType, 0)
sendEvent(name: "switch", value: "off")
sendEvent(name: "windowShade", value: "closed")
sendEvent(name: "position", value: 0, unit: "%")
}
def setPosition(BigDecimal position) {
String dType = currentDeviceType()
if (!isTypeAllowed(dType, "shade")) {
log.warn "[CMD] setPosition() ignorado para deviceType=${dType}"
return
}
int pos = Math.max(0, Math.min(100, position.intValue()))
int type = (settings.cmdTypeLevel as Integer) ?: 0
sendCommand(type, pos)
sendEvent(name: "position", value: pos, unit: "%")
sendEvent(name: "windowShade", value: pos >= 99 ? "open" : pos <= 1 ? "closed" : "partially open")
sendEvent(name: "switch", value: pos > 0 ? "on" : "off")
}
def startPositionChange(String direction) {
String dType = currentDeviceType()
if (!isTypeAllowed(dType, "shade")) {
log.warn "[CMD] startPositionChange() ignorado para deviceType=${dType}"
return
}

int levelType = (settings.cmdTypeLevel as Integer) ?: 0
if (direction == "up") {
sendCommand(levelType, 100)
sendEvent(name: "windowShade", value: "opening")
} else {
sendCommand(levelType, 0)
sendEvent(name: "windowShade", value: "closing")
}
}
def stopPositionChange() {
String dType = currentDeviceType()
if (!isTypeAllowed(dType, "shade")) {
log.warn "[CMD] stopPositionChange() ignorado para deviceType=${dType}"
return
}

sendCommand(206, 128)
sendEvent(name: "windowShade", value: "partially open")
if (logEnable) log.debug "[CMD] stopPositionChange() — stop nativo type=206 val=128"
}



def push(buttonNumber) {
if (logEnable) log.debug "[CHILD] push(buttonNumber=${buttonNumber})"
String btnName = ""
switch(buttonNumber as Integer) {
case 1: btnName = "POWER"; break
case 2: btnName = "MUTE"; break
case 3: btnName = "VOLUMEUP"; break
case 4: btnName = "VOLUMEDOWN"; break
case 5: btnName = "CHANNELUP"; break
case 6: btnName = "CHANNELDOWN"; break
case 7: btnName = "UP"; break
case 8: btnName = "DOWN"; break
case 9: btnName = "LEFT"; break
case 10: btnName = "RIGHT"; break
case 11: btnName = "ENTER"; break
case 12: btnName = "BACK"; break
case 13: btnName = "MENU"; break
case 14: btnName = "SOURCE"; break
case 15: btnName = "EXIT"; break
case 20: btnName = "TEMP_UP"; break
case 21: btnName = "TEMP_DOWN"; break
case 22: btnName = "MODE"; break
case 23: btnName = "WIND"; break
default: btnName = "POWER"; break
}
log.info "[CHILD] Push(${buttonNumber}) -> Mapeado para botão IR: '${btnName}'"
sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)
sendButton(btnName)
}



def siren() {
String dType = currentDeviceType()
if (!isTypeAllowed(dType, "alarm")) {
log.warn "[CMD] siren() ignorado para deviceType=${dType}"
return
}
on()
}
def strobe() {
String dType = currentDeviceType()
if (!isTypeAllowed(dType, "alarm")) {
log.warn "[CMD] strobe() ignorado para deviceType=${dType}"
return
}
on()
}
def both() {
String dType = currentDeviceType()
if (!isTypeAllowed(dType, "alarm")) {
log.warn "[CMD] both() ignorado para deviceType=${dType}"
return
}
on()
}



def handleEvent(Object val, Object cmdType, Map extraData = [:]) {
if (logEnable) log.debug "[EVENT] val=${val} type=${cmdType} extra=${extraData}"
String type = settings.deviceType ?: "switch"
int v = 0
if (val != null) {
try { v = val instanceof Number ? ((Number)val).intValue() : Integer.parseInt(val.toString()) }
catch (Exception ignored) {}
}
int ct = 0
try { ct = cmdType instanceof Number ? ((Number)cmdType).intValue() : Integer.parseInt(cmdType.toString()) }
catch (Exception ignored) {}
switch (type) {
case "dimmer":

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
break
case "switch":
case "audio":
case "virtual":
case "siren":


if (val == null) {
if (logEnable) log.debug "[EVENT] Switch: val=null - ignorando notificação sem valor"
return
}
if (ct == 128 || ct == 129 || ct == 0) {
String sw = (v > 0) ? "on" : "off"
sendEvent(name: "switch", value: sw, descriptionText: "${device.displayName} ${sw}")
}
break
case "curtain":
int pos = Math.max(0, Math.min(100, v))
sendEvent(name: "position", value: pos, unit: "%")
sendEvent(name: "switch", value: pos > 0 ? "on" : "off")
sendEvent(name: "windowShade", value: pos >= 99 ? "open" : pos <= 1 ? "closed" : "partially open")
break
case "motion":
String motion = v > 0 ? "active" : "inactive"
sendEvent(name: "motion", value: motion, descriptionText: "${device.displayName} ${motion}")
break
case "contact":
String contact = v > 0 ? "open" : "closed"
sendEvent(name: "contact", value: contact, descriptionText: "${device.displayName} ${contact}")
break
case "temperature":
BigDecimal temp = v > 1000 ? v / 100.0 : v
sendEvent(name: "temperature", value: temp, unit: "°C")
break
default:
if (logEnable) log.debug "[EVENT] Tipo '${type}' sem handler específico"
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
String dType = currentDeviceType()
if (isSensorType(dType)) {
log.warn "[CMD] Ignorado: deviceType=${dType} é sensor e não suporta atuação"
return
}
if (!parent) {
log.error "[CMD] Parent não disponível"
return
}
String devid = settings.devid ?: ""
String channel = settings.channel ?: "P1"
if (!devid) { log.error "[CMD] devid não configurado!"; return }

if (channel.startsWith("AI_IR_")) {
if (logEnable) log.debug "[CMD] Canal virtual IR '${channel}' — Comando local simulado (sem trafegar rfSetA)"
return
}
try {
parent.sendDeviceCommand(devid, channel, cmdType, val)
} catch (Exception e) {
log.error "[CMD] Falha ao chamar parent: ${e.message}"
}
}
 
private String currentDeviceType() {
return settings.deviceType ?: (device.currentValue("deviceType")?.toString() ?: "switch")
}
 
private boolean isSensorType(String deviceType) {
return deviceType in ["motion", "contact", "temperature"]
}
 
private boolean isTypeAllowed(String deviceType, String commandGroup) {
Map rules = [
switchOnOff: ["switch", "dimmer", "curtain", "siren", "audio", "virtual"],
level: ["dimmer"],
shade: ["curtain"],
alarm: ["siren"]
]
List allowed = (List)rules[commandGroup]
if (!allowed) return false
return allowed.contains(deviceType)
}
 
def sendIR(String remoteId, String button) {
if (logEnable) log.debug "[CMD] sendIR(remoteId=${remoteId}, button=${button}) — ${device.displayName}"
if (!parent) {
log.error "[CMD] Parent não disponível"
return
}
String devid = settings.devid ?: ""
if (!devid) { log.error "[CMD] devid não configurado!"; return }
try {
parent.sendIRCommand(devid, remoteId, button)
} catch (Exception e) {
log.error "[CMD] Falha ao chamar parent.sendIRCommand: ${e.message}"
}
}
 
def sendButton(String button) {
if (logEnable) log.debug "[CMD] sendButton(button=${button}) — ${device.displayName}"
String remoteId = settings.defaultRemoteId ?: ""
if (!remoteId) {
log.error "[CMD] sendButton falhou: defaultRemoteId não configurado nas preferências!"
return
}
sendIR(remoteId, button)
}
