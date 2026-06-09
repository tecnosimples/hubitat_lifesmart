/**
 * LifeSmart Smart Station Local
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
name: "LifeSmart Smart Station Local",
namespace: "tecnosimples",
author: "TecnoSimples"
) {
capability "Initialize"
capability "Refresh"
command "Descobrir Dispositivos"
command "Limpar Cache IR"
command "Verificar Licença"
attribute "status", "string"
attribute "hubUID", "string"
attribute "lastResponse", "string"
attribute "deviceCount", "number"
attribute "node", "string"
}
preferences {
section("Conexão") {
input name: "deviceIP", type: "text", title: "IP da Smart Station", required: true, defaultValue: "10.0.1.15"
input name: "lsUser", type: "text", title: "Usuário Local", required: true, defaultValue: "admin"
input name: "lsPass", type: "password", title: "Senha Local", required: true, defaultValue: "admin"
input name: "logEnable", type: "bool", title: "Habilitar Logs", defaultValue: true
input name: "forceWWMode", type: "enum", title: "Forçar modo SL_LI_WW_V2 (segurança)", options: ["auto","dimmer","color"], defaultValue: "auto"
}
section("Licença TecnoSimples") {
input name: "licenseUrl", type: "text", title: "<small>URL do serviço de licença (não alterar)</small>", required: false
}
}
}



 
private Map GL_ENUM_KEYS() {
return [
"timestamp": 2,
"req": 3,
"args": 4,
"valtag": 7,
"act": 9,
"node": 10,
"ret": 11,
"cron_name": 13,
"name": 16,
"ts": 21,
"devid": 22,
"nid": 38,
"cls": 39,
"val": 41,
"ver": 42,
"cgy": 46,
"key": 47,
"response_token": 53,
"type": 78,
"icon": 89,
"uuid": 90,
"act_change": 92,
"_chd": 106,
"agtid": 113
]
}
 
private Map GL_ENUM_NAMES() {
Map result = [:]
GL_ENUM_KEYS().each { String name, Integer id -> result[id] = name }
return result
}



 
private byte[] sliceBytes(byte[] data, int from, int to) {
byte[] result = new byte[to - from]
for (int i = from; i < to; i++) result[i - from] = data[i]
return result
}
private byte[] concatBytes(byte[] a, byte[] b) {
byte[] result = new byte[a.length + b.length]
int i = 0
for (byte x : a) result[i++] = x
for (byte x : b) result[i++] = x
return result
}
private byte[] listToBytes(List ints) {
byte[] result = new byte[ints.size()]
ints.eachWithIndex { int v, int i -> result[i] = (byte)(v & 0xFF) }
return result
}



 
private byte[] encodeVarint(long value) {
List parts = []
while ((value & 0xFFFFFF80L) != 0L) {
parts << (int)(((value & 0x7FL) | 0x80L) & 0xFFL)
value >>= 7
}
parts << (int)(value & 0x7FL)
return listToBytes(parts)
}
 
private List decodeVarint(byte[] data, int offset) {
long result = 0L
int shift = 0
while (true) {
if (offset >= data.length) throw new Exception("Varint truncado em offset ${offset}")
int b = data[offset++] & 0xFF
result |= ((long)(b & 0x7F)) << shift
if ((b & 0x80) == 0) break
shift += 7
}
return [result, offset]
}
 
private long zigzagEncode(long n) { return (n << 1L) ^ (n >> 63L) }
 
private long zigzagDecode(long n) { return (n >> 1L) ^ (-(n & 1L)) }



 
private byte[] encodeKey(Object key) {
if (key instanceof Number) {
int id = ((Number)key).intValue()
return [(byte)0x13, (byte)(id & 0xFF)] as byte[]
}
String s = key.toString()
Integer id = (Integer)GL_ENUM_KEYS()[s]
if (id != null) {
return [(byte)0x13, (byte)(id & 0xFF)] as byte[]
}
return encodeStringBytes(s)
}
 
private byte[] encodeVal(Object value) {
if (value == null) {
return [(byte)0x00] as byte[]
}
if (value instanceof Boolean) {
return value ? [(byte)0x02] as byte[] : [(byte)0x03] as byte[]
}
if (value instanceof Map && ((Map)value).containsKey("__enum")) {
int id = ((Number)((Map)value)["__enum"]).intValue()
return [(byte)0x13, (byte)(id & 0xFF)] as byte[]
}


if (value instanceof Map && "TS" == ((Map)value)["type"] && ((Map)value).containsKey("value")) {
Object tsN = ((Map)value)["value"]
if (tsN instanceof Number) {
byte[] varint = encodeVarint(zigzagEncode(((Number)tsN).longValue()))
byte[] result = new byte[1 + varint.length]
result[0] = (byte)0x06
for (int i = 0; i < varint.length; i++) result[1 + i] = varint[i]
return result
}
}
if (value instanceof Number) {
byte[] varint = encodeVarint(zigzagEncode(((Number)value).longValue()))
byte[] result = new byte[1 + varint.length]
result[0] = (byte)0x04
for (int i = 0; i < varint.length; i++) result[1 + i] = varint[i]
return result
}
if (value instanceof String) {
return encodeStringBytes((String)value)
}
if (value instanceof Map) {
return encodeDictFull((Map)value)
}
if (value instanceof List) {
return encodeList((List)value)
}
throw new Exception("[GL00 ENCODE] Tipo não suportado: ${value.class.name}")
}
 
private byte[] encodeStringBytes(String s) {
byte[] utf8 = s.getBytes("UTF-8")
byte[] lenVar = encodeVarint((long)utf8.length)
byte[] result = new byte[1 + lenVar.length + utf8.length]
result[0] = (byte)0x11
int pos = 1
for (byte b : lenVar) result[pos++] = b
for (byte b : utf8) result[pos++] = b
return result
}
 
private byte[] encodeList(List list) {
if (list.isEmpty()) {
return [(byte)0x01] as byte[]
}
byte[] body = [] as byte[]
int idx = 0
for (Object item : list) {

byte[] keyBytes = encodeVal(idx)

byte[] valBytes = encodeVal(item)
body = concatBytes(body, keyBytes)
body = concatBytes(body, valBytes)
idx++
}
byte[] result = new byte[1 + 1 + body.length]
result[0] = (byte)0x12
result[1] = (byte)(idx & 0xFF)
for (int i = 0; i < body.length; i++) result[2 + i] = body[i]
return result
}
 
private byte[] encodeDictFull(Map map) {
byte[] body = encodeDictBody(map)
byte[] result = new byte[1 + body.length]
result[0] = (byte)0x12
for (int i = 0; i < body.length; i++) result[1 + i] = body[i]
return result
}
 
private byte[] encodeDictBody(Map map) {
byte[] pairs = new byte[0]
map.each { k, v ->
pairs = concatBytes(pairs, encodeKey(k))
pairs = concatBytes(pairs, encodeVal(v))
}
byte[] result = new byte[1 + pairs.length]
result[0] = (byte)(map.size() & 0xFF)
for (int i = 0; i < pairs.length; i++) result[1 + i] = pairs[i]
return result
}



 
private byte[] buildPacket(List dicts) {
byte[] payload = new byte[0]
dicts.each { d -> payload = concatBytes(payload, encodeDictBody((Map)d)) }
int size = payload.length
byte[] header = new byte[10]
header[0] = (byte)0x47 
header[1] = (byte)0x4C 
header[2] = (byte)0x30 
header[3] = (byte)0x30 
header[4] = (byte)0x00 
header[5] = (byte)0x00 
header[6] = (byte)((size >> 24) & 0xFF)
header[7] = (byte)((size >> 16) & 0xFF)
header[8] = (byte)((size >> 8) & 0xFF)
header[9] = (byte)(size & 0xFF)
if (logEnable) log.debug "[BUILD] Pacote ${10+size}B | payload=${size}B | header=${hubitat.helper.HexUtils.byteArrayToHexString(header)}"
return concatBytes(header, payload)
}



 
private List decodeVal(byte[] data, int offset) {
if (offset >= data.length) throw new Exception("[DECODE] Offset ${offset} além do fim (${data.length}B)")
int type = data[offset++] & 0xFF
if (type == 0x00) { 
return [null, offset]
}
if (type == 0x02) { 
return [true, offset]
}
if (type == 0x03) { 
return [false, offset]
}
if (type == 0x04) { 
List res = decodeVarint(data, offset)
return [zigzagDecode((long)res[0]), (int)res[1]]
}
if (type == 0x05) { 
int idx = data[offset++] & 0xFF
String hex = hubitat.helper.HexUtils.byteArrayToHexString(sliceBytes(data, offset, offset + 8))
return [["type": "HEX", "index": idx, "hex": hex], offset + 8]
}
if (type == 0x06) { 


List res = decodeVarint(data, offset)
return [["type": "TS", "value": zigzagDecode((long)res[0])], (int)res[1]]
}
if (type == 0x11) { 
List lenRes = decodeVarint(data, offset)
int length = (int)(long)lenRes[0]
int off2 = (int)lenRes[1]
String str = new String(sliceBytes(data, off2, off2 + length), "UTF-8")
return [str, off2 + length]
}
if (type == 0x12) { 
return decodeDict(data, offset)
}
if (type == 0x13) { 
int enumId = data[offset++] & 0xFF
String name = (String)GL_ENUM_NAMES()[enumId] ?: "enum_${enumId}"
return [name, offset]
}


int ctxStart = Math.max(0, offset - 5)
int ctxEnd = Math.min(data.length, offset + 10)
byte[] ctx = sliceBytes(data, ctxStart, ctxEnd)
String hexCtx = hubitat.helper.HexUtils.byteArrayToHexString(ctx)
log.warn "[DECODE] Tipo desconhecido: 0x${String.format('%02X', type)} em offset ${offset-1} | bytes ao redor: ${hexCtx} | total=${data.length}B"
return [null, offset]
}
 
private int scanForNextStringKey(byte[] data, int offset) {
int maxScan = Math.min(offset + 256, data.length - 2)
for (int i = offset; i < maxScan; i++) {
if ((data[i] & 0xFF) == 0x11) {
int nextByte = (i + 1 < data.length) ? (data[i+1] & 0xFF) : 0
if (nextByte > 0 && nextByte <= 32) {
return i
}
}
}
return offset
}
 
private List decodeDict(byte[] data, int offset) {
int count = data[offset++] & 0xFF
List items = []
for (int i = 0; i < count; i++) {
List kr = decodeVal(data, offset)
offset = (int)kr[1]
if (kr[0] == null) {


log.warn "[DECODE] Offset desalinhado em entrada ${i}/${count} — tentando re-sincronizar"
int rescue = scanForNextStringKey(data, offset)
if (rescue > offset) {
offset = rescue
continue
}
log.warn "[DECODE] Re-sincronizacao falhou — interrompendo dict (${items.size()} entradas)"
break
}
List vr = decodeVal(data, offset)
offset = (int)vr[1]
items << [key: kr[0], val: vr[0]]
}

List keys = items.collect { it.key }
boolean isList = items.size() > 0 &&
keys.every { it instanceof Long } &&
keys.collect { ((Long)it).longValue() } == (0..<items.size()).collect { (long)it }
if (isList) {
return [items.collect { it.val }, offset]
}
Map m = [:]
items.each { m[it.key] = it.val }
return [m, offset]
}
 
private List parsePacket(byte[] data) {
if (data.length < 10) throw new Exception("[PARSE] Pacote muito curto: ${data.length}B")
if ((data[0] & 0xFF) != 0x47 || (data[1] & 0xFF) != 0x4C ||
(data[2] & 0xFF) != 0x30 || (data[3] & 0xFF) != 0x30) {
throw new Exception("[PARSE] Header inválido: ${hubitat.helper.HexUtils.byteArrayToHexString(sliceBytes(data, 0, 4))}")
}
int size = (((data[6] & 0xFF) << 24) | ((data[7] & 0xFF) << 16) |
((data[8] & 0xFF) << 8) | (data[9] & 0xFF))
if (data.length < 10 + size) throw new Exception("[PARSE] Incompleto: precisa ${10+size}B, tem ${data.length}B")
List result = []
int offset = 10
int end = 10 + size
while (offset < end) {
try {
List res = decodeDict(data, offset)
result << res[0]
offset = (int)res[1]
} catch (Exception e) {
log.warn "[PARSE] Erro ao decodificar dict em offset ${offset}: ${e.message} — interrompendo parse"
break
}
}
return result
}



 
private byte[] buildLoginPacket(String uid, String pwd) {
Map dict1 = ["_sel": 1, "sn": 1, "req": false]
Map dict2 = [
"args": [
"cid": "6D56899B-82DA-403D-8291-50B57EE05DBA",
"cver": "1.0.48p1",
"uid": uid,
"nick": "admin",
"cname": "LifeSmart",
"pwd": pwd
],
"node": "A3MAAABaAEkBRzQ0Mzc0OA/ac",
"act": "Login"
]
return buildPacket([dict1, dict2])
}
 
private byte[] buildGetEpsPacket(String nodeVal) {

Map innerGrp = ["m": 1, "s": false, "_chd": 1]

Map config = [
(90): false, 
(42): false, 
(14): innerGrp, 
(89): false, 
(39): false, 
(16): false, 
(38): false, 
"_": "eps", 
(113): false, 
(46): false 
]

Map dict2Args = [
(14): [(98): config], 
"_chd": 1
]
Map dict1 = ["req": false, "timestamp": 10]
Map dict2 = [
"args": dict2Args,
"node": nodeVal + "/me/ep",
"act": ["__enum": 91] 
]
return buildPacket([dict1, dict2])
}
 
private byte[] buildGetAisPacket(String nodeVal) {
Map config = [
"ind": false,
"type": false,
"list": false,
"icon": false,
"name": false,
"loc": false,
"cls": false,
"_": "lcs"
]
Map dict2Args = [
(14): [(98): config],
"_chd": 2
]
Map dict1 = ["req": false, "timestamp": 10]
Map dict2 = [
"args": dict2Args,
"node": nodeVal + "/me/ai",
"act": ["__enum": 91]
]
return buildPacket([dict1, dict2])
}



 
private void handleLoginResponse(byte[] data) {
try {
List dicts = parsePacket(data)
if (logEnable) log.debug "[LOGIN RESP] ${dicts}"
if (dicts.size() < 2 || !(dicts[1] instanceof Map)) {
log.error "[LOGIN] Resposta malformada: ${dicts}"
state.phase = "idle"
return
}
Map d2 = (Map)dicts[1]
if (d2.containsKey("err")) {
log.error "[LOGIN] Falhou: ${d2.err}"
sendEvent(name: "status", value: "Erro login: ${d2.err}")
try { interfaces.rawSocket.close() } catch (Exception ignored) {}
state.phase = "idle"
return
}

Object ret = d2["ret"]
if (!(ret instanceof Map)) {
log.error "[LOGIN] ret ausente. d2 keys: ${d2.keySet()}"
state.phase = "idle"
return
}
Map retMap = (Map)ret

String node = extractNode(retMap)
if (!node) {
log.error "[LOGIN] NODE não encontrado. ret: ${retMap}"
state.phase = "idle"
return
}
log.info "[LOGIN] ✅ OK! NODE = ${node}"
state.node = node
sendEvent(name: "node", value: node)
sendEvent(name: "status", value: "Login OK — GetEPS...")

state.phase = "eps_sent"
state.rxBuffer = ""
byte[] epsPkt = buildGetEpsPacket(node)
String epsHex = hubitat.helper.HexUtils.byteArrayToHexString(epsPkt)
if (logEnable) log.debug "[EPS] Enviando (${epsPkt.length}B): ${epsHex.take(40)}..."
interfaces.rawSocket.sendMessage(epsHex)

byte[] aisPkt = buildGetAisPacket(node)
String aisHex = hubitat.helper.HexUtils.byteArrayToHexString(aisPkt)
if (logEnable) log.debug "[AIS] Enviando (${aisPkt.length}B): ${aisHex.take(40)}..."
interfaces.rawSocket.sendMessage(aisHex)
} catch (Exception e) {
log.error "[LOGIN] Exceção: ${e.message}"
state.phase = "idle"
}
}
 
private String extractNode(Map retMap) {


List candidates = [4L, 4, "args", "4"]
for (Object key4 : candidates) {
Object slot = retMap[key4]
if (!(slot instanceof Map)) continue
Map slotMap = (Map)slot
List baseCandidates = ["base", 1L, 1, "1"]
for (Object baseKey : baseCandidates) {
Object base = slotMap[baseKey]
if (!(base instanceof Map)) continue
Map baseMap = (Map)base
List nodeCandidates = [1L, 1, "1"]
for (Object nodeKey : nodeCandidates) {
Object nodeVal = baseMap[nodeKey]
if (nodeVal instanceof String && nodeVal.length() > 5) {
return (String)nodeVal
}
}
}
}

String found = ""
retMap.each { k, v ->
if (found) return
if (v instanceof Map) {
((Map)v).each { k2, v2 ->
if (found) return
if (v2 instanceof Map) {
((Map)v2).each { k3, v3 ->
if (!found && v3 instanceof String && ((String)v3).length() > 10) {
found = (String)v3
}
}
}
}
}
}
return found
}
 
private void handleEpsResponse(byte[] data) {
try {
List dicts = parsePacket(data)
if (logEnable) log.debug "[EPS RESP] ${dicts.size()} dicts, ${data.length}B"
if (dicts.size() < 2 || !(dicts[1] instanceof Map)) {
log.error "[EPS] Resposta malformada"
state.phase = "idle"
return
}
Map d2 = (Map)dicts[1]
if (d2.containsKey("err")) {
log.error "[EPS] Erro: ${d2.err}"
sendEvent(name: "status", value: "Erro GetEPS: ${d2.err}")
state.phase = "idle"
return
}

Object ret = d2["ret"]
if (!(ret instanceof Map)) {
log.error "[EPS] ret inválido: ${ret?.class?.name}"
state.phase = "idle"
return
}
Map retMap = (Map)ret

Object inner = retMap[1L] ?: retMap[1] ?: retMap["1"]
if (!(inner instanceof Map)) {
log.warn "[EPS] ret[1] inesperado. Chaves de ret: ${retMap.keySet()}"
state.phase = "idle"
return
}
Map innerMap = (Map)inner
Object eps = innerMap["eps"]
if (!(eps instanceof Map)) {
log.warn "[EPS] campo 'eps' não encontrado. Chaves: ${innerMap.keySet()}"
state.phase = "idle"
return
}
Map epsMap = (Map)eps
int rawCount = epsMap.size()



Map epsState = [:]
epsMap.each { devid, dev ->
if (dev instanceof Map) {

Map dm = (Map)dev
Map saved = [:]
dm.each { k, v -> saved[k] = v }
epsState[devid.toString()] = saved
} else if (devid instanceof Map && (dev instanceof String || dev instanceof Long)) {

String realDevid = dev.toString().trim()
if (realDevid.matches(/[0-9a-fA-F]{4}/)) {
Map dm = (Map)devid
Map saved = [:]
dm.each { k, v -> saved[k] = v }
epsState[realDevid] = saved
log.info "[EPS] ✅ Recuperado ${realDevid} cls=${dm.cls} '${dm.name}' (key-value invertido)"
} else {
if (logEnable) log.warn "[EPS] Dispositivo ${devid} falhou decode (val=${dev}) — não recuperável"
}
} else {
if (logEnable) log.warn "[EPS] Dispositivo ${devid} falhou decode (tipo=${dev?.class?.simpleName}, val=${dev}) — não salvo"
}
}
int decodedCount = epsState.size()
log.info "[EPS] ✅ ${decodedCount}/${rawCount} dispositivos decodificados (${rawCount - decodedCount} falhas de decode)"
sendEvent(name: "deviceCount", value: decodedCount)
sendEvent(name: "lastResponse", value: "${decodedCount}/${rawCount} devs — ${new Date().format('HH:mm:ss')}")
sendEvent(name: "status", value: "${decodedCount}/${rawCount} dispositivos OK")

epsState.each { devid, dev ->
Map dm = (Map)dev
String cls = dm["cls"]?.toString() ?: "?"
String name = dm["name"]?.toString() ?: "?"
String cgy = dm["cgy"]?.toString() ?: "?"
log.info "[EP] ${devid} | cls=${cls} | cgy=${cgy} | ${name}"
}
state.eps = epsState
state.lastEpsTime = now()
state.phase = "polling"
if (logEnable) dumpEpsSummary()

log.info "[EPS] Socket mantido aberto para polling/eventos"

runIn(5, "doPoll")

flushPendingCommandIfAny()


runInMillis(300, "DescobrirDispositivosDeferred")
} catch (Exception e) {
log.error "[EPS] Exceção: ${e.message}"
if (logEnable) log.debug "[EPS] Stack: ${e.getStackTrace()}"
state.phase = "idle"
}
}



 
def connectAndLogin() {

try {
interfaces.rawSocket.close()
} catch (Exception ignored) {}

unschedule("sendLogin")
unschedule("doPoll")
unschedule("DescobrirDispositivosDeferred")

state.discoveredRemotes = [:]
state.phase = "connecting"
state.rxBuffer = ""
String ip = (settings.deviceIP ?: "10.0.1.15").trim()
log.info "[TCP] Conectando em ${ip}:8888..."
sendEvent(name: "status", value: "Conectando a ${ip}...")
try {
interfaces.rawSocket.connect(ip, 8888, byteInterface: true)

runInMillis(600, "sendLogin")
} catch (Exception e) {
log.error "[TCP] Falha: ${e.message}"
sendEvent(name: "status", value: "Erro TCP: ${e.message}")
state.phase = "idle"
}
}
 
def sendLogin() {
if (state.phase != "connecting") {
if (logEnable) log.debug "[LOGIN] sendLogin ignorado (fase=${state.phase})"
return
}
log.info "[TCP] Conexão estabelecida — enviando Login..."
state.phase = "login_sent"
state.rxBuffer = ""
String uid = (settings.lsUser ?: "admin").trim()
String pwd = (settings.lsPass ?: "admin").trim()
byte[] pkt = buildLoginPacket(uid, pwd)
String hex = hubitat.helper.HexUtils.byteArrayToHexString(pkt)
if (logEnable) log.debug "[LOGIN] Enviando (${pkt.length}B): ${hex.take(40)}..."
try {
interfaces.rawSocket.sendMessage(hex)
} catch (Exception e) {
log.error "[LOGIN] Falha ao enviar: ${e.message}"
state.phase = "idle"
}
}
 
def socketStatus(String message) {
if (logEnable) log.debug "[SOCKET STATUS] ${message}"
String msg = message.toLowerCase()

if (state.phase == "connecting" && !msg.contains("error") && !msg.contains("closed")) {
if (logEnable) log.debug "[SOCKET] Status antecipado — adiantando sendLogin"
unschedule("sendLogin")
sendLogin()
return
}
if (msg.contains("error") || msg.contains("closed") || msg.contains("disconnect")) {


if (state.phase == "login_sent" || state.phase == "eps_sent") {
log.warn "[SOCKET] '${message}' em fase ${state.phase} — mantendo fase para aguardar resposta"
return
}
if (state.phase != "done") {
log.warn "[SOCKET] Conexão encerrada: ${message}"
sendEvent(name: "status", value: "Socket fechado: ${message}")
}
state.phase = "idle"
try { interfaces.rawSocket.close() } catch (Exception ignored) {}
}
}
 
def parse(String description) {
if (logEnable) log.debug "[PARSE] +${description.length()/2}B (fase=${state.phase}, buf=${(state.rxBuffer?.length() ?: 0)/2}B)"
state.rxBuffer = (state.rxBuffer ?: "") + description
while (true) {
if (state.rxBuffer.length() < 20) return 

String magic = state.rxBuffer.substring(0, 8)
if (magic != "474C3030") {
log.warn "[PARSE] Magic inválido: ${magic} — descartando buffer"
state.rxBuffer = ""
return
}

String sizeHex = state.rxBuffer.substring(12, 20)
int size = Integer.parseInt(sizeHex, 16)
int expectedHexLen = (10 + size) * 2
if (state.rxBuffer.length() < expectedHexLen) {
if (logEnable) log.debug "[PARSE] Pacote incompleto. Buffer: ${state.rxBuffer.length()/2}/${10 + size}B"
return
}

String packetHex = state.rxBuffer.substring(0, expectedHexLen)

state.rxBuffer = state.rxBuffer.substring(expectedHexLen)
byte[] fullPacket = hubitat.helper.HexUtils.hexStringToByteArray(packetHex)
if (logEnable) log.debug "[PARSE] Pacote completo extraído: ${fullPacket.length}B"

processSinglePacket(fullPacket)
}
}
 
private void processSinglePacket(byte[] fullPacket) {

try {
String asciiStr = new String(fullPacket, "ISO-8859-1")
def matcher = asciiStr =~ /(AI_IR_[a-f0-9]+_\d+|AIB_\d+)/
while (matcher.find()) {
String remoteId = matcher.group(1)
int remoteIdPos = matcher.start() 
int endOfStr = remoteIdPos + remoteId.length()
if (endOfStr >= asciiStr.length() || ((int)asciiStr.charAt(endOfStr) & 0xFF) != 0x12) {


continue
}
String name = "Controle IR ${remoteId}"

int namePos = asciiStr.lastIndexOf("\u0011\u0004name\u0011", remoteIdPos)
if (namePos == -1 || (remoteIdPos - namePos) > 400) {
namePos = asciiStr.indexOf("\u0011\u0004name\u0011", remoteIdPos)
}
if (namePos != -1 && Math.abs(remoteIdPos - namePos) < 400) {
int strLen = (int)asciiStr.charAt(namePos + 7)
if (strLen > 0 && strLen <= 128 && namePos + 8 + strLen <= asciiStr.length()) {
String rawName = asciiStr.substring(namePos + 8, namePos + 8 + strLen)
try {
name = new String(rawName.getBytes("ISO-8859-1"), "UTF-8")
} catch (Exception ignored) {
name = rawName
}
}
} else {

namePos = asciiStr.lastIndexOf("\u0013\u0010\u0011", remoteIdPos)
if (namePos == -1 || (remoteIdPos - namePos) > 400) {
namePos = asciiStr.indexOf("\u0013\u0010\u0011", remoteIdPos)
}
if (namePos != -1 && Math.abs(remoteIdPos - namePos) < 400) {
int strLen = (int)asciiStr.charAt(namePos + 3)
if (strLen > 0 && strLen <= 128 && namePos + 4 + strLen <= asciiStr.length()) {
String rawName = asciiStr.substring(namePos + 4, namePos + 4 + strLen)
try {
name = new String(rawName.getBytes("ISO-8859-1"), "UTF-8")
} catch (Exception ignored) {
name = rawName
}
}
}
}
if (state.discoveredRemotes == null) state.discoveredRemotes = [:]
String devid = ""
def devidMatcher = remoteId =~ /AI_IR_([a-f0-9]+)_\d+/
if (devidMatcher.find()) {
devid = devidMatcher.group(1)
} else {

int startWin = Math.max(0, remoteIdPos - 1500)
int endWin = Math.min(asciiStr.length(), remoteIdPos + 1500)
String window = asciiStr.substring(startWin, endWin)
def epMatcher = window =~ /\/ep\/([a-f0-9]{4})/
if (epMatcher.find()) {
devid = epMatcher.group(1)
}
}
if (devid) {
if (state.discoveredRemotes[devid] == null) state.discoveredRemotes[devid] = [:]


String oldName = (String)state.discoveredRemotes[devid][remoteId]
boolean isGenericNew = name.startsWith("Controle IR ")
boolean isGenericOld = (oldName == null || oldName.startsWith("Controle IR "))

if (oldName == null || (isGenericOld && !isGenericNew)) {
boolean isNew = (oldName != name)
state.discoveredRemotes[devid][remoteId] = name
if (isNew || logEnable) {
log.info "[AUTO-IR] 🎯 Controle IR Descoberto: devid=${devid} | ID=${remoteId} | Nome='${name}'"
}
}

try {
int cmdlistPos = asciiStr.lastIndexOf("cmdlist", remoteIdPos)
if (cmdlistPos == -1 || (remoteIdPos - cmdlistPos) > 2500) {
cmdlistPos = asciiStr.indexOf("cmdlist", remoteIdPos)
}
if (cmdlistPos != -1 && Math.abs(remoteIdPos - cmdlistPos) < 2500) {
int typePos = cmdlistPos + 7
if (typePos < asciiStr.length() && (int)asciiStr.charAt(typePos) == 0x11) {
int valLen = 0
int shift = 0
int offset = typePos + 1
while (offset < asciiStr.length()) {
int b = (int)asciiStr.charAt(offset) & 0xFF
offset++
valLen |= (b & 0x7F) << shift
if ((b & 0x80) == 0) {
break
}
shift += 7
}
int strLen = valLen
if (strLen > 0 && offset + strLen <= asciiStr.length()) {
String cmdlistVal = asciiStr.substring(offset, offset + strLen)
log.info "[AUTO-IR-DEBUG] RAW CMDLIST para '${name}' (ID: ${remoteId}): " + cmdlistVal.take(1000)
def buttons = []
String panelType = ""

def panelMatcher = cmdlistVal =~ /PANEL="([^"]+)"/
if (panelMatcher.find()) {
panelType = panelMatcher.group(1)
if (panelType.contains("TV")) {
buttons.addAll(["POWER", "MUTE", "VOLUMEUP", "VOLUMEDOWN", "CHANNELUP", "CHANNELDOWN", "MENU", "SOURCE", "UP", "DOWN", "LEFT", "RIGHT", "ENTER", "BACK", "EXIT"])
} else if (panelType.contains("AC")) {
buttons.addAll(["POWER", "TEMP_UP", "TEMP_DOWN", "MODE", "WIND"])
}
}

def exclusions = [
"code", "KEYMAP", "EX", "PARAMS", "CODESTATES", "CODEGENERATOR",
"mode", "power", "wind", "temp", "swing", "keyDetail", "key",
"wind_auto", "swing_auto", "temp_min", "wind_max", "temp_max",
"AUTO", "WARM", "HUMI", "COLD", "desc", "name", "icon", "UI",
"UC", "B", "L", "IO", "DOC", "UT", "TRIGGERDEF", "ACTDEF",
"SCHDEF", "OPCODE", "AND", "IF", "cond", "falseact", "trueact",
"actdelay", "act", "SET", "io", "AICMD", "PARAMS", "type",
"val", "delay", "freq", "data", "brand", "format", "url", "idx"
]
def btnMatcher = cmdlistVal =~ /\b([A-Za-z0-9_]+)\s*=\s*\{/
while (btnMatcher.find()) {
String btn = btnMatcher.group(1)
if (!exclusions.contains(btn) && !buttons.contains(btn)) {
if (!(btn.startsWith("http") || btn.startsWith("tcpcmd") || btn.startsWith("od") || btn.toLowerCase() == btn)) {
buttons << btn
}
}
}
if (buttons) {
log.info "[AUTO-IR] 💡 Botões válidos para '${name}' (Panel: ${panelType ?: 'N/A'}): ${buttons.join(', ')}"
}
}
}
}
} catch (Exception e) {
if (logEnable) log.debug "[AUTO-IR] Erro ao extrair botões do cmdlist: ${e.message}"
}
}
}
} catch (Exception e) {
log.warn "[AUTO-IR] Falha ao escanear remotes: ${e.message}"
}

String fullHex = hubitat.helper.HexUtils.byteArrayToHexString(fullPacket)
if (logEnable) {
if (fullHex.length() <= 20000) {

fullHex.toList().collate(4000).eachWithIndex { chunk, idx ->
log.debug "[PKT-HEX-FULL] P${idx+1}: " + chunk.join('')
}
} else {
int maxLen = Math.min(fullHex.length(), 200)
String preview = fullHex.take(maxLen)
log.debug "[PKT-HEX] ${preview}... (${fullPacket.length}B total)"
}
}

String pktType = "unknown"
boolean isNotification = false
try {
List dicts = parsePacket(fullPacket)
if (dicts && dicts.size() >= 1) {
Map d0 = dicts.size() > 0 && dicts[0] instanceof Map ? (Map)dicts[0] : [:]
Map d1 = dicts.size() > 1 && dicts[1] instanceof Map ? (Map)dicts[1] : [:]
if (d0.containsKey("noti") && d0["noti"] == "datachg") {
pktType = "notification_datachg"
isNotification = true
} else if (d1.containsKey("_schg")) {
pktType = "notification_schg"
isNotification = true
} else if (d0.containsKey("act")) {
pktType = "has_act"
} else if (d0.containsKey("ret") || d1.containsKey("ret")) {
pktType = "response"
} else if (d0.containsKey("err") || d1.containsKey("err")) {
pktType = "error"
} else {
pktType = "metadata"
}
}
} catch (Exception ignored) {}
if (isNotification && (state.phase == "polling" || state.phase == "polling_eps_sent" || (state.phase == "idle" && state.node))) {
if (logEnable) log.debug "[PARSE] Notificação recebida (type=${pktType}, fase=${state.phase})"
handleNotification(fullPacket)
return
}
switch (state.phase) {
case "login_sent": handleLoginResponse(fullPacket); break
case "eps_sent": handleEpsResponse(fullPacket); break
case "polling": handlePollResponse(fullPacket); break
case "polling_eps_sent": handlePollResponse(fullPacket); break
default:
if (state.node && !isNotification) {
log.warn "[PARSE] Pacote em fase inesperada (${state.phase}) — tentando processar como EPS"
handleEpsResponse(fullPacket)
} else if (logEnable) {
log.debug "[PARSE] Pacote inesperado (type=${pktType}, fase=${state.phase}) — ignorando"
}
}
}



 
def doPoll() {
if (state.phase != "polling") {
if (logEnable) log.debug "[POLL] Ignorado — não em fase polling (fase=${state.phase})"
return
}
if (!state.node) {
log.warn "[POLL] NODE vazio — reconectando"
connectAndLogin()
return
}
if (logEnable) log.debug "[POLL] Iniciando GetEPS (polling)"
state.phase = "polling_eps_sent"
state.rxBuffer = ""
byte[] pkt = buildGetEpsPacket(state.node)
String hex = hubitat.helper.HexUtils.byteArrayToHexString(pkt)
try {
interfaces.rawSocket.sendMessage(hex)
} catch (Exception e) {
log.error "[POLL] Falha ao enviar GetEPS: ${e.message}"
state.phase = "polling"
}
}
 
private void handlePollResponse(byte[] data) {
try {
List dicts = parsePacket(data)
if (dicts.size() < 2 || !(dicts[1] instanceof Map)) {
log.warn "[POLL RESP] Resposta malformada"
state.phase = "polling"
return
}
Map d2 = (Map)dicts[1]
if (d2.containsKey("err")) {
log.warn "[POLL RESP] Erro: ${d2.err}"
state.phase = "polling"
return
}
Object ret = d2["ret"]
if (!(ret instanceof Map)) {
state.phase = "polling"
return
}
Map retMap = (Map)ret
Object inner = retMap[1L] ?: retMap[1] ?: retMap["1"]
if (!(inner instanceof Map)) {
state.phase = "polling"
return
}
Map innerMap = (Map)inner
Object eps = innerMap["eps"]
if (!(eps instanceof Map)) {
state.phase = "polling"
return
}
Map epsMap = (Map)eps

Map oldState = state.epsState ?: [:]

if (logEnable && epsMap["8b0e"]) {
Map polarDev = (Map)epsMap["8b0e"]
log.debug "[POLL-DEBUG] Polar 8b0e no polling: ${polarDev.keySet()}"

if (polarDev["m"] instanceof Map) {
Map m = (Map)polarDev["m"]
log.debug "[POLL-DEBUG] Polar 8b0e.m: ${m.keySet()}"
}
}
detectChangesAndNotify(oldState, epsMap)


Map currentEps = (state.eps instanceof Map) ? (Map)state.eps : [:]
if (!currentEps || currentEps.isEmpty()) {
Map epsSnapshot = [:]
epsMap.each { devid, dev ->
if (dev instanceof Map) {
Map saved = [:]
((Map)dev).each { k, v -> saved[k] = v }
epsSnapshot[devid.toString()] = saved
}
}
state.eps = epsSnapshot
log.info "[POLL RESP] state.eps populado (${epsSnapshot.size()} dispositivos)"

runInMillis(500, "DescobrirDispositivosDeferred")
}

state.lastEpsTime = now()
state.phase = "polling"

unschedule("doPoll")
runIn(5, "doPoll")
} catch (Exception e) {
log.error "[POLL RESP] Exceção: ${e.message}"
state.phase = "polling"
}
}
 
private void handleNotification(byte[] data) {
try {
List dicts = parsePacket(data)
if (dicts.size() < 2 || !(dicts[1] instanceof Map)) {
if (logEnable) log.debug "[NOTIF] Pacote malformado"
return
}
Map d1 = (Map)dicts[1]
Object schg = d1["_schg"]
if (!(schg instanceof Map)) {
if (logEnable) log.debug "[NOTIF] Campo _schg não encontrado"
return
}
Map schgMap = (Map)schg
schgMap.each { path, changes ->
if (!(changes instanceof Map)) return

String pathStr = path.toString()
List parts = pathStr.split("/")
if (parts.size() < 4) {
if (logEnable) log.debug "[NOTIF] Path muito curto: ${pathStr} (parts=${parts.size()})"
return
}
String devid = parts[3] 
String channel = parts.size() >= 6 ? parts[5] : "status" 

Object chg = changes instanceof Map ? ((Map)changes)["chg"] : null
if (!(chg instanceof Map)) {
if (logEnable) log.debug "[NOTIF] Campo chg não encontrado em ${pathStr}"
return
}
Map chgMap = (Map)chg
Object val = chgMap["val"]
Object typeRaw = chgMap["type"]

if (logEnable && (devid == "8b0e" || devid == "8b10")) {
log.debug "[NOTIF-DEBUG] devid=${devid} chgMap=${chgMap} changes=${changes}"
}

log.info "[NOTIF] ⚡ ${devid}/${channel}: val=${val} type=${typeRaw} (path=${pathStr})"

notifyChild(devid, channel, val, typeRaw, chgMap)
if (val == null && typeRaw == null && state.phase == "polling") {

unschedule("doPoll")
runIn(1, "doPoll")
}
}
} catch (Exception e) {
log.error "[NOTIF] Exceção: ${e.message}"
if (logEnable) log.debug "[NOTIF] Stack: ${e.stackTrace}"
}
}
 
private void detectChangesAndNotify(Map oldState, Map newEps) {
newEps.each { devid, dev ->
if (!(dev instanceof Map)) return
String devStr = devid.toString()
Map dm = (Map)dev
Map oldDev = oldState[devStr] ?: [:]

if (logEnable && (devStr == "8b10" || devStr == "8b0e" || devStr == "8aec" || devStr == "8b1b")) {
log.debug "[DEEP-DEBUG] Dados brutos do dispositivo ${devStr}: ${dm}"
}

Object newVal = dm["val"]
Object oldVal = oldDev["val"]

Map telemetry = extractTelemetryFromDevice(dm)

if (newVal == null && telemetry.containsKey("sensorVal")) {
newVal = telemetry["sensorVal"]
}

Map polarState = extractPolarSwitchState(dm)

if (polarState) {
polarState.each { channel, chData ->
Object chVal = (chData instanceof Map) ? ((Map)chData)["val"] : chData
Object chType = (chData instanceof Map) ? ((Map)chData)["type"] : 0
Map oldCh = oldDev["m"] instanceof Map ? (Map)oldDev["m"] : [:]
Map oldChData = oldCh[channel] instanceof Map ? (Map)oldCh[channel] : [:]
Object oldChVal = oldChData["val"]
if (chVal != oldChVal) {
if (logEnable) log.info "[POLAR] ${devStr}/${channel}: ${oldChVal} → ${chVal}"
notifyChild(devStr, channel, chVal, chType, [:])
}
}

Map newDev = [:]
dm.each { k, v -> if (v != null) newDev[k] = v }
oldState[devStr] = newDev
return 
}

if (telemetry.containsKey("rgbwVal")) {
Object newRgbwVal = telemetry["rgbwVal"]
Object oldRgbwVal = extractTelemetryFromDevice(oldDev).get("rgbwVal")
if (newRgbwVal.toString() != (oldRgbwVal?.toString() ?: "")) {
if (logEnable) log.info "[RGBW] ${devStr}: ${oldRgbwVal} → ${newRgbwVal}"
notifyChild(devStr, "RGBW", newRgbwVal, (int)(telemetry["rgbwType"] ?: 255), [:])
}
Map newDev = [:]
dm.each { k, v -> if (v != null) newDev[k] = v }
oldState[devStr] = newDev
return
}

if (newVal != oldVal || telemetry.containsKey("t") || telemetry.containsKey("h") || telemetry.containsKey("v") || telemetry.containsKey("batt") || telemetry.containsKey("lux")) {
if (logEnable && (telemetry.containsKey("t") || telemetry.containsKey("h") || telemetry.containsKey("v"))) {
log.info "[TELEMETRIA] 🎯 Encontrado: devid=${devStr} T=${telemetry.t} H=${telemetry.h} V=${telemetry.v} sensorVal=${telemetry.sensorVal}"
}

notifyChild(devStr, "status", newVal, dm["type"], telemetry)

Map newDev = [:]
dm.each { k, v -> if (v != null) newDev[k] = v }
oldState[devStr] = newDev
}
}
state.epsState = oldState
}
 
private Map extractTelemetryFromDevice(Map dm) {
Map result = [:]
try {

Object chd = dm["_chd"]
if (!(chd instanceof Map)) return result
Map chdMap = (Map)chd
Object m = chdMap["m"]
if (!(m instanceof Map)) return result
Map mMap = (Map)m
Object innerChd = mMap["_chd"]
if (!(innerChd instanceof Map)) return result
Map innerChdMap = (Map)innerChd

Object tData = innerChdMap["T"]
if (tData instanceof Map) {
Object tVal = ((Map)tData)["val"]
if (tVal != null) result["t"] = tVal
}

if (!result.containsKey("t")) {
Object tTopData = dm["T"]
if (tTopData instanceof Map) {
Object tVal = ((Map)tTopData)["val"]
if (tVal != null) result["t"] = tVal
}
}

Object vData = innerChdMap["V"]
if (vData instanceof Map) {
Map vMap = (Map)vData
Object vVal = vMap["val"]
Object vType = vMap["type"]
if (vVal != null) {
int vTypeInt = vType instanceof Number ? ((Number)vType).intValue() : 0
result["v"] = vVal
result["batt"] = vVal
result["battType"] = vTypeInt
}
}

Object hData = innerChdMap["H"]
if (hData instanceof Map) {
Object hVal = ((Map)hData)["val"]
if (hVal != null) result["h"] = hVal
}

Object zData = mMap["Z"]
if (zData instanceof Map) {
Object zVal = ((Map)zData)["val"]
if (zVal != null) result["lux"] = zVal
}
if (!result.containsKey("lux")) {
Object zInnerData = innerChdMap["Z"] ?: innerChdMap["Z1"]
if (zInnerData instanceof Map) {
Object zVal = ((Map)zInnerData)["val"]
if (zVal != null) result["lux"] = zVal
}
}

Object aData = innerChdMap["A"]
if (aData instanceof Map) {
Map aMap = (Map)aData
Object aVal = aMap["val"]
if (aVal != null) {
result["sensorVal"] = aVal
}
}

Object mData = innerChdMap["M"] ?: innerChdMap["M1"]
if (mData instanceof Map) {
Map mMap2 = (Map)mData
Object mVal = mMap2["val"]
if (mVal != null) {
result["sensorVal"] = mVal
}
}

Object rgbwData = innerChdMap["RGBW"]
if (rgbwData instanceof Map) {
Object rgbwVal = ((Map)rgbwData)["val"]
Object rgbwType = ((Map)rgbwData)["type"]
if (rgbwVal != null) {
result["rgbwVal"] = rgbwVal
result["rgbwType"] = rgbwType ?: 255
}
}


String dmCls = dm["cls"]?.toString() ?: ""
if (dmCls) {
Map prof = (Map)DEVICE_CLASS_MAP()[dmCls]
if (prof) {
String luxCh = prof["luxChannel"]?.toString()
if (luxCh && !result.containsKey("lux")) {
Object lData = innerChdMap[luxCh]
if (lData instanceof Map) {
Object lVal = ((Map)lData)["val"]
if (lVal != null) result["lux"] = lVal
}
}
String battCh = prof["battChannel"]?.toString()
if (battCh && !result.containsKey("batt")) {
Object bData = innerChdMap[battCh]
if (bData instanceof Map) {
Object bVal = ((Map)bData)["val"]
Object bType = ((Map)bData)["type"]
if (bVal != null) {
result["v"] = bVal
result["batt"] = bVal
result["battType"] = bType instanceof Number ? ((Number)bType).intValue() : 0
}
}
}
}
}
} catch (Exception e) {
if (logEnable) log.debug "[TELEMETRIA] Erro ao extrair: ${e.message}"
}
return result
}
 
private Map extractPolarSwitchState(Map dm) {
Map result = [:]
try {

Object m = dm["m"]
if (!(m instanceof Map)) return result
Map mMap = (Map)m

mMap.each { key, chData ->
if (key.toString().matches("P\\d+") && chData instanceof Map) {
Map chMap = (Map)chData
Object chVal = chMap["val"]
if (chVal != null) {
result[key.toString()] = [val: chVal, type: chMap["type"]]
}
}
}
} catch (Exception e) {
if (logEnable) log.debug "[POLAR] Erro ao extrair estado: ${e.message}"
}
return result
}
 
private void notifyChild(String devid, String channel, Object value, Object typeRaw = 0, Map extraData = [:]) {
try {
int rawVal = 0
if (value != null) {
try { rawVal = value instanceof Number ? ((Number)value).intValue() : Integer.parseInt(value.toString()) }
catch (Exception ignored) {}
}
Map profile = resolveDeviceProfile(devid)
Object finalVal = (value != null) ? rawVal : null

if (profile.deviceType == "rgbw" && value instanceof Map) {
finalVal = value
}
if (finalVal != null && (profile.deviceType == "dimmer" || profile.deviceType == "ctdimmer")) {
int scale = ((profile.levelScale ?: 255) as Integer)
if (scale > 100) {
finalVal = (int) Math.round(rawVal * 100.0 / scale)
if (logEnable) log.debug "[NOTIFY] Escala ${profile.deviceType}: raw=${rawVal} → pct=${finalVal}% (scale=${scale})"
}
}
if (finalVal != null && profile.deviceType == "curtain") {

if (rawVal > 100) {
finalVal = (int) Math.round(rawVal * 100.0 / 255.0)
if (logEnable) log.debug "[NOTIFY] Escala Cortina: raw=${rawVal} → pct=${finalVal}%"
}
}

String matchChannel = channel
Map extra2 = [:]
if (extraData instanceof Map) extra2.putAll(extraData)
extra2.srcChannel = channel
if (profile.auxChannels instanceof Map && ((Map)profile.auxChannels).values().contains(channel)) {
matchChannel = (profile.channels instanceof List && profile.channels) ? ((List)profile.channels)[0].toString() : "P1"
}
def children = getChildDevices()
if (logEnable) log.debug "[NOTIFY] Procurando child devid=${devid} ch=${channel} (match=${matchChannel}) entre ${children.size()} children"

List altChannels = [matchChannel]
if (matchChannel in ["status", "P1", "A", "M", "M1", "T"]) {
altChannels = ["status", "P1", "A", "M", "M1", "T"]
}
boolean found = false
children.each { child ->
String childDevid = ""
String childChannel = ""
try { childDevid = child.getDevid() ?: "" } catch (Exception ignored) {}
try { childChannel = child.getChannel() ?: "" } catch (Exception ignored) {}
if (childDevid == devid && altChannels.contains(childChannel)) {
found = true
if (logEnable) log.debug "[NOTIFY] ✅ Child encontrado: ${child.displayName} (ch=${childChannel})"

if (extraData && (extraData.containsKey("t") || extraData.containsKey("v") || extraData.containsKey("batt"))) {
log.info "[TELEMETRIA] 🎯 Encontrado: devid=${devid} t=${extraData.t} v=${extraData.v} batt=${extraData.batt}"
}
try {
child.handleEvent(finalVal, typeRaw, extra2)
log.info "[NOTIFY] ✅ ${child.displayName} atualizado: val=${finalVal} type=${typeRaw} src=${extra2.srcChannel}"
} catch (Exception e2) {
log.error "[NOTIFY] Erro ao chamar handleEvent: ${e2.message}"
}
}
}
if (!found) {
if (logEnable) log.warn "[NOTIFY] ⚠️ Sem child para devid=${devid} ch=${channel}"
}
} catch (Exception e) {
log.error "[NOTIFY] Exceção: ${e.message}"
}
}
 
def sendDeviceCommand(String devid, String channel, int cmdType, int value) {
if (!checkLicense()) { log.error "[CMD] Licença inativa — comando bloqueado"; return }
Map profile = resolveDeviceProfile(devid)

if (logEnable) {
Map epsEntry = (state.eps instanceof Map) ? (Map)state.eps[devid] : null
if (epsEntry instanceof Map) {
log.debug "[CMD-DEBUG] state.eps[${devid}] keys=[${epsEntry.keySet().join(', ')}] cls=${epsEntry.cls} cgy=${epsEntry.cgy}"
Object m = epsEntry["m"]
if (m != null) {
if (m instanceof Map) {
log.debug "[CMD-DEBUG]   m.keys=[${((Map)m).keySet().join(', ')}]"
((Map)m).each { mk, mv ->
if (mv instanceof Map) log.debug "[CMD-DEBUG]   m.${mk} = [${((Map)mv).keySet().join(', ')}]"
else log.debug "[CMD-DEBUG]   m.${mk} = ${mv}"
}
} else {
log.debug "[CMD-DEBUG]   m=TIPO=!Map val=${m.toString().take(80)}"
}
} else {
log.debug "[CMD-DEBUG]   m=null"
}
Object chd = epsEntry["_chd"]
if (chd != null) {
if (chd instanceof Map) {
Map chdMap = (Map)chd
log.debug "[CMD-DEBUG]   _chd keys=[${chdMap.keySet().join(', ')}]"
chdMap.each { ck, cv ->
if (cv instanceof Map) {
log.debug "[CMD-DEBUG]   _chd.${ck} keys=[${((Map)cv).keySet().join(', ')}]"
if (ck == "m" && ((Map)cv).containsKey("_chd")) {
Object inner = ((Map)cv)["_chd"]
if (inner instanceof Map) log.debug "[CMD-DEBUG]   _chd.m._chd keys=[${((Map)inner).keySet().join(', ')}]"
else log.debug "[CMD-DEBUG]   _chd.m._chd=!Map"
}
} else {
log.debug "[CMD-DEBUG]   _chd.${ck} = ${cv.toString().take(60)}"
}
}
} else {
log.debug "[CMD-DEBUG]   _chd=!Map val=${chd.toString().take(80)}"
}
} else {
log.debug "[CMD-DEBUG]   _chd=null"
}
log.debug "[CMD-DEBUG] Profile resolveu: type=${profile.deviceType} channels=${profile.channels} cmdKey=${profile.cmdKey ?: '(default)'} valtag=${profile.valtag ?: 'm'}"
} else {
log.warn "[CMD-DEBUG] state.eps[${devid}] NÃO encontrado ou não é Map"
}
}
if (profile.readOnly == true || profile.localControl == false) {
log.warn "[CMD] ${devid}/${channel}: Dispositivo sem controle local (readOnly). Ignorando comando type=${cmdType} val=${value}."
sendEvent(name: "status", value: "${devid}: somente leitura local")
return
}
int normalizedCmdType = normalizeCmdTypeForProfile(profile, cmdType, value)
int normalizedValue = normalizeValueForProfile(profile, normalizedCmdType, value)
String cmdKey = profile.containsKey("cmdKey") ? profile.cmdKey : channel
String valtag = profile.containsKey("valtag") ? profile.valtag : "m"


Object sendVal = (int)normalizedValue
if (profile.deviceType == "rgbw" && normalizedCmdType != 128) {
sendVal = [type: "TS", value: ((long)normalizedValue) & 0xFFFFFFFFL]
}
Map cmd = [
devid: devid,
channel: channel,
cmdKey: cmdKey,
cmdType: normalizedCmdType,
value: sendVal,
valtag: valtag,
ts: now(),
attempts: 0
]
if (!isSessionReadyForCommand()) {
state.pendingCmd = cmd
log.warn "[CMD] Sessão indisponível (fase=${state.phase}, node=${state.node ? 'ok' : 'vazio'}). Comando enfileirado."
sendEvent(name: "status", value: "Reconectando para enviar comando...")
connectAndLogin()
runIn(3, "retryPendingCommand")
return
}
boolean sent = sendRfSetACommand(devid, cmdKey, normalizedCmdType, sendVal, valtag)
if (!sent) {
state.pendingCmd = cmd
runIn(2, "retryPendingCommand")
}
}
 
def sendIRCommand(String devid, String remoteId, String buttonName) {
if (!checkLicense()) { log.error "[CMD] Licença inativa — comando IR bloqueado"; return }
if (logEnable) log.debug "[CMD] sendIRCommand(devid=${devid}, remoteId=${remoteId}, buttonName=${buttonName})"
Map cmd = [
isIR: true,
devid: devid,
remoteId: remoteId,
button: buttonName,
ts: now(),
attempts: 0
]
if (!isSessionReadyForCommand()) {
state.pendingCmd = cmd
log.warn "[CMD] Sessão indisponível (fase=${state.phase}, node=${state.node ? 'ok' : 'vazio'}). Comando IR enfileirado."
sendEvent(name: "status", value: "Reconectando para enviar comando IR...")
connectAndLogin()
runIn(3, "retryPendingCommand")
return
}
boolean sent = sendIRCommandRaw(devid, remoteId, buttonName)
if (!sent) {
state.pendingCmd = cmd
runIn(2, "retryPendingCommand")
}
}
 
private boolean sendIRCommandRaw(String devid, String remoteId, String buttonName) {
if (!state.node) return false
try {
if (logEnable) log.debug "[CMD] Enviando IR: remoteId=${remoteId} button=${buttonName}"
Map dict0 = ["_sel": 1, "req": false, "timestamp": 10]
Map dict1 = [
"args": [
"opt": buttonName,
"cron_name": remoteId,
"valtag": "m",
"response_token": state.node + "/me"
],
"node": state.node + "/me/ai",
"act": "RunA"
]
byte[] pkt = buildPacket([dict0, dict1])
String hex = hubitat.helper.HexUtils.byteArrayToHexString(pkt)
interfaces.rawSocket.sendMessage(hex)
return true
} catch (Exception e) {
log.error "[CMD] Falha ao enviar IR no socket: ${e.message}"
if (e.message?.contains("reset") || e.message?.contains("Write failed")) {
log.warn "[CMD] Socket reset — forçando reconexão..."
state.phase = "idle"
runIn(1, "connectAndLogin")
}
return false
}
}
 
private boolean isSessionReadyForCommand() {
boolean phaseOk = (state.phase == "polling" || state.phase == "polling_eps_sent" || state.phase == "done")
return phaseOk && state.node
}
 
private boolean sendRfSetACommand(String devid, String channel, int cmdType, Object value, String valtag = "m") {
if (!state.node) return false
try {
if (logEnable) log.debug "[CMD] devid=${devid} ch=${channel} type=${cmdType} val=${value} valtag='${valtag}'"
Map dict0 = ["_sel": 1, "req": false, "timestamp": 10]
Map dict1 = [
"args": [val: value, valtag: valtag, devid: devid, key: channel, type: cmdType],
"node": state.node + "/me/ep",
"act": "rfSetA"
]
byte[] pkt = buildPacket([dict0, dict1])
String hex = hubitat.helper.HexUtils.byteArrayToHexString(pkt)
interfaces.rawSocket.sendMessage(hex)
return true
} catch (Exception e) {
log.error "[CMD] Falha no socket: ${e.message}"
if (e.message?.contains("reset") || e.message?.contains("Write failed")) {
log.warn "[CMD] Socket reset — forçando reconexão..."
state.phase = "idle"
runIn(1, "connectAndLogin")
}
return false
}
}
 
private List<Map> buildCommandArgsAttemptsForDevice(String devid, String channel, int cmdType, int value) {
String ch = channel?.toString() ?: "P1"
int ct = (cmdType > 0 ? cmdType : 128)


return [[
"val": value,
"valtag": "m",
"devid": devid,
"key": ch,
"type": ct
]]
}
 
private Map buildCommandMetaForAttempt(String cls, String devid, int attemptIndex) {
return [node: state.node + "/me/ep", act: "rfSetA"]
}
 
def retryPendingCommand() {
Map cmd = (state.pendingCmd instanceof Map) ? (Map)state.pendingCmd : null
if (!cmd) return
int attempts = (cmd.attempts instanceof Number) ? ((Number)cmd.attempts).intValue() : 0
if (!isSessionReadyForCommand()) {
if (attempts >= 1) {
log.error "[CMD] ❌ Comando pendente expirou sem sessão ativa: ${cmd}"
sendEvent(name: "status", value: "Falha: sem conexão para comando")
state.pendingCmd = null
return
}
cmd.attempts = attempts + 1
state.pendingCmd = cmd
log.warn "[CMD] Retry aguardando sessão ativa (tentativa=${cmd.attempts})"
connectAndLogin()
runIn(3, "retryPendingCommand")
return
}
boolean ok = false
if (cmd.isIR == true) {
ok = sendIRCommandRaw(cmd.devid?.toString() ?: "", cmd.remoteId?.toString() ?: "", cmd.button?.toString() ?: "")
} else {
Object retryVal = (cmd.value instanceof Map) ? (Map)cmd.value : ((cmd.value as Integer) ?: 0)
ok = sendRfSetACommand(
cmd.devid?.toString() ?: "",
cmd.cmdKey?.toString() ?: cmd.channel?.toString() ?: "P1",
(cmd.cmdType as Integer) ?: 128,
retryVal,
cmd.valtag != null ? cmd.valtag.toString() : "m"
)
}
if (ok) {
log.info "[CMD] ✅ Comando pendente enviado com sucesso"
sendEvent(name: "status", value: "Comando enviado")
state.pendingCmd = null
return
}
if (attempts >= 1) {
log.error "[CMD] ❌ Comando pendente falhou após retry: ${cmd}"
sendEvent(name: "status", value: "Falha ao enviar comando")
state.pendingCmd = null
return
}
cmd.attempts = attempts + 1
state.pendingCmd = cmd
log.warn "[CMD] Retry de envio agendado (tentativa=${cmd.attempts})"
runIn(2, "retryPendingCommand")
}
 
private void flushPendingCommandIfAny() {
Map cmd = (state.pendingCmd instanceof Map) ? (Map)state.pendingCmd : null
if (!cmd) return
if (!isSessionReadyForCommand()) return
if (logEnable) log.debug "[CMD] Flush pending command após sessão ativa"
boolean ok = false
if (cmd.isIR == true) {
ok = sendIRCommandRaw(cmd.devid?.toString() ?: "", cmd.remoteId?.toString() ?: "", cmd.button?.toString() ?: "")
} else {
Object flushVal = (cmd.value instanceof Map) ? (Map)cmd.value : ((cmd.value as Integer) ?: 0)
ok = sendRfSetACommand(
cmd.devid?.toString() ?: "",
cmd.cmdKey?.toString() ?: cmd.channel?.toString() ?: "P1",
(cmd.cmdType as Integer) ?: 128,
flushVal,
cmd.valtag != null ? cmd.valtag.toString() : "m"
)
}
if (ok) {
state.pendingCmd = null
sendEvent(name: "status", value: "Comando enviado")
}
}
 
 
 
private void dumpEpsSummary() {
Map eps = (state.eps instanceof Map) ? (Map)state.eps : [:]
if (!eps) return
log.debug "[EPS-DUMP] ${eps.size()} dispositivos:"
eps.each { k, v ->
Map d = (v instanceof Map) ? (Map)v : [:]
log.debug "[EPS-DUMP]   devid=${k} name='${d.name}' cls=${d.cls} cgy=${d.cgy}"
}
}
def "Limpar Cache IR"() {
log.info "[CMD] Limpando Cache IR..."
state.discoveredRemotes = [:]
def children = getChildDevices()
int deleted = 0
children.each { child ->
if (child.deviceNetworkId.contains("AI_IR_") || child.deviceNetworkId.contains("AIB_")) {
try {
deleteChildDevice(child.deviceNetworkId)
deleted++
} catch (Exception e) {
log.error "[LIMPEZA] Erro ao deletar child ${child.deviceNetworkId}: ${e.message}"
}
}
}
log.info "[CMD] Cache IR limpo. ${deleted} child devices de IR foram removidos."
sendEvent(name: "status", value: "Cache IR limpo (${deleted} apagados). Clique em Refresh.")
}



def installed() {
log.info "[LIFECYCLE] Driver instalado"
state.phase = "idle"
state.rxBuffer = ""
state.node = ""
state.eps = [:]
state.pendingCmd = null
}
def updated() {
log.info "[LIFECYCLE] Preferências salvas"
initialize()
}
def initialize() {
log.info "[LIFECYCLE] Driver inicializado"
state.phase = "idle"
state.rxBuffer = ""
state.pendingCmd = null
sendEvent(name: "hubUID", value: getHubUID())


unschedule("licenseHeartbeat")
schedule("0 7 3 * * ?", "licenseHeartbeat") 
requestLicenseCheck()
}
def refresh() {
requestLicenseCheck()
if (!checkLicense()) return
log.info "[CMD] Refresh — iniciando Login + GetEPS"
connectAndLogin()
}
 
def "Descobrir Dispositivos"() {
if (!checkLicense()) return
Map eps = (state.eps instanceof Map) ? (Map)state.eps : [:]
if (!eps || eps.isEmpty()) {

if (state.node) {
log.warn "[DISCOVER] Cache vazio — reconectando para obter EPS..."
sendEvent(name: "status", value: "Reconectando para descobrir dispositivos...")
connectAndLogin()
runIn(8, "DescobrirDispositivosDeferred")
return
}
log.warn "[DISCOVER] Nenhum dispositivo em state.eps. Execute 'Refresh' primeiro."
sendEvent(name: "status", value: "Sem dispositivos no cache. Inicie conexao primeiro.")
return
}
int created = 0
int existing = 0
int errors = 0
int skipped = 0
eps.each { devidObj, devObj ->
String devid = devidObj?.toString()
if (!devid || !(devObj instanceof Map)) {
skipped++
return
}
Map dev = (Map)devObj
String cls = dev["cls"]?.toString() ?: ""
String cgy = dev["cgy"]?.toString() ?: ""
String name = dev["name"]?.toString() ?: "LifeSmart ${devid}"
Map profile = resolveDeviceProfile(devid)
String deviceType = profile.deviceType?.toString() ?: "switch"
List channels = (profile.channels instanceof List) ? (List)profile.channels : ["P1"]
channels.each { ch ->
String channel = ch?.toString() ?: "P1"
String dni = buildChildDni(devid, channel)
String childLabel = "${name} [${channel}]"

String driverName = (profile.channelDriverMap instanceof Map && profile.channelDriverMap[channel])
? profile.channelDriverMap[channel].toString()
: (profile.childDriver ?: "LifeSmart Device")
try {
def existingChild = getChildDevice(dni)
if (existingChild) {
existing++
try {
existingChild.setLabel(childLabel)
} catch (Exception ignored) {}
if (logEnable) log.debug "[DISCOVER] Já existe: ${dni}"
} else {
addChildDevice(
"tecnosimples",
driverName,
dni,
[
label: childLabel,
isComponent: false,
name: childLabel
]
)
def child = getChildDevice(dni)
if (child) {
Map prefs = [
devid: devid,
channel: channel,
deviceType: deviceType,
deviceCls: cls
]
child.updateSetting("devid", [value: prefs.devid, type: "text"])
child.updateSetting("channel", [value: prefs.channel, type: "text"])
child.updateSetting("deviceType", [value: prefs.deviceType, type: "enum"])
child.updateSetting("deviceCls", [value: prefs.deviceCls, type: "text"])
if (profile.auxChannels instanceof Map && profile.auxChannels.colorTemperature) {
child.updateSetting("ctChannel", [value: profile.auxChannels.colorTemperature.toString(), type: "text"])
}
try { child.initialize() } catch (Exception ignored) {}
}
created++
log.info "[DISCOVER] Child criado: ${childLabel} | dni=${dni} | type=${deviceType}"
}
} catch (Exception e) {
errors++
log.error "[DISCOVER] Erro ao criar/atualizar child devid=${devid} ch=${channel}: ${e.message}"
}
}
}

if (state.discoveredRemotes instanceof Map) {
state.discoveredRemotes.each { String spotDevid, Object remotesObj ->
if (remotesObj instanceof Map) {
Map remotes = (Map)remotesObj
remotes.each { String remoteId, String name ->

String channel = remoteId
String dni = buildChildDni(spotDevid, channel)
String childLabel = "${name} [IR]"
String driverName = "LifeSmart Device"
try {
def existingChild = getChildDevice(dni)
if (existingChild) {
existing++
try {
if (existingChild.label != childLabel) {
existingChild.setLabel(childLabel)
existingChild.name = childLabel
}
} catch (Exception ignored) {}

try {
existingChild.updateSetting("devid", [value: spotDevid, type: "text"])
existingChild.updateSetting("channel", [value: channel, type: "text"])
existingChild.updateSetting("deviceType", [value: "virtual", type: "enum"])
existingChild.updateSetting("deviceCls", [value: "virtual_ir", type: "text"])
existingChild.updateSetting("defaultRemoteId", [value: remoteId, type: "text"])
try { existingChild.initialize() } catch (Exception ignored) {}
} catch (Exception e) {
if (logEnable) log.debug "[DISCOVER-IR] Erro ao atualizar configurações do child IR existente ${dni}: ${e.message}"
}
if (logEnable) log.debug "[DISCOVER-IR] Já existe controle IR: ${dni}"
} else {
addChildDevice(
"tecnosimples",
driverName,
dni,
[
label: childLabel,
isComponent: false,
name: childLabel
]
)
def child = getChildDevice(dni)
if (child) {
Map prefs = [
devid: spotDevid,
channel: channel,
deviceType: "virtual",
deviceCls: "virtual_ir"
]
child.updateSetting("devid", [value: prefs.devid, type: "text"])
child.updateSetting("channel", [value: prefs.channel, type: "text"])
child.updateSetting("deviceType", [value: prefs.deviceType, type: "enum"])
child.updateSetting("deviceCls", [value: prefs.deviceCls, type: "text"])
child.updateSetting("defaultRemoteId", [value: remoteId, type: "text"])
try { child.initialize() } catch (Exception ignored) {}
}
created++
log.info "[DISCOVER-IR] Child IR criado: ${childLabel} | dni=${dni} | remoteId=${remoteId}"
}
} catch (Exception e) {
errors++
log.error "[DISCOVER-IR] Erro ao criar/atualizar child IR devid=${spotDevid} remoteId=${remoteId}: ${e.message}"
}
}
}
}
}
String summary = "Discover OK — criados:${created}, existentes:${existing}, ignorados:${skipped}, erros:${errors}"
log.info "[DISCOVER] ${summary}"
sendEvent(name: "status", value: summary)
}
 
def "DescobrirDispositivosDeferred"() {
"Descobrir Dispositivos"()
}
 
private String buildChildDni(String devid, String channel) {
return "LS-${device.id}-${devid}-${channel}"
}
 
private Map DEVICE_CLASS_MAP() {
return [

"SL_SW_MJ1_V1": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_MJ2_V1": [deviceType: "switch", channels: ["P1", "P2"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_MJ3_V1": [deviceType: "switch", channels: ["P1", "P2", "P3"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_MJ1_V3": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_MJ2_V3": [deviceType: "switch", channels: ["P1", "P2"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_MJ3_V3": [deviceType: "switch", channels: ["P1", "P2", "P3"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_MJ1": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_MJ2": [deviceType: "switch", channels: ["P1", "P2"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_MJ3": [deviceType: "switch", channels: ["P1", "P2", "P3"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],

"SL_SW_ND1_V2": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_ND2_V2": [deviceType: "switch", channels: ["P1", "P2"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_ND3_V2": [deviceType: "switch", channels: ["P1", "P2", "P3"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],

"SL_SW_ND1": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_ND2": [deviceType: "switch", channels: ["P1", "P2"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_ND3": [deviceType: "switch", channels: ["P1", "P2", "P3"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_MC_ND1": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_MC_ND2": [deviceType: "switch", channels: ["P1", "P2"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_MC_ND3": [deviceType: "switch", channels: ["P1", "P2", "P3"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],

"SL_LI_WW_V2": [deviceType: "dimmer", channels: ["P1"], cmdType: 207, cmdTypeOff: 128, levelScale: 255, onVal: 255, offVal: 0, childDriver: "LifeSmart Dimmer"],
"SL_LI_WW_V1": [deviceType: "dimmer", channels: ["P1"], cmdType: 129, levelScale: 100, onVal: 100, offVal: 0],
"ZG#TS00522": [deviceType: "dimmer", channels: ["L1", "L2"], cmdType: 128, levelScale: 255, onVal: 1, offVal: 0, childDriver: "LifeSmart Dimmer"],



"ZG#TS130F": [deviceType: "curtain", channels: ["D1"], cmdKey: "tD1", cmdType: 207, levelScale: 100, valtag: "m"],
"SL_CURTAIN": [deviceType: "curtain", channels: ["D1"], cmdKey: "tD1", cmdType: 207, levelScale: 100, valtag: "m"],
"SL_PIR_MOTION": [deviceType: "motion", channels: ["status"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Motion Sensor"],
"SL_SEN_PIR": [deviceType: "motion", channels: ["status"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Motion Sensor"],
"SL_SEN_DOOR": [deviceType: "contact", channels: ["status"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Contact Sensor"],
"SL_SEN_MAG": [deviceType: "contact", channels: ["status"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Contact Sensor"],
"SL_DF_GG_V1": [deviceType: "contact", channels: ["A"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Contact Sensor"],
"SL_DF_MM_V1": [deviceType: "motion", channels: ["M"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Motion Sensor"],
"SL_SC_BG_V1": [deviceType: "contact", channels: ["P1"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Contact Sensor"],
"SL_SC_BB_V2": [deviceType: "button", channels: ["P1"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Button"],
"SL_DF_SR_V1": [deviceType: "siren", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0],


"SL_SW_IF1_V4": [deviceType: "switch", channels: ["L1"], readOnly: true],
"SL_SW_IF2_V4": [deviceType: "switch", channels: ["L1", "L2"], readOnly: true],
"SL_SW_IF3_V4": [deviceType: "switch", channels: ["L1", "L2", "L3"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_IF3": [deviceType: "switch", channels: ["L1", "L2", "L3"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SF_IF3": [deviceType: "switch", channels: ["L1", "L2", "L3"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Relay"],

"SL_SW_CP3_V1": [deviceType: "switch", channels: ["L1", "L2", "L3"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Relay"],

"SL_SW_NS1_V1": [deviceType: "switch", channels: ["L1"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_NS2_V1": [deviceType: "switch", channels: ["L1", "L2"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_SW_NS3_V1": [deviceType: "switch", channels: ["L1", "L2", "L3"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Relay"],

"SL_DOOYA_V3": [deviceType: "curtain", channels: ["P1"], cmdType: 207, levelScale: 100, valtag: "m"],

"SL_SC_BM_V1": [deviceType: "motion", channels: ["M"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Motion Sensor"],
"SL_BP_MZ_V1": [deviceType: "motion", channels: ["P1"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Motion Sensor", luxChannel: "P2", battChannel: "P3"],
"ZG#HE200_ZB": [deviceType: "motion", channels: ["M1"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Motion Sensor"],
"ZG#TS06012": [deviceType: "motion", channels: ["M1"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Motion Sensor", luxChannel: "Z1"],

"SL_SC_BE_V1": [deviceType: "environment", channels: ["T"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Environment Sensor"],

"ZG#PMT300-S-ZTN": [deviceType: "contact", channels: ["PM1"], cmdType: 128, onVal: 1, offVal: 0],

"SL_LK_LS_V3": [deviceType: "contact", channels: ["EVTLO"], cmdType: 128, onVal: 1, offVal: 0],

"ZG#e70f96b3773a4c92#": [deviceType: "switch", channels: ["ALM1"], cmdType: 207, onVal: 1, offVal: 0],

"SL_NATURE_V1": [deviceType: "switch", channels: ["P1","P2","P3"],
cmdType: 128, onVal: 1, offVal: 0,
childDriver: "LifeSmart Relay"],

"V_IND_S": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 1, offVal: 0,
childDriver: "LifeSmart Relay"],

"SL_P_V1": [deviceType: "switch", channels: ["P2","P3","P4","P5","P6","P7"],
cmdType: 128, cmdTypeOn: 129, cmdTypeOff: 128, onVal: 0, offVal: 0,
childDriver: "LifeSmart Relay",
channelDriverMap: ["P5": "LifeSmart Contact Sensor", "P6": "LifeSmart Contact Sensor", "P7": "LifeSmart Contact Sensor"]],
"SL_JEMA": [deviceType: "switch", channels: ["P2","P3","P4","P5","P6","P7","P8","P9","P10"],
cmdType: 128, cmdTypeOn: 129, cmdTypeOff: 128, onVal: 0, offVal: 0,
childDriver: "LifeSmart Relay",
channelDriverMap: ["P5": "LifeSmart Contact Sensor", "P6": "LifeSmart Contact Sensor", "P7": "LifeSmart Contact Sensor"]],

"SL_P_IR_V2": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0],

"ZG#06ffff2027":[deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 1, offVal: 0, childDriver: "LifeSmart Relay"],


"SL_OL": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_OL_3C": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_OL_DE": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_OL_UK": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_OL_UL": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"OD_WE_OT1": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],
"SL_OL_W": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],

"SL_S": [deviceType: "switch", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0, childDriver: "LifeSmart Relay"],

"SL_SIREN": [deviceType: "siren", channels: ["P1"], cmdType: 128, onVal: 255, offVal: 0],

"SL_CT_RGBW_V1": [deviceType: "rgbw", channels: ["RGBW"], cmdType: 255, valtag: "m", childDriver: "LifeSmart RGBW"]
]
}
 
private Map resolveDeviceProfile(String devid) {
Map eps = (state.eps instanceof Map) ? (Map)state.eps : [:]
Map dev = (eps[devid] instanceof Map) ? (Map)eps[devid] : [:]
String cls = dev["cls"]?.toString() ?: ""

if (cls == "SL_LI_WW_V2") {
String wwMode = (settings.forceWWMode && settings.forceWWMode != "auto")
? settings.forceWWMode.toString()
: (dev["icon"]?.toString() == "colour" ? "color" : "dimmer")
if (wwMode == "color") {
return [deviceType: "ctdimmer", channels: ["P1"], auxChannels: [colorTemperature: "P2"],
cmdType: 207, cmdTypeOff: 128, levelScale: 255, onVal: 255, offVal: 0,
childDriver: "LifeSmart CT Dimmer"]
}

}
Map profile = (Map)DEVICE_CLASS_MAP()[cls]
if (profile) return profile

String cgy = dev["cgy"]?.toString() ?: ""
String name = dev["name"]?.toString() ?: ""
String deviceType = mapDeviceTypeFallback(cls, cgy, name)
List epsChannels = extractChannelsFromEpsDevice(dev)
List channels = epsChannels ?: mapChannelsFallback(cls, cgy, name)
if (logEnable) log.warn "[PROFILE] CLS sem mapeamento: '${cls}' (devid=${devid}) — canais=${channels} (${epsChannels ? 'do EPS' : 'fallback'})"
return [
deviceType: deviceType,
channels: channels,
cmdType: 128,
onVal: 1,
offVal: 0,
levelScale: 255,
valtag: "m"
]
}
 
private int normalizeCmdTypeForProfile(Map profile, int requestedCmdType, int value) {
if (requestedCmdType > 0) return requestedCmdType
int defaultType = (profile?.cmdType ?: 128) as Integer

if (profile?.containsKey("cmdTypeOn") && value > 0) {
return (profile.cmdTypeOn as Integer)
}
if (profile?.containsKey("cmdTypeOff") && value == 0) {
return (profile.cmdTypeOff as Integer)
}

if (profile?.deviceType == "dimmer" && profile?.onVal == 1) {
return (value > 0) ? 129 : 128
}
return defaultType
}
 
private int normalizeValueForProfile(Map profile, int cmdType, int requestedValue) {


if (requestedValue == 0 || requestedValue == 1) {
if (cmdType == 128) return requestedValue > 0 ? ((profile?.onVal ?: 255) as Integer) : 0
if (cmdType == 129) return requestedValue 
if (cmdType == 207 && requestedValue == 1) return 1 
}
if (cmdType == 129 && profile?.deviceType == "dimmer" && profile?.onVal == 1) {
return requestedValue > 0 ? 1 : 0
}

if (cmdType == 129 && profile?.deviceType == "switch" && profile?.containsKey("cmdTypeOn")) {
return requestedValue > 0 ? ((profile?.onVal ?: 0) as Integer) : 0
}
if (cmdType == 128) {
return requestedValue > 0 ? ((profile?.onVal ?: 255) as Integer) : 0
}
if (cmdType == 129 || cmdType == 130 || cmdType == 207) {
int pct = Math.max(0, Math.min(100, requestedValue))
int scale = ((profile?.levelScale ?: 255) as Integer)
return (int) Math.round(pct * scale / 100.0)
}
if (cmdType == 136) {
return Math.max(0, Math.min(100, requestedValue))
}
return requestedValue
}
 
private String mapDeviceTypeFallback(String cls, String cgy, String name) {
String s = "${cls} ${cgy} ${name}".toLowerCase()
if (s.contains("curtain") || s.contains("cortina") || s.contains("shade")) return "curtain"
if (s.contains("motion") || s.contains("pir")) return "motion"
if (s.contains("contact") || s.contains("door") || s.contains("window")) return "contact"
if (s.contains("siren")) return "siren"
if (s.contains("dimmer")) return "dimmer"
if (s.contains("temp") || s.contains("temperature")) return "temperature"
if (s.contains("audio") || s.contains("sound")) return "audio"
return "switch"
}
 
private List mapChannelsFallback(String cls, String cgy, String name) {
String s = "${cls} ${cgy} ${name}".toLowerCase()
if (s.contains("3ch") || s.contains("3 canais") || s.contains("3 canal")) return ["P1", "P2", "P3"]
if (s.contains("2ch") || s.contains("2 canais") || s.contains("2 canal")) return ["P1", "P2"]
return ["P1"]
}
 
private List extractChannelsFromEpsDevice(Map dev) {
try {

Object m = dev["m"]
if (m instanceof Map) {
List control = ((Map)m).keySet()
.collect { it.toString() }
.findAll { String k -> k.matches(/[PLD]\d+/) }
.sort()
if (control) return control
}

Object chd = dev["_chd"]
if (!(chd instanceof Map)) return []
Object mChd = ((Map)chd)["m"]
if (!(mChd instanceof Map)) return []
Object innerChd = ((Map)mChd)["_chd"]
if (!(innerChd instanceof Map)) return []
List allKeys = ((Map)innerChd).keySet().collect { it.toString() }
List control = allKeys.findAll { String k -> k.matches(/[PLD]\d+/) }.sort()
if (control) return control

List sensor = allKeys.findAll { it in ["A", "M", "D1"] }.sort()
if (sensor) return sensor
return []
} catch (Exception e) {
if (logEnable) log.debug "[EPS CHANNELS] Erro ao extrair canais: ${e.message}"
return []
}
}



private String getHubUID() {
def hub = location.hub
try { String v = hub.zigbeeEui?.toString(); if (v) return v } catch (Exception ignored) {}
try { String v = hub.zigbeeId?.toString(); if (v) return v } catch (Exception ignored) {}
try { String v = hub.id?.toString(); if (v) return v } catch (Exception ignored) {}
log.error "[LICENÇA] Nenhuma propriedade de ID única disponível no hub"
return "UNKNOWN"
}
private String licenseEndpoint() {
return (settings.licenseUrl?.trim()) ?: "https://script.google.com/macros/s/AKfycbwvNpGsy-9Xs4NRk6eer3HRZBXbkfhHJtqp23aIZJA1KBZJUJZ0OJnQvJ6PF36kcHjl/exec"
}
 
private void requestLicenseCheck() {
Map params = [uri: licenseEndpoint(), query: [action: "check", uid: getHubUID()], timeout: 15]
sendEvent(name: "status", value: "🔄 Verificando licença...")
try {
asynchttpGet("licenseCheckCallback", params, [hops: 0])
} catch (Exception e) {
log.warn "[LICENÇA] Falha ao iniciar consulta: ${e.message} — mantém último-status-bom"
applyLicenseResponse(null)
}
}
 
def licenseCheckCallback(resp, data) {
int st = 0
try { st = (resp?.status ?: 0) as int } catch (Exception ignored) {}
if (st in [301, 302, 307, 308]) {
String loc = null
try { loc = resp?.headers?.find { it.key?.toString()?.equalsIgnoreCase("location") }?.value } catch (Exception ignored) {}
int hops = (data?.hops ?: 0) as int
if (loc && hops < 3) {
asynchttpGet("licenseCheckCallback", [uri: loc, timeout: 15], [hops: hops + 1])
return
}
}
Map response = null
try {
if (st == 200 && resp?.data) {
response = (Map) new groovy.json.JsonSlurper().parseText(resp.data.toString())
} else {
log.warn "[LICENÇA] HTTP ${st} — offline/erro, mantém último-status-bom"
}
} catch (Exception e) {
log.warn "[LICENÇA] Resposta inválida (${e.message}) — mantém último-status-bom"
}
applyLicenseResponse(response)
}
 
private void applyLicenseResponse(Map response) {
Map prev = (atomicState.lic instanceof Map) ? (Map)atomicState.lic : null
boolean wasLicensed = licenseEvaluate(prev, now())
atomicState.lic = licenseApplyCheck(prev, response, now())
boolean nowLicensed = licenseEvaluate((Map)atomicState.lic, now())
sendEvent(name: "status", value: nowLicensed
? licenseStatusText((Map)atomicState.lic)
: (response == null ? "📡 Offline — usando última validação" : licenseStatusText((Map)atomicState.lic)))
if (nowLicensed && !wasLicensed && settings.deviceIP) {
log.info "[LICENÇA] Licenciado — conectando."
connectAndLogin()
} else if (!nowLicensed && wasLicensed) {
log.warn "[LICENÇA] Licença deixou de ser válida — bloqueando."
}
}
 
def licenseHeartbeat() {
long last = (atomicState.lic instanceof Map) ? ((atomicState.lic.lastCheck ?: 0L) as long) : 0L
if (now() - last >= 4L * 24 * 60 * 60 * 1000) requestLicenseCheck()
}
private boolean checkLicense() {
Map lic = (atomicState.lic instanceof Map) ? (Map)atomicState.lic : null
boolean ok = licenseEvaluate(lic, now())
if (logEnable) log.debug "[LICENÇA] checkLicense: status=${lic?.status} expiry=${lic?.expiry} → ${ok}"
if (!ok) {
log.warn "[LICENÇA] Inativa (status=${lic?.status ?: 'sem cache'}). HubUID: ${getHubUID()}"
sendEvent(name: "status", value: licenseStatusText(lic))
}
return ok
}
 
private boolean licenseEvaluate(Map st, long nowMs) {
if (st == null) return false
String status = st["status"]?.toString()
if (status != "active" && status != "trial") return false
String expiry = st["expiry"]?.toString() ?: ""
if (!expiry) return false
long maxSeen = (st["maxSeen"] ?: 0L) as long
long effective = Math.max(nowMs, maxSeen)
return licenseDateStr(effective) <= expiry
}
 
private Map licenseApplyCheck(Map st, Map response, long nowMs) {
long maxSeen = Math.max((st?.maxSeen ?: 0L) as long, nowMs)
if (response == null) {
if (st == null) return [status: "unknown", expiry: "", lastCheck: 0L, maxSeen: maxSeen]
Map n = new LinkedHashMap(st)
n["maxSeen"] = maxSeen
return n
}
return [status: (response["status"]?.toString() ?: "notfound"),
expiry: (response["expiry"]?.toString() ?: ""),
lastCheck: nowMs, maxSeen: maxSeen]
}
 
private String licenseDateStr(long ms) {
return new Date(ms).format("yyyy-MM-dd", location.timeZone ?: TimeZone.getTimeZone("UTC"))
}
 
private String licenseStatusText(Map lic) {
String uid = getHubUID()
if (lic == null) return "⚠️ Sem licença (HubUID: ${uid})"
switch (lic["status"]?.toString()) {
case "active": return "✅ Licença ativa (vence ${lic.expiry})"
case "trial": return "🧪 Trial (vence ${lic.expiry})"
case "revoked": return "❌ Licença revogada"
case "unknown": return "📡 Offline — sem validação ainda (HubUID: ${uid})"
default: return "⚠️ Sem licença (HubUID: ${uid})"
}
}
def "Verificar Licença"() {
log.info "[LICENÇA] Verificação manual solicitada — HubUID: ${getHubUID()}"
requestLicenseCheck()
}
