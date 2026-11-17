package app.prolauncher.signkeyboard
import android.content.Context
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
data class SimplePoint3D(val x: Float, val y: Float, val z: Float)
data class SignRecognitionOutput(
val token: String,
val displayLabel: String,
val confidence: Float,
val isPinchClick: Boolean,
val pinchRatio: Float,
val featureVector: FloatArray,
val primaryLandmarks: List<SimplePoint3D>,
val secondaryLandmarks: List<SimplePoint3D>,
val isTwoHandSign: Boolean,
val secondHandAction: String? = null
)
data class CustomSignMacro(
val name: String,
val outputText: String,
val isTwoHand: Boolean,
val vector: FloatArray
)
class KoreanSignClassifier(context: Context) {
private val prefs = context.getSharedPreferences("sign_keyboard_prefs_v1", Context.MODE_PRIVATE)
private val customMacros = mutableListOf<CustomSignMacro>()
private val emaX0 = FloatArray(21)
private val emaY0 = FloatArray(21)
private val emaZ0 = FloatArray(21)
private var emaInit0 = false
private val emaX1 = FloatArray(21)
private val emaY1 = FloatArray(21)
private val emaZ1 = FloatArray(21)
private var emaInit1 = false
private val emaAlpha = 0.48f
private val wristHistory = ArrayDeque<SimplePoint3D>()
private val indexTipHistory = ArrayDeque<SimplePoint3D>()
private val pinkyTipHistory = ArrayDeque<SimplePoint3D>()
private val maxHistoryFrames = 12
init {
loadCustomMacros()
}
fun resetSmoothing() {
emaInit0 = false
emaInit1 = false
wristHistory.clear()
indexTipHistory.clear()
pinkyTipHistory.clear()
}
fun isAslMode(): Boolean = prefs.getBoolean("asl_english_mode", false)
fun setAslMode(enabled: Boolean) {
prefs.edit().putBoolean("asl_english_mode", enabled).apply()
}
fun isTwoHandWordMode(): Boolean = prefs.getBoolean("two_hand_word_mode", true)
fun setTwoHandWordMode(enabled: Boolean) {
prefs.edit().putBoolean("two_hand_word_mode", enabled).apply()
}
fun getPinchThreshold(): Float = prefs.getFloat("pinch_threshold", 0.40f)
fun setPinchThreshold(value: Float) {
prefs.edit().putFloat("pinch_threshold", value.coerceIn(0.28f, 0.52f)).apply()
}
fun getDwellMs(): Long = prefs.getLong("dwell_ms", 600L)
fun setDwellMs(value: Long) {
prefs.edit().putLong("dwell_ms", value.coerceIn(300L, 1500L)).apply()
}
fun isPinchCommitMode(): Boolean = prefs.getBoolean("pinch_commit_mode", false)
fun setPinchCommitMode(enabled: Boolean) {
prefs.edit().putBoolean("pinch_commit_mode", enabled).apply()
}
fun getCustomMacros(): List<CustomSignMacro> = customMacros.toList()
fun addCustomMacro(name: String, outputText: String, isTwoHand: Boolean, vector: FloatArray) {
customMacros.removeAll { it.name == name }
customMacros.add(CustomSignMacro(name, outputText, isTwoHand, vector))
saveCustomMacros()
}
private fun loadCustomMacros() {
customMacros.clear()
val raw = prefs.getString("custom_macros_v3_json", null) ?: return
try {
val arr = JSONArray(raw)
for (i in 0 until arr.length()) {
val obj = arr.getJSONObject(i)
val vecArr = obj.getJSONArray("vector")
val vec = FloatArray(vecArr.length()) { idx -> vecArr.getDouble(idx).toFloat() }
customMacros.add(
CustomSignMacro(
name = obj.getString("name"),
outputText = obj.getString("outputText"),
isTwoHand = obj.optBoolean("isTwoHand", false),
vector = vec
)
)
}
} catch (_: Exception) {
}
}
private fun saveCustomMacros() {
val arr = JSONArray()
for (m in customMacros) {
val obj = JSONObject()
obj.put("name", m.name)
obj.put("outputText", m.outputText)
obj.put("isTwoHand", m.isTwoHand)
val vecArr = JSONArray()
m.vector.forEach { vecArr.put(it.toDouble()) }
obj.put("vector", vecArr)
arr.put(obj)
}
prefs.edit().putString("custom_macros_v3_json", arr.toString()).apply()
}
private fun smoothPrimary(raw: List<NormalizedLandmark>): List<SimplePoint3D> {
if (raw.size < 21) return emptyList()
if (!emaInit0) {
for (i in 0 until 21) {
emaX0[i] = raw[i].x()
emaY0[i] = raw[i].y()
emaZ0[i] = raw[i].z()
}
emaInit0 = true
} else {
for (i in 0 until 21) {
emaX0[i] = emaAlpha * raw[i].x() + (1f - emaAlpha) * emaX0[i]
emaY0[i] = emaAlpha * raw[i].y() + (1f - emaAlpha) * emaY0[i]
emaZ0[i] = emaAlpha * raw[i].z() + (1f - emaAlpha) * emaZ0[i]
}
}
val pts = List(21) { i -> SimplePoint3D(emaX0[i], emaY0[i], emaZ0[i]) }
pushTrajectory(pts[0], pts[8], pts[20])
return pts
}
private fun smoothSecondary(raw: List<NormalizedLandmark>?): List<SimplePoint3D> {
if (raw == null || raw.size < 21) {
emaInit1 = false
return emptyList()
}
if (!emaInit1) {
for (i in 0 until 21) {
emaX1[i] = raw[i].x()
emaY1[i] = raw[i].y()
emaZ1[i] = raw[i].z()
}
emaInit1 = true
} else {
for (i in 0 until 21) {
emaX1[i] = emaAlpha * raw[i].x() + (1f - emaAlpha) * emaX1[i]
emaY1[i] = emaAlpha * raw[i].y() + (1f - emaAlpha) * emaY1[i]
emaZ1[i] = emaAlpha * raw[i].z() + (1f - emaAlpha) * emaZ1[i]
}
}
return List(21) { i -> SimplePoint3D(emaX1[i], emaY1[i], emaZ1[i]) }
}
private fun pushTrajectory(wrist: SimplePoint3D, indexTip: SimplePoint3D, pinkyTip: SimplePoint3D) {
if (wristHistory.size >= maxHistoryFrames) wristHistory.removeFirst()
if (indexTipHistory.size >= maxHistoryFrames) indexTipHistory.removeFirst()
if (pinkyTipHistory.size >= maxHistoryFrames) pinkyTipHistory.removeFirst()
wristHistory.addLast(wrist)
indexTipHistory.addLast(indexTip)
pinkyTipHistory.addLast(pinkyTip)
}
private fun dist2D(a: SimplePoint3D, b: SimplePoint3D): Float {
return hypot(a.x - b.x, a.y - b.y)
}
fun extractCombinedFeatureVector(
p1: List<SimplePoint3D>,
p2: List<SimplePoint3D>
): FloatArray {
val vec = FloatArray(36)
if (p1.size < 21) return vec
val w1 = p1[0]
val scale1 = dist2D(w1, p1[9]).coerceAtLeast(0.045f)
val tips = intArrayOf(4, 8, 12, 16, 20)
var k = 0
for (idx in tips) vec[k++] = (dist2D(w1, p1[idx]) / scale1).coerceIn(0f, 3.5f)
for (idx in tips) vec[k++] = ((p1[idx].x - w1.x) / scale1).coerceIn(-2.5f, 2.5f)
for (idx in tips) vec[k++] = ((p1[idx].y - w1.y) / scale1).coerceIn(-2.5f, 2.5f)
if (p2.size >= 21) {
val w2 = p2[0]
val scale2 = dist2D(w2, p2[9]).coerceAtLeast(0.045f)
for (idx in tips) vec[k++] = (dist2D(w2, p2[idx]) / scale2).coerceIn(0f, 3.5f)
for (idx in tips) vec[k++] = ((p2[idx].x - w2.x) / scale2).coerceIn(-2.5f, 2.5f)
for (idx in tips) vec[k++] = ((p2[idx].y - w2.y) / scale2).coerceIn(-2.5f, 2.5f)
vec[30] = (dist2D(w1, w2) / scale1).coerceIn(0f, 5f)
vec[31] = (dist2D(p1[8], p2[8]) / scale1).coerceIn(0f, 5f)
vec[32] = ((w1.y - w2.y) / scale1).coerceIn(-3f, 3f)
} else {
k = 33
}
if (indexTipHistory.size >= 4) {
val firstIdx = indexTipHistory.first()
val lastIdx = indexTipHistory.last()
vec[33] = ((lastIdx.x - firstIdx.x) / scale1).coerceIn(-2.5f, 2.5f)
vec[34] = ((lastIdx.y - firstIdx.y) / scale1).coerceIn(-2.5f, 2.5f)
}
if (pinkyTipHistory.size >= 4) {
val firstP = pinkyTipHistory.first()
val lastP = pinkyTipHistory.last()
vec[35] = ((lastP.y - firstP.y) / scale1).coerceIn(-2.5f, 2.5f)
}
return vec
}
private fun cosineSimilarity(a: FloatArray, b: FloatArray, len: Int): Float {
var dot = 0f
var normA = 0f
var normB = 0f
val n = minOf(a.size, b.size, len)
for (i in 0 until n) {
dot += a[i] * b[i]
normA += a[i] * a[i]
normB += b[i] * b[i]
}
val denom = sqrt(normA) * sqrt(normB)
return if (denom > 1e-6f) dot / denom else 0f
}
private fun classifyAssistHand(p2: List<SimplePoint3D>): String? {
if (p2.size < 21 || isTwoHandWordMode()) return null
val wrist = p2[0]
val palmScale = dist2D(wrist, p2[9]).coerceAtLeast(0.045f)
val pinchDist = minOf(
dist2D(p2[4], p2[8]),
dist2D(p2[3], p2[8]) * 1.04f,
dist2D(p2[4], p2[7]) * 1.04f
)
val pinchRatio = pinchDist / palmScale
val indexExt = dist2D(p2[8], wrist) > dist2D(p2[6], wrist) * 1.12f
val middleExt = dist2D(p2[12], wrist) > dist2D(p2[10], wrist) * 1.12f
val ringExt = dist2D(p2[16], wrist) > dist2D(p2[14], wrist) * 1.10f
val pinkyExt = dist2D(p2[20], wrist) > dist2D(p2[18], wrist) * 1.10f
return when {
pinchRatio <= getPinchThreshold() -> "[COMMIT_NOW]"
!indexExt && !middleExt && !ringExt && !pinkyExt && (dist2D(p2[8], wrist) / palmScale < 1.15f) -> "[BS]"
indexExt && middleExt && ringExt && pinkyExt && (dist2D(p2[8], p2[12]) / palmScale > 0.36f) -> "[SPACE]"
else -> null
}
}
private fun classifyTwoHandSign(
h1: List<SimplePoint3D>,
h2: List<SimplePoint3D>,
asl: Boolean
): Triple<String, String, Float>? {
if (h1.size < 21 || h2.size < 21) return null
val (leftH, rightH) = if (h1[0].x <= h2[0].x) Pair(h1, h2) else Pair(h2, h1)
val scale = ((dist2D(leftH[0], leftH[9]) + dist2D(rightH[0], rightH[9])) * 0.5f).coerceAtLeast(0.05f)
val lThumb = dist2D(leftH[4], leftH[17]) > dist2D(leftH[2], leftH[17]) * 1.16f
val lIndex = dist2D(leftH[8], leftH[0]) > dist2D(leftH[6], leftH[0]) * 1.12f
val lMiddle = dist2D(leftH[12], leftH[0]) > dist2D(leftH[10], leftH[0]) * 1.12f
val lRing = dist2D(leftH[16], leftH[0]) > dist2D(leftH[14], leftH[0]) * 1.10f
val lPinky = dist2D(leftH[20], leftH[0]) > dist2D(leftH[18], leftH[0]) * 1.10f
val rThumb = dist2D(rightH[4], rightH[17]) > dist2D(rightH[2], rightH[17]) * 1.16f
val rIndex = dist2D(rightH[8], rightH[0]) > dist2D(rightH[6], rightH[0]) * 1.12f
val rMiddle = dist2D(rightH[12], rightH[0]) > dist2D(rightH[10], rightH[0]) * 1.12f
val rRing = dist2D(rightH[16], rightH[0]) > dist2D(rightH[14], rightH[0]) * 1.10f
val rPinky = dist2D(rightH[20], rightH[0]) > dist2D(rightH[18], rightH[0]) * 1.10f
val lFlatOpen = lIndex && lMiddle && lRing && lPinky
val rFlatOpen = rIndex && rMiddle && rRing && rPinky
val lFist = !lIndex && !lMiddle && !lRing && !lPinky
val rFist = !rIndex && !rMiddle && !rRing && !rPinky
val lIndexOnly = lIndex && !lMiddle && !lRing && !lPinky
val rIndexOnly = rIndex && !rMiddle && !rRing && !rPinky
val indexTipDist = dist2D(leftH[8], rightH[8]) / scale
val middleTipDist = dist2D(leftH[12], rightH[12]) / scale
val wristDist = dist2D(leftH[0], rightH[0]) / scale
val pinkyEdgeDist = dist2D(leftH[20], rightH[20]) / scale
val thumbTipDist = dist2D(leftH[4], rightH[4]) / scale
val lPinch = (dist2D(leftH[4], leftH[8]) / scale) < 0.48f
val rPinch = (dist2D(rightH[4], rightH[8]) / scale) < 0.48f
return when {
lFlatOpen && rFlatOpen && indexTipDist < 0.85f && wristDist > 1.35f ->
if (asl) Triple("HOUSE ", "👐 ASL: HOUSE (집)", 0.94f)
else Triple("집 ", "👐 한국수어: 집 (양손 지붕 수형)", 0.94f)
lFlatOpen && rFlatOpen && pinkyEdgeDist < 0.95f && wristDist < 1.55f && indexTipDist >= 0.85f ->
if (asl) Triple("BOOK ", "👐 ASL: BOOK / SCHOOL", 0.92f)
else Triple("학교 ", "👐 한국수어: 학교 / 공부하다", 0.92f)
lIndexOnly && rIndexOnly && indexTipDist < 1.15f ->
if (asl) Triple("MEET ", "👐 ASL: MEET (만나다)", 0.93f)
else Triple("만나다 ", "👐 한국수어: 만나다 (양손 검지 대면)", 0.93f)
lIndexOnly && rIndexOnly && indexTipDist >= 1.15f && thumbTipDist < 1.10f ->
if (asl) Triple("SAME ", "👐 ASL: SAME / ALSO", 0.90f)
else Triple("같다 ", "👐 한국수어: 같다 / 함께", 0.90f)
((lThumb && lFist && rFlatOpen && rightH[9].y > leftH[0].y - 0.05f) ||
(rThumb && rFist && lFlatOpen && leftH[9].y > rightH[0].y - 0.05f)) && wristDist < 1.75f ->
if (asl) Triple("HELP ", "👐 ASL: HELP (도와주세요)", 0.94f)
else Triple("도와주세요 ", "👐 한국수어: 도와주다 (도와주세요)", 0.94f)
((lFlatOpen && rFlatOpen) && abs(leftH[9].y - rightH[9].y) > 0.08f &&
(dist2D(leftH[8], rightH[9]) / scale < 1.05f || dist2D(rightH[8], leftH[9]) / scale < 1.05f)) ->
if (asl) Triple("THANK YOU ", "👐 ASL: THANK YOU", 0.93f)
else Triple("감사합니다 ", "👐 한국수어: 감사합니다 (고맙다)", 0.93f)
((lIndex && lMiddle && !lRing && !lPinky) && (rIndex && rMiddle && !rRing && !rPinky) &&
indexTipDist < 0.95f) ||
((lIndex && lMiddle && !lRing) && dist2D(leftH[8], rightH[0]) / scale < 0.90f) ||
((rIndex && rMiddle && !rRing) && dist2D(rightH[8], leftH[0]) / scale < 0.90f) ->
if (asl) Triple("HOSPITAL ", "👐 ASL: HOSPITAL / MEDICAL", 0.92f)
else Triple("병원 ", "👐 한국수어: 병원 / 진료", 0.92f)
(lFlatOpen && !rFlatOpen && (dist2D(rightH[8], leftH[9]) / scale < 0.78f || dist2D(rightH[12], leftH[9]) / scale < 0.78f)) ||
(rFlatOpen && !lFlatOpen && (dist2D(leftH[8], rightH[9]) / scale < 0.78f || dist2D(leftH[12], rightH[9]) / scale < 0.78f)) ->
if (asl) Triple("MEDICINE ", "👐 ASL: MEDICINE (약)", 0.91f)
else Triple("약국 ", "👐 한국수어: 약 / 약국", 0.91f)
((lFist && rFlatOpen && dist2D(rightH[9], leftH[9]) / scale < 0.95f) ||
(rFist && lFlatOpen && dist2D(leftH[9], rightH[9]) / scale < 0.95f)) ->
if (asl) Triple("LOVE ", "👐 ASL: LOVE", 0.92f)
else Triple("사랑합니다 ", "👐 한국수어: 사랑하다", 0.92f)
lFist && rFist && wristDist < 1.45f ->
if (asl) Triple("WORK ", "👐 ASL: WORK (일하다)", 0.91f)
else Triple("일하다 ", "👐 한국수어: 일하다", 0.91f)
lPinch && rPinch && indexTipDist < 0.85f ->
if (asl) Triple("MORE ", "👐 ASL: MORE", 0.92f)
else Triple("더 ", "👐 한국수어: 더 / 추가", 0.92f)
lFlatOpen && rFlatOpen && lThumb && rThumb && wristDist >= 1.55f ->
if (asl) Triple("FINISHED ", "👐 ASL: FINISHED / ALL DONE", 0.90f)
else Triple("끝 ", "👐 한국수어: 끝 / 완료", 0.90f)
else -> null
}
}
private fun classifyAslOneHand(
lm: List<SimplePoint3D>,
palmScale: Float,
pinchRatio: Float,
isTightFist: Boolean,
thumbExt: Boolean,
indexExt: Boolean,
middleExt: Boolean,
ringExt: Boolean,
pinkyExt: Boolean,
indexHooked: Boolean
): Triple<String, String, Float> {
val dxIdx = lm[8].x - lm[5].x
val dyIdx = lm[8].y - lm[5].y
val indexUp = dyIdx < -0.04f && abs(dyIdx) > abs(dxIdx) * 1.10f
val indexDown = dyIdx > 0.04f && abs(dyIdx) > abs(dxIdx) * 1.08f
val indexSide = abs(dxIdx) > abs(dyIdx) * 1.08f
val indexMiddleSpread = dist2D(lm[8], lm[12]) / palmScale
val thumbToIndexPip = dist2D(lm[4], lm[6]) / palmScale
val thumbToMiddlePip = dist2D(lm[4], lm[10]) / palmScale
val thumbToRingPip = dist2D(lm[4], lm[14]) / palmScale
var indexDeltaX = 0f
var pinkyDeltaY = 0f
if (indexTipHistory.size >= 5) {
indexDeltaX = abs(indexTipHistory.last().x - indexTipHistory.first().x) / palmScale
}
if (pinkyTipHistory.size >= 5) {
pinkyDeltaY = abs(pinkyTipHistory.last().y - pinkyTipHistory.first().y) / palmScale
}
val isCurvedC = !indexExt && !middleExt && pinchRatio in 0.52f..1.05f &&
(dist2D(lm[8], lm[0]) / palmScale in 1.18f..1.68f)
val isRoundO = pinchRatio <= 0.48f && !middleExt && !ringExt && !pinkyExt
val isCrossedR = indexExt && middleExt && !ringExt && !pinkyExt && indexMiddleSpread < 0.19f
return when {
thumbExt && !indexExt && !middleExt && !ringExt && !pinkyExt && (lm[4].y > lm[2].y + 0.04f) ->
Triple("[BS]", "👎 ASL: Backspace", 0.91f)
thumbExt && indexExt && middleExt && ringExt && pinkyExt && indexMiddleSpread > 0.38f ->
Triple("[SPACE]", "🖐️ ASL: Space", 0.91f)
thumbExt && indexExt && !middleExt && !ringExt && pinkyExt ->
Triple("I LOVE YOU ", "🤟 ASL: I LOVE YOU", 0.94f)
thumbExt && !indexExt && !middleExt && !ringExt && pinkyExt ->
Triple("Y", "ASL Letter Y", 0.92f)
!thumbExt && !indexExt && !middleExt && !ringExt && pinkyExt && pinkyDeltaY > 0.35f ->
Triple("J", "ASL Letter J (Motion)", 0.90f)
!thumbExt && !indexExt && !middleExt && !ringExt && pinkyExt ->
Triple("I", "ASL Letter I", 0.92f)
pinchRatio <= 0.48f && middleExt && ringExt && pinkyExt ->
Triple("F", "ASL Letter F", 0.93f)
!thumbExt && indexExt && middleExt && ringExt && !pinkyExt ->
Triple("W", "ASL Letter W", 0.92f)
!thumbExt && indexExt && middleExt && ringExt && pinkyExt && indexMiddleSpread <= 0.36f ->
Triple("B", "ASL Letter B", 0.92f)
indexExt && middleExt && !ringExt && !pinkyExt && indexDown ->
Triple("P", "ASL Letter P", 0.89f)
indexExt && middleExt && !ringExt && !pinkyExt && indexSide ->
Triple("H", "ASL Letter H", 0.90f)
isCrossedR && indexUp ->
Triple("R", "ASL Letter R", 0.88f)
thumbExt && indexExt && middleExt && !ringExt && !pinkyExt && indexUp && indexMiddleSpread > 0.32f ->
Triple("K", "ASL Letter K", 0.89f)
!thumbExt && indexExt && middleExt && !ringExt && !pinkyExt && indexUp && indexMiddleSpread > 0.36f ->
Triple("V", "ASL Letter V", 0.92f)
!thumbExt && indexExt && middleExt && !ringExt && !pinkyExt && indexUp && indexMiddleSpread <= 0.36f ->
Triple("U", "ASL Letter U", 0.91f)
thumbExt && indexExt && !middleExt && !ringExt && !pinkyExt && indexUp ->
Triple("L", "ASL Letter L", 0.93f)
thumbExt && indexExt && !middleExt && !ringExt && !pinkyExt && indexSide ->
Triple("G", "ASL Letter G", 0.90f)
thumbExt && indexExt && !middleExt && !ringExt && !pinkyExt && indexDown ->
Triple("Q", "ASL Letter Q", 0.89f)
!thumbExt && indexExt && !middleExt && !ringExt && !pinkyExt && indexUp && indexDeltaX > 0.38f ->
Triple("Z", "ASL Letter Z (Motion)", 0.89f)
!thumbExt && indexExt && !middleExt && !ringExt && !pinkyExt && indexUp ->
Triple("D", "ASL Letter D", 0.91f)
!thumbExt && indexHooked && !middleExt && !ringExt && !pinkyExt ->
Triple("X", "ASL Letter X", 0.88f)
isRoundO ->
Triple("O", "ASL Letter O", 0.90f)
isCurvedC ->
Triple("C", "ASL Letter C", 0.88f)
!indexExt && !middleExt && !ringExt && !pinkyExt -> {
when {
thumbExt || (lm[4].y < lm[6].y - 0.02f && thumbToIndexPip < 0.55f) ->
Triple("A", "ASL Letter A", 0.90f)
thumbToIndexPip < 0.42f && thumbToMiddlePip < 0.45f ->
Triple("T", "ASL Letter T", 0.87f)
thumbToMiddlePip < 0.44f && thumbToRingPip < 0.48f ->
Triple("N", "ASL Letter N", 0.86f)
thumbToRingPip < 0.45f ->
Triple("M", "ASL Letter M", 0.86f)
lm[4].y > lm[8].y + 0.02f ->
Triple("E", "ASL Letter E", 0.87f)
else ->
Triple("S", "ASL Letter S", 0.89f)
}
}
else -> Triple("", "ASL Handshape Analyzing...", 0.35f)
}
}
fun classify(
primaryRaw: List<NormalizedLandmark>,
secondaryRaw: List<NormalizedLandmark>? = null
): SignRecognitionOutput {
if (primaryRaw.size < 21) {
resetSmoothing()
return SignRecognitionOutput(
"",
if (isAslMode()) "Show 1 or 2 hands to camera" else "한 손 또는 양손을 카메라에 비춰주세요",
0f,
false,
1f,
FloatArray(36),
emptyList(),
emptyList(),
false,
null
)
}
val p1 = smoothPrimary(primaryRaw)
val p2 = smoothSecondary(secondaryRaw)
val hasTwoHands = p2.size >= 21
val asl = isAslMode()
val featureVec = extractCombinedFeatureVector(p1, p2)
val wrist = p1[0]
val palmScale = dist2D(wrist, p1[9]).coerceAtLeast(0.045f)
val dTipToTip = dist2D(p1[4], p1[8])
val dThumbIpToIndexTip = dist2D(p1[3], p1[8])
val dThumbTipToIndexDip = dist2D(p1[4], p1[7])
val minPadDist = minOf(dTipToTip, dThumbIpToIndexTip * 1.04f, dThumbTipToIndexDip * 1.04f)
val pinchRatio = (minPadDist / palmScale).coerceIn(0.05f, 2.5f)
val indexTipToPalm = dist2D(p1[8], p1[0]) / palmScale
val thumbToMiddlePip = dist2D(p1[4], p1[10]) / palmScale
val indexExt = dist2D(p1[8], wrist) > dist2D(p1[6], wrist) * 1.12f
val middleExt = dist2D(p1[12], wrist) > dist2D(p1[10], wrist) * 1.12f
val ringExt = dist2D(p1[16], wrist) > dist2D(p1[14], wrist) * 1.10f
val pinkyExt = dist2D(p1[20], wrist) > dist2D(p1[18], wrist) * 1.10f
val thumbExt = dist2D(p1[4], p1[17]) > dist2D(p1[2], p1[17]) * 1.18f
val indexHooked = !indexExt && (dist2D(p1[8], wrist) / palmScale in 1.12f..1.58f)
val isTightFist = !indexExt && !middleExt && !ringExt && !pinkyExt &&
indexTipToPalm < 1.12f && thumbToMiddlePip < 0.65f
val isPinchClick = !isTightFist && pinchRatio <= getPinchThreshold()
var bestMacro: CustomSignMacro? = null
var bestSim = 0f
val compareDim = if (hasTwoHands) 36 else 15
for (macro in customMacros) {
if (macro.isTwoHand == hasTwoHands) {
val sim = cosineSimilarity(featureVec, macro.vector, compareDim)
if (sim > bestSim) {
bestSim = sim
bestMacro = macro
}
}
}
if (bestMacro != null && bestSim >= 0.92f) {
return SignRecognitionOutput(
token = bestMacro.outputText,
displayLabel = "★ ${bestMacro.name} (${bestMacro.outputText.trim()})",
confidence = bestSim,
isPinchClick = isPinchClick,
pinchRatio = pinchRatio,
featureVector = featureVec,
primaryLandmarks = p1,
secondaryLandmarks = p2,
isTwoHandSign = hasTwoHands,
secondHandAction = null
)
}
if (hasTwoHands && isTwoHandWordMode()) {
val twoHandMatch = classifyTwoHandSign(p1, p2, asl)
if (twoHandMatch != null) {
return SignRecognitionOutput(
token = twoHandMatch.first,
displayLabel = twoHandMatch.second,
confidence = twoHandMatch.third,
isPinchClick = isPinchClick,
pinchRatio = pinchRatio,
featureVector = featureVec,
primaryLandmarks = p1,
secondaryLandmarks = p2,
isTwoHandSign = true,
secondHandAction = null
)
}
}
val assistAction = classifyAssistHand(p2)
if (asl) {
val (tok, lbl, cf) = classifyAslOneHand(
p1, palmScale, pinchRatio, isTightFist,
thumbExt, indexExt, middleExt, ringExt, pinkyExt, indexHooked
)
return SignRecognitionOutput(
token = tok,
displayLabel = lbl,
confidence = cf,
isPinchClick = isPinchClick,
pinchRatio = pinchRatio,
featureVector = featureVec,
primaryLandmarks = p1,
secondaryLandmarks = p2,
isTwoHandSign = false,
secondHandAction = assistAction
)
}
val dxIdx = p1[8].x - p1[5].x
val dyIdx = p1[8].y - p1[5].y
val indexPointsUp = dyIdx < -0.04f && abs(dyIdx) > abs(dxIdx) * 1.12f
val indexPointsDown = dyIdx > 0.04f && abs(dyIdx) > abs(dxIdx) * 1.08f
val indexPointsSide = abs(dxIdx) > abs(dyIdx) * 1.08f
val indexMiddleSpread = dist2D(p1[8], p1[12]) / palmScale
val middleRingSpread = dist2D(p1[12], p1[16]) / palmScale
val (token, label, conf) = when {
isTightFist -> Triple("[BS]", "✊ 지우기 (Backspace)", 0.93f)
thumbExt && indexExt && middleExt && ringExt && pinkyExt && indexMiddleSpread > 0.35f ->
Triple("[SPACE]", "🖐️ 띄어쓰기 (Space)", 0.91f)
thumbExt && !indexExt && !middleExt && !ringExt && pinkyExt ->
Triple("[ENTER]", "🤙 줄바꿈 (Enter)", 0.92f)
thumbExt && !indexExt && !middleExt && !ringExt && !pinkyExt && (p1[4].y < p1[2].y - 0.03f) ->
Triple("ㅗ", "👍 지문자 ㅗ (오)", 0.90f)
thumbExt && !indexExt && !middleExt && !ringExt && !pinkyExt && (p1[4].y > p1[2].y + 0.03f) ->
Triple("ㅜ", "👎 지문자 ㅜ (우)", 0.89f)
thumbExt && indexExt && !middleExt && !ringExt && !pinkyExt && indexPointsDown ->
Triple("ㄱ", "지문자 ㄱ (기역)", 0.91f)
thumbExt && indexExt && !middleExt && !ringExt && !pinkyExt && indexPointsSide ->
Triple("ㄴ", "지문자 ㄴ (니은)", 0.91f)
thumbExt && indexHooked && middleExt && !ringExt && !pinkyExt ->
Triple("ㅋ", "지문자 ㅋ (키읔)", 0.87f)
!thumbExt && indexExt && !middleExt && !ringExt && !pinkyExt && indexPointsUp ->
Triple("ㅏ", "지문자 ㅏ (아)", 0.92f)
thumbExt && indexExt && !middleExt && !ringExt && !pinkyExt && indexPointsUp ->
Triple("ㅓ", "지문자 ㅓ (어)", 0.89f)
!thumbExt && indexExt && !middleExt && !ringExt && !pinkyExt && indexPointsSide ->
Triple("ㅡ", "지문자 ㅡ (으)", 0.90f)
!thumbExt && !indexExt && !middleExt && !ringExt && pinkyExt ->
Triple("ㅣ", "지문자 ㅣ (이)", 0.92f)
indexExt && middleExt && !ringExt && !pinkyExt && indexPointsSide && indexMiddleSpread <= 0.38f ->
Triple("ㄷ", "지문자 ㄷ (디귿)", 0.89f)
indexExt && middleExt && ringExt && !pinkyExt && indexPointsSide && middleRingSpread <= 0.36f ->
Triple("ㄹ", "지문자 ㄹ (리을)", 0.88f)
indexExt && middleExt && ringExt && !pinkyExt && indexPointsSide && middleRingSpread > 0.36f ->
Triple("ㅌ", "지문자 ㅌ (티읕)", 0.87f)
!thumbExt && indexExt && middleExt && ringExt && pinkyExt && indexPointsSide ->
Triple("ㅍ", "지문자 ㅍ (피읖)", 0.88f)
!thumbExt && indexExt && middleExt && !ringExt && !pinkyExt && indexPointsDown && indexMiddleSpread > 0.34f ->
Triple("ㅅ", "지문자 ㅅ (시옷)", 0.90f)
!thumbExt && indexExt && middleExt && !ringExt && !pinkyExt && indexPointsDown && indexMiddleSpread <= 0.34f ->
Triple("ㅠ", "지문자 ㅠ (유)", 0.88f)
!thumbExt && indexExt && middleExt && !ringExt && !pinkyExt && indexPointsUp && indexMiddleSpread > 0.42f ->
Triple("ㅑ", "지문자 ㅑ (야)", 0.90f)
!thumbExt && indexExt && middleExt && !ringExt && !pinkyExt && indexPointsUp && indexMiddleSpread <= 0.42f ->
Triple("ㅕ", "지문자 ㅕ (여)", 0.89f)
thumbExt && indexExt && middleExt && !ringExt && !pinkyExt && indexMiddleSpread > 0.36f ->
Triple("ㅈ", "지문자 ㅈ (지읒)", 0.89f)
thumbExt && indexExt && middleExt && ringExt && !pinkyExt ->
Triple("ㅊ", "지문자 ㅊ (치읓)", 0.87f)
!thumbExt && indexExt && middleExt && ringExt && pinkyExt && indexMiddleSpread <= 0.35f ->
Triple("ㅂ", "지문자 ㅂ (비읍)", 0.90f)
!thumbExt && indexExt && middleExt && ringExt && !pinkyExt && middleRingSpread > 0.36f ->
Triple("ㅛ", "지문자 ㅛ (요)", 0.87f)
!thumbExt && indexExt && middleExt && ringExt && !pinkyExt && middleRingSpread <= 0.36f ->
Triple("ㅁ", "지문자 ㅁ (미음)", 0.87f)
pinchRatio <= 0.48f && middleExt && ringExt && pinkyExt ->
Triple("ㅇ", "지문자 ㅇ (이응)", 0.93f)
!thumbExt && indexExt && !middleExt && !ringExt && pinkyExt ->
Triple("ㅎ", "지문자 ㅎ (히읗)", 0.91f)
thumbExt && indexExt && !middleExt && !ringExt && pinkyExt ->
Triple("안녕하세요 ", "🤟 인사 수어 (안녕하세요)", 0.94f)
else -> Triple("", "수형 분석 중 (한손/양손 모두 지원)", 0.35f)
}
return SignRecognitionOutput(
token = token,
displayLabel = label,
confidence = conf,
isPinchClick = isPinchClick,
pinchRatio = pinchRatio,
featureVector = featureVec,
primaryLandmarks = p1,
secondaryLandmarks = p2,
isTwoHandSign = false,
secondHandAction = assistAction
)
}
}
