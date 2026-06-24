package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.util.TypedValue
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

object WidgetColumnRenderer {

    data class WidgetSize(val widthPx: Int, val heightPx: Int)

    internal const val CONTENT_TOP = 80f
    internal const val LEFT_PADDING = 5f
    internal const val COL_WIDTH_PADDING = 10f
    internal const val EMPTY_COLUMN_WEIGHT_FACTOR = 0.35f
    private const val DEFAULT_BG_COLOR = 0x4D000000.toInt()
    internal const val DAY_MILLIS = 24L * 60 * 60 * 1000
    private const val TAG = "CalendarImageGenerator"

    internal data class ColumnRenderConfig(
        val pastEventScaleFactor: Float,
        val useCompressDeclinedMaxLines: Boolean,
        val useTimeScaleForPast: Boolean,
        val widgetTag: String
    )

    internal fun renderDayColumns(
        canvas: Canvas,
        width: Int,
        height: Int,
        paints: WidgetRenderingHelper.PaintBundle,
        settings: PrefsManager.WidgetSettings,
        events: List<CalendarEvent>,
        weights: FloatArray,
        todayIndex: Int,
        dayMillisList: List<Long>,
        config: ColumnRenderConfig,
        timeFormat: java.text.DateFormat,
        now: Long,
        topOffset: Float = 0f,
        drawHeader: (colIndex: Int, dayMillis: Long, isToday: Boolean, currentX: Float, colWidth: Float, top: Float) -> Unit
    ) {
        var totalWeight = 0f
        for (weight in weights) totalWeight += weight

        Log.d(TAG, "--- renderDayColumns (top=$topOffset, tag=${config.widgetTag}) ---")
        Log.d(TAG, "Total Row Weight: $totalWeight, Weights: ${weights.joinToString()}")

        val eventsByDay = dayMillisList.map { dayMillis ->
            val dayEnd = dayMillis + DAY_MILLIS
            events.filter { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }
        }

        var currentX = 0f

        val actualTodayMillis = startOfDayMillis()

        for (i in dayMillisList.indices) {
            val colWidth = (width * (weights[i] / totalWeight))
            val dayMillis = dayMillisList[i]
            val isToday = (dayMillis == actualTodayMillis)
            Log.d(TAG, "Col $i: width=$colWidth weight=${weights[i]} isToday=$isToday day=${SimpleDateFormat("EEE d", Locale.getDefault()).format(dayMillis)}")

            if (i > 0) {
                canvas.drawLine(currentX, topOffset, currentX, topOffset + height.toFloat(), paints.linePaint)
            }

            drawHeader(i, dayMillis, isToday, currentX, colWidth, topOffset)

            val dayEvents = eventsByDay[i]
            val textWidth = (colWidth - COL_WIDTH_PADDING).toInt()
            val availableHeight = height - CONTENT_TOP

            if (textWidth > 0) {
                val (optimalFontScale, compressDeclined) = WidgetRenderingHelper.calculateOptimalFontScale(
                    dayEvents = dayEvents,
                    textWidth = textWidth,
                    availableHeight = availableHeight,
                    basePaint = paints.textPaint,
                    settings = settings,
                    timeFormat = timeFormat,
                    currentTimeMillis = now,
                    todayIndex = todayIndex,
                    currentDayIndex = i,
                    pastEventScaleFactor = config.pastEventScaleFactor
                )

                renderColumnEvents(
                    canvas, dayEvents, optimalFontScale, compressDeclined,
                    paints, settings, timeFormat, currentX, colWidth, textWidth, height,
                    todayIndex, i, now, config, topOffset
                )
            }

            currentX += colWidth
        }
    }

