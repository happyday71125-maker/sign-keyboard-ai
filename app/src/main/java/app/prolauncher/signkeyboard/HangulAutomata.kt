package app.prolauncher.signkeyboard
import android.view.inputmethod.InputConnection
import android.widget.EditText
class HangulAutomata {
private val choseongList = charArrayOf(
'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ',
'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
)
private val jungseongList = charArrayOf(
'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
)
private val jongseongList = charArrayOf(
'\u0000', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ',
'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ',
'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
)
private var cho: Int = -1
private var jung: Int = -1
private var jong: Int = 0
private val recentSyllables = StringBuilder()
fun isComposing(): Boolean = cho != -1 || jung != -1
fun currentComposingText(): String {
if (cho != -1 && jung == -1) return choseongList[cho].toString()
if (cho == -1 && jung != -1) return jungseongList[jung].toString()
if (cho != -1 && jung != -1) {
val code = 0xAC00 + (cho * 21 * 28) + (jung * 28) + jong
return code.toChar().toString()
}
return ""
}
fun getCurrentWordPrefix(): String {
return recentSyllables.toString() + currentComposingText()
}
fun clearWordHistory() {
recentSyllables.clear()
}
fun inputJamo(ch: Char): Pair<String, String> {
val isConsonant = choseongList.contains(ch)
val isVowel = jungseongList.contains(ch)
if (!isConsonant && !isVowel) {
val committed = flush() + ch.toString()
if (ch == ' ' || ch == '\n') recentSyllables.clear()
else appendRecent(ch.toString())
return Pair(committed, "")
}
if (isConsonant) {
val choIdx = choseongList.indexOf(ch)
if (cho == -1 && jung == -1) {
cho = choIdx
return Pair("", currentComposingText())
}
if (cho != -1 && jung == -1) {
val doubled = combineDoubleConsonant(choseongList[cho], ch)
if (doubled != null) {
cho = choseongList.indexOf(doubled)
return Pair("", currentComposingText())
}
val prev = currentComposingText()
appendRecent(prev)
cho = choIdx
return Pair(prev, currentComposingText())
}
if (cho != -1 && jung != -1 && jong == 0) {
val jongIdx = jongseongList.indexOf(ch)
if (jongIdx > 0) {
jong = jongIdx
return Pair("", currentComposingText())
} else {
val prev = currentComposingText()
appendRecent(prev)
cho = choIdx
jung = -1
jong = 0
return Pair(prev, currentComposingText())
}
}
if (cho != -1 && jung != -1 && jong > 0) {
val combinedJong = combineCompoundJongseong(jongseongList[jong], ch)
if (combinedJong != null) {
jong = jongseongList.indexOf(combinedJong)
return Pair("", currentComposingText())
}
val prev = currentComposingText()
appendRecent(prev)
cho = choIdx
jung = -1
jong = 0
return Pair(prev, currentComposingText())
}
val prev = flush()
cho = choIdx
return Pair(prev, currentComposingText())
} else {
val jungIdx = jungseongList.indexOf(ch)
if (cho != -1 && jung == -1) {
jung = jungIdx
return Pair("", currentComposingText())
}
if (cho != -1 && jung != -1 && jong == 0) {
val combinedVowel = combineCompoundVowel(jungseongList[jung], ch)
if (combinedVowel != null) {
jung = jungseongList.indexOf(combinedVowel)
return Pair("", currentComposingText())
}
val prev = flush()
jung = jungIdx
return Pair(prev, currentComposingText())
}
if (cho != -1 && jung != -1 && jong > 0) {
val splitPair = splitCompoundJongseong(jongseongList[jong])
if (splitPair != null) {
jong = jongseongList.indexOf(splitPair.first)
val prev = currentComposingText()
appendRecent(prev)
cho = choseongList.indexOf(splitPair.second)
jung = jungIdx
jong = 0
return Pair(prev, currentComposingText())
} else {
val movedConsonant = jongseongList[jong]
jong = 0
val prev = currentComposingText()
appendRecent(prev)
cho = choseongList.indexOf(movedConsonant)
jung = jungIdx
jong = 0
return Pair(prev, currentComposingText())
}
}
if (cho == -1 && jung != -1) {
val combinedVowel = combineCompoundVowel(jungseongList[jung], ch)
if (combinedVowel != null) {
jung = jungseongList.indexOf(combinedVowel)
return Pair("", currentComposingText())
}
val prev = flush()
jung = jungIdx
return Pair(prev, currentComposingText())
}
cho = -1
jung = jungIdx
jong = 0
return Pair("", currentComposingText())
}
}
private fun appendRecent(str: String) {
recentSyllables.append(str)
if (recentSyllables.length > 14) {
recentSyllables.delete(0, recentSyllables.length - 14)
}
}
fun backspace(): Boolean {
if (!isComposing()) {
if (recentSyllables.isNotEmpty()) {
recentSyllables.deleteCharAt(recentSyllables.length - 1)
}
return false
}
if (jong > 0) {
val splitPair = splitCompoundJongseong(jongseongList[jong])
jong = if (splitPair != null) jongseongList.indexOf(splitPair.first) else 0
return true
}
if (jung != -1) {
jung = -1
return true
}
if (cho != -1) {
cho = -1
return true
}
return false
}
fun flush(): String {
val text = currentComposingText()
if (text.isNotEmpty()) appendRecent(text)
cho = -1
jung = -1
jong = 0
return text
}
fun applyToInputConnection(ic: InputConnection?, ch: Char) {
if (ic == null) return
val (committed, composing) = inputJamo(ch)
if (committed.isNotEmpty()) {
ic.commitText(committed, 1)
}
if (composing.isNotEmpty()) {
ic.setComposingText(composing, 1)
} else {
ic.finishComposingText()
}
}
fun applyWordToInputConnection(ic: InputConnection?, word: String) {
if (ic == null) return
val pending = flush()
if (pending.isNotEmpty()) {
ic.commitText(pending, 1)
}
ic.finishComposingText()
ic.commitText(word, 1)
if (word.endsWith(" ") || word.endsWith("\n")) {
recentSyllables.clear()
} else {
appendRecent(word)
}
}
fun replacePrefixWithSuggestedWord(ic: InputConnection?, suggestedWord: String) {
if (ic == null) return
val recentLen = recentSyllables.length
cho = -1
jung = -1
jong = 0
recentSyllables.clear()
ic.setComposingText("", 1)
ic.finishComposingText()
if (recentLen > 0) {
ic.deleteSurroundingText(recentLen, 0)
}
ic.commitText("$suggestedWord ", 1)
}
fun applyBackspaceToInputConnection(ic: InputConnection?) {
if (ic == null) return
if (backspace()) {
val comp = currentComposingText()
if (comp.isNotEmpty()) {
ic.setComposingText(comp, 1)
} else {
ic.commitText("", 1)
}
} else {
ic.deleteSurroundingText(1, 0)
}
}
fun applyTokenToEditText(editText: EditText, token: String, wasComposingBefore: Boolean): Boolean {
val currentText = editText.text?.toString() ?: ""
when (token) {
"[BS]" -> {
if (backspace()) {
val comp = currentComposingText()
val base = if (currentText.isNotEmpty()) currentText.dropLast(1) else ""
val next = base + comp
editText.setText(next)
editText.setSelection(next.length)
return comp.isNotEmpty()
} else {
if (currentText.isNotEmpty()) {
val next = currentText.dropLast(1)
editText.setText(next)
editText.setSelection(next.length)
}
return false
}
}
"[SPACE]" -> {
flush()
recentSyllables.clear()
val next = "$currentText "
editText.setText(next)
editText.setSelection(next.length)
return false
}
"[ENTER]" -> {
flush()
recentSyllables.clear()
val next = "$currentText\n"
editText.setText(next)
editText.setSelection(next.length)
return false
}
else -> {
if (token.length == 1) {
val (committed, composing) = inputJamo(token[0])
val base = if (wasComposingBefore && currentText.isNotEmpty()) {
currentText.dropLast(1)
} else {
currentText
}
val next = base + committed + composing
editText.setText(next)
editText.setSelection(next.length)
return composing.isNotEmpty()
} else {
flush()
recentSyllables.clear()
val next = currentText + token
editText.setText(next)
editText.setSelection(next.length)
return false
}
}
}
}
private fun combineDoubleConsonant(first: Char, second: Char): Char? {
if (first != second) return null
return when (first) {
'ㄱ' -> 'ㄲ'
'ㄷ' -> 'ㄸ'
'ㅂ' -> 'ㅃ'
'ㅅ' -> 'ㅆ'
'ㅈ' -> 'ㅉ'
else -> null
}
}
private fun combineCompoundVowel(first: Char, second: Char): Char? {
return when (Pair(first, second)) {
Pair('ㅏ', 'ㅣ') -> 'ㅐ'
Pair('ㅑ', 'ㅣ') -> 'ㅒ'
Pair('ㅓ', 'ㅣ') -> 'ㅔ'
Pair('ㅕ', 'ㅣ') -> 'ㅖ'
Pair('ㅗ', 'ㅏ') -> 'ㅘ'
Pair('ㅗ', 'ㅐ') -> 'ㅙ'
Pair('ㅗ', 'ㅣ') -> 'ㅚ'
Pair('ㅜ', 'ㅓ') -> 'ㅝ'
Pair('ㅜ', 'ㅔ') -> 'ㅞ'
Pair('ㅜ', 'ㅣ') -> 'ㅟ'
Pair('ㅡ', 'ㅣ') -> 'ㅢ'
else -> null
}
}
private fun combineCompoundJongseong(first: Char, second: Char): Char? {
return when (Pair(first, second)) {
Pair('ㄱ', 'ㅅ') -> 'ㄳ'
Pair('ㄴ', 'ㅈ') -> 'ㄵ'
Pair('ㄴ', 'ㅎ') -> 'ㄶ'
Pair('ㄹ', 'ㄱ') -> 'ㄺ'
Pair('ㄹ', 'ㅁ') -> 'ㄻ'
Pair('ㄹ', 'ㅂ') -> 'ㄼ'
Pair('ㄹ', 'ㅅ') -> 'ㄽ'
Pair('ㄹ', 'ㅌ') -> 'ㄾ'
Pair('ㄹ', 'ㅍ') -> 'ㄿ'
Pair('ㄹ', 'ㅎ') -> 'ㅀ'
Pair('ㅂ', 'ㅅ') -> 'ㅄ'
else -> null
}
}
private fun splitCompoundJongseong(jongChar: Char): Pair<Char, Char>? {
return when (jongChar) {
'ㄳ' -> Pair('ㄱ', 'ㅅ')
'ㄵ' -> Pair('ㄴ', 'ㅈ')
'ㄶ' -> Pair('ㄴ', 'ㅎ')
'ㄺ' -> Pair('ㄹ', 'ㄱ')
'ㄻ' -> Pair('ㄹ', 'ㅁ')
'ㄼ' -> Pair('ㄹ', 'ㅂ')
'ㄽ' -> Pair('ㄹ', 'ㅅ')
'ㄾ' -> Pair('ㄹ', 'ㅌ')
'ㄿ' -> Pair('ㄹ', 'ㅍ')
'ㅀ' -> Pair('ㄹ', 'ㅎ')
'ㅄ' -> Pair('ㅂ', 'ㅅ')
else -> null
}
}
}
