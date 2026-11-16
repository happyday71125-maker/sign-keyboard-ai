package app.prolauncher.signkeyboard
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
class HandOverlayView @JvmOverloads constructor(
context: Context,
attrs: AttributeSet? = null
) : View(context, attrs) {
private var landmarks: List<SimplePoint3D> = emptyList()
private var recognizedLabel: String = "손을 비춰주세요"
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
private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = Color.parseColor("#38BDF8")
strokeWidth = 5.5f
style = Paint.Style.STROKE
}
private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
color = Color.parseColor("#FACC15")
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
textSize = 32f
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
newLandmarks: List<SimplePoint3D>,
label: String,
progress: Float,
pinching: Boolean
) {
this.landmarks = newLandmarks
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
override fun onDraw(canvas: Canvas) {
super.onDraw(canvas)
val w = width.toFloat()
val h = height.toFloat()
if (w <= 0f || h <= 0f) return
val now = SystemClock.uptimeMillis()
if (landmarks.size >= 21) {
for (conn in boneConnections) {
val a = landmarks[conn[0]]
val b = landmarks[conn[1]]
canvas.drawLine(
a.x * w, a.y * h,
b.x * w, b.y * h,
bonePaint
)
}
for (i in landmarks.indices) {
val pt = landmarks[i]
val cx = pt.x * w
val cy = pt.y * h
val radius = if (i == 4 || i == 8) 10.5f else 6.5f
canvas.drawCircle(cx, cy, radius, jointPaint)
}
val midX = (landmarks[4].x + landmarks[8].x) * 0.5f * w
val midY = (landmarks[4].y + landmarks[8].y) * 0.5f * h
if (isPinching) {
canvas.drawCircle(midX, midY, 30f, pinchRingPaint)
} else if (dwellProgress > 0.05f) {
arcRect.set(midX - 30f, midY - 30f, midX + 30f, midY + 30f)
canvas.drawArc(arcRect, -90f, 360f * dwellProgress, false, progressArcPaint)
}
}
val displayStr = if (now < flashUntilUptimeMs) {
borderRect.set(7f, 7f, w - 7f, h - 7f)
canvas.drawRoundRect(borderRect, 18f, 18f, flashBorderPaint)
lastCommittedBadge
} else {
recognizedLabel
}
val textWidth = badgeTextPaint.measureText(displayStr)
val padH = 22f
val boxW = (textWidth + padH * 2).coerceAtMost(w - 24f)
badgeRect.set(12f, 12f, 12f + boxW, 66f)
canvas.drawRoundRect(badgeRect, 14f, 14f, badgeBgPaint)
canvas.drawText(displayStr, 12f + padH, 49f, badgeTextPaint)
}
}
