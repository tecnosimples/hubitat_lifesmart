/**
 * LifeSmart CT Dimmer
 *
 * TecnoSimples Tecnologia LTDA
 * contato@tecnosimples.com.br | (14) 99760-6885
 * (c) 2026 TecnoSimples - Todos os direitos reservados.
 *
 * Produto licenciado. Distribuido via Hubitat Package Manager.
 * Versao do pacote: 1.0.0
 */
 
metadata {
definition(
name: "LifeSmart CT Dimmer",
namespace: "tecnosimples",
author: "TecnoSimples",
importUrl: "https://raw.githubusercontent.com/tecnosimples/hubitat-lifesmart/main/LifeSmart-CT-Dimmer.groovy"
) {
capability "Switch"
capability "SwitchLevel"
capability "ChangeLevel"
capability "ColorTemperature"
capability "Refresh"
capability "Actuator"
attribute "deviceCls", "string"
attribute "colorName", "string"
}
preferences {
section("Configurações do Dispositivo") {
input name: "logEnable", type: "bool", title: "Ativar logs de debug", defaultValue: false
}
section("<small>Configurações Internas (Não alterar)</small>") {
input name: "devid", type: "text", title: "ID Interno", required: true
input name: "channel", type: "text", title: "Canal (brilho)", required: true
input name: "ctChannel", type: "text", title: "Canal (cor)", required: false
input name: "deviceCls", type: "text", title: "Classe", required: false
input name: "cmdTypeOn", type: "number", title: "Override ON Type", defaultValue: 0
input name: "cmdTypeLevel", type: "number", title: "Override Level Type", defaultValue: 0
}
}
}



def installed() {
log.info "[CT] Instalado: ${device.displayName}"
initialize()
}
def updated() {
if (logEnable) log.debug "[CT] Preferências salvas"
initialize()
}
def initialize() {
sendEvent(name: "deviceCls", value: settings.deviceCls ?: "")
if (device.currentValue("switch") == null) sendEvent(name: "switch", value: "off")
if (device.currentValue("level") == null) sendEvent(name: "level", value: 0)
if (device.currentValue("colorTemperature") == null) sendEvent(name: "colorTemperature", value: 2700, unit: "K")
}
def refresh() {
if (logEnable) log.debug "[CT] Refresh — estado sincronizado via Parent/notify"
}



