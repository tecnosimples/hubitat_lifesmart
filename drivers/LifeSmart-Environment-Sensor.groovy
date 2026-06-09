/**
 * LifeSmart Environment Sensor
 *
 * TecnoSimples Tecnologia LTDA
 * contato@tecnosimples.com.br | (14) 99760-6885
 * (c) 2026 TecnoSimples - Todos os direitos reservados.
 *
 * Produto licenciado. Distribuido via Hubitat Package Manager.
 * Versao do pacote: 1.1.2
 */
 
metadata {
definition(
name: "LifeSmart Environment Sensor",
namespace: "tecnosimples",
author: "TecnoSimples"
) {
capability "Sensor"
capability "TemperatureMeasurement"
capability "RelativeHumidityMeasurement"
capability "IlluminanceMeasurement"
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
sendEvent(name: "deviceType", value: "environment")
}
def refresh() {
if (parent) parent.refresh()
}
 
def handleEvent(Object val, Object cmdType, Map extraData = [:]) {
if (logEnable) log.debug "[EVENT] Environment Sensor: val=${val} type=${cmdType} extra=${extraData}"
try {

Object tempRaw = extraData["t"] ?: extraData["temp"]
if (tempRaw != null) {
float t = tempRaw instanceof Number ? tempRaw.floatValue() : Float.parseFloat(tempRaw.toString())
if (t > 500) t = t / 100.0f
else if (t > 100) t = t / 10.0f
if (t >= -40 && t <= 80) {
float cur = device.currentValue("temperature") ? (device.currentValue("temperature") as float) : -999
if (Math.abs(t - cur) > 0.1) {
log.info "${device.displayName} temperature is ${t}°C"
sendEvent(name: "temperature", value: t, unit: "°C")
}
} else {
if (logEnable) log.warn "[EVENT] Temperatura fora do range: ${t}°C (raw=${tempRaw})"
}
}

Object humRaw = extraData["h"]
if (humRaw != null) {
int h = humRaw instanceof Number ? humRaw.intValue() : Integer.parseInt(humRaw.toString())

float hPct = h
if (h > 100) hPct = h / 10.0f
if (hPct >= 0 && hPct <= 100) {
float cur = device.currentValue("humidity") ? (device.currentValue("humidity") as float) : -1
if (Math.abs(hPct - cur) > 0.1) {
log.info "${device.displayName} humidity is ${hPct}%"
sendEvent(name: "humidity", value: hPct, unit: "%")
}
} else {
if (logEnable) log.warn "[EVENT] Umidade fora do range: ${hPct}% (raw=${humRaw})"
}
}

Object luxRaw = extraData["lux"]
if (luxRaw != null) {
int lux = luxRaw instanceof Number ? luxRaw.intValue() : Integer.parseInt(luxRaw.toString())
int cur = device.currentValue("illuminance") ? (device.currentValue("illuminance") as int) : -1
if (lux != cur) {
log.info "${device.displayName} illuminance is ${lux} lux"
sendEvent(name: "illuminance", value: lux, unit: "lux")
}
}

Object battRaw = extraData["batt"] ?: extraData["v"]
Object battType = extraData["battType"]
if (battRaw != null) {
int b = battRaw instanceof Number ? battRaw.intValue() : Integer.parseInt(battRaw.toString())
int battTypeI = battType instanceof Number ? battType.intValue() : 0
int batteryPct
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
int cur = device.currentValue("battery") ? (device.currentValue("battery") as int) : -1
if (batteryPct != cur) {
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
