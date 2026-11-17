package app.prolauncher.signkeyboard
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
class MainActivity : AppCompatActivity() {
private lateinit var classifier: KoreanSignClassifier
private lateinit var automata: HangulAutomata
private var cameraController: SignCameraController? = null
private var speechRecognizer: SpeechRecognizer? = null
private var previewView: PreviewView? = null
private var overlayView: HandOverlayView? = null
private var practiceEdit: EditText? = null
private var statusText: TextView? = null
private var macroListText: TextView? = null
private var billboardCard: LinearLayout? = null
private var billboardText: TextView? = null
private var sttCaptionText: TextView? = null
private var sttListenBtn: Button? = null
private var langBtn: Button? = null
private var twoHandBtn: Button? = null
private var isPracticeComposing = false
private var isBillboardFlipped = false
private var isListeningStt = false
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
classifier = KoreanSignClassifier(this)
automata = HangulAutomata()
val scroll = ScrollView(this).apply {
setBackgroundColor(Color.parseColor("#0B1120"))
}
val container = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
setPadding(dp(16), dp(18), dp(16), dp(32))
}
val title = TextView(this).apply {
text = "🤟 수어 키보드 AI v3.0 (한·영 / 한손·양손 통합)"
setTextColor(Color.WHITE)
setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
typeface = Typeface.DEFAULT_BOLD
}
val subtitle = TextView(this).apply {
text = "한국수어(KSL 24자 지문자 + 양손 공식 수어 단어)와 미국수어(ASL A~Z 26자 + 양손 수어), 180° 대면 필담 전광판, 한·영 실시간 음성 자막(STT)을 모두 지원합니다."
setTextColor(Color.parseColor("#94A3B8"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
setPadding(0, dp(4), 0, dp(12))
}
container.addView(title)
container.addView(subtitle)
val stepRow = LinearLayout(this).apply {
orientation = LinearLayout.HORIZONTAL
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply { bottomMargin = dp(8) }
}
stepRow.addView(createStepButton("① 카메라·마이크 권한") {
ActivityCompat.requestPermissions(
this,
arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
1001
)
})
stepRow.addView(createStepButton("② 키보드 켜기") {
startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
})
stepRow.addView(createStepButton("③ 키보드 선택") {
val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
imm.showInputMethodPicker()
})
container.addView(stepRow)
val modeRow = LinearLayout(this).apply {
orientation = LinearLayout.HORIZONTAL
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply { bottomMargin = dp(12) }
}
val lBtn = createActionButton(getLangModeTitle(), "#4F46E5") {
val next = !classifier.isAslMode()
automata.flush()
automata.clearWordHistory()
classifier.setAslMode(next)
langBtn?.text = getLangModeTitle()
}
langBtn = lBtn
val thBtn = createActionButton(getTwoHandModeTitle(), "#059669") {
val next = !classifier.isTwoHandWordMode()
classifier.setTwoHandWordMode(next)
twoHandBtn?.text = getTwoHandModeTitle()
}
twoHandBtn = thBtn
modeRow.addView(lBtn)
modeRow.addView(thBtn)
container.addView(modeRow)
val bridgeHeader = TextView(this).apply {
text = "🗣️ 대면 필담 전광판 & 상대방 음성 실시간 자막 (한/영 자동 연동)"
setTextColor(Color.parseColor("#FACC15"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
typeface = Typeface.DEFAULT_BOLD
setPadding(0, dp(2), 0, dp(6))
}
container.addView(bridgeHeader)
val bCard = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
setBackgroundColor(Color.parseColor("#1E3A8A"))
setPadding(dp(14), dp(14), dp(14), dp(14))
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply { bottomMargin = dp(8) }
}
billboardCard = bCard
val billboardHint = TextView(this).apply {
text = "맞은편 상대방에게 보여줄 큰글씨 전광판"
setTextColor(Color.parseColor("#93C5FD"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
}
val bText = TextView(this).apply {
text = "청각장애인입니다. 화면의 글씨를 봐주시거나 아래 음성 자막 버튼을 눌러 말씀해 주세요."
setTextColor(Color.parseColor("#FEF08A"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
typeface = Typeface.DEFAULT_BOLD
setPadding(0, dp(6), 0, dp(6))
}
billboardText = bText
bCard.addView(billboardHint)
bCard.addView(bText)
container.addView(bCard)
val bridgeBtnRow = LinearLayout(this).apply {
orientation = LinearLayout.HORIZONTAL
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply { bottomMargin = dp(8) }
}
val flipBtn = createActionButton("🔄 맞은편 180° 뒤집기", "#0284C7") {
isBillboardFlipped = !isBillboardFlipped
billboardCard?.rotation = if (isBillboardFlipped) 180f else 0f
}
val listenBtn = createActionButton("🎙️ 상대방 음성 자막 켜기", "#059669") {
toggleSpeechToText()
}
sttListenBtn = listenBtn
bridgeBtnRow.addView(flipBtn)
bridgeBtnRow.addView(listenBtn)
container.addView(bridgeBtnRow)
val captionView = TextView(this).apply {
text = "💬 상대방 음성 자막: (KSL 모드 시 한국어, ASL 모드 시 영어 음성을 자막으로 변환합니다)"
setTextColor(Color.parseColor("#A7F3D0"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
typeface = Typeface.DEFAULT_BOLD
setBackgroundColor(Color.parseColor("#064E3B"))
setPadding(dp(12), dp(10), dp(12), dp(10))
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply { bottomMargin = dp(12) }
}
sttCaptionText = captionView
container.addView(captionView)
val camBox = FrameLayout(this).apply {
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
dp(230)
).apply { bottomMargin = dp(8) }
setBackgroundColor(Color.parseColor("#1E293B"))
}
val pv = PreviewView(this).apply {
layoutParams = FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT,
FrameLayout.LayoutParams.MATCH_PARENT
)
implementationMode = PreviewView.ImplementationMode.COMPATIBLE
}
val ov = HandOverlayView(this).apply {
layoutParams = FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT,
FrameLayout.LayoutParams.MATCH_PARENT
)
}
camBox.addView(pv)
camBox.addView(ov)
previewView = pv
overlayView = ov
container.addView(camBox)
val statView = TextView(this).apply {
text = "현재 인식 상태: 한손/양손 대기 중"
setTextColor(Color.parseColor("#38BDF8"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
setPadding(0, 0, 0, dp(6))
}
statusText = statView
container.addView(statView)
val editView = EditText(this).apply {
hint = "한손 지문자(한글 24자 / 영어 A~Z) 또는 양손 공식 수어를 비추면 이곳에 자동 입력됩니다..."
setHintTextColor(Color.parseColor("#64748B"))
setTextColor(Color.WHITE)
setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
setBackgroundColor(Color.parseColor("#1E293B"))
setPadding(dp(12), dp(12), dp(12), dp(12))
minLines = 2
}
practiceEdit = editView
container.addView(editView)
val editActionRow = LinearLayout(this).apply {
orientation = LinearLayout.HORIZONTAL
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply { topMargin = dp(6); bottomMargin = dp(10) }
}
editActionRow.addView(createActionButton("✨ 수어 어순 -> 문장 다듬기", "#0284C7") {
val raw = practiceEdit?.text?.toString() ?: ""
if (raw.isNotBlank()) {
val polished = KslSentenceHelper.naturalizeSignGloss(raw, classifier.isAslMode())
practiceEdit?.setText(polished)
practiceEdit?.setSelection(polished.length)
billboardText?.text = polished.trim()
overlayView?.triggerVisualFlash("✨ 문장 다듬기 완료")
}
})
editActionRow.addView(createActionButton("🖥️ 전광판에 크게 띄우기", "#4F46E5") {
val raw = practiceEdit?.text?.toString()?.trim() ?: ""
if (raw.isNotEmpty()) {
billboardText?.text = raw
Toast.makeText(this, "상단 전광판에 반영되었습니다.", Toast.LENGTH_SHORT).show()
}
})
container.addView(editActionRow)
val dwellLabel = TextView(this).apply {
text = "⏱️ 수형 유지 자동입력 시간 (Dwell): ${classifier.getDwellMs()} ms"
setTextColor(Color.parseColor("#E2E8F0"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
setPadding(0, dp(4), 0, dp(4))
}
val dwellSeek = SeekBar(this).apply {
max = 1200
progress = (classifier.getDwellMs() - 300L).toInt().coerceIn(0, 1200)
setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
val ms = 300L + p.toLong()
classifier.setDwellMs(ms)
dwellLabel.text = "⏱️ 수형 유지 자동입력 시간 (Dwell): $ms ms"
}
override fun onStartTrackingTouch(seekBar: SeekBar?) {}
override fun onStopTrackingTouch(seekBar: SeekBar?) {}
})
}
container.addView(dwellLabel)
container.addView(dwellSeek)
val macroTitle = TextView(this).apply {
text = "★ 한손/양손 공식 수어 동작 추가 등록 (36차원 양손+궤적 학습)"
setTextColor(Color.parseColor("#FACC15"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
typeface = Typeface.DEFAULT_BOLD
setPadding(0, dp(14), 0, dp(6))
}
val macroInput = EditText(this).apply {
hint = "현재 한손 또는 양손 수어로 입력할 단어/문장 (예: 반갑습니다 / Nice to meet you)"
setHintTextColor(Color.parseColor("#64748B"))
setTextColor(Color.WHITE)
setBackgroundColor(Color.parseColor("#1E293B"))
setPadding(dp(10), dp(10), dp(10), dp(10))
}
val recordBtn = Button(this).apply {
text = "📸 현재 카메라의 한손/양손 수어 동작으로 위 문구 등록"
isAllCaps = false
setTextColor(Color.WHITE)
setBackgroundColor(Color.parseColor("#0284C7"))
setOnClickListener {
val phrase = macroInput.text.toString().trim()
val vec = cameraController?.lastFeatureVector
val isTwo = cameraController?.lastDetectedTwoHands ?: false
if (phrase.isEmpty()) {
Toast.makeText(this@MainActivity, "등록할 단어/문장을 먼저 입력해주세요.", Toast.LENGTH_SHORT).show()
return@setOnClickListener
}
if (vec == null || vec.all { it == 0f }) {
Toast.makeText(this@MainActivity, "카메라에 한 손 또는 양손이 인식된 상태에서 눌러주세요.", Toast.LENGTH_SHORT).show()
return@setOnClickListener
}
classifier.addCustomMacro(phrase, "$phrase ", isTwo, vec.clone())
macroInput.setText("")
refreshMacroList()
val handTag = if (isTwo) "양손 수어" else "한손 수어"
Toast.makeText(this@MainActivity, "[$handTag] '$phrase' 동작이 저장되었습니다!", Toast.LENGTH_SHORT).show()
}
}
val macroView = TextView(this).apply {
setTextColor(Color.parseColor("#CBD5E1"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
setPadding(0, dp(6), 0, dp(12))
}
macroListText = macroView
refreshMacroList()
container.addView(macroTitle)
container.addView(macroInput)
container.addView(recordBtn)
container.addView(macroView)
val guideTitle = TextView(this).apply {
text = "📖 한손·양손 & 한국수어(KSL)·미국수어(ASL) 지원 안내"
setTextColor(Color.WHITE)
setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
typeface = Typeface.DEFAULT_BOLD
setPadding(0, dp(10), 0, dp(6))
}
val guideLines = listOf(
"1. [한손 한국수어 지문자 24자] : 자음 14자(ㄱ~ㅎ) + 모음 10자(ㅏ~ㅣ) 전수 인식 및 완성형 한글 조합",
"2. [한손 미국수어(ASL) 알파벳 26자] : A~Z 전수 인식 (동적 궤적 문자 J, Z 및 주먹 계열 A, E, S, M, N, T 엄지 위치 구분 포함)",
"3. [양손 공식 수어 단어 (KSL & ASL)] : 두 손을 함께 비추면 하늘색(주손)+보라색(보조손) 42관절을 동시 추적하여 아래 공식 양손 수어를 즉시 인식합니다:",
"   • ⛺ 집 / HOUSE : 양손을 펴서 지붕 모양으로 손끝 맞대기",
"   • 📖 학교 / BOOK : 양손 손바닥을 펴서 새끼손가락 쪽을 나란히 맞대기",
"   • 🤝 만나다 / MEET : 양손 검지(☝️)를 세워 서로 마주 보며 다가오기",
"   • 👍 도와주세요 / HELP : 한 손 엄지를 세우고 다른 손바닥으로 밑에서 받치기",
"   • 🙏 감사합니다 / THANK YOU : 한 손등 위를 다른 손날로 두드리기",
"   • 🏥 병원 / HOSPITAL : 양손 검·중지를 십자(+) 교차하거나 손목 짚기",
"   • 💊 약국 / MEDICINE : 한 손바닥 중심에 다른 손 검지/중지 끝 맞대기",
"   • ❤️ 사랑합니다 / LOVE : 한 손 주먹을 다른 손바닥으로 감싸기",
"   • 🔨 일하다 / WORK : 양손 주먹을 쥐고 마주 두드리기",
"   • 🤏 더 / MORE : 양손 핀치(엄지+검지) 끝을 서로 맞대기",
"   • 👐 끝 / FINISHED : 양손 다섯 손가락을 모두 활짝 펴서 벌리기"
).joinToString("\n")
val guideBody = TextView(this).apply {
text = guideLines
setTextColor(Color.parseColor("#94A3B8"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.6f)
setLineSpacing(dp(3).toFloat(), 1f)
}
container.addView(guideTitle)
container.addView(guideBody)
scroll.addView(container)
setContentView(scroll)
}
private fun getLangModeTitle(): String {
return if (classifier.isAslMode()) "🇺🇸 현재: 영어수어 (ASL A~Z)" else "🇰🇷 현재: 한국수어 (KSL 한글)"
}
private fun getTwoHandModeTitle(): String {
return if (classifier.isTwoHandWordMode()) "👐 양손: 공식 수어 단어 인식" else "⚡ 양손: 지문자 고속 확정 보조"
}
private fun toggleSpeechToText() {
if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
!= PackageManager.PERMISSION_GRANTED
) {
ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
return
}
if (isListeningStt) {
stopSpeechToText()
} else {
startSpeechToText()
}
}
private fun startSpeechToText() {
if (!SpeechRecognizer.isRecognitionAvailable(this)) {
sttCaptionText?.text = "⚠️ 이 기기에서 기본 음성 인식 서비스를 사용할 수 없습니다."
return
}
try {
if (speechRecognizer == null) {
speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
setRecognitionListener(object : RecognitionListener {
override fun onReadyForSpeech(params: Bundle?) {
val langName = if (classifier.isAslMode()) "English" else "한국어"
sttCaptionText?.text = "🎙️ [$langName] 듣고 있습니다... 상대방이 말씀하시면 실시간 자막으로 변환됩니다."
}
override fun onBeginningOfSpeech() {}
override fun onRmsChanged(rmsdB: Float) {}
override fun onBufferReceived(buffer: ByteArray?) {}
override fun onEndOfSpeech() {
isListeningStt = false
sttListenBtn?.text = "🎙️ 상대방 음성 자막 켜기"
}
override fun onError(error: Int) {
isListeningStt = false
sttListenBtn?.text = "🎙️ 상대방 음성 자막 켜기"
}
override fun onResults(results: Bundle?) {
val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
if (!matches.isNullOrEmpty()) {
sttCaptionText?.text = "💬 상대방 말씀: \"${matches[0]}\""
overlayView?.triggerVisualFlash("💬 음성 자막 수신")
}
}
override fun onPartialResults(partialResults: Bundle?) {
val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
if (!partial.isNullOrEmpty()) {
sttCaptionText?.text = "💬 실시간 자막: ${partial[0]}..."
}
}
override fun onEvent(eventType: Int, params: Bundle?) {}
})
}
}
val localeStr = if (classifier.isAslMode()) Locale.US.toString() else Locale.KOREAN.toString()
val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeStr)
putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
}
speechRecognizer?.startListening(intent)
isListeningStt = true
sttListenBtn?.text = "⏹️ 음성 자막 중지"
} catch (e: Exception) {
sttCaptionText?.text = "⚠️ 음성 자막 시작 오류: ${e.message}"
}
}
private fun stopSpeechToText() {
try {
speechRecognizer?.stopListening()
} catch (_: Exception) {
}
isListeningStt = false
sttListenBtn?.text = "🎙️ 상대방 음성 자막 켜기"
}
override fun onResume() {
super.onResume()
langBtn?.text = getLangModeTitle()
twoHandBtn?.text = getTwoHandModeTitle()
if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
== PackageManager.PERMISSION_GRANTED
) {
startPreviewCamera()
} else {
ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
}
}
override fun onPause() {
cameraController?.stop()
stopSpeechToText()
super.onPause()
}
override fun onDestroy() {
try {
speechRecognizer?.destroy()
} catch (_: Exception) {
}
super.onDestroy()
}
override fun onRequestPermissionsResult(
requestCode: Int,
permissions: Array<out String>,
grantResults: IntArray
) {
super.onRequestPermissionsResult(requestCode, permissions, grantResults)
if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
startPreviewCamera()
} else if (requestCode == 1002 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
startSpeechToText()
}
}
private fun startPreviewCamera() {
val pv = previewView ?: return
if (cameraController == null) {
cameraController = SignCameraController(
context = this,
classifier = classifier
) { output, progress, committedToken ->
overlayView?.updateState(
primary = output.primaryLandmarks,
secondary = output.secondaryLandmarks,
label = output.displayLabel,
progress = progress,
pinching = output.isPinchClick
)
runOnUiThread {
val handCnt = if (output.secondaryLandmarks.isNotEmpty()) "양손(42관절)" else "한손(21관절)"
statusText?.text = "[$handCnt] ${output.displayLabel} (핀치: ${"%.2f".format(output.pinchRatio)})"
if (committedToken != null) {
val edit = practiceEdit
if (edit != null) {
isPracticeComposing = automata.applyTokenToEditText(
edit,
committedToken,
isPracticeComposing
)
val currentStr = edit.text?.toString()?.trim() ?: ""
if (currentStr.isNotEmpty()) {
billboardText?.text = currentStr
}
overlayView?.triggerVisualFlash(committedToken)
}
}
}
}
}
cameraController?.start(this, pv)
}
private fun refreshMacroList() {
val list = classifier.getCustomMacros()
macroListText?.text = if (list.isEmpty()) {
"등록된 커스텀 수어 단어가 없습니다."
} else {
"등록된 커스텀 수어 (${list.size}개): " + list.joinToString(", ") {
val tag = if (it.isTwoHand) "양손" else "한손"
"${it.name}($tag)"
}
}
}
private fun createStepButton(label: String, onClick: () -> Unit): Button {
return Button(this).apply {
text = label
isAllCaps = false
setTextColor(Color.WHITE)
setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
setBackgroundColor(Color.parseColor("#1E293B"))
gravity = Gravity.CENTER
layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
marginEnd = dp(5)
}
setOnClickListener { onClick() }
}
}
private fun createActionButton(label: String, bgHex: String, onClick: () -> Unit): Button {
return Button(this).apply {
text = label
isAllCaps = false
setTextColor(Color.WHITE)
setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
setBackgroundColor(Color.parseColor(bgHex))
gravity = Gravity.CENTER
layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
marginEnd = dp(6)
}
setOnClickListener { onClick() }
}
}
private fun dp(value: Int): Int {
return TypedValue.applyDimension(
TypedValue.COMPLEX_UNIT_DIP,
value.toFloat(),
resources.displayMetrics
).toInt()
}
}
