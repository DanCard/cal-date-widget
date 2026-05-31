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
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

object CalendarImageGenerator {

    data class WidgetSize(val widthPx: Int, val heightPx: Int)
    internal data class TomorrowIndicatorHeaderLayout(
        val dayName: String,
        val indicator: String,
        val rainbowTextSize: Float,
        val spacing: Float
    )

    internal const val TOMORROW_INDICATOR = "🌈"

    private const val CONTENT_TOP = 80f
    private const val LEFT_PADDING = 5f
    private const val COL_WIDTH_PADDING = 10f
    internal const val EMPTY_COLUMN_WEIGHT_FACTOR = 0.35f
    private const val DEFAULT_BG_COLOR = 0x4D000000.toInt()
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    private const val DEBUG_TAG = "WidgetDebug"

    internal data class ColumnRenderConfig(
        val pastEventScaleFactor: Float,
        val useCompressDeclinedMaxLines: Boolean,
        val useTimeScaleForPast: Boolean,
        val widgetTag: String
    )

    fun drawWeeklyCalendar(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Bitmap {
        val size = calculateWidgetSize(context, appWidgetManager, appWidgetId)
        try {
            Log.d("CalendarImageGenerator", "Starting drawWeeklyCalendar for ID: $appWidgetId")
            val prefsManager = PrefsManager(context)
            val settings = prefsManager.loadSettings(appWidgetId)

            val bitmap = Bitmap.createBitmap(size.widthPx, size.heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            drawBackground(canvas, settings)

            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                drawPermissionRequired(canvas, size, settings)
                return bitmap
            }

            val paints = WidgetRenderingHelper.createPaints(settings)

            val todayMillis = startOfDayMillis()
            val startMillis = WeeklyDisplayLogic.getStartDate(Calendar.getInstance(), settings.weekStartDay)

            val dm = context.resources.displayMetrics
            val heightDp = size.heightPx / dm.density
            val weeks = WeeklyDisplayLogic.weeksToShow(settings.twoLineModeEnabled, heightDp)

            val totalDays = 7 * weeks
            val todayIndexInWeek = daysBetween(startMillis, todayMillis).coerceIn(0, 6)

            // Build the global day list. Row 1 uses effective shifting; Row 2 starts 7 days after Row 1.
            val globalDayMillisList = (0 until totalDays).map { globalIdx ->
                val w = globalIdx / 7
                val i = globalIdx % 7
                val baseEffective = WeeklyDisplayLogic.getEffectiveDayMillis(startMillis, i, todayIndexInWeek)
                baseEffective + (w * 7 * DAY_MILLIS)
                }

                val allEvents = fetchAndFilterEvents(context, settings, startMillis, totalDays + 7)

                val bandHeight = size.heightPx / weeks
            val now = System.currentTimeMillis()
            val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)

            for (w in 0 until weeks) {
                val rowDayMillisList = globalDayMillisList.subList(w * 7, (w + 1) * 7)
                
                val rowTodayIndex = if (w == 0) todayIndexInWeek else -1
                
                // Base weighting for this row
                val baseRowWeights = WeeklyDisplayLogic.getColumnWeights(rowTodayIndex, 7)
                
                // Adjust weights for empty columns
                val rowWeights = computeAdjustedWeights(allEvents, baseRowWeights, rowDayMillisList)

                val config = ColumnRenderConfig(
                    pastEventScaleFactor = 0.7f,
                    useCompressDeclinedMaxLines = true,
                    useTimeScaleForPast = true,
                    widgetTag = "Weekly"
                )

                renderDayColumns(
                    canvas, size.widthPx, bandHeight, paints, settings, allEvents,
                    rowWeights, rowTodayIndex, rowDayMillisList, config, timeFormat, now,
                    topOffset = (w * bandHeight).toFloat()
                ) { _, dayMillis, isToday, currentX, colWidth, top ->
                    drawWeeklyDayHeader(canvas, paints, dayMillis, isToday, currentX, colWidth, top)
                }
            }

            return bitmap
        } catch (e: Exception) {
            Log.e("CalendarImageGenerator", "Error drawing weekly widget", e)
            return createErrorBitmap(e, size.widthPx, size.heightPx)
        }
    }

