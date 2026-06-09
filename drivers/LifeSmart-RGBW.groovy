/**
 * LifeSmart RGBW
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
name: "LifeSmart RGBW",
namespace: "tecnosimples",
author: "TecnoSimples",
importUrl: "https://raw.githubusercontent.com/tecnosimples/hubitat-lifesmart/main/LifeSmart-RGBW.groovy"
) {
capability "Switch"
capability "SwitchLevel"
capability "ColorControl"
capability "Refresh"
capability "Actuator"
command "setWhiteLevel", [[name: "level*", type: "NUMBER", description: "Brilho do canal branco (W) 0-100%"]]
attribute "deviceCls", "string"
attribute "colorName", "string"
attribute "whiteLevel", "number"
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
log.info "[RGBW] Instalado: ${device.displayName}"
initialize()
}
def updated() {
if (logEnable) log.debug "[RGBW] Preferências salvas"
initialize()
}
def initialize() {
sendEvent(name: "deviceCls", value: settings.deviceCls ?: "")
if (device.currentValue("switch") == null) sendEvent(name: "switch", value: "off")
if (device.currentValue("level") == null) sendEvent(name: "level", value: 100, unit: "%")
if (device.currentValue("hue") == null) sendEvent(name: "hue", value: 50)
if (device.currentValue("saturation") == null) sendEvent(name: "saturation", value: 100)
if (device.currentValue("whiteLevel") == null) sendEvent(name: "whiteLevel", value: 0, unit: "%")
if (device.currentValue("colorName") == null) sendEvent(name: "colorName", value: "Cyan")
}
def refresh() {
if (logEnable) log.debug "[RGBW] Refresh — estado sincronizado via Parent/notify"
}



def on() {
if (logEnable) log.debug "[CMD] on() — ${device.displayName}"
long last = getLastPacked()
if ((last & 0xFFFFFFFFL) == 0L) {

int h = parseIntSafe(device.currentValue("hue"), 50)
int s = parseIntSafe(device.currentValue("saturation"), 100)
int lvl = parseIntSafe(device.currentValue("level"), 100)
sendHSBW(h, s, lvl, 0)
} else {
sendRawColor((int)(last & 4294967295L))
}
sendEvent(name: "switch", value: "on")
}
def off() {
if (logEnable) log.debug "[CMD] off() — ${device.displayName}"
sendCommand(128, 0)
sendEvent(name: "switch", value: "off")
}



def setLevel(BigDecimal level) { setLevel(level, 0) }
def setLevel(BigDecimal level, BigDecimal duration) {
int lvl = Math.max(0, Math.min(100, level.intValue()))
if (lvl == 0) { off(); return }
int h = parseIntSafe(device.currentValue("hue"), 50)
int s = parseIntSafe(device.currentValue("saturation"), 100)
int w = parseIntSafe(device.currentValue("whiteLevel"), 0)
if (s > 0) {

sendHSBW(h, s, lvl, w)
sendEvent(name: "level", value: lvl, unit: "%")
} else {

sendHSBW(h, 0, 0, lvl)
sendEvent(name: "level", value: lvl, unit: "%")
sendEvent(name: "whiteLevel", value: lvl, unit: "%")
}
sendEvent(name: "switch", value: "on")
}



def setColor(Map colormap) {
int h = parseIntSafe(colormap?.hue, device.currentValue("hue") ?: 50)
int s = parseIntSafe(colormap?.saturation, device.currentValue("saturation") ?: 100)
int lvl = parseIntSafe(colormap?.level, device.currentValue("level") ?: 100)
lvl = Math.max(1, Math.min(100, lvl))
if (logEnable) log.debug "[CMD] setColor(h=${h} s=${s} v=${lvl})"
sendHSBW(h, s, lvl, 0)
sendEvent(name: "hue", value: h)
sendEvent(name: "saturation", value: s)
sendEvent(name: "level", value: lvl, unit: "%")
sendEvent(name: "whiteLevel", value: 0, unit: "%")
sendEvent(name: "switch", value: "on")
sendEvent(name: "colorName", value: colorNameForHue(h, s))
}
def setHue(BigDecimal hue) {
int h = Math.max(0, Math.min(100, hue.intValue()))
int s = parseIntSafe(device.currentValue("saturation"), 100)
int lvl = parseIntSafe(device.currentValue("level"), 100)
setColor([hue: h, saturation: s, level: lvl])
}
def setSaturation(BigDecimal saturation) {
int h = parseIntSafe(device.currentValue("hue"), 50)
int s = Math.max(0, Math.min(100, saturation.intValue()))
int lvl = parseIntSafe(device.currentValue("level"), 100)
setColor([hue: h, saturation: s, level: lvl])
}
 
def setWhiteLevel(level) {
int w = Math.max(0, Math.min(100, parseIntSafe(level, 0)))
if (logEnable) log.debug "[CMD] setWhiteLevel(${level}) → W=${w}"
int h = parseIntSafe(device.currentValue("hue"), 50)
int s = parseIntSafe(device.currentValue("saturation"), 100)
int b = parseIntSafe(device.currentValue("level"), 0)
if (s == 0) b = 0 
sendHSBW(h, s, b, w)
sendEvent(name: "whiteLevel", value: w, unit: "%")
sendEvent(name: "switch", value: (b > 0 || w > 0) ? "on" : "off")
if (s == 0) sendEvent(name: "level", value: w, unit: "%")
}



 
private void sendHSBW(int hHubitat, int sHubitat, int bHubitat, int wHubitat) {

List rgb = hubitat.helper.ColorUtils.hsvToRGB([
Math.max(0, Math.min(100, hHubitat)),
Math.max(0, Math.min(100, sHubitat)),
Math.max(0, Math.min(100, bHubitat))
])
int r = Math.max(0, Math.min(255, ((Number)rgb[0]).intValue()))
int g = Math.max(0, Math.min(255, ((Number)rgb[1]).intValue()))
int b = Math.max(0, Math.min(255, ((Number)rgb[2]).intValue()))
int w = (int)Math.round(Math.max(0, Math.min(100, wHubitat)) * 255.0 / 100)

int packed = ((w & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF)
if (logEnable) {
log.debug "[RGBW] sendHSBW: H=${hHubitat} S=${sHubitat} V=${bHubitat} W%=${wHubitat} → R=${r} G=${g} B=${b} W=${w} → 0x${String.format('%08X', packed & 0xFFFFFFFFL as Long)}"
}
sendRawColor(packed)
}
private void sendRawColor(int packed) {
state.lastPacked = packed & 4294967295L 
sendCommand(255, packed)
}



def handleEvent(Object val, Object cmdType, Map extraData = [:]) {
int ct = parseIntSafe(cmdType, 0)
if (ct == 128) {
sendEvent(name: "switch", value: "off")
return
}
Long packedLong = extractPackedLong(val)
if (logEnable) log.debug "[EVENT RGBW] raw=${val} type=${ct} → packedLong=${packedLong}"
if (packedLong == null) return
long pL = packedLong & 4294967295L 

int w = (int)((pL >> 24) & 0xFFL)
int r = (int)((pL >> 16) & 0xFFL)
int g = (int)((pL >> 8) & 0xFFL)
int b = (int)(pL & 0xFFL)
if (logEnable) log.debug "[EVENT RGBW] R=${r} G=${g} B=${b} W=${w}"

if (r == 0 && g == 0 && b == 0 && w == 0) {
sendEvent(name: "switch", value: "off")
return
}
state.lastPacked = pL

List hsv = hubitat.helper.ColorUtils.rgbToHSV([r, g, b])
int hue = Math.max(0, Math.min(100, (int)Math.round(((Number)hsv[0]).doubleValue())))
int sat = Math.max(0, Math.min(100, (int)Math.round(((Number)hsv[1]).doubleValue())))
int rgbLvl = (int)Math.round(((Number)hsv[2]).doubleValue()) 
int wPct = (int)Math.round(w * 100.0 / 255)

int lvl = (rgbLvl > 0) ? rgbLvl : wPct
sendEvent(name: "switch", value: "on")
sendEvent(name: "hue", value: hue)
sendEvent(name: "saturation", value: sat)
sendEvent(name: "level", value: Math.max(1, lvl), unit: "%")
sendEvent(name: "whiteLevel", value: wPct, unit: "%")
sendEvent(name: "colorName", value: (rgbLvl == 0) ? "White" : colorNameForHue(hue, sat))
}
 
private Long extractPackedLong(Object val) {
if (val == null) return null
if (val instanceof Number) return ((Number)val).longValue()
if (val instanceof Map && ((Map)val).containsKey("value")) {
Object v = ((Map)val)["value"]
if (v instanceof Number) return ((Number)v).longValue()
}
return null
}



def getDevid() { return settings.devid ?: "" }
def getChannel() { return settings.channel ?: "RGBW" }
private long getLastPacked() {
if (state.lastPacked == null) return 0L
try { return ((Number)state.lastPacked).longValue() } catch (Exception ignored) { return 0L }
}
private void sendCommand(int cmdType, int val) {
if (!parent) { log.error "[CMD] Parent não disponível"; return }
String devid = settings.devid ?: ""
if (!devid) { log.error "[CMD] devid não configurado!"; return }
try {
parent.sendDeviceCommand(devid, settings.channel ?: "RGBW", cmdType, val)
} catch (Exception e) {
log.error "[CMD] Falha ao chamar parent: ${e.message}"
}
}
private int parseIntSafe(Object o, Object dflt) {
int d = (dflt instanceof Number) ? ((Number)dflt).intValue() : 0
if (o == null) return d
try { return o instanceof Number ? ((Number)o).intValue() : Integer.parseInt(o.toString()) }
catch (Exception ignored) { return d }
}
private String colorNameForHue(int hue, int sat) {
if (sat < 10) return "White"
if (hue < 5) return "Red"
if (hue < 11) return "Orange"
if (hue < 20) return "Yellow"
if (hue < 40) return "Chartreuse"
if (hue < 55) return "Green"
if (hue < 65) return "Spring Green"
if (hue < 75) return "Cyan"
if (hue < 83) return "Azure"
if (hue < 91) return "Blue"
if (hue < 95) return "Violet"
if (hue < 98) return "Magenta"
return "Red"
}
