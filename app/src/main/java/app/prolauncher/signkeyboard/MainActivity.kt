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
text = "🤟 수어 키보드 AI v2.0 (농아인 소통 스튜디오)"
setTextColor(Color.WHITE)
setTextSize(TypedValue.COMPLEX_UNIT_SP, 20.5f)
typeface = Typeface.DEFAULT_BOLD
}
val subtitle = TextView(this).apply {
text = "전면 카메라 24자 지문자·양손 수어 인식, 수어 어순 문장 보정, 180° 대면 필담 전광판, 상대방 음성 실시간 자막(STT)을 통합 지원합니다."
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
).apply { bottomMargin = dp(10) }
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
val bridgeHeader = TextView(this).apply {
text = "🗣️ 대면 필담 전광판 & 상대방 음성 실시간 자막 (병원·관공서·매장용)"
setTextColor(Color.parseColor("#FACC15"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
typeface = Typeface.DEFAULT_BOLD
setPadding(0, dp(4), 0, dp(6))
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
text = "상대방에게 보여줄 큰글씨 전광판"
setTextColor(Color.parseColor("#93C5FD"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
}
val bText = TextView(this).apply {
text = "청각장애인입니다. 화면의 글씨를 봐주시거나 아래 음성 자막 버튼을 눌러 말씀해 주세요."
setTextColor(Color.parseColor("#FEF08A"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
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
text = "💬 상대방 음성 자막: (상대방이 말하면 이곳에 실시간 큰글씨 자막이 표시됩니다)"
setTextColor(Color.parseColor("#A7F3D0"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
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
dp(220)
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
text = "현재 인식 상태: 대기 중 (양손 고속 모드 ON)"
setTextColor(Color.parseColor("#38BDF8"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
setPadding(0, 0, 0, dp(6))
}
statusText = statView
container.addView(statView)
val editView = EditText(this).apply {
hint = "수어로 입력하거나 상황별 칩을 누르면 이곳과 상단 전광판에 실시간 표시됩니다..."
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
val polished = KslSentenceHelper.naturalizeKslGloss(raw)
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
val quickChipScroll = HorizontalScrollView(this).apply {
isHorizontalScrollBarEnabled = false
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply { bottomMargin = dp(12) }
}
val quickChipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
for (cat in KslSentenceHelper.situationCategories) {
for ((shortLabel, sentence) in cat.phrases.take(2)) {
val chip = TextView(this).apply {
text = shortLabel
setTextColor(Color.WHITE)
setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
setPadding(dp(10), dp(6), dp(10), dp(6))
setBackgroundColor(Color.parseColor("#334155"))
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.WRAP_CONTENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply { marginEnd = dp(6) }
setOnClickListener {
practiceEdit?.setText(sentence)
practiceEdit?.setSelection(sentence.length)
billboardText?.text = sentence.trim()
}
}
quickChipRow.addView(chip)
}
}
quickChipScroll.addView(quickChipRow)
container.addView(quickChipScroll)
val dwellLabel = TextView(this).apply {
text = "⏱️ 수형 유지 자동입력 시간 (Dwell): ${classifier.getDwellMs()} ms (180ms 떨림 보정 포함)"
setTextColor(Color.parseColor("#E2E8F0"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
setPadding(0, dp(6), 0, dp(4))
}
val dwellSeek = SeekBar(this).apply {
max = 1200
progress = (classifier.getDwellMs() - 300L).toInt().coerceIn(0, 1200)
setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
val ms = 300L + p.toLong()
classifier.setDwellMs(ms)
dwellLabel.text = "⏱️ 수형 유지 자동입력 시간 (Dwell): $ms ms (180ms 떨림 보정 포함)"
}
override fun onStartTrackingTouch(seekBar: SeekBar?) {}
override fun onStopTrackingTouch(seekBar: SeekBar?) {}
})
}
container.addView(dwellLabel)
container.addView(dwellSeek)
val macroTitle = TextView(this).apply {
text = "★ 나만의 수어 동작 -> 단어/문장 녹화"
setTextColor(Color.parseColor("#FACC15"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
typeface = Typeface.DEFAULT_BOLD
setPadding(0, dp(14), 0, dp(6))
}
val macroInput = EditText(this).apply {
hint = "현재 손 모양으로 입력할 문구 (예: 수어 통역사 부탁드립니다)"
setHintTextColor(Color.parseColor("#64748B"))
setTextColor(Color.WHITE)
setBackgroundColor(Color.parseColor("#1E293B"))
setPadding(dp(10), dp(10), dp(10), dp(10))
}
val recordBtn = Button(this).apply {
text = "📸 현재 손 모양으로 위 문구 등록"
isAllCaps = false
setTextColor(Color.WHITE)
setBackgroundColor(Color.parseColor("#0284C7"))
setOnClickListener {
val phrase = macroInput.text.toString().trim()
val vec = cameraController?.lastFeatureVector
if (phrase.isEmpty()) {
Toast.makeText(this@MainActivity, "등록할 문구를 먼저 입력해주세요.", Toast.LENGTH_SHORT).show()
return@setOnClickListener
}
if (vec == null || vec.all { it == 0f }) {
Toast.makeText(this@MainActivity, "카메라에 손이 인식된 상태에서 눌러주세요.", Toast.LENGTH_SHORT).show()
return@setOnClickListener
}
classifier.addCustomMacro(phrase, "$phrase ", vec.clone())
macroInput.setText("")
refreshMacroList()
Toast.makeText(this@MainActivity, "'$phrase' 수어 동작이 저장되었습니다!", Toast.LENGTH_SHORT).show()
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
text = "📖 한국 수어 지문자 24자 & 양손 고속 입력 요약"
setTextColor(Color.WHITE)
setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
typeface = Typeface.DEFAULT_BOLD
setPadding(0, dp(10), 0, dp(6))
}
val guideLines = listOf(
"• ㄱ / ㄴ / ㅋ : 엄지·검지 펴서 아래(ㄱ), 옆(ㄴ), 검지 갈고리+중지(ㅋ)",
"• ㄷ / ㄹ / ㅌ / ㅍ : 검·중지 옆(ㄷ), 검·중·약지 모으기(ㄹ), 벌리기(ㅌ), 네 손가락 옆(ㅍ)",
"• ㅁ / ㅂ : 검·중·약지 위 모으기(ㅁ), 네 손가락 위로 붙여 펴기(ㅂ)",
"• ㅅ / ㅇ : 검·중지 아래 V자(ㅅ), 엄지·검지 동그라미 👌(ㅇ)",
"• ㅈ / ㅊ / ㅎ : 엄·검·중 펴기(ㅈ), 네 손가락 벌리기(ㅊ), 검지·새끼 펴기 🤘(ㅎ)",
"• ㅏ~ㅣ (모음 10자) : 검지 위(ㅏ), 검·중지 위 벌림(ㅑ), 엄·검지 위(ㅓ), 검·중지 위 모음(ㅕ), 엄지 위 👍(ㅗ), 검·중·약지 위 벌림(ㅛ), 엄지 아래 👎(ㅜ), 검·중지 아래 모음(ㅠ), 검지 옆(ㅡ), 새끼 위(ㅣ)",
"• 👐 양손 고속 입력 : 오른손으로 지문자를 만든 채 왼손 핀치(🤏) 시 대기 없이 즉시 입력!",
"• 🖐️ 손바닥 펴기 : 띄어쓰기 | ✊ 주먹 : 지우기 | 🤙 엄지+새끼 : 줄바꿈 | 🤟 ILY : '안녕하세요 '"
).joinToString("\n")
val guideBody = TextView(this).apply {
text = guideLines
setTextColor(Color.parseColor("#94A3B8"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.8f)
setLineSpacing(dp(3).toFloat(), 1f)
}
container.addView(guideTitle)
container.addView(guideBody)
scroll.addView(container)
setContentView(scroll)
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
sttCaptionText?.text = "🎙️ 듣고 있습니다... 상대방이 말씀하시면 자막으로 변환됩니다."
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
val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toString())
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
newLandmarks = output.smoothedLandmarks,
label = output.displayLabel,
progress = progress,
pinching = output.isPinchClick
)
runOnUiThread {
val assistInfo = if (output.secondHandAction != null) " | 양손보조: ${output.secondHandAction}" else ""
statusText?.text = "인식: ${output.displayLabel} (핀치: ${"%.2f".format(output.pinchRatio)}$assistInfo)"
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
"등록된 커스텀 수어 (${list.size}개): " + list.joinToString(", ") { it.name }
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
setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
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
