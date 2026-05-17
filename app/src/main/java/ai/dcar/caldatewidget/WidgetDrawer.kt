package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.content.pm.PackageManager
import android.util.Log
import android.util.TypedValue
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object WidgetDrawer {

    data class WidgetSize(val widthPx: Int, val heightPx: Int)
    internal data class TomorrowIndicatorHeaderLayout(
        val dayName: String,
        val indicator: String,
        val rainbowTextSize: Float,
        val spacing: Float
    )

    internal const val TOMORROW_INDICATOR = "🌈"

    private const val CONTENT_TOP = 80f
    private const val BASE_TEXT_SIZE = 48f
    private const val LEFT_PADDING = 5f
    private const val COL_WIDTH_PADDING = 10f
    private const val EMPTY_COLUMN_WEIGHT_FACTOR = 0.65f
    private const val DEFAULT_BG_COLOR = 0x4D000000.toInt()
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    internal data class ColumnRenderConfig(
        val pastEventScaleFactor: Float,
        val useCompressDeclinedMaxLines: Boolean,
        val useTimeScaleForPast: Boolean,
        val widgetTag: String
    )

    fun drawWeeklyCalendar(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Bitmap {
        val size = calculateWidgetSize(context, appWidgetManager, appWidgetId)
        try {
            Log.d("WidgetDrawer", "Starting drawWeeklyCalendar for ID: $appWidgetId")
            val prefsManager = PrefsManager(context)
            val settings = prefsManager.loadSettings(appWidgetId)

            val bitmap = Bitmap.createBitmap(size.widthPx, size.heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            drawBackground(canvas, settings)
            val paints = WidgetRenderingHelper.createPaints(settings)

            val todayMillis = startOfDayMillis()
            val startMillis = WeeklyDisplayLogic.getStartDate(Calendar.getInstance(), settings.weekStartDay)

            val events = fetchFilteredEvents(context, settings, todayMillis, 7)

            val todayIndex = ((todayMillis - startMillis) / DAY_MILLIS).toInt().coerceIn(0, 6)

            val numDays = 7
            val dayMillisList = (0 until numDays).map { i ->
                WeeklyDisplayLogic.getEffectiveDayMillis(startMillis, i, todayIndex)
            }

            val weights = computeAdjustedWeights(
                events,
                WeeklyDisplayLogic.getColumnWeights(todayIndex, numDays),
                dayMillisList
            )

            val config = ColumnRenderConfig(
                pastEventScaleFactor = 0.7f,
                useCompressDeclinedMaxLines = true,
                useTimeScaleForPast = true,
                widgetTag = "Weekly"
            )

            val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)
            val now = System.currentTimeMillis()

            renderDayColumns(
                canvas, size.widthPx, size.heightPx, paints, settings, events,
                weights, todayIndex, dayMillisList, config, timeFormat, now
            ) { _, dayMillis, isToday, currentX, colWidth ->
                if (isToday) {
                    paints.dayHeaderPaint.color = Color.YELLOW
                    paints.dayHeaderPaint.isFakeBoldText = true
                } else {
                    paints.dayHeaderPaint.color = Color.LTGRAY
                    paints.dayHeaderPaint.isFakeBoldText = false
                }
                paints.dayHeaderPaint.textSize = WeeklyDisplayLogic.getHeaderTextSize(colWidth, isToday)

                val header = WeeklyDisplayLogic.chooseHeaderText(colWidth, dayMillis) { text ->
                    paints.dayHeaderPaint.measureText(text)
                }
                paints.dayHeaderPaint.textSize *= header.scale

                val fm = paints.dayHeaderPaint.fontMetrics
                val headerBaseline = (CONTENT_TOP / 2f) - (fm.ascent + fm.descent) / 2f
                canvas.drawText(header.text, currentX + colWidth / 2, headerBaseline, paints.dayHeaderPaint)
            }

            return bitmap
        } catch (e: Exception) {
            return createErrorBitmap(e, size.widthPx, size.heightPx)
        }
    }

    fun drawDailyCalendar(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Bitmap {
        val size = calculateWidgetSize(context, appWidgetManager, appWidgetId)
        try {
            Log.d("WidgetDrawer", "Starting drawDailyCalendar for ID: $appWidgetId")
            val prefsManager = PrefsManager(context)
            val settings = prefsManager.loadSettings(appWidgetId)

            val bitmap = Bitmap.createBitmap(size.widthPx, size.heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            drawBackground(canvas, settings)
            val paints = WidgetRenderingHelper.createPaints(settings)

            val todayMillis = startOfDayMillis()

            var numDays = calculateNumberOfDays(context, size.widthPx, size.heightPx)

            var events = emptyList<CalendarEvent>()
            var didAutoAdvance = false
            var startCalendar = Calendar.getInstance()
            val hasPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            Log.d("WidgetDrawer", "Permission Check (Daily): $hasPermission")

            if (hasPermission) {
                val repo = CalendarRepository(context)
                val selectedIds = settings.selectedCalendarIds.ifEmpty { repo.getDefaultCalendarIds() }
                val selection = selectedIds.ifEmpty { null }

                val checkStart = calculateStartDate(startCalendar)
                val todayEnd = checkStart + DAY_MILLIS
                val todayEvents = repo.getEvents(checkStart, 1, selection).filter {
                    WeeklyDisplayLogic.shouldDisplayEventOnDay(it, checkStart, todayEnd)
                }
                val now = System.currentTimeMillis()

                if (DailyDisplayLogic.shouldAutoAdvance(todayEvents, now)) {
                     startCalendar.add(Calendar.DAY_OF_YEAR, 1)
                     didAutoAdvance = true
                     Log.d("WidgetDrawer", "All events for today are in the past. Auto-advancing to tomorrow.")
                } else {
                    Log.d(
                        "WidgetDrawer",
                        "Daily widget stays on today. now=$now todayEvents=${todayEvents.size} titles=${todayEvents.joinToString { it.title }}"
                    )
                }

                val startMillis = calculateStartDate(startCalendar)
                val maxFetchDays = (numDays + 1).coerceAtMost(7)
                events = repo.getEvents(startMillis, maxFetchDays, selection)
            }

            val startMillis = calculateStartDate(startCalendar)

            if (!settings.showDeclinedEvents) {
                events = events.filter { !it.isDeclined }
            }

            events = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

            val dm = context.resources.displayMetrics
            val heightDp = size.heightPx / dm.density
            val baselineDays = numDays

            if (heightDp >= 100f) {
                var emptyDays = 0
                var maxEventsOnAnyDay = 0

                for (i in 0 until baselineDays) {
                    val dayMillis = startMillis + (i * DAY_MILLIS)
                    val dayEnd = dayMillis + DAY_MILLIS
                    val dayEventCount = events.count { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }

                    if (dayEventCount == 0) emptyDays++
                    if (dayEventCount > maxEventsOnAnyDay) maxEventsOnAnyDay = dayEventCount
                }

                val maxPossibleDays = (baselineDays + 1).coerceAtMost(7)
                if (emptyDays >= 1 && maxEventsOnAnyDay <= 1 && baselineDays < maxPossibleDays) {
                    Log.d("WidgetSize", "Dynamic expansion: emptyDays=$emptyDays, maxEvents=$maxEventsOnAnyDay, heightDp=$heightDp. Expanding numDays from $baselineDays to $maxPossibleDays.")
                    numDays = maxPossibleDays
                }
            }

            val todayIndex = ((todayMillis - startMillis) / DAY_MILLIS).toInt().coerceIn(0, numDays - 1)

            val dayMillisList = (0 until numDays).map { i ->
                startMillis + (i * DAY_MILLIS)
            }

            val weights = computeAdjustedWeights(
                events,
                DailyDisplayLogic.getColumnWeights(todayIndex, numDays),
                dayMillisList
            )

            val config = ColumnRenderConfig(
                pastEventScaleFactor = 0.8f,
                useCompressDeclinedMaxLines = true,
                useTimeScaleForPast = true,
                widgetTag = "Daily"
            )

            val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)
            val now = System.currentTimeMillis()
            val dayFormatEEE = SimpleDateFormat("EEE", Locale.getDefault())
            val dayFormatEEEd = SimpleDateFormat("EEE d", Locale.getDefault())

            renderDayColumns(
                canvas, size.widthPx, size.heightPx, paints, settings, events,
                weights, todayIndex, dayMillisList, config, timeFormat, now
            ) { colIndex, dayMillis, isToday, currentX, colWidth ->
                val dayName = if (colIndex < todayIndex) {
                    dayFormatEEE.format(dayMillis)
                } else {
                    dayFormatEEEd.format(dayMillis)
                }

                val headerTextSize = if (isToday) {
                    colWidth * 0.3f
                } else {
                    colWidth * 0.25f
                }.coerceIn(30f, 70f)

                if (isToday) {
                    paints.dayHeaderPaint.color = Color.YELLOW
                    paints.dayHeaderPaint.isFakeBoldText = true
                    paints.dayHeaderPaint.textSize = headerTextSize
                } else {
                    paints.dayHeaderPaint.color = Color.LTGRAY
                    paints.dayHeaderPaint.isFakeBoldText = false
                    paints.dayHeaderPaint.textSize = headerTextSize
                }

                val fm = paints.dayHeaderPaint.fontMetrics
                val headerBaseline = (CONTENT_TOP / 2f) - (fm.ascent + fm.descent) / 2f
                val centerX = currentX + colWidth / 2f
                val shouldDrawIndicator = shouldDrawTomorrowIndicator(didAutoAdvance, colIndex, 0)
                if (shouldDrawIndicator) {
                    val indicatorDrawn = drawHeaderWithTomorrowIndicator(
                        canvas = canvas,
                        dayName = dayName,
                        centerX = centerX,
                        centerY = CONTENT_TOP / 2f,
                        colWidth = colWidth,
                        basePaint = paints.dayHeaderPaint
                    )
                    if (indicatorDrawn) {
                        Log.d("WidgetDrawer", "Drawing tomorrow indicator for daily widget $appWidgetId")
                    } else {
                        canvas.drawText(dayName, centerX, headerBaseline, paints.dayHeaderPaint)
                        Log.d("WidgetDrawer", "Skipped tomorrow indicator due to narrow column for widget $appWidgetId")
                    }
                } else {
                    canvas.drawText(dayName, centerX, headerBaseline, paints.dayHeaderPaint)
                }
            }

            return bitmap
        } catch (e: Exception) {
            Log.e("WidgetDrawer", "Error drawing daily widget", e)
            return createErrorBitmap(e, size.widthPx, size.heightPx)
        }
    }

    private fun renderDayColumns(
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
        drawHeader: (colIndex: Int, dayMillis: Long, isToday: Boolean, currentX: Float, colWidth: Float) -> Unit
    ) {
        var totalWeight = 0f
        for (weight in weights) totalWeight += weight

        val eventsByDay = dayMillisList.mapIndexed { _, dayMillis ->
            val dayEnd = dayMillis + DAY_MILLIS
            events.filter { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }
        }

        var currentX = 0f

        for (i in dayMillisList.indices) {
            val colWidth = (width * (weights[i] / totalWeight))

            if (i > 0) {
                canvas.drawLine(currentX, 0f, currentX, height.toFloat(), paints.linePaint)
            }

            val dayMillis = dayMillisList[i]
            val isToday = (i == todayIndex)

            drawHeader(i, dayMillis, isToday, currentX, colWidth)

            val dayEvents = eventsByDay[i]

            val textWidth = (colWidth - COL_WIDTH_PADDING).toInt()
            val availableHeight = height - CONTENT_TOP

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
                paints, settings, timeFormat, currentX, colWidth, height,
                todayIndex, i, now, config
            )

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
        height: Int,
        todayIndex: Int,
        dayIndex: Int,
        now: Long,
        config: ColumnRenderConfig
    ) {
        var yPos = CONTENT_TOP
        val hasFutureEvents = dayEvents.any { it.endTime >= now }

        for (event in dayEvents) {
            canvas.save()
            canvas.clipRect(currentX, CONTENT_TOP, currentX + colWidth, height.toFloat())

            paints.textPaint.color = settings.textColor

            val isLessInteresting = WidgetRenderingHelper.isLessInteresting(event)
            Log.d("WidgetDebug", "${config.widgetTag} Event: '${event.title}', status=${event.selfStatus}, declined=${event.isDeclined}, lessInteresting=$isLessInteresting")

            var eventScale = if (dayIndex == todayIndex && event.endTime < now) {
                optimalFontScale * config.pastEventScaleFactor
            } else if (isLessInteresting) {
                val invScale = (0.8f - 0.2f * optimalFontScale).coerceIn(0.5f, 0.7f)
                optimalFontScale * invScale
            } else {
                optimalFontScale
            }

            val minEventScale = if (isLessInteresting) 0.5f else 0.7f
            if (eventScale < minEventScale) eventScale = minEventScale

            paints.textPaint.textSize = BASE_TEXT_SIZE * eventScale

            val isPastTodayEvent = (dayIndex == todayIndex && event.endTime < now)
            val forceOneLine = (isPastTodayEvent && hasFutureEvents) || (compressDeclined && isLessInteresting)

            val dynamicMaxLines = if (forceOneLine) 1 else if (config.useCompressDeclinedMaxLines && compressDeclined) 3 else 0

            val textWidth = (colWidth - COL_WIDTH_PADDING).toInt()

            if (textWidth > 0) {
                val timeScale = if (config.useTimeScaleForPast && isPastTodayEvent) 0.5f else 1f
                val finalText = WidgetRenderingHelper.buildEventText(event, true, settings, timeFormat, timeScale)

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
                    canvas.translate(-(currentX + LEFT_PADDING), -yPos)

                    val spacing = if (dayIndex == todayIndex) paints.textPaint.textSize * 0.1f else paints.textPaint.textSize * 0.2f
                    val consumedHeight = layout.height.toFloat() + spacing
                    yPos += consumedHeight
                } catch (e: Exception) {
                    Log.e("WidgetDrawer", "StaticLayout crash: title='${event.title}', width=$textWidth", e)
                }
            }

            canvas.restore()
        }
    }

    private fun drawBackground(canvas: Canvas, settings: PrefsManager.WidgetSettings) {
        if (settings.bgColor != Color.TRANSPARENT) {
            canvas.drawColor(settings.bgColor)
        } else {
            canvas.drawColor(DEFAULT_BG_COLOR)
        }
    }

    private fun startOfDayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun fetchFilteredEvents(
        context: Context,
        settings: PrefsManager.WidgetSettings,
        startMillis: Long,
        days: Int
    ): List<CalendarEvent> {
        val hasPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return emptyList()

        val repo = CalendarRepository(context)
        val selectedIds = settings.selectedCalendarIds.ifEmpty { repo.getDefaultCalendarIds() }
        var events = repo.getEvents(startMillis, days, selectedIds.ifEmpty { null })

        if (!settings.showDeclinedEvents) {
            events = events.filter { !it.isDeclined }
        }

        return WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())
    }

    private fun computeAdjustedWeights(
        events: List<CalendarEvent>,
        weights: FloatArray,
        dayMillisList: List<Long>
    ): FloatArray {
        val adjusted = weights.copyOf()
        for (i in dayMillisList.indices) {
            val dayMillis = dayMillisList[i]
            val dayEnd = dayMillis + DAY_MILLIS
            val hasEvents = events.any { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }
            if (!hasEvents) {
                adjusted[i] *= EMPTY_COLUMN_WEIGHT_FACTOR
            }
        }
        return adjusted
    }

    private fun calculateWidgetSize(
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
                Log.d("WidgetSize", "Widget $appWidgetId: Using appWidgetSizes[0] = ${widthDp}x${heightDp}dp")
            }
        }

        if (!usedAppWidgetSizes) {
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeightDp)
            widthDp = minWidthDp.toFloat()
            heightDp = maxHeightDp.toFloat()
            Log.d("WidgetSize", "Widget $appWidgetId: Fallback to min/max = ${widthDp}x${heightDp}dp")
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

        Log.d("WidgetSize", "Widget $appWidgetId: Final size = ${widthPx}x${heightPx}px")

        return WidgetSize(widthPx, heightPx)
    }

    private fun calculateNumberOfDays(context: Context, widthPx: Int, @Suppress("UNUSED_PARAMETER") heightPx: Int): Int {
        val dm = context.resources.displayMetrics
        val widthDp = widthPx / dm.density

        val cellWidthDp = 92f
        val numDays = ((widthDp / cellWidthDp) + 0.5f).toInt().coerceIn(1, 7)
        Log.d("WidgetSize", "calculateNumberOfDays: px=$widthPx, density=${dm.density}, dp=$widthDp, cellWidth=$cellWidthDp -> numDays=$numDays")
        return numDays
    }

    @VisibleForTesting
    internal fun shouldDrawTomorrowIndicator(
        didAutoAdvance: Boolean,
        currentColumn: Int,
        firstVisibleColumn: Int
    ): Boolean {
        return didAutoAdvance && currentColumn == firstVisibleColumn
    }

    private fun drawHeaderWithTomorrowIndicator(
        canvas: Canvas,
        dayName: String,
        centerX: Float,
        centerY: Float,
        colWidth: Float,
        basePaint: Paint
    ): Boolean {
        val horizontalPadding = 10f
        val availableWidth = (colWidth - horizontalPadding).coerceAtLeast(1f)
        val baseTextWidth = basePaint.measureText(dayName)
        if (baseTextWidth >= availableWidth) {
            return false
        }

        val layout = buildTomorrowIndicatorHeaderLayout(
            dayName = dayName,
            dayNameWidth = baseTextWidth,
            baseTextSize = basePaint.textSize,
            availableWidth = availableWidth
        ) { candidateTextSize ->
            val rainbowPaint = Paint(basePaint).apply { textSize = candidateTextSize }
            rainbowPaint.measureText(TOMORROW_INDICATOR)
        } ?: return false

        val rainbowPaint = Paint(basePaint).apply { textSize = layout.rainbowTextSize }
        val rainbowWidth = rainbowPaint.measureText(layout.indicator)
        val combinedWidth = baseTextWidth + layout.spacing + rainbowWidth
        val leftX = centerX - (combinedWidth / 2f)
        val baseCenterX = leftX + (baseTextWidth / 2f)
        val rainbowCenterX = leftX + baseTextWidth + layout.spacing + (rainbowWidth / 2f)

        val baseFm = basePaint.fontMetrics
        val baseBaseline = centerY - (baseFm.ascent + baseFm.descent) / 2f
        val rainbowFm = rainbowPaint.fontMetrics
        val rainbowBaseline = centerY - (rainbowFm.ascent + rainbowFm.descent) / 2f

        canvas.drawText(dayName, baseCenterX, baseBaseline, basePaint)
        canvas.drawText(layout.indicator, rainbowCenterX, rainbowBaseline, rainbowPaint)
        return true
    }

    @VisibleForTesting
    internal fun drawHeaderWithTomorrowIndicatorForTest(
        canvas: Canvas,
        dayName: String,
        centerX: Float,
        centerY: Float,
        colWidth: Float,
        basePaint: Paint
    ): Boolean {
        return drawHeaderWithTomorrowIndicator(canvas, dayName, centerX, centerY, colWidth, basePaint)
    }

    internal fun calculateRainbowIndicatorTextSize(
        dayNameWidth: Float,
        baseTextSize: Float,
        availableWidth: Float,
        rainbowWidthAtTextSize: (Float) -> Float
    ): Float? {
        val spacing = (baseTextSize * 0.12f).coerceAtLeast(2f)
        var scale = 0.62f

        while (scale >= 0.35f) {
            val candidateTextSize = (baseTextSize * scale).coerceIn(18f, 44f)
            val rainbowWidth = rainbowWidthAtTextSize(candidateTextSize)
            val combinedWidth = dayNameWidth + spacing + rainbowWidth
            if (combinedWidth <= availableWidth) {
                return candidateTextSize
            }
            scale -= 0.05f
        }

        return null
    }

    @VisibleForTesting
    internal fun buildTomorrowIndicatorHeaderLayout(
        dayName: String,
        dayNameWidth: Float,
        baseTextSize: Float,
        availableWidth: Float,
        rainbowWidthAtTextSize: (Float) -> Float
    ): TomorrowIndicatorHeaderLayout? {
        val spacing = (baseTextSize * 0.12f).coerceAtLeast(2f)
        val rainbowTextSize = calculateRainbowIndicatorTextSize(
            dayNameWidth = dayNameWidth,
            baseTextSize = baseTextSize,
            availableWidth = availableWidth,
            rainbowWidthAtTextSize = rainbowWidthAtTextSize
        ) ?: return null

        return TomorrowIndicatorHeaderLayout(
            dayName = dayName,
            indicator = TOMORROW_INDICATOR,
            rainbowTextSize = rainbowTextSize,
            spacing = spacing
        )
    }

    private fun calculateStartDate(calendar: Calendar): Long {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return cal.timeInMillis
    }

    private fun createErrorBitmap(e: Exception, width: Int = 800, height: Int = 400): Bitmap {
        val errorBitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(errorBitmap)
        val p = Paint().apply { color = Color.RED; textSize = 40f }
        c.drawText("Error: ${e.localizedMessage}", 50f, 200f, p)
        return errorBitmap
    }
}
