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

    fun drawWeeklyCalendar(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Bitmap {
        try {
            Log.d("WidgetDrawer", "Starting drawWeeklyCalendar for ID: $appWidgetId")
            val prefsManager = PrefsManager(context)
            val settings = prefsManager.loadSettings(appWidgetId)

            val size = calculateWidgetSize(context, appWidgetManager, appWidgetId)
            val bitmap = Bitmap.createBitmap(size.widthPx, size.heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val width = size.widthPx
            val height = size.heightPx

            if (settings.bgColor != Color.TRANSPARENT) {
                canvas.drawColor(settings.bgColor)
            } else {
                canvas.drawColor(DEFAULT_BG_COLOR)
            }

            val paints = WidgetRenderingHelper.createPaints(settings)

            val calendar = Calendar.getInstance()
            val todayCal = Calendar.getInstance()
            todayCal.set(Calendar.HOUR_OF_DAY, 0)
            todayCal.set(Calendar.MINUTE, 0)
            todayCal.set(Calendar.SECOND, 0)
            todayCal.set(Calendar.MILLISECOND, 0)
            val todayMillis = todayCal.timeInMillis
        
            val startMillis = WeeklyDisplayLogic.getStartDate(calendar, settings.weekStartDay)
        
            var events = emptyList<CalendarEvent>()
            val hasPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            Log.d("WidgetDrawer", "Permission Check (Weekly): $hasPermission")

            if (hasPermission) {
                val repo = CalendarRepository(context)
                // If no calendars selected, use smart default (personal calendars only)
                val selectedIds = settings.selectedCalendarIds.ifEmpty { repo.getDefaultCalendarIds() }
                events = repo.getEvents(todayMillis, 7, selectedIds.ifEmpty { null })
            }

            if (!settings.showDeclinedEvents) {
                events = events.filter { !it.isDeclined }
            }

            // Filter near-duplicate events
            events = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

            val diff = (todayMillis - startMillis) / DAY_MILLIS
            val todayIndex = diff.toInt()
        
            val validStart = 0
            val validEnd = 6
        
            val allWeights = WeeklyDisplayLogic.getColumnWeights(todayIndex, 7)
            for (i in validStart..validEnd) {
                val dayMillis = WeeklyDisplayLogic.getEffectiveDayMillis(startMillis, i, todayIndex)
                val dayEnd = dayMillis + DAY_MILLIS
                val hasEvents = events.any { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }
                if (!hasEvents) {
                    allWeights[i] *= EMPTY_COLUMN_WEIGHT_FACTOR
                }
            }

            var totalWeight = 0f
            for (i in validStart..validEnd) {
                totalWeight += allWeights[i]
            }

            var currentX = 0f

            val contentTop = CONTENT_TOP
            val bottomPadding = 0f
            val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)
            val now = System.currentTimeMillis()

            for (i in validStart..validEnd) {
                val colWidth = (width * (allWeights[i] / totalWeight))
            
                if (i > validStart) {
                    canvas.drawLine(currentX, 0f, currentX, height.toFloat(), paints.linePaint)
                }

                // Header
                val dayMillis = WeeklyDisplayLogic.getEffectiveDayMillis(startMillis, i, todayIndex)
                val isToday = (i == todayIndex)

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
                val headerBaseline = (contentTop / 2f) - (fm.ascent + fm.descent) / 2f
                canvas.drawText(header.text, currentX + colWidth / 2, headerBaseline, paints.dayHeaderPaint)

                // Events
                val dayEnd = dayMillis + DAY_MILLIS
                val dayEvents = events.filter { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }

                val textWidth = (colWidth - COL_WIDTH_PADDING).toInt()
                val availableHeight = height - (contentTop + bottomPadding)

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
                    pastEventScaleFactor = 0.7f
                )

                var yPos = contentTop
                
                for (event in dayEvents) {
                    canvas.save()
                    canvas.clipRect(currentX, contentTop, currentX + colWidth, height.toFloat())

                    paints.textPaint.color = settings.textColor

                    val isLessInteresting = WidgetRenderingHelper.isLessInteresting(event)
                    Log.d("WidgetDebug", "Weekly Event: '${event.title}', status=${event.selfStatus}, declined=${event.isDeclined}, lessInteresting=$isLessInteresting")
                    
                    var eventScale = if (i == todayIndex && event.endTime < now) {
                        optimalFontScale * 0.7f
                    } else if (isLessInteresting) {
                        val invScale = (0.8f - 0.2f * optimalFontScale).coerceIn(0.5f, 0.7f)
                        optimalFontScale * invScale
                    } else {
                        optimalFontScale
                    }
                    
                    // Enforce minimum sizes
                    val minEventScale = if (isLessInteresting) 0.5f else 0.7f
                    if (eventScale < minEventScale) eventScale = minEventScale
                    
                    paints.textPaint.textSize = BASE_TEXT_SIZE * eventScale

                    val hasFutureEvents = dayEvents.any { it.endTime >= now }
                    val isPastTodayEvent = (i == todayIndex && event.endTime < now)
                    val forceOneLine = (isPastTodayEvent && hasFutureEvents) || (compressDeclined && isLessInteresting)
                    
                    val dynamicMaxLines = if (forceOneLine) 1 else if (compressDeclined) 3 else 0

                    if (textWidth > 0) {
                        val timeScale = if (isPastTodayEvent) 0.5f else 1f
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

                            val spacing = if (i == todayIndex) paints.textPaint.textSize * 0.1f else paints.textPaint.textSize * 0.2f
                            val consumedHeight = layout.height.toFloat() + spacing
                            yPos += consumedHeight
                        } catch (e: Exception) {
                            Log.e("WidgetDrawer", "StaticLayout crash: title='${event.title}', width=$textWidth", e)
                        }
                    }
                    canvas.restore()
                }
                currentX += colWidth
            }
            return bitmap
        } catch (e: Exception) {
            return createErrorBitmap(e)
        }
    }

    fun drawDailyCalendar(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Bitmap {
        try {
            Log.d("WidgetDrawer", "Starting drawDailyCalendar for ID: $appWidgetId")
            val prefsManager = PrefsManager(context)
            val settings = prefsManager.loadSettings(appWidgetId)

            val size = calculateWidgetSize(context, appWidgetManager, appWidgetId)
            val bitmap = Bitmap.createBitmap(size.widthPx, size.heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val width = size.widthPx
            val height = size.heightPx

            if (settings.bgColor != Color.TRANSPARENT) {
                canvas.drawColor(settings.bgColor)
            } else {
                canvas.drawColor(DEFAULT_BG_COLOR)
            }

            val paints = WidgetRenderingHelper.createPaints(settings)

            val calendar = Calendar.getInstance()
            val todayCal = Calendar.getInstance()
            todayCal.set(Calendar.HOUR_OF_DAY, 0)
            todayCal.set(Calendar.MINUTE, 0)
            todayCal.set(Calendar.SECOND, 0)
            todayCal.set(Calendar.MILLISECOND, 0)
            val todayMillis = todayCal.timeInMillis

            var numDays = calculateNumberOfDays(context, size.widthPx, size.heightPx)

            var events = emptyList<CalendarEvent>()
            var didAutoAdvance = false
            val hasPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            Log.d("WidgetDrawer", "Permission Check (Daily): $hasPermission")

            if (hasPermission) {
                val repo = CalendarRepository(context)
                val selectedIds = settings.selectedCalendarIds.ifEmpty { repo.getDefaultCalendarIds() }
                val selection = selectedIds.ifEmpty { null }
                
                val checkStart = calculateStartDate(calendar)
                val todayEnd = checkStart + DAY_MILLIS
                val todayEvents = repo.getEvents(checkStart, 1, selection).filter {
                    WeeklyDisplayLogic.shouldDisplayEventOnDay(it, checkStart, todayEnd)
                }
                val now = System.currentTimeMillis()

                if (DailyDisplayLogic.shouldAutoAdvance(todayEvents, now)) {
                     calendar.add(Calendar.DAY_OF_YEAR, 1)
                     didAutoAdvance = true
                     Log.d("WidgetDrawer", "All events for today are in the past. Auto-advancing to tomorrow.")
                } else {
                    Log.d(
                        "WidgetDrawer",
                        "Daily widget stays on today. now=$now todayEvents=${todayEvents.size} titles=${todayEvents.joinToString { it.title }}"
                    )
                }

                // Final start date
                val startMillis = calculateStartDate(calendar)
                val maxFetchDays = (numDays + 1).coerceAtMost(7)
                events = repo.getEvents(startMillis, maxFetchDays, selection)
            }

            val startMillis = calculateStartDate(calendar) // Ensure startMillis matches the potentially advanced calendar

            if (!settings.showDeclinedEvents) {
                events = events.filter { !it.isDeclined }
            }

            // Filter near-duplicate events
            events = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

            // Dynamic days logic: Only for widgets at least 2 rows high (~100dp)
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

            val diff = (todayMillis - startMillis) / DAY_MILLIS
            val todayIndex = diff.toInt()

            val validStart = 0
            val validEnd = numDays - 1

            val allWeights = DailyDisplayLogic.getColumnWeights(todayIndex, numDays)
            for (i in validStart..validEnd) {
                val dayMillis = startMillis + (i * DAY_MILLIS)
                val dayEnd = dayMillis + DAY_MILLIS
                val hasEvents = events.any { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }
                if (!hasEvents) {
                    allWeights[i] *= EMPTY_COLUMN_WEIGHT_FACTOR
                }
            }

            var totalWeight = 0f
            for (i in validStart..validEnd) {
                totalWeight += allWeights[i]
            }

            var currentX = 0f
            val dayFormatEEE = SimpleDateFormat("EEE", Locale.getDefault())
            val dayFormatEEEd = SimpleDateFormat("EEE d", Locale.getDefault())

            val contentTop = CONTENT_TOP
            val bottomPadding = 0f
            val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)
            val now = System.currentTimeMillis()

            for (i in validStart..validEnd) {
                val colWidth = (width * (allWeights[i] / totalWeight))

                if (i > validStart) {
                    canvas.drawLine(currentX, 0f, currentX, height.toFloat(), paints.linePaint)
                }

                val dayMillis = startMillis + (i * DAY_MILLIS)
                val dayName = if (i < todayIndex) {
                    dayFormatEEE.format(dayMillis)
                } else {
                    dayFormatEEEd.format(dayMillis)
                }

                val headerTextSize = if (i == todayIndex) {
                    colWidth * 0.3f
                } else {
                    colWidth * 0.25f
                }.coerceIn(30f, 70f)

                if (i == todayIndex) {
                    paints.dayHeaderPaint.color = Color.YELLOW
                    paints.dayHeaderPaint.isFakeBoldText = true
                    paints.dayHeaderPaint.textSize = headerTextSize
                } else {
                    paints.dayHeaderPaint.color = Color.LTGRAY
                    paints.dayHeaderPaint.isFakeBoldText = false
                    paints.dayHeaderPaint.textSize = headerTextSize
                }

                val fm = paints.dayHeaderPaint.fontMetrics
                val headerBaseline = (contentTop / 2f) - (fm.ascent + fm.descent) / 2f
                val centerX = currentX + colWidth / 2f
                val shouldDrawTomorrowIndicator = shouldDrawTomorrowIndicator(didAutoAdvance, i, validStart)
                if (shouldDrawTomorrowIndicator) {
                    val indicatorDrawn = drawHeaderWithTomorrowIndicator(
                        canvas = canvas,
                        dayName = dayName,
                        centerX = centerX,
                        centerY = contentTop / 2f,
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

                val dayEnd = dayMillis + DAY_MILLIS
                val dayEvents = events.filter { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }

                val textWidth = (colWidth - COL_WIDTH_PADDING).toInt()
                val availableHeight = height - (contentTop + bottomPadding)

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
                    pastEventScaleFactor = 0.8f
                )

                var yPos = contentTop
                for (event in dayEvents) {
                    canvas.save()
                    canvas.clipRect(currentX, contentTop, currentX + colWidth, height.toFloat())

                    paints.textPaint.color = settings.textColor

                    val isLessInteresting = WidgetRenderingHelper.isLessInteresting(event)
                    Log.d("WidgetDebug", "Daily Event: '${event.title}', status=${event.selfStatus}, declined=${event.isDeclined}, lessInteresting=$isLessInteresting")

                    var eventScale = if (i == todayIndex && event.endTime < now) {
                        optimalFontScale * 0.8f
                    } else if (isLessInteresting) {
                        val invScale = (0.8f - 0.2f * optimalFontScale).coerceIn(0.5f, 0.7f)
                        optimalFontScale * invScale
                    } else {
                        optimalFontScale
                    }

                    // Enforce minimum sizes
                    val minEventScale = if (isLessInteresting) 0.5f else 0.7f
                    if (eventScale < minEventScale) eventScale = minEventScale

                    paints.textPaint.textSize = BASE_TEXT_SIZE * eventScale

                    val hasFutureEvents = dayEvents.any { it.endTime >= now }
                    val isPastTodayEvent = (i == todayIndex && event.endTime < now)
                    val forceOneLine = (isPastTodayEvent && hasFutureEvents) || (compressDeclined && isLessInteresting)
                    val dynamicMaxLines = if (forceOneLine) 1 else 0

                    if (textWidth > 0) {
                        val finalText = WidgetRenderingHelper.buildEventText(event, true, settings, timeFormat)

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

                            val spacing = if (i == todayIndex) paints.textPaint.textSize * 0.1f else paints.textPaint.textSize * 0.2f
                            val consumedHeight = layout.height.toFloat() + spacing
                            yPos += consumedHeight
                        } catch (e: Exception) {
                            Log.e("WidgetDrawer", "StaticLayout crash: title='${event.title}', width=$textWidth", e)
                        }
                    }

                    canvas.restore()
                }

                currentX += colWidth
            }

            return bitmap
        } catch (e: Exception) {
            Log.e("WidgetDrawer", "Error drawing daily widget", e)
            return createErrorBitmap(e)
        }
    }

    private fun calculateWidgetSize(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ): WidgetSize {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val dm = context.resources.displayMetrics

        // Try to use appWidgetSizes (Android 12+) for accurate current size
        var widthDp = 250f
        var heightDp = 110f

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<android.util.SizeF>("appWidgetSizes")
            if (!sizes.isNullOrEmpty()) {
                // First entry is portrait size, which is what we want
                val portraitSize = sizes[0]
                widthDp = portraitSize.width
                heightDp = portraitSize.height
                Log.d("WidgetSize", "Widget $appWidgetId: Using appWidgetSizes[0] = ${widthDp}x${heightDp}dp")
            }
        }

        // Fallback to min/max if appWidgetSizes not available
        if (widthDp == 250f && heightDp == 110f) {
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeightDp)
            // In portrait: width is min, height is max
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

    private fun calculateNumberOfDays(context: Context, widthPx: Int, heightPx: Int): Int {
        val dm = context.resources.displayMetrics
        val widthDp = widthPx / dm.density

        val cellWidthDp = 92f // Fixed cell width
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

    private fun createErrorBitmap(e: Exception): Bitmap {
        val errorBitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
        val c = Canvas(errorBitmap)
        val p = Paint().apply { color = Color.RED; textSize = 40f }
        c.drawText("Error: ${e.localizedMessage}", 50f, 200f, p)
        return errorBitmap
    }
}