    private fun renderColumnEvents(
        canvas: Canvas,
        dayEvents: List<CalendarEvent>,
        optimalFontScale: Float,
        compressDeclined: Boolean,
        paints: WidgetRenderingHelper.PaintBundle,
        settings: PrefsManager.WidgetSettings,
        timeFormat: java.text.DateFormat,
        currentX: Float,
        colWidth: Float,
        textWidth: Int,
        height: Int,
        todayIndex: Int,
        dayIndex: Int,
        now: Long,
        config: ColumnRenderConfig,
        topOffset: Float = 0f
    ) {
        var yPos = topOffset + CONTENT_TOP
        val isTodayColumn = dayIndex == todayIndex
        val hasFutureEvents = dayEvents.any { it.endTime >= now }
        val verboseDebug = Log.isLoggable(TAG, Log.VERBOSE)

        for (event in dayEvents) {
            canvas.save()
            canvas.clipRect(currentX, topOffset + CONTENT_TOP, currentX + colWidth, topOffset + height.toFloat())

            paints.textPaint.color = settings.textColor

            val isPastTodayEvent = isTodayColumn && event.endTime < now
            val decision = WidgetRenderingHelper.decideEventLayout(
                event = event,
                optimalScale = optimalFontScale,
                isPastTodayEvent = isPastTodayEvent,
                hasFutureEvents = hasFutureEvents,
                compressDeclined = compressDeclined,
                pastEventScaleFactor = config.pastEventScaleFactor,
                useTimeScaleForPast = config.useTimeScaleForPast
            )

            if (verboseDebug) {
                Log.v(
                    TAG,
                    "${config.widgetTag} Event: '${event.title}', status=${event.selfStatus}, declined=${event.isDeclined}, lessInteresting=${decision.isLessInteresting}"
                )
            }

            paints.textPaint.textSize = WidgetRenderingHelper.BASE_TEXT_SIZE * decision.eventScale

            val dynamicMaxLines = when {
                decision.forceOneLine -> 1
                config.useCompressDeclinedMaxLines && compressDeclined -> 3
                else -> 0
            }

            val finalText = WidgetRenderingHelper.buildEventText(
                event, true, settings, timeFormat, decision.timeScale
            )

            try {
                val builder = android.text.StaticLayout.Builder.obtain(
                    finalText, 0, finalText.length, paints.textPaint, textWidth
                )
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)

                if (dynamicMaxLines > 0) {
                    builder.setMaxLines(dynamicMaxLines)
                    builder.setEllipsize(android.text.TextUtils.TruncateAt.END)
                } else {
                    builder.setEllipsize(null)
                }

                val layout = builder.build()

                canvas.translate(currentX + LEFT_PADDING, yPos)
                layout.draw(canvas)

                val consumedHeight = layout.height.toFloat() +
                        WidgetRenderingHelper.eventSpacing(paints.textPaint.textSize, isTodayColumn)
                yPos += consumedHeight
            } catch (e: Exception) {
                Log.e(TAG, "StaticLayout crash: title='${event.title}', width=$textWidth", e)
            }

            canvas.restore()

            if (yPos > topOffset + height) break
        }
    }

    @VisibleForTesting
    internal fun computeAdjustedWeights(
        events: List<CalendarEvent>,
        weights: FloatArray,
        dayMillisList: List<Long>
    ): FloatArray {
        val adjusted = weights.copyOf()
        for (i in dayMillisList.indices) {
            val dayMillis = dayMillisList[i]
            // +DAY_MILLIS is safe here: this is event-membership filtering (does this day have
            // any events?), not day-counting. Even across a DST transition the boundary is close
            // enough for the "has events" check.
            val dayEnd = dayMillis + DAY_MILLIS
            val hasEvents = events.any { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }
            if (!hasEvents) {
                adjusted[i] *= EMPTY_COLUMN_WEIGHT_FACTOR
            }
        }
        return adjusted
    }

    /**
     * Like [computeAdjustedWeights], but decides the empty-column shrink across ALL stacked
     * weeks so both bands share one width profile and their vertical separators line up.
     *
     * A weekday column is only narrowed (×[EMPTY_COLUMN_WEIGHT_FACTOR]) when it is event-empty
     * in *every* week — that keeps the space-saving compaction for genuinely empty weekdays
     * while never producing a per-row width that breaks alignment.
     *
     * @param baseWeights the shared 7-column profile (e.g. from [WeeklyDisplayLogic.getColumnWeights])
     * @param allDayMillis week-major list of size 7 * [weeks]
     * For [weeks] == 1 this is identical to [computeAdjustedWeights].
     */
    @VisibleForTesting
    internal fun computeAlignedAdjustedWeights(
        events: List<CalendarEvent>,
        baseWeights: FloatArray,
        allDayMillis: List<Long>,
        weeks: Int
    ): FloatArray {
        val adjusted = baseWeights.copyOf()
        for (i in 0 until 7) {
            val anyWeekHasEvents = (0 until weeks).any { w ->
                val dayMillis = allDayMillis[w * 7 + i]
                val dayEnd = dayMillis + DAY_MILLIS
                events.any { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }
            }
            if (!anyWeekHasEvents) {
                adjusted[i] *= EMPTY_COLUMN_WEIGHT_FACTOR
            }
        }
        return adjusted
    }

    internal fun verticallyCenterBaseline(paint: Paint, centerY: Float): Float {
        val fm = paint.fontMetrics
        return centerY - (fm.ascent + fm.descent) / 2f
    }

    internal fun drawBackground(canvas: Canvas, settings: PrefsManager.WidgetSettings) {
        if (settings.bgColor != Color.TRANSPARENT) {
            canvas.drawColor(settings.bgColor)
        } else {
            canvas.drawColor(DEFAULT_BG_COLOR)
        }
    }

    internal fun startOfDayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    internal fun daysBetween(fromMillis: Long, toMillis: Long): Int {
        val zone = ZoneId.systemDefault()
        val from = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val to = Instant.ofEpochMilli(toMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(from, to).toInt()
    }

    internal fun fetchAndFilterEvents(
        context: Context,
        settings: PrefsManager.WidgetSettings,
        startMillis: Long,
        days: Int
    ): List<CalendarEvent> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "fetchAndFilterEvents permission: $hasPermission")
        if (!hasPermission) return emptyList()

        val repo = CalendarRepository(context)
        val selectedIds = settings.selectedCalendarIds.ifEmpty { repo.getDefaultCalendarIds() }
        Log.d(TAG, "fetchAndFilterEvents selectedIds: $selectedIds")

        var events = repo.getEvents(startMillis, days, selectedIds.ifEmpty { null })
        Log.d(TAG, "fetchAndFilterEvents rawCount: ${events.size}")

        if (!settings.showDeclinedEvents) {
            val countBefore = events.size
            events = events.filter { !it.isDeclined }
            Log.d(TAG, "fetchAndFilterEvents afterDeclinedFilter: ${events.size} (removed ${countBefore - events.size})")
        }

        val filtered = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())
        Log.d(TAG, "fetchAndFilterEvents finalCount: ${filtered.size}")
        return filtered
    }

    internal fun calculateWidgetSize(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ): WidgetSize {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val dm = context.resources.displayMetrics

        var widthDp = 250f
        var heightDp = 110f
        var usedAppWidgetSizes = false

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<android.util.SizeF>("appWidgetSizes")
            if (!sizes.isNullOrEmpty()) {
                val portraitSize = sizes[0]
                widthDp = portraitSize.width
                heightDp = portraitSize.height
                usedAppWidgetSizes = true
                Log.d(TAG, "Widget $appWidgetId: Using appWidgetSizes[0] = ${widthDp}x${heightDp}dp")
            }
        }

        if (!usedAppWidgetSizes) {
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeightDp)
            widthDp = minWidthDp.toFloat()
            heightDp = maxHeightDp.toFloat()
            Log.d(TAG, "Widget $appWidgetId: Fallback to min/max = ${widthDp}x${heightDp}dp")
        }

        val widthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            widthDp,
            dm
        ).toInt().coerceAtLeast(1)

        val heightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            heightDp,
            dm
        ).toInt().coerceAtLeast(1)

        Log.d(TAG, "Widget $appWidgetId: Final size = ${widthPx}x${heightPx}px")

        return WidgetSize(widthPx, heightPx)
    }

    internal fun calculateStartDate(calendar: Calendar): Long {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    internal fun drawPermissionRequired(canvas: Canvas, size: WidgetSize, settings: PrefsManager.WidgetSettings, context: Context) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = settings.textColor
            textAlign = Paint.Align.CENTER
            textSize = 14f * canvas.density
        }
        val line1 = context.getString(R.string.permission_required)
        val line2 = context.getString(R.string.permission_tap_to_grant)
        val lines = listOf(line1, line2)
        val yStart = size.heightPx / 2f - (lines.size - 1) * paint.textSize / 2f
        lines.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                size.widthPx / 2f,
                yStart + index * paint.textSize,
                paint
            )
        }
    }

    internal fun createErrorBitmap(e: Exception, width: Int, height: Int, context: Context): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val errorBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(errorBitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            textSize = (minOf(w, h) / 8f).coerceIn(12f, 40f)
            textAlign = Paint.Align.CENTER
        }
        val message = context.getString(R.string.error_prefix, e.localizedMessage ?: "Unknown")
        c.drawText(message, w / 2f, h / 2f, p)
        return errorBitmap
    }

    internal fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }
}
