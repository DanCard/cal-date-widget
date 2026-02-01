package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.CalendarContract
import android.content.pm.PackageManager
import android.util.Log
import android.util.TypedValue
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object WidgetDrawer {

    data class WidgetSize(val widthPx: Int, val heightPx: Int)

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
                canvas.drawColor(0x4D000000.toInt())
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
                events = repo.getEvents(startMillis, 7, selectedIds.ifEmpty { null })
            }

            if (!settings.showDeclinedEvents) {
                events = events.filter { !it.isDeclined }
            }

            // Filter near-duplicate events
            events = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

            val diff = (todayMillis - startMillis) / (24 * 60 * 60 * 1000)
            val todayIndex = diff.toInt()
        
            val validStart = 0
            val validEnd = 6
        
            val allWeights = WeeklyDisplayLogic.getColumnWeights(todayIndex, 7)
            var totalWeight = 0f
            for (i in validStart..validEnd) {
                totalWeight += allWeights[i]
            }
        
            var currentX = 0f
            val dayFormatEEE = SimpleDateFormat("EEE", Locale.getDefault())
            val dayFormatEEEd = SimpleDateFormat("EEE d", Locale.getDefault())
        
            val contentTop = 80f
            val bottomPadding = 0f
            val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)
            val now = System.currentTimeMillis()

            for (i in validStart..validEnd) {
                val colWidth = (width * (allWeights[i] / totalWeight))
            
                if (i > validStart) {
                    canvas.drawLine(currentX, 0f, currentX, height.toFloat(), paints.linePaint)
                }

                // Header
                val dayMillis = startMillis + (i * 24 * 60 * 60 * 1000)
                val dayName = if (i < todayIndex) {
                    dayFormatEEE.format(dayMillis)
                } else {
                    dayFormatEEEd.format(dayMillis)
                }

                if (i == todayIndex) {
                    paints.dayHeaderPaint.color = Color.YELLOW
                    paints.dayHeaderPaint.isFakeBoldText = true
                    paints.dayHeaderPaint.textSize = 80f
                } else {
                    paints.dayHeaderPaint.color = Color.LTGRAY
                    paints.dayHeaderPaint.isFakeBoldText = false
                    paints.dayHeaderPaint.textSize = 40f
                }

                val fm = paints.dayHeaderPaint.fontMetrics
                val headerBaseline = (contentTop / 2f) - (fm.ascent + fm.descent) / 2f
                canvas.drawText(dayName, currentX + colWidth / 2, headerBaseline, paints.dayHeaderPaint)

                // Events
                val dayEnd = dayMillis + (24 * 60 * 60 * 1000)
                val dayEvents = events.filter { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }

                val textWidth = (colWidth - 10f).toInt()
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
                    
                    paints.textPaint.textSize = 48f * eventScale

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

                            canvas.translate(currentX + 5f, yPos)
                            layout.draw(canvas)
                            canvas.translate(-(currentX + 5f), -yPos)

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
                canvas.drawColor(0x4D000000.toInt())
            }

            val paints = WidgetRenderingHelper.createPaints(settings)

            val calendar = Calendar.getInstance()
            val todayCal = Calendar.getInstance()
            todayCal.set(Calendar.HOUR_OF_DAY, 0)
            todayCal.set(Calendar.MINUTE, 0)
            todayCal.set(Calendar.SECOND, 0)
            todayCal.set(Calendar.MILLISECOND, 0)
            val todayMillis = todayCal.timeInMillis

            val numDays = calculateNumberOfDays(context, size.widthPx, size.heightPx)

            var events = emptyList<CalendarEvent>()
            val hasPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            Log.d("WidgetDrawer", "Permission Check (Daily): $hasPermission")

            if (hasPermission) {
                val repo = CalendarRepository(context)
                // If no calendars selected, use smart default (personal calendars only)
                val selectedIds = settings.selectedCalendarIds.ifEmpty { repo.getDefaultCalendarIds() }
                val selection = selectedIds.ifEmpty { null }
                
                // Auto-advance logic: If we have events today and they are ALL in the past, skip today
                val checkStart = calculateStartDate(calendar)
                val todayEvents = repo.getEvents(checkStart, 1, selection)
                val now = System.currentTimeMillis()

                if (DailyDisplayLogic.shouldAutoAdvance(todayEvents, now, settings.showDeclinedEvents)) {
                     calendar.add(Calendar.DAY_OF_YEAR, 1)
                     Log.d("WidgetDrawer", "All events for today are in the past. Auto-advancing to tomorrow.")
                }

                // Final start date
                val startMillis = calculateStartDate(calendar)
                events = repo.getEvents(startMillis, numDays, selection)
            }

            val startMillis = calculateStartDate(calendar) // Ensure startMillis matches the potentially advanced calendar

            if (!settings.showDeclinedEvents) {
                events = events.filter { !it.isDeclined }
            }

            // Filter near-duplicate events
            events = WeeklyDisplayLogic.filterNearDuplicates(events, System.currentTimeMillis())

            val diff = (todayMillis - startMillis) / (24 * 60 * 60 * 1000)
            val todayIndex = diff.toInt()

            val validStart = 0
            val validEnd = numDays - 1

            val allWeights = DailyDisplayLogic.getColumnWeights(todayIndex, numDays)
            var totalWeight = 0f
            for (i in validStart..validEnd) {
                totalWeight += allWeights[i]
            }

            var currentX = 0f
            val dayFormatEEE = SimpleDateFormat("EEE", Locale.getDefault())
            val dayFormatEEEd = SimpleDateFormat("EEE d", Locale.getDefault())

            val contentTop = 80f
            val bottomPadding = 0f
            val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)
            val now = System.currentTimeMillis()

            for (i in validStart..validEnd) {
                val colWidth = (width * (allWeights[i] / totalWeight))

                if (i > validStart) {
                    canvas.drawLine(currentX, 0f, currentX, height.toFloat(), paints.linePaint)
                }

                val dayMillis = startMillis + (i * 24 * 60 * 60 * 1000)
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
                canvas.drawText(dayName, currentX + colWidth / 2, headerBaseline, paints.dayHeaderPaint)

                val dayEnd = dayMillis + (24 * 60 * 60 * 1000)
                val dayEvents = events.filter { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }

                val textWidth = (colWidth - 10f).toInt()
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

                    paints.textPaint.textSize = 48f * eventScale

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

                            canvas.translate(currentX + 5f, yPos)
                            layout.draw(canvas)
                            canvas.translate(-(currentX + 5f), -yPos)

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
        return numDays
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
