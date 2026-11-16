package app.prolauncher.signkeyboard
import android.content.Context
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.json.JSONArray
import org.json.JSONObject
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
val smoothedLandmarks: List<SimplePoint3D>,
val secondHandAction: String? = null
)
data class CustomSignMacro(
val name: String,
val outputText: String,
val vector: FloatArray
)
class KoreanSignClassifier(context: Context) {
private val prefs = context.getSharedPreferences("sign_keyboard_prefs_v1", Context.MODE_PRIVATE)
private val customMacros = mutableListOf<CustomSignMacro>()
private val emaX = FloatArray(21)
private val emaY = FloatArray(21)
private val emaZ = FloatArray(21)
private var emaInitialized = false
private val emaAlpha = 0.48f
init {
loadCustomMacros()
}
fun resetSmoothing() {
emaInitialized = false
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
fun isTwoHandFastMode(): Boolean = prefs.getBoolean("two_hand_fast_mode", true)
fun setTwoHandFastMode(enabled: Boolean) {
prefs.edit().putBoolean("two_hand_fast_mode", enabled).apply()
}
fun getCustomMacros(): List<CustomSignMacro> = customMacros.toList()
fun addCustomMacro(name: String, outputText: String, vector: FloatArray) {
customMacros.removeAll { it.name == name }
customMacros.add(CustomSignMacro(name, outputText, vector))
saveCustomMacros()
}
private fun loadCustomMacros() {
customMacros.clear()
val raw = prefs.getString("custom_macros_json", null) ?: return
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
val vecArr = JSONArray()
m.vector.forEach { vecArr.put(it.toDouble()) }
obj.put("vector", vecArr)
arr.put(obj)
}
prefs.edit().putString("custom_macros_json", arr.toString()).apply()
}
private fun smoothLandmarks(raw: List<NormalizedLandmark>): List<SimplePoint3D> {
if (raw.size < 21) return emptyList()
if (!emaInitialized) {
for (i in 0 until 21) {
emaX[i] = raw[i].x()
emaY[i] = raw[i].y()
emaZ[i] = raw[i].z()
}
emaInitialized = true
} else {
for (i in 0 until 21) {
emaX[i] = emaAlpha * raw[i].x() + (1f - emaAlpha) * emaX[i]
emaY[i] = emaAlpha * raw[i].y() + (1f - emaAlpha) * emaY[i]
emaZ[i] = emaAlpha * raw[i].z() + (1f - emaAlpha) * emaZ[i]
}
}
return List(21) { i -> SimplePoint3D(emaX[i], emaY[i], emaZ[i]) }
}
private fun dist2D(a: SimplePoint3D, b: SimplePoint3D): Float {
return hypot(a.x - b.x, a.y - b.y)
}
fun extractFeatureVector(pts: List<SimplePoint3D>): FloatArray {
val wrist = pts[0]
val palmScale = dist2D(wrist, pts[9]).coerceAtLeast(0.04f)
val indices = intArrayOf(4, 8, 12, 16, 20)
val vec = FloatArray(15)
var k = 0
for (idx in indices) {
vec[k++] = (dist2D(wrist, pts[idx]) / palmScale).coerceIn(0f, 3.5f)
}
for (idx in indices) {
vec[k++] = ((pts[idx].x - wrist.x) / palmScale).coerceIn(-2.5f, 2.5f)
}
for (idx in indices) {
vec[k++] = ((pts[idx].y - wrist.y) / palmScale).coerceIn(-2.5f, 2.5f)
}
return vec
}
private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
if (a.size != b.size || a.isEmpty()) return 0f
var dot = 0f
var normA = 0f
var normB = 0f
for (i in a.indices) {
dot += a[i] * b[i]
normA += a[i] * a[i]
normB += b[i] * b[i]
}
val denom = sqrt(normA) * sqrt(normB)
return if (denom > 1e-6f) dot / denom else 0f
}
private fun classifyAssistHand(rawSecond: List<NormalizedLandmark>?): String? {
if (rawSecond == null || rawSecond.size < 21 || !isTwoHandFastMode()) return null
val pts = rawSecond.map { SimplePoint3D(it.x(), it.y(), it.z()) }
val wrist = pts[0]
val palmScale = dist2D(wrist, pts[9]).coerceAtLeast(0.045f)
val pinchDist = minOf(
dist2D(pts[4], pts[8]),
dist2D(pts[3], pts[8]) * 1.04f,
dist2D(pts[4], pts[7]) * 1.04f
)
val pinchRatio = pinchDist / palmScale
val indexExt = dist2D(pts[8], wrist) > dist2D(pts[6], wrist) * 1.12f
val middleExt = dist2D(pts[12], wrist) > dist2D(pts[10], wrist) * 1.12f
val ringExt = dist2D(pts[16], wrist) > dist2D(pts[14], wrist) * 1.10f
val pinkyExt = dist2D(pts[20], wrist) > dist2D(pts[18], wrist) * 1.10f
return when {
pinchRatio <= getPinchThreshold() -> "[COMMIT_NOW]"
!indexExt && !middleExt && !ringExt && !pinkyExt && (dist2D(pts[8], wrist) / palmScale < 1.15f) -> "[BS]"
indexExt && middleExt && ringExt && pinkyExt && (dist2D(pts[8], pts[12]) / palmScale > 0.36f) -> "[SPACE]"
else -> null
}
}
fun classify(
primaryRaw: List<NormalizedLandmark>,
secondaryRaw: List<NormalizedLandmark>? = null
): SignRecognitionOutput {
if (primaryRaw.size < 21) {
resetSmoothing()
return SignRecognitionOutput("", "손을 카메라에 비춰주세요", 0f, false, 1f, FloatArray(15), emptyList(), null)
}
val lm = smoothLandmarks(primaryRaw)
val assistAction = classifyAssistHand(secondaryRaw)
val wrist = lm[0]
val palmScale = dist2D(wrist, lm[9]).coerceAtLeast(0.045f)
val featureVec = extractFeatureVector(lm)
val dTipToTip = dist2D(lm[4], lm[8])
val dThumbIpToIndexTip = dist2D(lm[3], lm[8])
val dThumbTipToIndexDip = dist2D(lm[4], lm[7])
val minPadDist = minOf(dTipToTip, dThumbIpToIndexTip * 1.04f, dThumbTipToIndexDip * 1.04f)
val pinchRatio = (minPadDist / palmScale).coerceIn(0.05f, 2.5f)
val indexTipToPalm = dist2D(lm[8], lm[0]) / palmScale
val thumbToMiddlePip = dist2D(lm[4], lm[10]) / palmScale
val indexExt = dist2D(lm[8], wrist) > dist2D(lm[6], wrist) * 1.12f
val middleExt = dist2D(lm[12], wrist) > dist2D(lm[10], wrist) * 1.12f
val ringExt = dist2D(lm[16], wrist) > dist2D(lm[14], wrist) * 1.10f
val pinkyExt = dist2D(lm[20], wrist) > dist2D(lm[18], wrist) * 1.10f
val thumbExt = dist2D(lm[4], lm[17]) > dist2D(lm[2], lm[17]) * 1.18f
val indexHooked = !indexExt && (dist2D(lm[8], wrist) / palmScale in 1.12f..1.58f)
val isTightFist = !indexExt && !middleExt && !ringExt && !pinkyExt &&
indexTipToPalm < 1.12f && thumbToMiddlePip < 0.65f
val isPinchClick = !isTightFist && pinchRatio <= getPinchThreshold()
var bestMacro: CustomSignMacro? = null
var bestSim = 0f
for (macro in customMacros) {
val sim = cosineSimilarity(featureVec, macro.vector)
if (sim > bestSim) {
bestSim = sim
bestMacro = macro
}
}
if (bestMacro != null && bestSim >= 0.93f) {
return SignRecognitionOutput(
token = bestMacro.outputText,
displayLabel = "★ ${bestMacro.name} (${bestMacro.outputText.trim()})",
confidence = bestSim,
isPinchClick = isPinchClick,
pinchRatio = pinchRatio,
featureVector = featureVec,
smoothedLandmarks = lm,
secondHandAction = assistAction
)
}
val dxIdx = lm[8].x - lm[5].x
val dyIdx = lm[8].y - lm[5].y
val indexPointsUp = dyIdx < -0.04f && kotlin.math.abs(dyIdx) > kotlin.math.abs(dxIdx) * 1.12f
val indexPointsDown = dyIdx > 0.04f && kotlin.math.abs(dyIdx) > kotlin.math.abs(dxIdx) * 1.08f
val indexPointsSide = kotlin.math.abs(dxIdx) > kotlin.math.abs(dyIdx) * 1.08f
val indexMiddleSpread = dist2D(lm[8], lm[12]) / palmScale
val middleRingSpread = dist2D(lm[12], lm[16]) / palmScale
val (token, label, conf) = when {
isTightFist -> Triple("[BS]", "✊ 지우기 (Backspace)", 0.93f)
thumbExt && indexExt && middleExt && ringExt && pinkyExt && indexMiddleSpread > 0.35f ->
Triple("[SPACE]", "🖐️ 띄어쓰기 (Space)", 0.91f)
thumbExt && !indexExt && !middleExt && !ringExt && pinkyExt ->
Triple("[ENTER]", "🤙 줄바꿈 (Enter)", 0.92f)
thumbExt && !indexExt && !middleExt && !ringExt && !pinkyExt && (lm[4].y < lm[2].y - 0.03f) ->
Triple("ㅗ", "👍 지문자 ㅗ (오)", 0.90f)
thumbExt && !indexExt && !middleExt && !ringExt && !pinkyExt && (lm[4].y > lm[2].y + 0.03f) ->
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
else -> Triple("", "수형 분석 중...", 0.35f)
}
return SignRecognitionOutput(
token = token,
displayLabel = label,
confidence = conf,
isPinchClick = isPinchClick,
pinchRatio = pinchRatio,
featureVector = featureVec,
smoothedLandmarks = lm,
secondHandAction = assistAction
)
}
}
