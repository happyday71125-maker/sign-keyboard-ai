package app.prolauncher.signkeyboard
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
class HandOverlayView @JvmOverloads constructor(
context: Context,
attrs: AttributeSet? = null
) : View(context, attrs) {
private var primaryLandmarks: List<SimplePoint3D> = emptyList()
private var secondaryLandmarks: List<SimplePoint3D> = emptyList()
private var recognizedLabel: String = "한 손 또는 양손을 비춰주세요"
private var dwellProgress: Float = 0f
private var isPinching: Boolean = false
private var flashUntilUptimeMs: Long = 0L
private var lastCommittedBadge: String = ""
private val boneConnections = arrayOf(
intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 4),
intArrayOf(0, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 8),
intArrayOf(5, 9), intArrayOf(9, 10), intArrayOf(10, 11), intArrayOf(11, 12),
intArrayOf(9, 13), intArrayOf(13, 14), intArrayOf(14, 15), intArrayOf(15, 16),
intArrayOf(13, 17), intArrayOf(17, 18), intArrayOf(18, 19), intArrayOf(19, 20),
intArrayOf(0, 17)
)
private val primaryBonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = Color.parseColor("#38BDF8")
strokeWidth = 5.5f
style = Paint.Style.STROKE
}
private val secondaryBonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = Color.parseColor("#C084FC")
strokeWidth = 5.5f
style = Paint.Style.STROKE
}
private val twoHandBridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = Color.parseColor("#34D399")
strokeWidth = 4f
style = Paint.Style.STROKE
pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
}
private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = Color.parseColor("#FACC15")
style = Paint.Style.FILL
}
private val secondJointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = Color.parseColor("#F472B6")
style = Paint.Style.FILL
}
private val pinchRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = Color.parseColor("#22C55E")
strokeWidth = 7.5f
style = Paint.Style.STROKE
}
private val flashBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = Color.parseColor("#10B981")
strokeWidth = 14f
style = Paint.Style.STROKE
}
private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = Color.parseColor("#CC0F172A")
style = Paint.Style.FILL
}
private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = Color.WHITE
textSize = 30f
isFakeBoldText = true
}
private val progressArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = Color.parseColor("#10B981")
strokeWidth = 8.5f
style = Paint.Style.STROKE
}
private val arcRect = RectF()
private val badgeRect = RectF()
private val borderRect = RectF()
fun updateState(
primary: List<SimplePoint3D>,
secondary: List<SimplePoint3D>,
label: String,
progress: Float,
pinching: Boolean
) {
this.primaryLandmarks = primary
this.secondaryLandmarks = secondary
this.recognizedLabel = label
this.dwellProgress = progress.coerceIn(0f, 1f)
this.isPinching = pinching
postInvalidateOnAnimation()
}
fun triggerVisualFlash(committedText: String) {
this.flashUntilUptimeMs = SystemClock.uptimeMillis() + 260L
this.lastCommittedBadge = "✓ 입력됨: $committedText"
postInvalidateOnAnimation()
}
private fun drawHandSkeleton(
canvas: Canvas,
pts: List<SimplePoint3D>,
w: Float,
h: Float,
boneP: Paint,
jointP: Paint
) {
if (pts.size < 21) return
for (conn in boneConnections) {
val a = pts[conn[0]]
val b = pts[conn[1]]
canvas.drawLine(a.x * w, a.y * h, b.x * w, b.y * h, boneP)
}
for (i in pts.indices) {
val pt = pts[i]
val radius = if (i == 4 || i == 8) 10f else 6f
canvas.drawCircle(pt.x * w, pt.y * h, radius, jointP)
}
}
override fun onDraw(canvas: Canvas) {
super.onDraw(canvas)
val w = width.toFloat()
val h = height.toFloat()
if (w <= 0f || h <= 0f) return
val now = SystemClock.uptimeMillis()
if (primaryLandmarks.size >= 21) {
drawHandSkeleton(canvas, primaryLandmarks, w, h, primaryBonePaint, jointPaint)
}
if (secondaryLandmarks.size >= 21) {
drawHandSkeleton(canvas, secondaryLandmarks, w, h, secondaryBonePaint, secondJointPaint)
if (primaryLandmarks.size >= 21) {
canvas.drawLine(
primaryLandmarks[9].x * w, primaryLandmarks[9].y * h,
secondaryLandmarks[9].x * w, secondaryLandmarks[9].y * h,
twoHandBridgePaint
)
}
}
if (primaryLandmarks.size >= 21) {
val midX = if (secondaryLandmarks.size >= 21) {
(primaryLandmarks[9].x + secondaryLandmarks[9].x) * 0.5f * w
} else {
(primaryLandmarks[4].x + primaryLandmarks[8].x) * 0.5f * w
}
val midY = if (secondaryLandmarks.size >= 21) {
(primaryLandmarks[9].y + secondaryLandmarks[9].y) * 0.5f * h
} else {
(primaryLandmarks[4].y + primaryLandmarks[8].y) * 0.5f * h
}
if (isPinching) {
canvas.drawCircle(midX, midY, 30f, pinchRingPaint)
} else if (dwellProgress > 0.05f) {
arcRect.set(midX - 30f, midY - 30f, midX + 30f, midY + 30f)
canvas.drawArc(arcRect, -90f, 360f * dwellProgress, false, progressArcPaint)
}
}
val handCountTag = when {
primaryLandmarks.size >= 21 && secondaryLandmarks.size >= 21 -> "[양손 인식] "
primaryLandmarks.size >= 21 -> "[한손 인식] "
else -> ""
}
val displayStr = if (now < flashUntilUptimeMs) {
borderRect.set(7f, 7f, w - 7f, h - 7f)
canvas.drawRoundRect(borderRect, 18f, 18f, flashBorderPaint)
lastCommittedBadge
} else {
handCountTag + recognizedLabel
}
val textWidth = badgeTextPaint.measureText(displayStr)
val padH = 20f
val boxW = (textWidth + padH * 2).coerceAtMost(w - 20f)
badgeRect.set(10f, 10f, 10f + boxW, 64f)
canvas.drawRoundRect(badgeRect, 14f, 14f, badgeBgPaint)
canvas.drawText(displayStr, 10f + padH, 47f, badgeTextPaint)
}
}