    internal fun drawWeeklyDayHeader(
        canvas: Canvas,
        paints: WidgetRenderingHelper.PaintBundle,
        dayMillis: Long,
        isToday: Boolean,
        currentX: Float,
        colWidth: Float,
        topOffset: Float = 0f
    ) {
        if (isToday) {
            paints.dayHeaderPaint.color = Color.YELLOW
            paints.dayHeaderPaint.isFakeBoldText = true
        } else {
            paints.dayHeaderPaint.color = Color.LTGRAY
            paints.dayHeaderPaint.isFakeBoldText = false
        }
        val baseHeaderSize = WeeklyDisplayLogic.getHeaderTextSize(colWidth, isToday)
        paints.dayHeaderPaint.textSize = baseHeaderSize

        val header = WeeklyDisplayLogic.chooseHeaderText(colWidth, dayMillis) { text ->
            paints.dayHeaderPaint.measureText(text)
        }
        paints.dayHeaderPaint.textSize = baseHeaderSize * header.scale

        canvas.drawText(
            header.text,
            currentX + colWidth / 2,
            verticallyCenterBaseline(paints.dayHeaderPaint, topOffset + CONTENT_TOP / 2f),
            paints.dayHeaderPaint
        )
    }

    fun drawDailyCalendar(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Bitmap {
        val size = calculateWidgetSize(context, appWidgetManager, appWidgetId)
        try {
            Log.d("CalendarImageGenerator", "Starting drawDailyCalendar for ID: $appWidgetId")
            val prefsManager = PrefsManager(context)
            val settings = prefsManager.loadSettings(appWidgetId)

            val bitmap = Bitmap.createBitmap(size.widthPx, size.heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            drawBackground(canvas, settings)

            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                drawPermissionRequired(canvas, size, settings)
                return bitmap
            }

            val paints = WidgetRenderingHelper.createPaints(settings)

            val todayMillis = startOfDayMillis()
            val cols = calculateNumberOfDays(context, size.widthPx, size.heightPx)

            val startCalendar = Calendar.getInstance()
            var didAutoAdvance = false

            Log.d("CalendarImageGenerator", "Permission Check (Daily): $hasPermission")

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

                if (DailyDisplayLogic.shouldAutoAdvance(
                        todayEvents,
                        now,
                        settings.dailyAutoAdvanceCutoffMinuteOfDay
                    )
                ) {
                    startCalendar.add(Calendar.DAY_OF_YEAR, 1)
                    didAutoAdvance = true
                    Log.d("CalendarImageGenerator", "All events for today are in the past. Auto-advancing to tomorrow.")
                }
            }

            val startMillis = calculateStartDate(startCalendar)
            val dm = context.resources.displayMetrics
            val heightDp = size.heightPx / dm.density
            val grid = DailyDisplayLogic.computeGridLayout(cols, heightDp, settings.twoLineModeEnabled)

            val events = if (hasPermission) {
                fetchAndFilterEvents(context, settings, startMillis, grid.days)
            } else {
                emptyList()
            }

            val globalDayMillisList = (0 until grid.days).map { startMillis + it * DAY_MILLIS }

            val bandHeight = size.heightPx / grid.rows
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

            for (r in 0 until grid.rows) {
                val rowStart = r * grid.cols
                val rowEnd = minOf(rowStart + grid.cols, grid.days)
                val rowDayMillisList = globalDayMillisList.subList(rowStart, rowEnd)
                
                val rowTodayIndex = if (r == 0) {
                    daysBetween(startMillis, todayMillis).coerceIn(0, rowDayMillisList.size - 1)
                } else {
                    -1
                }

                val rowWeights = computeAdjustedWeights(
                    events,
                    DailyDisplayLogic.getColumnWeights(rowTodayIndex, rowDayMillisList.size),
                    rowDayMillisList
                )

                renderDayColumns(
                    canvas, size.widthPx, bandHeight, paints, settings, events,
                    rowWeights, rowTodayIndex, rowDayMillisList, config, timeFormat, now,
                    topOffset = (r * bandHeight).toFloat()
                ) { colIndex, dayMillis, isToday, currentX, colWidth, top ->
                    val globalIndex = rowStart + colIndex
                    val dayName = if (globalIndex < daysBetween(startMillis, todayMillis)) {
                        dayFormatEEE.format(dayMillis)
                    } else {
                        dayFormatEEEd.format(dayMillis)
                    }
                    drawDailyDayHeader(
                        canvas, paints, appWidgetId, globalIndex, isToday, currentX, colWidth,
                        didAutoAdvance, dayName, top
                    )
                }
            }

            return bitmap
        } catch (e: Exception) {
            Log.e("CalendarImageGenerator", "Error drawing daily widget", e)
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
        topOffset: Float = 0f,
        drawHeader: (colIndex: Int, dayMillis: Long, isToday: Boolean, currentX: Float, colWidth: Float, top: Float) -> Unit
    ) {
        var totalWeight = 0f
        for (weight in weights) totalWeight += weight

        Log.d("WeightDebug", "--- renderDayColumns (top=$topOffset, tag=${config.widgetTag}) ---")
        Log.d("WeightDebug", "Total Row Weight: $totalWeight, Weights: ${weights.joinToString()}")

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
            Log.d("WeightDebug", "Col $i: width=$colWidth weight=${weights[i]} isToday=$isToday day=${SimpleDateFormat("EEE d", Locale.getDefault()).format(dayMillis)}")

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
        val verboseDebug = Log.isLoggable(DEBUG_TAG, Log.VERBOSE)

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
                    DEBUG_TAG,
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
                Log.e("CalendarImageGenerator", "StaticLayout crash: title='${event.title}', width=$textWidth", e)
            }

            canvas.restore()

            if (yPos > topOffset + height) break
        }
    }

    private fun verticallyCenterBaseline(paint: Paint, centerY: Float): Float {
        val fm = paint.fontMetrics
        return centerY - (fm.ascent + fm.descent) / 2f
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

    /**
     * DST-safe whole-day count between two local-midnight epoch millis. Using
     * (to-from)/DAY_MILLIS silently produces N-1 or N+1 across a DST transition
     * because local midnight-to-midnight is 23h or 25h on those days.
     */
    private fun daysBetween(fromMillis: Long, toMillis: Long): Int {
        val zone = ZoneId.systemDefault()
        val from = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val to = Instant.ofEpochMilli(toMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(from, to).toInt()
    }

    private fun fetchAndFilterEvents(
        context: Context,
        settings: PrefsManager.WidgetSettings,
        startMillis: Long,
        days: Int
    ): List<CalendarEvent> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        Log.d("CalendarImageGenerator", "fetchAndFilterEvents permission: $hasPermission")
        if (!hasPermission) return emptyList()

        val repo = CalendarRepository(context)
        val selectedIds = settings.selectedCalendarIds.ifEmpty { repo.getDefaultCalendarIds() }
        Log.d("CalendarImageGenerator", "fetchAndFilterEvents selectedIds: $selectedIds")
        
        var events = repo.getEvents(startMillis, days, selectedIds.ifEmpty { null })
        Log.d("CalendarImageGenerator", "fetchAndFilterEvents rawCount: ${events.size}")

        if (!settings.showDeclinedEvents) {
            val countBefore = events.size
            events = events.filter { !it.isDeclined }
            Log.d("CalendarImageGenerator", "fetchAndFilterEvents afterDeclinedFilter: ${events.size} (removed ${countBefore - events.size})")
        }

        val filtered = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())
        Log.d("CalendarImageGenerator", "fetchAndFilterEvents finalCount: ${filtered.size}")
        return filtered
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

    internal fun drawDailyDayHeader(
        canvas: Canvas,
        paints: WidgetRenderingHelper.PaintBundle,
        appWidgetId: Int,
        colIndex: Int,
        isToday: Boolean,
        currentX: Float,
        colWidth: Float,
        didAutoAdvance: Boolean,
        dayName: String,
        topOffset: Float = 0f
    ) {
        paints.dayHeaderPaint.textSize = DailyDisplayLogic.getHeaderTextSize(colWidth, isToday)
        if (isToday) {
            paints.dayHeaderPaint.color = Color.YELLOW
            paints.dayHeaderPaint.isFakeBoldText = true
        } else {
            paints.dayHeaderPaint.color = Color.LTGRAY
            paints.dayHeaderPaint.isFakeBoldText = false
        }

        val headerBaseline = verticallyCenterBaseline(paints.dayHeaderPaint, topOffset + CONTENT_TOP / 2f)
        val centerX = currentX + colWidth / 2f
        if (shouldDrawTomorrowIndicator(didAutoAdvance, colIndex)) {
            val indicatorDrawn = drawHeaderWithTomorrowIndicator(
                canvas = canvas,
                dayName = dayName,
                centerX = centerX,
                centerY = topOffset + CONTENT_TOP / 2f,
                colWidth = colWidth,
                basePaint = paints.dayHeaderPaint
            )
            if (indicatorDrawn) {
                Log.d("CalendarImageGenerator", "Drawing tomorrow indicator for daily widget $appWidgetId")
            } else {
                canvas.drawText(dayName, centerX, headerBaseline, paints.dayHeaderPaint)
                Log.d("CalendarImageGenerator", "Skipped tomorrow indicator due to narrow column for widget $appWidgetId")
            }
        } else {
            canvas.drawText(dayName, centerX, headerBaseline, paints.dayHeaderPaint)
        }
    }

    @VisibleForTesting
    internal fun shouldDrawTomorrowIndicator(
        didAutoAdvance: Boolean,
        currentColumn: Int
    ): Boolean {
        return didAutoAdvance && currentColumn == 0
    }

    @VisibleForTesting
    internal fun drawHeaderWithTomorrowIndicator(
        canvas: Canvas,
        dayName: String,
        centerX: Float,
        centerY: Float,
        colWidth: Float,
        basePaint: Paint
    ): Boolean {
        val horizontalPadding = 10f
        val availableWidth = (colWidth - horizontalPadding).coerceAtLeast(1f)
        
        var baseTextWidth = basePaint.measureText(dayName)
        var layout = buildTomorrowIndicatorHeaderLayout(
            dayName = dayName,
            dayNameWidth = baseTextWidth,
            baseTextSize = basePaint.textSize,
            availableWidth = availableWidth
        ) { candidateTextSize ->
            val rainbowPaint = Paint(basePaint).apply { textSize = candidateTextSize }
            rainbowPaint.measureText(TOMORROW_INDICATOR)
        }

        var scaleDownCount = 0
        while ((layout == null || baseTextWidth >= availableWidth) && basePaint.textSize > 10f && scaleDownCount < 20) {
            basePaint.textSize *= 0.9f
            baseTextWidth = basePaint.measureText(dayName)
            layout = buildTomorrowIndicatorHeaderLayout(
                dayName = dayName,
                dayNameWidth = baseTextWidth,
                baseTextSize = basePaint.textSize,
                availableWidth = availableWidth
            ) { candidateTextSize ->
                val rainbowPaint = Paint(basePaint).apply { textSize = candidateTextSize }
                rainbowPaint.measureText(TOMORROW_INDICATOR)
            }
            scaleDownCount++
        }

        if (layout == null || baseTextWidth >= availableWidth) {
            return false
        }

        val rainbowPaint = Paint(basePaint).apply { textSize = layout.rainbowTextSize }
        val rainbowWidth = rainbowPaint.measureText(layout.indicator)
        val combinedWidth = baseTextWidth + layout.spacing + rainbowWidth
        val leftX = centerX - (combinedWidth / 2f)
        val baseCenterX = leftX + (baseTextWidth / 2f)
        val rainbowCenterX = leftX + baseTextWidth + layout.spacing + (rainbowWidth / 2f)

        val baseBaseline = verticallyCenterBaseline(basePaint, centerY)
        val rainbowBaseline = verticallyCenterBaseline(rainbowPaint, centerY)

        canvas.drawText(dayName, baseCenterX, baseBaseline, basePaint)
        canvas.drawText(layout.indicator, rainbowCenterX, rainbowBaseline, rainbowPaint)
        return true
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

    private fun drawPermissionRequired(canvas: Canvas, size: WidgetSize, settings: PrefsManager.WidgetSettings) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = settings.textColor
            textAlign = Paint.Align.CENTER
            textSize = 14f * canvas.density
        }
        val text = "Permission Required\nTap to grant"
        val lines = text.split("\n")
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

    private fun createErrorBitmap(e: Exception, width: Int, height: Int): Bitmap {
        val errorBitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(errorBitmap)
        val p = Paint().apply { color = Color.RED; textSize = 40f }
        c.drawText("Error: ${e.localizedMessage}", 50f, 200f, p)
        return errorBitmap
    }
}
