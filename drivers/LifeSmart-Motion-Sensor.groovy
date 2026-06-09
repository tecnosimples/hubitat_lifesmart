/**
 * LifeSmart Motion Sensor
 *
 * TecnoSimples Tecnologia LTDA
 * contato@tecnosimples.com.br | (14) 99760-6885
 * (c) 2026 TecnoSimples - Todos os direitos reservados.
 *
 * Produto licenciado. Distribuido via Hubitat Package Manager.
 * Versao do pacote: 1.1.0
 */
 
metadata {
definition(
name: "LifeSmart Motion Sensor",
namespace: "tecnosimples",
author: "TecnoSimples"
) {
capability "Sensor"
capability "MotionSensor"
capability "Battery"
capability "TemperatureMeasurement"
capability "IlluminanceMeasurement"
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
sendEvent(name: "deviceType", value: "motion")
if (device.currentValue("motion") == null) sendEvent(name: "motion", value: "inactive")
}
def refresh() {
if (parent) parent.refresh()
}
 
def handleEvent(Object val, Object cmdType, Map extraData = [:]) {
if (logEnable) log.debug "[EVENT] Sensor de Movimento: val=${val} type=${cmdType} extra=${extraData}"
try {


Object stateVal = val
if (stateVal == null && extraData.containsKey("sensorVal")) {
stateVal = extraData["sensorVal"]
}
if (stateVal != null) {
int v = 0
if (stateVal instanceof Boolean) v = stateVal ? 1 : 0
else v = stateVal instanceof Number ? ((Number)stateVal).intValue() : Integer.parseInt(stateVal.toString())
String state = (v > 0) ? "active" : "inactive"
if (device.currentValue("motion") != state) {
log.info "${device.displayName} is ${state}"
sendEvent(name: "motion", value: state, descriptionText: "${device.displayName} is ${state}", isStateChange: true)
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
Object battType = extraData["battType"]
if (battRaw != null) {
int b = battRaw instanceof Number ? battRaw.intValue() : Integer.parseInt(battRaw.toString())
int battTypeI = battType instanceof Number ? battType.intValue() : 0
int batteryPct = 0
if (battTypeI == 10 || (b >= 200 && b <= 350)) {

float volts = b / 100.0f
batteryPct = Math.min(100, Math.max(0, (int)((volts - 2.2f) * 125)))
} else if (b > 3000) {

batteryPct = Math.min(100, Math.max(0, (int)((b - 2500) / 5)))
} else if (b <= 255) {

batteryPct = Math.min(100, Math.max(0, (int)Math.round(b * 100.0 / 255.0)))
} else {
batteryPct = Math.min(100, b)
}
int currentBatt = device.currentValue("battery") ? (device.currentValue("battery") as int) : -1
if (batteryPct != currentBatt) {
log.info "${device.displayName} battery is ${batteryPct}%"
sendEvent(name: "battery", value: batteryPct, unit: "%")
}
}

Object luxRaw = extraData["lux"]
if (luxRaw != null) {
int lux = luxRaw instanceof Number ? luxRaw.intValue() : Integer.parseInt(luxRaw.toString())
int currentLux = device.currentValue("illuminance") ? (device.currentValue("illuminance") as int) : -1
if (lux != currentLux) {
log.info "${device.displayName} illuminance is ${lux} lux"
sendEvent(name: "illuminance", value: lux, unit: "lux")
}
}
} catch (Exception e) {
log.error "Error handleEvent: ${e.message}"
}
}
def getDevid() { return settings.devid ?: "" }
def getChannel() { return settings.channel ?: "" }
