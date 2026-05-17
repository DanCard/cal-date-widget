package ai.dcar.caldatewidget

import android.graphics.Color
import android.graphics.Paint
import android.provider.CalendarContract
import android.text.SpannableString
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.LruCache
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WidgetRenderingHelper {

    data class PaintBundle(
        val textPaint: android.text.TextPaint,
        val dayHeaderPaint: Paint,
        val linePaint: Paint
    )

    // Cache key for font scaling
    private data class FontScaleCacheKey(
        val eventHash: Int,
        val textWidth: Int,
        val availableHeight: Float,
        val settingsHash: Int,
        val isToday: Boolean,
        val currentTimeWindow: Long // Bucket time to ~1 minute to invalidate past/future status
    )

    // LRU Cache: Store result (optimalScale, compressDeclined)
    // Size 100 should be enough for a few widgets * 7 days
    private val fontScaleCache = LruCache<FontScaleCacheKey, Pair<Float, Boolean>>(100)

    const val BASE_TEXT_SIZE = 48f

    data class EventLayoutDecision(
        val eventScale: Float,
        val forceOneLine: Boolean,
        val timeScale: Float,
        val isLessInteresting: Boolean
    )

    fun isLessInteresting(event: CalendarEvent): Boolean {
        return event.selfStatus == CalendarContract.Attendees.ATTENDEE_STATUS_INVITED ||
                event.selfStatus == CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE ||
                event.isDeclined
    }

    fun decideEventLayout(
        event: CalendarEvent,
        optimalScale: Float,
        isPastTodayEvent: Boolean,
        hasFutureEvents: Boolean,
        compressDeclined: Boolean,
        pastEventScaleFactor: Float,
        useTimeScaleForPast: Boolean = true
    ): EventLayoutDecision {
        val isLessInteresting = isLessInteresting(event)
        var scale = when {
            isPastTodayEvent -> optimalScale * pastEventScaleFactor
            isLessInteresting -> {
                val invScale = (0.8f - 0.2f * optimalScale).coerceIn(0.5f, 0.7f)
                optimalScale * invScale
            }
            else -> optimalScale
        }
        val minScale = if (isLessInteresting) 0.5f else 0.7f
        if (scale < minScale) scale = minScale

        val forceOneLine = (isPastTodayEvent && hasFutureEvents) ||
                (compressDeclined && isLessInteresting)
        val timeScale = if (useTimeScaleForPast && isPastTodayEvent) 0.5f else 1f
        return EventLayoutDecision(scale, forceOneLine, timeScale, isLessInteresting)
    }

    fun eventSpacing(textSize: Float, isToday: Boolean): Float =
        textSize * if (isToday) 0.1f else 0.2f

    fun createPaints(settings: PrefsManager.WidgetSettings): PaintBundle {
        val textPaint = android.text.TextPaint().apply {
            color = settings.textColor
            textSize = 48f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
            setShadowLayer(6f, 3f, 3f, settings.shadowColor)
        }
        val dayHeaderPaint = Paint().apply {
            color = Color.LTGRAY
            textSize = 30f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
        }
        return PaintBundle(textPaint, dayHeaderPaint, linePaint)
    }

    fun buildEventText(
        event: CalendarEvent,
        includeStart: Boolean,
        settings: PrefsManager.WidgetSettings,
        timeFormat: java.text.DateFormat,
        timeScale: Float = 1f
    ): CharSequence {
        val safeTitle = event.title
            .replace("\u2026", "")
            .replace("...", "")
        if (includeStart && !event.isAllDay) {
            val timeString = if (settings.showAmPm) {
                timeFormat.format(Date(event.startTime))
            } else {
                SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(event.startTime))
            }
            val spannable = SpannableString("$timeString $safeTitle")

            spannable.setSpan(
                ForegroundColorSpan(settings.startTimeColor),
                0,
                timeString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            spannable.setSpan(object : CharacterStyle() {
                override fun updateDrawState(tp: android.text.TextPaint) {
                    tp.setShadowLayer(6f, 3f, 3f, settings.startTimeShadowColor)
                }
            }, 0, timeString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            if (timeScale != 1f) {
                spannable.setSpan(
                    android.text.style.RelativeSizeSpan(timeScale),
                    0,
                    timeString.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            if (event.isDeclined) {
                spannable.setSpan(
                    android.text.style.StrikethroughSpan(),
                    0,
                    spannable.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            return spannable
        }
        if (event.isDeclined) {
            val spannable = SpannableString(safeTitle)
            spannable.setSpan(
                android.text.style.StrikethroughSpan(),
                0,
                spannable.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            return spannable
        }
        return safeTitle
    }

    fun calculateOptimalFontScale(
        dayEvents: List<CalendarEvent>,
        textWidth: Int,
        availableHeight: Float,
        basePaint: android.text.TextPaint,
        settings: PrefsManager.WidgetSettings,
        timeFormat: java.text.DateFormat,
        currentTimeMillis: Long,
        todayIndex: Int,
        currentDayIndex: Int,
        pastEventScaleFactor: Float
    ): Pair<Float, Boolean> {
        if (dayEvents.isEmpty() || textWidth <= 0 || availableHeight <= 0f) {
            return Pair(0.5f, false)
        }

        // Generate Cache Key
        // Bucket time to 1 minute (60000ms) to ensure cache validity for "past/future" status
        // which drives the 'isPastTodayEvent' logic.
        val timeWindow = currentTimeMillis / 60000 
        val eventHash = dayEvents.hashCode() // Depends on List implementation, usually good
        val settingsHash = settings.hashCode()
        val isToday = (todayIndex == currentDayIndex)

        val cacheKey = FontScaleCacheKey(
            eventHash,
            textWidth,
            availableHeight,
            settingsHash,
            isToday,
            timeWindow
        )

        fontScaleCache.get(cacheKey)?.let {
            // Cache Hit
            return it
        }

        // Cache Miss - Calculate
        // Function to run binary search
        fun findScale(compressDeclined: Boolean): Float {
            var minScale = 0.7f
            var maxScale = 1.5f
            var optimalScale = -1.0f // Sentinel

            for (iteration in 0 until 10) {
                val midScale = (minScale + maxScale) / 2f
                val totalHeight = measureTotalHeightForScale(
                    midScale,
                    dayEvents,
                    textWidth,
                    basePaint,
                    settings,
                    timeFormat,
                    currentTimeMillis,
                    todayIndex,
                    currentDayIndex,
                    compressDeclined,
                    pastEventScaleFactor
                )

                if (totalHeight <= availableHeight) {
                    minScale = midScale
                    optimalScale = midScale
                } else {
                    maxScale = midScale
                }
            }
            return optimalScale
        }

        val normalScale = findScale(false)
        val result = if (normalScale != -1.0f) {
            Pair(normalScale, false)
        } else {
            val compressedScale = findScale(true)
            if (compressedScale != -1.0f) Pair(compressedScale, true) else Pair(0.7f, true)
        }

        // Save to Cache
        fontScaleCache.put(cacheKey, result)
        return result
    }

    fun measureTotalHeightForScale(
        scale: Float,
        dayEvents: List<CalendarEvent>,
        textWidth: Int,
        basePaint: android.text.TextPaint,
        settings: PrefsManager.WidgetSettings,
        timeFormat: java.text.DateFormat,
        currentTimeMillis: Long,
        todayIndex: Int,
        currentDayIndex: Int,
        compressDeclined: Boolean,
        pastEventScaleFactor: Float
    ): Float {
        val isTodayColumn = currentDayIndex == todayIndex
        val hasFutureEvents = dayEvents.any { it.endTime >= currentTimeMillis }
        var totalHeight = 0f

        for (event in dayEvents) {
            val isPastTodayEvent = isTodayColumn && event.endTime < currentTimeMillis
            val decision = decideEventLayout(
                event = event,
                optimalScale = scale,
                isPastTodayEvent = isPastTodayEvent,
                hasFutureEvents = hasFutureEvents,
                compressDeclined = compressDeclined,
                pastEventScaleFactor = pastEventScaleFactor
            )

            val measurePaint = android.text.TextPaint(basePaint)
            measurePaint.textSize = BASE_TEXT_SIZE * decision.eventScale

            val eventText = buildEventText(event, true, settings, timeFormat, decision.timeScale)
            val layout = android.text.StaticLayout.Builder.obtain(
                eventText, 0, eventText.length, measurePaint, textWidth
            )
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .setMaxLines(if (decision.forceOneLine) 1 else 10)
                .setEllipsize(if (decision.forceOneLine) android.text.TextUtils.TruncateAt.END else null)
                .build()

            if (!decision.forceOneLine) {
                val safeTitle = event.title
                    .replace("\u2026", "")
                    .replace("...", "")
                val lineLimit = when {
                    safeTitle.length < 15 -> 3
                    safeTitle.length < 20 -> 4
                    safeTitle.length < 25 -> 5
                    safeTitle.length < 30 -> 6
                    safeTitle.length < 35 -> 7
                    else -> 8
                }
                if (layout.lineCount > lineLimit) {
                    return Float.MAX_VALUE
                }
            }

            totalHeight += layout.height + eventSpacing(measurePaint.textSize, isTodayColumn)
        }
        return totalHeight
    }

    fun logCurrentDayDebug(
        tag: String,
        dayEvents: List<CalendarEvent>,
        pastEventsOnToday: List<CalendarEvent>,
        currentFutureEvents: List<CalendarEvent>,
        availableHeight: Float,
        measuredHeight: Float,
        optimalScale: Float,
        currentTimeMillis: Long,
        estimatedLineHeight: Float,
        compressDeclined: Boolean
    ) {
        Log.d(tag, "=== Current Day Debug ===")
        Log.d(tag, "Total events: ${dayEvents.size}, Past events: ${pastEventsOnToday.size}, Future events: ${currentFutureEvents.size}")
        Log.d(tag, "Available height: $availableHeight, Est line height: $estimatedLineHeight")
        Log.d(tag, "Measured height: $measuredHeight, Optimal scale: $optimalScale (Compressed: $compressDeclined)")
        Log.d(tag, "Current time: ${SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT).format(Date(currentTimeMillis))}")
    }
}