def on() {
if (logEnable) log.debug "[CMD] on() — ${device.displayName}"
int onType = (settings.cmdTypeOn != null) ? (settings.cmdTypeOn as Integer) : 0

sendCommand(brightnessChannel(), onType, 255)
sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} ligado")
}
def off() {
if (logEnable) log.debug "[CMD] off() — ${device.displayName}"
int onType = (settings.cmdTypeOn != null) ? (settings.cmdTypeOn as Integer) : 0
sendCommand(brightnessChannel(), onType, 0)
sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} desligado")
}
def setLevel(BigDecimal level) { setLevel(level, 0) }
def setLevel(BigDecimal level, BigDecimal duration) {

int lvl = Math.max(0, Math.min(100, level.intValue()))
if (logEnable) log.debug "[CMD] setLevel(${lvl}) — ${device.displayName}"
int levelType = (settings.cmdTypeLevel != null && settings.cmdTypeLevel != 0) ? (settings.cmdTypeLevel as Integer) : 207
int onType = (settings.cmdTypeOn != null && settings.cmdTypeOn != 0) ? (settings.cmdTypeOn as Integer) : 129
if (lvl > 0 && device.currentValue("switch") == "off") {
if (logEnable) log.debug "[CMD] Apagado — ligando antes de ajustar nível"
sendCommand(brightnessChannel(), onType, 1)
}
sendCommand(brightnessChannel(), levelType, lvl)
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






def setColorTemperature(BigDecimal kelvin) { setColorTemperature(kelvin, null, null) }
def setColorTemperature(BigDecimal kelvin, BigDecimal level, BigDecimal transitionTime) {
int k = Math.max(2700, Math.min(6000, kelvin.intValue()))
int percent = ctEncodePercent(k)
int displayK = ctDecodeKelvin(percent)
if (logEnable) log.debug "[CMD] setColorTemperature(${kelvin.intValue()}K → ${displayK}K → ${percent}%) ch=${colorChannel()}"

sendCommand(colorChannel(), 207, percent)
sendEvent(name: "colorTemperature", value: displayK, unit: "K")
sendEvent(name: "colorName", value: ctNameFor(displayK))
if (level != null && level.intValue() > 0) setLevel(level)
}



def handleEvent(Object val, Object cmdType, Map extraData = [:]) {
if (logEnable) log.debug "[EVENT] val=${val} type=${cmdType} extra=${extraData}"
String src = extraData?.srcChannel?.toString() ?: ""
if (src && src == colorChannel()) {
handleColorEvent(val, cmdType)
} else {
handleBrightnessEvent(val, cmdType)
}
}
 
private void handleColorEvent(Object val, Object cmdType) {
if (val == null) {
if (logEnable) log.debug "[EVENT] Cor: notificação sem valor — ignorada"
return
}
int pct = Math.max(0, Math.min(100, parseIntSafe(val, 0)))
int k = ctDecodeKelvin(pct)
sendEvent(name: "colorTemperature", value: k, unit: "K")
sendEvent(name: "colorName", value: ctNameFor(k))
}
 
private void handleBrightnessEvent(Object val, Object cmdType) {
int v = parseIntSafe(val, 0)
int ct = parseIntSafe(cmdType, 0)
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
if (logEnable) log.debug "[EVENT] Brilho: notificação sem valor — ignorada"
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
 
def getCtChannel() {
return settings.ctChannel ?: ""
}
private String brightnessChannel() { return settings.channel ?: "P1" }
private String colorChannel() { return settings.ctChannel ?: "P2" }
private int parseIntSafe(Object o, int dflt) {
if (o == null) return dflt
try { return o instanceof Number ? ((Number)o).intValue() : Integer.parseInt(o.toString()) }
catch (Exception ignored) { return dflt }
}
 
private List ctAnchors() {
return [
[2700, 0], [2900, 10], [3100, 15], [3200, 20],
[3300, 28], [3400, 32], [3500, 35], [3700, 39],
[3800, 45], [4000, 48], [4100, 54], [4200, 56],
[4300, 59], [4400, 62], [4500, 64], [4600, 69],
[4700, 72], [4900, 74], [5100, 79], [5300, 85],
[5600, 90], [5800, 94], [6000,100]
]
}
 
private int ctEncodePercent(int k) {
List anchors = ctAnchors()
int clamped = Math.max(2700, Math.min(6000, k))
for (int i = 0; i < anchors.size() - 1; i++) {
int k0 = anchors[i][0], p0 = anchors[i][1]
int k1 = anchors[i+1][0], p1 = anchors[i+1][1]
if (clamped >= k0 && clamped <= k1) {
if (k0 == k1) return p0
return p0 + (int) Math.round((clamped - k0) * (p1 - p0) / (double)(k1 - k0))
}
}
return 100
}
 
private int ctDecodeKelvin(int pct) {
List anchors = ctAnchors()
int best = anchors[0][0]
for (int i = 0; i < anchors.size(); i++) {
if (pct >= (int) anchors[i][1]) best = anchors[i][0]
else break
}
return best
}
 
private String ctNameFor(int k) {
if (k <= 2900) return "Warm White"
if (k <= 3500) return "Soft White"
if (k <= 4500) return "White"
if (k <= 5500) return "Daylight"
return "Cool White"
}
 
private void sendCommand(String channel, int cmdType, int val) {
if (!parent) {
log.error "[CMD] Parent não disponível"
return
}
String devid = settings.devid ?: ""
if (!devid) { log.error "[CMD] devid não configurado!"; return }
String ch = channel ?: "P1"
if (ch.startsWith("AI_IR_")) {
if (logEnable) log.debug "[CMD] Canal virtual IR '${ch}' — ignorado"
return
}
try {
parent.sendDeviceCommand(devid, ch, cmdType, val)
} catch (Exception e) {
log.error "[CMD] Falha ao chamar parent: ${e.message}"
}
}
