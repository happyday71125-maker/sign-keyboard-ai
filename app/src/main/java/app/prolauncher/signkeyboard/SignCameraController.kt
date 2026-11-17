package app.prolauncher.signkeyboard
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
class SignCameraController(
private val context: Context,
private val classifier: KoreanSignClassifier,
private val onStateUpdate: (
output: SignRecognitionOutput,
dwellProgress: Float,
committedToken: String?
) -> Unit
) {
private var cameraProvider: ProcessCameraProvider? = null
private var handLandmarker: HandLandmarker? = null
private var cameraExecutor: ExecutorService? = null
private var currentCandidateToken: String = ""
private var candidateStartTimeMs: Long = 0L
private var lastValidCandidateSeenMs: Long = 0L
private var lastCommittedTimeMs: Long = 0L
private var wasPinching: Boolean = false
private var wasAssistTriggering: Boolean = false
@Volatile
var lastFeatureVector: FloatArray = FloatArray(36)
private set
@Volatile
var lastDetectedTwoHands: Boolean = false
private set
fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
if (cameraExecutor == null || cameraExecutor?.isShutdown == true) {
cameraExecutor = Executors.newSingleThreadExecutor()
}
cameraExecutor?.execute {
initHandLandmarkerIfNeeded()
}
val providerFuture = ProcessCameraProvider.getInstance(context)
providerFuture.addListener({
try {
val provider = providerFuture.get()
cameraProvider = provider
val preview = Preview.Builder().build().also {
it.setSurfaceProvider(previewView.surfaceProvider)
}
val imageAnalysis = ImageAnalysis.Builder()
.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
.build()
.also { analysis ->
val exec = cameraExecutor ?: return@also
analysis.setAnalyzer(exec) { imageProxy ->
processImageProxy(imageProxy)
}
}
provider.unbindAll()
provider.bindToLifecycle(
lifecycleOwner,
CameraSelector.DEFAULT_FRONT_CAMERA,
preview,
imageAnalysis
)
} catch (e: Exception) {
Log.e("SignCameraController", "CameraX 바인딩 실패: ${e.message}", e)
}
}, ContextCompat.getMainExecutor(context))
}
fun stop() {
try {
cameraProvider?.unbindAll()
} catch (_: Exception) {
}
cameraExecutor?.shutdownNow()
cameraExecutor = null
try {
handLandmarker?.close()
} catch (_: Exception) {
}
handLandmarker = null
classifier.resetSmoothing()
}
private fun initHandLandmarkerIfNeeded() {
if (handLandmarker != null) return
try {
val baseOptions = BaseOptions.builder()
.setModelAssetPath("hand_landmarker.task")
.build()
val options = HandLandmarker.HandLandmarkerOptions.builder()
.setBaseOptions(baseOptions)
.setNumHands(2)
.setMinHandDetectionConfidence(0.42f)
.setMinHandPresenceConfidence(0.42f)
.setMinTrackingConfidence(0.42f)
.setRunningMode(RunningMode.LIVE_STREAM)
.setResultListener { result, _ ->
handleLandmarkResult(result)
}
.setErrorListener { e ->
Log.e("SignCameraController", "MediaPipe 오류: ${e.message}")
}
.build()
handLandmarker = HandLandmarker.createFromOptions(context, options)
} catch (e: Exception) {
Log.e("SignCameraController", "HandLandmarker 초기화 실패: ${e.message}", e)
}
}
private fun processImageProxy(imageProxy: ImageProxy) {
try {
val landmarker = handLandmarker
if (landmarker == null) {
imageProxy.close()
return
}
val bmp = Bitmap.createBitmap(
imageProxy.width,
imageProxy.height,
Bitmap.Config.ARGB_8888
)
val rotationDegrees = imageProxy.imageInfo.rotationDegrees
imageProxy.use { proxy ->
bmp.copyPixelsFromBuffer(proxy.planes[0].buffer)
}
val matrix = Matrix().apply {
if (rotationDegrees != 0) {
postRotate(rotationDegrees.toFloat())
}
postScale(-1f, 1f)
}
val transformedBitmap = Bitmap.createBitmap(
bmp, 0, 0, bmp.width, bmp.height, matrix, true
)
if (transformedBitmap !== bmp) {
bmp.recycle()
}
val mpImage = BitmapImageBuilder(transformedBitmap).build()
landmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
} catch (e: Exception) {
Log.e("SignCameraController", "프레임 분석 오류: ${e.message}")
try {
imageProxy.close()
} catch (_: Exception) {
}
}
}
private fun handleLandmarkResult(result: HandLandmarkerResult) {
val now = SystemClock.uptimeMillis()
val hands = result.landmarks()
if (hands.isEmpty() || hands[0].size < 21) {
if (now - lastValidCandidateSeenMs > 180L) {
currentCandidateToken = ""
classifier.resetSmoothing()
}
wasPinching = false
wasAssistTriggering = false
lastDetectedTwoHands = false
val emptyOut = SignRecognitionOutput(
"",
if (classifier.isAslMode()) "Show 1 or 2 hands to camera" else "한 손 또는 양손을 비춰주세요",
0f,
false,
1f,
FloatArray(36),
emptyList(),
emptyList(),
false,
null
)
onStateUpdate(emptyOut, 0f, null)
return
}
val primaryHand = hands[0]
val secondaryHand = if (hands.size >= 2) hands[1] else null
lastDetectedTwoHands = secondaryHand != null
val output = classifier.classify(primaryHand, secondaryHand)
lastFeatureVector = output.featureVector
var committed: String? = null
var progress = 0f
val dwellMs = classifier.getDwellMs()
val assistAct = output.secondHandAction
if (assistAct != null && !wasAssistTriggering && (now - lastCommittedTimeMs > 280L)) {
wasAssistTriggering = true
when (assistAct) {
"[COMMIT_NOW]" -> {
if (output.token.isNotEmpty()) {
committed = output.token
lastCommittedTimeMs = now
progress = 1f
}
}
"[BS]", "[SPACE]" -> {
committed = assistAct
lastCommittedTimeMs = now
progress = 1f
}
}
} else if (assistAct == null) {
wasAssistTriggering = false
}
if (committed == null) {
if (classifier.isPinchCommitMode() && !output.isTwoHandSign) {
if (output.token.isNotEmpty() && output.token != "ㅇ" && output.token != "F" && output.token != "O") {
currentCandidateToken = output.token
lastValidCandidateSeenMs = now
}
if (output.isPinchClick && !wasPinching && (now - lastCommittedTimeMs > 300L)) {
val targetToken = if (currentCandidateToken.isNotEmpty()) currentCandidateToken else output.token
if (targetToken.isNotEmpty()) {
committed = targetToken
lastCommittedTimeMs = now
progress = 1f
}
}
wasPinching = output.isPinchClick
} else {
if (output.token.isNotEmpty() && output.confidence >= 0.80f) {
lastValidCandidateSeenMs = now
if (output.token == currentCandidateToken) {
val elapsed = now - candidateStartTimeMs
progress = (elapsed.toFloat() / dwellMs.toFloat()).coerceIn(0f, 1f)
if (elapsed >= dwellMs && (now - lastCommittedTimeMs >= dwellMs)) {
committed = output.token
lastCommittedTimeMs = now
candidateStartTimeMs = now + 220L
progress = 1f
}
} else {
currentCandidateToken = output.token
candidateStartTimeMs = now
progress = 0.10f
}
} else {
if (currentCandidateToken.isNotEmpty() && (now - lastValidCandidateSeenMs <= 180L)) {
val elapsed = now - candidateStartTimeMs
progress = (elapsed.toFloat() / dwellMs.toFloat()).coerceIn(0f, 0.95f)
} else {
currentCandidateToken = ""
progress = 0f
}
}
}
}
onStateUpdate(output, progress, committed)
}
}
