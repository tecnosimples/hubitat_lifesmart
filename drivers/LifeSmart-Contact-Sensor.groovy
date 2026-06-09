/**
 * LifeSmart Contact Sensor
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
name: "LifeSmart Contact Sensor",
namespace: "tecnosimples",
author: "TecnoSimples"
) {
capability "Sensor"
capability "ContactSensor"
capability "Battery"
capability "TemperatureMeasurement"
capability "Refresh"
attribute "deviceType", "string"
attribute "deviceCls", "string"
}
preferences {
section("Configurações") {
input name: "logEnable", type: "bool", title: "Ativar logs de debug", defaultValue: false
input name: "invertContact", type: "bool", title: "Inverter open/closed (ex: GC dry contact)", defaultValue: false
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
sendEvent(name: "deviceType", value: "contact")

if (device.currentValue("switch") != null) {
sendEvent(name: "switch", value: null, isStateChange: true)
}
if (device.currentValue("contact") == null) {
sendEvent(name: "contact", value: "closed")
}
}
def refresh() {
if (parent) parent.refresh()
}
 
def handleEvent(Object val, Object cmdType, Map extraData = [:]) {
if (logEnable) log.debug "[EVENT] Sensor: val=${val} type=${cmdType} extra=${extraData}"
try {


Object stateVal = val
if (stateVal == null && extraData.containsKey("sensorVal")) {
stateVal = extraData["sensorVal"]
}
if (stateVal != null) {
int v = 0
if (stateVal instanceof Boolean) v = stateVal ? 1 : 0
else v = stateVal instanceof Number ? ((Number)stateVal).intValue() : Integer.parseInt(stateVal.toString())
boolean invert = (settings.invertContact == true)
String state = ((v > 0) != invert) ? "open" : "closed"
if (device.currentValue("contact") != state) {
log.info "${device.displayName} is ${state}"
sendEvent(name: "contact", value: state, descriptionText: "${device.displayName} is ${state}", isStateChange: true)
}
}


Object tempRaw = extraData["t"] ?: extraData["temp"]
if (tempRaw != null) {
float t = tempRaw instanceof Number ? tempRaw.floatValue() : Float.parseFloat(tempRaw.toString())




if (t > 500) {
t = t / 100.0f
} else if (t > 100) {
t = t / 10.0f
}

if (t >= -40 && t <= 80) {
float currentTemp = device.currentValue("temperature") ? (device.currentValue("temperature") as float) : -999
if (Math.abs(t - currentTemp) > 0.1) {
log.info "${device.displayName} temperature is ${t}°C"
sendEvent(name: "temperature", value: t, unit: "°C")
}
} else {
if (logEnable) log.warn "[EVENT] Temperatura fora do range esperado: ${t}°C (raw=${tempRaw})"
}
}


Object battRaw = extraData["batt"] ?: extraData["v"]
if (battRaw != null) {
int b = battRaw instanceof Number ? battRaw.intValue() : Integer.parseInt(battRaw.toString())
int batteryPct = 0




if (b >= 200 && b <= 350) {


float volts = b / 100.0f
batteryPct = Math.min(100, Math.max(0, (int)((volts - 2.2f) * 125)))
} else if (b > 3000) {

batteryPct = Math.min(100, Math.max(0, (int)((b - 2500) / 5)))
} else if (b > 100) {

batteryPct = Math.min(100, Math.max(0, b / 100))
} else {
batteryPct = b
}
int currentBatt = device.currentValue("battery") ? (device.currentValue("battery") as int) : -1
if (batteryPct != currentBatt) {
log.info "${device.displayName} battery is ${batteryPct}%"
sendEvent(name: "battery", value: batteryPct, unit: "%")
}
}
} catch (Exception e) {
log.error "Error handleEvent: ${e.message}"
}
}
def getDevid() { return settings.devid ?: "" }
def getChannel() { return settings.channel ?: "" }
