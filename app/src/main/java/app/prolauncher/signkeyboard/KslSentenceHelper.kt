package app.prolauncher.signkeyboard
object KslSentenceHelper {
data class DeafPhraseCategory(
val tabTitleKo: String,
val tabTitleEn: String,
val phrasesKo: List<Pair<String, String>>,
val phrasesEn: List<Pair<String, String>>
)
val situationCategories: List<DeafPhraseCategory> = listOf(
DeafPhraseCategory(
tabTitleKo = "🚨 긴급/안내",
tabTitleEn = "🚨 Emergency",
phrasesKo = listOf(
"청각장애인 문자요청" to "청각장애인입니다. 음성 통화가 어려우니 문자나 카톡으로 연락 부탁드립니다. ",
"필담/화면소통 요청" to "청각장애인입니다. 말씀 대신 글씨를 적어주시거나 화면을 가리켜 알려주세요. ",
"119/112 응급도움" to "청각장애인 응급 상황입니다. 음성 통화가 불가능하니 현재 위치로 구급/경찰 출동을 부탁드립니다. ",
"수어통역사 요청" to "한국수어 통역사(손말이음센터 107) 연결이 필요합니다. 잠시만 기다려주세요. ",
"비상시 알림요청" to "소리를 듣지 못합니다. 비상 상황이나 순서가 되면 어깨를 가볍게 두드려 알려주세요. "
),
phrasesEn = listOf(
"Deaf - Text Please" to "I am Deaf. Please communicate with me by text or writing instead of voice calls. ",
"Emergency Help" to "Emergency: I am Deaf and cannot make voice calls. Please send help to my location. ",
"ASL Interpreter" to "I need a sign language interpreter, please. ",
"Tap Shoulder Alert" to "I cannot hear audio announcements. Please tap my shoulder or wave when it is my turn. "
)
),
DeafPhraseCategory(
tabTitleKo = "🏥 병원/약국",
tabTitleEn = "🏥 Medical",
phrasesKo = listOf(
"호명 시 손짓요청" to "청각장애인입니다. 제 차례가 되면 마이크 호명 대신 화면 표시나 손짓으로 불러주세요. ",
"증상 설명(통증)" to "어제부터 열이 나고 통증이 심해 진료받으러 왔습니다. ",
"복약지도 메모요청" to "약 복용 시간(식전/식후)과 하루 횟수, 주의사항을 약 봉투에 직접 적어주세요. ",
"처방전/진단서 발급" to "약국 제출용 처방전과 보험 청구용 진료비 세부내역서를 발급해 주세요. "
),
phrasesEn = listOf(
"Call Turn Visually" to "I am Deaf. Please wave or show my name on the screen when it is my turn. ",
"Pain & Fever" to "I have had a fever and severe pain since yesterday and need medical attention. ",
"Write Dosage" to "Please write the medication dosage and schedule directly on the bag. ",
"Prescription Copy" to "Could I please have a copy of my prescription and medical receipt? "
)
),
DeafPhraseCategory(
tabTitleKo = "🏛️ 관공서/은행",
tabTitleEn = "🏛️ Public/Bank",
phrasesKo = listOf(
"ARS 대신 문자인증" to "청각장애인이라 ARS 음성 인증을 들을 수 없습니다. SMS 문자인증이나 대면 확인으로 진행해 주세요. ",
"서명 위치 안내요청" to "서류에서 제가 작성하고 서명해야 할 부분에 형광펜이나 손가락으로 표시해 주세요. ",
"등본/신분증 민원" to "주민등록등본 발급 및 본인 확인 절차를 도와주시면 감사하겠습니다. "
),
phrasesEn = listOf(
"SMS Instead of Voice" to "I cannot use voice verification calls. Please use SMS text verification instead. ",
"Point Where to Sign" to "Please point to the lines where I need to fill out and sign on this form. ",
"ID Verification" to "I am here for document issuance and identity verification. "
)
),
DeafPhraseCategory(
tabTitleKo = "🏪 일상/택시",
tabTitleEn = "🏪 Daily/Taxi",
phrasesKo = listOf(
"배달 문앞+문자요청" to "청각장애인이라 초인종 소리를 듣지 못합니다. 문 앞에 두시고 문자 메시지 부탁드립니다. ",
"택시 목적지 하차" to "청각장애인입니다. 목적지 근처에 도착하면 손짓으로 알려주시면 감사하겠습니다. ",
"카페 진동벨 확인" to "음료가 나오면 진동벨이나 번호판 화면으로 확인하겠습니다. 영수증을 함께 주세요. "
),
phrasesEn = listOf(
"Leave at Door + Text" to "I cannot hear the doorbell. Please leave the delivery at the door and send a text. ",
"Taxi Destination" to "I am Deaf. Please tap or point when we arrive near the destination. ",
"Receipt & Buzzer" to "I will check the vibration pager or screen number when my order is ready. "
)
)
)
private val koDictionary = listOf(
"안녕하세요" to "ㅇㄴㅎㅅㅇ",
"감사합니다" to "ㄱㅅㅎㄴㄷ",
"죄송합니다" to "ㅈㅅㅎㄴㄷ",
"청각장애인입니다" to "ㅊㄱㅈㅇㅇ",
"도와주세요" to "ㄷㅇㅈㅅㅇ",
"병원" to "ㅂㅇ",
"약국" to "ㅇㄱ",
"예약" to "ㅇㅇ",
"진료" to "ㅈㄹ",
"아프다" to "ㅇㅍㄷ",
"학교" to "ㅎㄱ",
"집" to "ㅈ",
"만나다" to "ㅁㄴㄷ",
"사랑합니다" to "ㅅㄹㅎㄴㄷ",
"기다리다" to "ㄱㄷㄹㄷ",
"잠시만요" to "ㅈㅅㅁㅇ",
"수어통역사" to "ㅅㅇㅌㅇㅅ",
"손말이음센터" to "ㅅㅁㅇㅇㅅㅌ",
"화장실" to "ㅎㅈㅅ",
"택시" to "ㅌㅅ",
"배달" to "ㅂㄷ",
"영수증" to "ㅇㅅㅈ",
"어디인가요" to "ㅇㄷㅇㄱㅇ",
"얼마인가요" to "ㅇㅁㅇㄱㅇ",
"괜찮습니다" to "ㄱㅊㅅㄴㄷ",
"네, 맞습니다" to "ㄴ",
"아니요" to "ㅇㄴㅇ"
)
private val enDictionary = listOf(
"HELLO", "THANK YOU", "PLEASE", "HELP ME", "HOSPITAL", "DOCTOR",
"MEDICINE", "SORRY", "EXCUSE ME", "INTERPRETER", "DEAF", "TEXT ME",
"WHERE IS", "HOW MUCH", "BATHROOM", "HOUSE", "SCHOOL", "MEET",
"LOVE", "WAIT A MOMENT", "FINISHED", "YES", "NO", "UNDERSTAND"
)
private val choseongTable = charArrayOf(
'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ',
'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
)
private fun extractChoseong(str: String): String {
val sb = StringBuilder()
for (ch in str) {
if (ch in '가'..'힣') {
val idx = (ch.code - 0xAC00) / (21 * 28)
sb.append(choseongTable[idx])
} else {
sb.append(ch)
}
}
return sb.toString()
}
fun getSuggestions(prefix: String, isAslMode: Boolean): List<String> {
val clean = prefix.trim()
if (isAslMode) {
if (clean.isEmpty()) {
return listOf("HELLO", "THANK YOU", "PLEASE", "HELP ME", "DEAF", "HOSPITAL")
}
val upper = clean.uppercase()
val matched = enDictionary.filter { it.startsWith(upper) }
return if (matched.isNotEmpty()) matched.take(6)
else listOf("HELLO", "THANK YOU", "PLEASE", "HELP ME")
} else {
if (clean.isEmpty()) {
return listOf("안녕하세요", "감사합니다", "도와주세요", "청각장애인입니다", "병원", "잠시만요")
}
val choPrefix = extractChoseong(clean)
val matched = koDictionary.filter { (word, cho) ->
word.startsWith(clean) || cho.startsWith(choPrefix)
}.map { it.first }
return if (matched.isNotEmpty()) matched.take(6)
else listOf("안녕하세요", "감사합니다", "도와주세요", "확인했습니다")
}
}
fun naturalizeSignGloss(rawInput: String, isAslMode: Boolean): String {
val text = rawInput.trim()
if (text.isEmpty()) return ""
if (isAslMode) {
var en = text
.replace(Regex("(?i)\\bME\\b"), "I")
.replace(Regex("(?i)\\bNEED HELP\\b"), "need help, please")
.replace(Regex("(?i)\\bHOSPITAL WHERE\\b"), "Where is the hospital?")
.replace(Regex("(?i)\\bBATHROOM WHERE\\b"), "Where is the bathroom?")
.replace(Regex("(?i)\\bDOCTOR NEED\\b"), "I need to see a doctor")
if (!en.endsWith(".") && !en.endsWith("?") && !en.endsWith("!")) {
en = "$en, please."
}
return en.replaceFirstChar { it.uppercase() } + " "
}
var result = text
.replace("나 ", "제가 ")
.replace("내 ", "제 ")
.replace("너 ", "선생님 ")
result = when {
result.endsWith("도와주다") -> result.removeSuffix("도와주다").trim() + " 도와주시면 감사하겠습니다."
result.endsWith("만나다") -> result.removeSuffix("만나다").trim() + " 만나서 반갑습니다."
result.endsWith("공부하다") -> result.removeSuffix("공부하다").trim() + " 공부하고 있습니다."
result.endsWith("일하다") -> result.removeSuffix("일하다").trim() + " 일하고 있습니다."
result.endsWith("기다리다") -> result.removeSuffix("기다리다").trim() + " 잠시만 기다려주세요."
result.endsWith("원하다") -> result.removeSuffix("원하다").trim() + "하고 싶습니다."
result.endsWith("필요") -> result.removeSuffix("필요").trim() + "이(가) 필요합니다."
result.endsWith("어디") -> result.removeSuffix("어디").trim() + "은(는) 어디에 있나요?"
result.endsWith("언제") -> result.removeSuffix("언제").trim() + "은(는) 언제인가요?"
result.endsWith("얼마") -> result.removeSuffix("얼마").trim() + "은(는) 얼마인가요?"
result.endsWith("아프다") || result.endsWith("아파") ->
result.replace(Regex("(아프다|아파)$"), "아파서 진료를 받고 싶습니다.")
result.endsWith("없다") -> result.removeSuffix("없다").trim() + "이(가) 없습니다."
result.endsWith("있다") -> result.removeSuffix("있다").trim() + "이(가) 있습니다."
result.endsWith("끝") -> result.removeSuffix("끝").trim() + " 완료되었습니다."
result.endsWith("같다") -> result.removeSuffix("같다").trim() + " 동일하게 부탁드립니다."
result.endsWith("예약") -> result + "하려고 합니다."
!result.endsWith(".") && !result.endsWith("?") && !result.endsWith("!") &&
!result.endsWith("요") && !result.endsWith("다") -> "$result 부탁드립니다."
else -> result
}
return "$result "
}
}
