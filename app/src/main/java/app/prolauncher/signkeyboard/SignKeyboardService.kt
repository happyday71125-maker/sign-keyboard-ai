package app.prolauncher.signkeyboard
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
class SignKeyboardService : InputMethodService(), LifecycleOwner {
private val lifecycleRegistry = LifecycleRegistry(this)
override val lifecycle: Lifecycle
get() = lifecycleRegistry
private lateinit var automata: HangulAutomata
private lateinit var classifier: KoreanSignClassifier
private var cameraController: SignCameraController? = null
private var previewView: PreviewView? = null
private var overlayView: HandOverlayView? = null
private var composingBadge: TextView? = null
private var langToggleBtn: Button? = null
private var twoHandModeBtn: Button? = null
private var cameraToggleBtn: Button? = null
private var cameraContainer: FrameLayout? = null
private var suggestionRow: LinearLayout? = null
private var categoryTabRow: LinearLayout? = null
private var phraseRow: LinearLayout? = null
private var keypadContainer: LinearLayout? = null
private var selectedCategoryIdx: Int = 0
private var isCameraActive = true
override fun onCreate() {
super.onCreate()
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
automata = HangulAutomata()
classifier = KoreanSignClassifier(this)
}
override fun onCreateInputView(): View {
val root = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
setBackgroundColor(Color.parseColor("#0B1120"))
setPadding(dp(6), dp(6), dp(6), dp(8))
layoutParams = ViewGroup.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT,
ViewGroup.LayoutParams.WRAP_CONTENT
)
}
val topBar = LinearLayout(this).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
setPadding(dp(2), dp(2), dp(2), dp(4))
}
val badgeView = TextView(this).apply {
text = "🤟 한손/양손 수어 대기"
setTextColor(Color.parseColor("#38BDF8"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
typeface = Typeface.DEFAULT_BOLD
layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
}
composingBadge = badgeView
val langBtn = createSmallActionButton(getLangLabel(), "#4F46E5") {
val next = !classifier.isAslMode()
val flushed = automata.flush()
if (flushed.isNotEmpty()) currentInputConnection?.commitText(flushed, 1)
currentInputConnection?.finishComposingText()
automata.clearWordHistory()
classifier.setAslMode(next)
langToggleBtn?.text = getLangLabel()
rebuildCategoryTabs()
populateCategoryPhrases(selectedCategoryIdx)
rebuildKeypad()
refreshSuggestions()
}
langToggleBtn = langBtn
val twoHandBtn = createSmallActionButton(getTwoHandLabel(), "#059669") {
val next = !classifier.isTwoHandWordMode()
classifier.setTwoHandWordMode(next)
twoHandModeBtn?.text = getTwoHandLabel()
}
twoHandModeBtn = twoHandBtn
val naturalizeBtn = createSmallActionButton("✨ 다듬기", "#0284C7") {
naturalizeCurrentFieldText()
}
val camBtn = createSmallActionButton("📷 ON", "#1E293B") {
isCameraActive = !isCameraActive
if (isCameraActive) {
cameraContainer?.visibility = View.VISIBLE
cameraToggleBtn?.text = "📷 ON"
startCameraIfPermitted()
} else {
cameraController?.stop()
cameraContainer?.visibility = View.GONE
cameraToggleBtn?.text = "📷 OFF"
}
}
cameraToggleBtn = camBtn
val bridgeBtn = createSmallActionButton("🗣️", "#334155") {
val intent = Intent(this, MainActivity::class.java).apply {
addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
startActivity(intent)
}
val switchImeBtn = createSmallActionButton("🌐", "#334155") {
val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
imm.showInputMethodPicker()
}
topBar.addView(badgeView)
topBar.addView(langBtn)
topBar.addView(twoHandBtn)
topBar.addView(naturalizeBtn)
topBar.addView(camBtn)
topBar.addView(bridgeBtn)
topBar.addView(switchImeBtn)
root.addView(topBar)
val camFrame = FrameLayout(this).apply {
layoutParams = LinearLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT,
dp(155)
).apply { bottomMargin = dp(4) }
setBackgroundColor(Color.parseColor("#1E293B"))
}
val pv = PreviewView(this).apply {
layoutParams = FrameLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT,
ViewGroup.LayoutParams.MATCH_PARENT
)
implementationMode = PreviewView.ImplementationMode.COMPATIBLE
scaleType = PreviewView.ScaleType.FILL_CENTER
}
val ov = HandOverlayView(this).apply {
layoutParams = FrameLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT,
ViewGroup.LayoutParams.MATCH_PARENT
)
}
camFrame.addView(pv)
camFrame.addView(ov)
previewView = pv
overlayView = ov
cameraContainer = camFrame
root.addView(camFrame)
val suggestionScroll = HorizontalScrollView(this).apply {
isHorizontalScrollBarEnabled = false
layoutParams = LinearLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT,
ViewGroup.LayoutParams.WRAP_CONTENT
).apply { bottomMargin = dp(4) }
}
val sugRow = LinearLayout(this).apply {
orientation = LinearLayout.HORIZONTAL
}
suggestionRow = sugRow
suggestionScroll.addView(sugRow)
root.addView(suggestionScroll)
val catTabs = LinearLayout(this).apply {
orientation = LinearLayout.HORIZONTAL
layoutParams = LinearLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT,
ViewGroup.LayoutParams.WRAP_CONTENT
).apply { bottomMargin = dp(3) }
}
categoryTabRow = catTabs
val categoryScroll = HorizontalScrollView(this).apply {
isHorizontalScrollBarEnabled = false
layoutParams = LinearLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT,
ViewGroup.LayoutParams.WRAP_CONTENT
).apply { bottomMargin = dp(5) }
}
val pRow = LinearLayout(this).apply {
orientation = LinearLayout.HORIZONTAL
}
phraseRow = pRow
categoryScroll.addView(pRow)
root.addView(catTabs)
root.addView(categoryScroll)
val kpBox = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
layoutParams = LinearLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT,
ViewGroup.LayoutParams.WRAP_CONTENT
)
}
keypadContainer = kpBox
root.addView(kpBox)
rebuildCategoryTabs()
populateCategoryPhrases(selectedCategoryIdx)
rebuildKeypad()
refreshSuggestions()
return root
}
private fun rebuildCategoryTabs() {
val tabRow = categoryTabRow ?: return
tabRow.removeAllViews()
val asl = classifier.isAslMode()
KslSentenceHelper.situationCategories.forEachIndexed { idx, cat ->
val tabBtn = TextView(this).apply {
text = if (asl) cat.tabTitleEn else cat.tabTitleKo
setTextColor(Color.WHITE)
setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
typeface = Typeface.DEFAULT_BOLD
gravity = Gravity.CENTER
setPadding(dp(6), dp(4), dp(6), dp(4))
setBackgroundColor(if (idx == selectedCategoryIdx) Color.parseColor("#0284C7") else Color.parseColor("#1E293B"))
layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
marginEnd = dp(3)
}
setOnClickListener {
selectedCategoryIdx = idx
rebuildCategoryTabs()
populateCategoryPhrases(idx)
}
}
tabRow.addView(tabBtn)
}
}
private fun rebuildKeypad() {
val kp = keypadContainer ?: return
kp.removeAllViews()
val asl = classifier.isAslMode()
val rows = if (asl) {
listOf(
listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
listOf("Z", "X", "C", "V", "B", "N", "M", "[SPACE]", "[BS]", "[ENTER]")
)
} else {
listOf(
listOf("ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅎ"),
listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅏ", "ㅓ", "ㅗ", "ㅜ", "ㅡ", "ㅣ"),
listOf("ㅑ", "ㅕ", "ㅛ", "ㅠ", "ㅐ", "ㅔ", "[SPACE]", "[BS]", "[ENTER]")
)
}
for (rowTokens in rows) {
val rowLayout = LinearLayout(this).apply {
orientation = LinearLayout.HORIZONTAL
layoutParams = LinearLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT,
dp(39)
).apply { bottomMargin = dp(3) }
}
for (tok in rowTokens) {
val weight = when (tok) {
"[SPACE]" -> 2.2f
"[BS]", "[ENTER]" -> 1.3f
else -> 1f
}
val label = when (tok) {
"[SPACE]" -> if (asl) "Space" else "간격"
"[BS]" -> "⌫"
"[ENTER]" -> "↵"
else -> tok
}
val btn = Button(this).apply {
text = label
isAllCaps = false
setTextColor(Color.WHITE)
setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
setPadding(0, 0, 0, 0)
setBackgroundColor(
if (tok.startsWith("[")) Color.parseColor("#334155")
else Color.parseColor("#1E293B")
)
layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
marginStart = dp(2)
marginEnd = dp(2)
}
setOnClickListener {
it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
handleCommittedToken(tok)
}
}
rowLayout.addView(btn)
}
kp.addView(rowLayout)
}
}
private fun refreshSuggestions() {
val row = suggestionRow ?: return
row.removeAllViews()
val prefix = automata.getCurrentWordPrefix()
val suggestions = KslSentenceHelper.getSuggestions(prefix, classifier.isAslMode())
for (word in suggestions) {
val chip = TextView(this).apply {
text = "💡 $word"
setTextColor(Color.parseColor("#FEF08A"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
setPadding(dp(10), dp(4), dp(10), dp(4))
setBackgroundColor(Color.parseColor("#1E3A8A"))
layoutParams = LinearLayout.LayoutParams(
ViewGroup.LayoutParams.WRAP_CONTENT,
ViewGroup.LayoutParams.WRAP_CONTENT
).apply { marginEnd = dp(6) }
setOnClickListener {
automata.replacePrefixWithSuggestedWord(currentInputConnection, word)
overlayView?.triggerVisualFlash(word)
updateComposingIndicator("추천 입력: $word")
vibratePattern(isWord = true)
refreshSuggestions()
}
}
row.addView(chip)
}
}
private fun populateCategoryPhrases(categoryIndex: Int) {
val row = phraseRow ?: return
row.removeAllViews()
val category = KslSentenceHelper.situationCategories.getOrNull(categoryIndex) ?: return
val list = if (classifier.isAslMode()) category.phrasesEn else category.phrasesKo
for ((shortTitle, fullSentence) in list) {
val chip = TextView(this).apply {
text = shortTitle
setTextColor(Color.parseColor("#E2E8F0"))
setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
setPadding(dp(10), dp(5), dp(10), dp(5))
setBackgroundColor(Color.parseColor("#1E293B"))
layoutParams = LinearLayout.LayoutParams(
ViewGroup.LayoutParams.WRAP_CONTENT,
ViewGroup.LayoutParams.WRAP_CONTENT
).apply { marginEnd = dp(6) }
setOnClickListener {
automata.applyWordToInputConnection(currentInputConnection, fullSentence)
overlayView?.triggerVisualFlash(shortTitle)
updateComposingIndicator("문장 입력: $shortTitle")
vibratePattern(isWord = true)
refreshSuggestions()
}
}
row.addView(chip)
}
}
private fun naturalizeCurrentFieldText() {
val ic = currentInputConnection ?: return
val flushed = automata.flush()
if (flushed.isNotEmpty()) {
ic.commitText(flushed, 1)
}
ic.finishComposingText()
val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString()
if (!extracted.isNullOrBlank()) {
val polished = KslSentenceHelper.naturalizeSignGloss(extracted, classifier.isAslMode())
ic.deleteSurroundingText(extracted.length, extracted.length)
ic.commitText(polished, 1)
automata.clearWordHistory()
overlayView?.triggerVisualFlash("✨ 문장 다듬기 완료")
updateComposingIndicator("✨ ${polished.trim()}")
vibratePattern(isWord = true)
} else {
updateComposingIndicator("다듬을 수어 단어를 먼저 입력해주세요")
}
}
override fun onWindowShown() {
super.onWindowShown()
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
langToggleBtn?.text = getLangLabel()
twoHandModeBtn?.text = getTwoHandLabel()
rebuildCategoryTabs()
populateCategoryPhrases(selectedCategoryIdx)
rebuildKeypad()
refreshSuggestions()
if (isCameraActive) {
startCameraIfPermitted()
}
}
override fun onWindowHidden() {
super.onWindowHidden()
cameraController?.stop()
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
}
override fun onFinishInputView(finishingInput: Boolean) {
super.onFinishInputView(finishingInput)
val flushed = automata.flush()
if (flushed.isNotEmpty()) {
currentInputConnection?.commitText(flushed, 1)
}
automata.clearWordHistory()
}
override fun onDestroy() {
cameraController?.stop()
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
super.onDestroy()
}
private fun startCameraIfPermitted() {
val pv = previewView ?: return
if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
!= PackageManager.PERMISSION_GRANTED
) {
overlayView?.updateState(
emptyList(),
emptyList(),
"⚠️ '🗣️' 버튼을 눌러 카메라 권한을 허용해주세요",
0f,
false
)
return
}
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
if (committedToken != null) {
overlayView?.post {
handleCommittedToken(committedToken)
}
}
}
}
cameraController?.start(this, pv)
}
private fun handleCommittedToken(token: String) {
val ic = currentInputConnection ?: return
when (token) {
"[BS]" -> {
automata.applyBackspaceToInputConnection(ic)
overlayView?.triggerVisualFlash("⌫ 지우기")
updateComposingIndicator("⌫ 지우기")
vibratePattern(isWord = false)
}
"[SPACE]" -> {
automata.applyWordToInputConnection(ic, " ")
overlayView?.triggerVisualFlash("Space")
updateComposingIndicator("🖐️ 띄어쓰기")
vibratePattern(isWord = false)
}
"[ENTER]" -> {
val pending = automata.flush()
if (pending.isNotEmpty()) ic.commitText(pending, 1)
ic.finishComposingText()
automata.clearWordHistory()
val editorInfo = currentInputEditorInfo
if (editorInfo != null && (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
ic.performEditorAction(editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION)
} else {
ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
}
overlayView?.triggerVisualFlash("↵ Enter")
updateComposingIndicator("🤙 줄바꿈/전송")
vibratePattern(isWord = true)
}
else -> {
if (token.length == 1) {
automata.applyToInputConnection(ic, token[0])
vibratePattern(isWord = false)
} else {
automata.applyWordToInputConnection(ic, token)
vibratePattern(isWord = true)
}
val comp = automata.currentComposingText()
overlayView?.triggerVisualFlash(if (comp.isNotEmpty()) comp else token.trim())
updateComposingIndicator(
if (comp.isNotEmpty()) "조합 중: $comp (최근: $token)"
else "입력됨: ${token.trim()}"
)
}
}
refreshSuggestions()
}
private fun updateComposingIndicator(msg: String) {
composingBadge?.text = msg
}
private fun getLangLabel(): String {
return if (classifier.isAslMode()) "🇺🇸 ASL" else "🇰🇷 KSL"
}
private fun getTwoHandLabel(): String {
return if (classifier.isTwoHandWordMode()) "👐 양손단어" else "⚡ 양손보조"
}
private fun createSmallActionButton(label: String, bgHex: String, onClick: () -> Unit): Button {
return Button(this).apply {
text = label
isAllCaps = false
setTextColor(Color.WHITE)
setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
setPadding(dp(5), dp(2), dp(5), dp(2))
setBackgroundColor(Color.parseColor(bgHex))
layoutParams = LinearLayout.LayoutParams(
ViewGroup.LayoutParams.WRAP_CONTENT,
dp(29)
).apply { marginStart = dp(2) }
setOnClickListener { onClick() }
}
}
private fun vibratePattern(isWord: Boolean) {
try {
val vib = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
if (isWord) {
vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 25L, 45L, 40L), -1))
} else {
vib.vibrate(VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE))
}
} else {
@Suppress("DEPRECATION")
vib.vibrate(if (isWord) 55L else 20L)
}
} catch (_: Exception) {
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
