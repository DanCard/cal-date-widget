package ai.dcar.caldatewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.CalendarContract
import android.util.Log
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.text.SpannableString
import android.text.Spanned
import java.util.Date
import android.text.style.ForegroundColorSpan
import android.text.style.CharacterStyle

class DailyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    companion object {
        private data class WidgetSize(val widthPx: Int, val heightPx: Int)

        private data class PaintBundle(
            val textPaint: android.text.TextPaint,
            val dayHeaderPaint: Paint,
            val linePaint: Paint
        )

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_daily)

            val bitmap = drawDailyCalendar(context, appWidgetManager, appWidgetId)
            views.setImageViewBitmap(R.id.daily_canvas, bitmap)

            val configIntent = Intent(context, DailyConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            val configPendingIntent = PendingIntent.getActivity(
                context, appWidgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.daily_config_btn, configPendingIntent)

            val calendarUri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
            val calendarIntent = Intent(Intent.ACTION_VIEW).apply {
                data = calendarUri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, calendarIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.daily_canvas, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun drawDailyCalendar(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Bitmap {
            try {
                Log.d("DailyWidget", "========================================")
                Log.d("DailyWidget", "Starting drawDailyCalendar for ID: $appWidgetId")
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

                val paints = createPaints(settings)

                val calendar = Calendar.getInstance()
                val todayCal = Calendar.getInstance()
                todayCal.set(Calendar.HOUR_OF_DAY, 0)
                todayCal.set(Calendar.MINUTE, 0)
                todayCal.set(Calendar.SECOND, 0)
                todayCal.set(Calendar.MILLISECOND, 0)
                val todayMillis = todayCal.timeInMillis

                val numDays = calculateNumberOfDays(context, size.widthPx, size.heightPx)

                val startMillis = calculateStartDate(calendar, context)

                val repo = CalendarRepository(context)
                var events = repo.getEventsForWeek(startMillis)

                if (!settings.showDeclinedEvents) {
                    events = events.filter { !it.isDeclined }
                }

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

                    val buildEventText: (CalendarEvent, Boolean) -> CharSequence =
                        { event, includeStart -> buildEventText(event, includeStart, settings, timeFormat) }

                    val availableHeight = height - (contentTop + bottomPadding)

                    val optimalFontScale = calculateOptimalFontScale(
                        dayEvents = dayEvents,
                        textWidth = textWidth,
                        availableHeight = availableHeight,
                        basePaint = paints.textPaint,
                        buildEventText = buildEventText,
                        currentTimeMillis = now,
                        todayIndex = todayIndex,
                        currentDayIndex = i
                    )

                    val (pastEventsOnToday, currentFutureEvents) = if (i == todayIndex) {
                        dayEvents.partition { it.endTime < now }
                    } else {
                        Pair(emptyList(), dayEvents)
                    }

                    if (i == todayIndex) {
                        val estimatedLineHeight = (48f * optimalFontScale) * 1.2f
                        val measuredHeight = measureTotalHeightForScale(
                            optimalFontScale,
                            dayEvents,
                            textWidth,
                            paints.textPaint,
                            buildEventText,
                            now,
                            todayIndex,
                            i
                        )
                        logCurrentDayDebug(
                            dayEvents,
                            pastEventsOnToday,
                            currentFutureEvents,
                            availableHeight,
                            measuredHeight,
                            optimalFontScale,
                            now,
                            estimatedLineHeight
                        )
                        Log.d("DailyWidget", "Optimal font scale for today: $optimalFontScale")
                    } else {
                        Log.d("DailyWidget", "Optimal font scale for day $i: $optimalFontScale")
                    }

                    var remainingHeightForCurrentDay = availableHeight

                    var yPos = contentTop
                    for (event in dayEvents) {
                        canvas.save()
                        canvas.clipRect(currentX, contentTop, currentX + colWidth, height.toFloat())

                        paints.textPaint.color = settings.textColor

                        val eventScale = if (i == todayIndex && event.endTime < now) {
                            optimalFontScale * 0.8f
                        } else if (event.selfStatus == CalendarContract.Attendees.ATTENDEE_STATUS_INVITED || event.isDeclined) {
                            val invScale = (0.8f - 0.2f * optimalFontScale).coerceIn(0.5f, 0.7f)
                            optimalFontScale * invScale
                        } else {
                            optimalFontScale
                        }
                        paints.textPaint.textSize = 48f * eventScale

                        val isPastTodayEvent = (i == todayIndex && event.endTime < now)
                        // Harmonize with measureTotalHeightForScale: Unlimited lines for future events to avoid cutoff
                        val dynamicMaxLines = if (isPastTodayEvent) 1 else 0

                        if (textWidth > 0) {
                            val finalText = buildEventText(event, true)

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

                                if (i == todayIndex || layout.lineCount > 3) {
                                    Log.d("DailyWidget", "Layout for day $i '${event.title}': textWidth=$textWidth, maxLines=$dynamicMaxLines, actualLines=${layout.lineCount}, height=${layout.height}")
                                }

                                canvas.translate(currentX + 5f, yPos)
                                layout.draw(canvas)
                                canvas.translate(-(currentX + 5f), -yPos)

                                val spacing = if (i == todayIndex) paints.textPaint.textSize * 0.1f else paints.textPaint.textSize * 0.2f
                                val consumedHeight = when {
                                    i != todayIndex -> layout.height.toFloat() + spacing
                                    isPastTodayEvent -> layout.height.toFloat() + spacing
                                    else -> {
                                        layout.height.toFloat() + spacing
                                    }
                                }
                                yPos += consumedHeight
                                if (i == todayIndex) {
                                    remainingHeightForCurrentDay -= consumedHeight
                                }
                            } catch (e: Exception) {
                                Log.e("DailyWidget", "StaticLayout crash: title='${event.title}', width=$textWidth", e)
                            }
                        }

                        canvas.restore()
                    }

                    currentX += colWidth
                }

                return bitmap
            } catch (e: Exception) {
                Log.e("DailyWidgetProvider", "Error drawing widget", e)
                val errorBitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
                val c = Canvas(errorBitmap)
                val p = Paint().apply { color = Color.RED; textSize = 40f }
                c.drawText("Error: ${e.localizedMessage}", 50f, 200f, p)
                return errorBitmap
        }
    }

        private fun calculateWidgetSize(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ): WidgetSize {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

            val dm = context.resources.displayMetrics
            val widthPx = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP,
                minWidthDp.toFloat(),
                dm
            ).toInt().coerceAtLeast(1)

            val heightPx = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP,
                minHeightDp.toFloat(),
                dm
            ).toInt().coerceAtLeast(1)

            Log.d("DailyWidget", "Widget Dp: minWidthDp=$minWidthDp, minHeightDp=$minHeightDp")
            Log.d("DailyWidget", "Bitmap Px: widthPx=$widthPx, heightPx=$heightPx (Density: ${dm.density})")
            return WidgetSize(widthPx, heightPx)
        }

        private fun calculateNumberOfDays(context: Context, widthPx: Int, heightPx: Int): Int {
            val dm = context.resources.displayMetrics
            val widthDp = widthPx / dm.density
            val heightDp = heightPx / dm.density

            val cellWidthDp = calculateCellWidthDp(widthDp, heightDp)
            val numDays = ((widthDp / cellWidthDp) + 0.5f).toInt().coerceIn(1, 7)

            Log.d("DailyWidget", "=== calculateNumberOfDays ===")
            Log.d("DailyWidget", "Width: $widthPx px = ${widthDp}dp")
            Log.d("DailyWidget", "Height: $heightPx px = ${heightDp}dp")
            Log.d("DailyWidget", "Cell width: $cellWidthDp dp")
            Log.d("DailyWidget", "Width / Cell = ${widthDp / cellWidthDp}")
            Log.d("DailyWidget", "+ 0.5 = ${(widthDp / cellWidthDp) + 0.5f}")
            Log.d("DailyWidget", "toInt() = $numDays")
            Log.d("DailyWidget", "Final numDays: $numDays")
            return numDays
        }

        private fun calculateCellWidthDp(widthDp: Float, heightDp: Float): Float {
            // Fixed cell width to ensure consistency across different heights (1x4 vs 2x4)
            // 370dp width / 92dp ~= 4.02 -> 4 days
            return 92f
        }

        private fun calculateStartDate(calendar: Calendar, context: Context): Long {
            val cal = calendar.clone() as Calendar
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            val repo = CalendarRepository(context)
            val now = System.currentTimeMillis()
            val todayStart = cal.timeInMillis
            val todayEnd = todayStart + (24 * 60 * 60 * 1000)

            val todayEvents = repo.getEventsForWeek(todayStart)
            val futureEventsToday = todayEvents.filter { it.endTime > now && WeeklyDisplayLogic.shouldDisplayEventOnDay(it, todayStart, todayEnd) }

            if (futureEventsToday.isEmpty()) {
                Log.d("DailyWidget", "All events for today have elapsed, starting with tomorrow")
                cal.add(Calendar.DAY_OF_MONTH, 1)
            } else {
                Log.d("DailyWidget", "Starting with current day")
            }

            return cal.timeInMillis
        }

        private fun calculateOptimalFontScale(
            dayEvents: List<CalendarEvent>,
            textWidth: Int,
            availableHeight: Float,
            basePaint: android.text.TextPaint,
            buildEventText: (CalendarEvent, Boolean) -> CharSequence,
            currentTimeMillis: Long,
            todayIndex: Int,
            currentDayIndex: Int
        ): Float {
            if (dayEvents.isEmpty() || textWidth <= 0 || availableHeight <= 0f) {
                return 0.5f
            }

            var minScale = 0.3f
            var maxScale = 1.5f
            var optimalScale = 1.0f

            for (iteration in 0 until 10) {
                val midScale = (minScale + maxScale) / 2f
                val totalHeight = measureTotalHeightForScale(
                    midScale,
                    dayEvents,
                    textWidth,
                    basePaint,
                    buildEventText,
                    currentTimeMillis,
                    todayIndex,
                    currentDayIndex
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

        private fun measureTotalHeightForScale(
            scale: Float,
            dayEvents: List<CalendarEvent>,
            textWidth: Int,
            basePaint: android.text.TextPaint,
            buildEventText: (CalendarEvent, Boolean) -> CharSequence,
            currentTimeMillis: Long,
            todayIndex: Int,
            currentDayIndex: Int
        ): Float {
            var totalHeight = 0f
            for (event in dayEvents) {
                val eventScale = if (currentDayIndex == todayIndex && event.endTime < currentTimeMillis) {
                    scale * 0.8f
                } else if (event.selfStatus == CalendarContract.Attendees.ATTENDEE_STATUS_INVITED || event.isDeclined) {
                    val invScale = (0.8f - 0.2f * scale).coerceIn(0.5f, 0.7f)
                    scale * invScale
                } else {
                    scale
                }
                val measurePaint = android.text.TextPaint(basePaint)
                measurePaint.textSize = 48f * eventScale

                val isPastTodayEvent = currentDayIndex == todayIndex && event.endTime < currentTimeMillis
                val eventText = buildEventText(event, true)
                val layout = android.text.StaticLayout.Builder.obtain(
                    eventText, 0, eventText.length, measurePaint, textWidth
                )
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    // Unlimited lines for future/current events to ensure accurate height measurement
                    .setMaxLines(if (isPastTodayEvent) 1 else 0)
                    .setEllipsize(if (isPastTodayEvent) android.text.TextUtils.TruncateAt.END else null)
                    .build()

                if (!isPastTodayEvent) {
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

                val spacing = if (currentDayIndex == todayIndex) measurePaint.textSize * 0.1f else measurePaint.textSize * 0.2f
                totalHeight += layout.height + spacing
            }
            return totalHeight
        }

        private fun createPaints(settings: PrefsManager.WidgetSettings): PaintBundle {
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

        private fun buildEventText(
            event: CalendarEvent,
            includeStart: Boolean,
            settings: PrefsManager.WidgetSettings,
            timeFormat: java.text.DateFormat
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

                return spannable
            }
            return safeTitle
        }

        private fun logCurrentDayDebug(
            dayEvents: List<CalendarEvent>,
            pastEventsOnToday: List<CalendarEvent>,
            currentFutureEvents: List<CalendarEvent>,
            availableHeight: Float,
            measuredHeight: Float,
            optimalScale: Float,
            currentTimeMillis: Long,
            estimatedLineHeight: Float
        ) {
            Log.d("DailyWidget", "=== Current Day Debug ===")
            Log.d("DailyWidget", "Total events: ${dayEvents.size}, Past events: ${pastEventsOnToday.size}, Future events: ${currentFutureEvents.size}")
            Log.d("DailyWidget", "Available height: $availableHeight, Est line height: $estimatedLineHeight")
            Log.d("DailyWidget", "Measured height: $measuredHeight, Optimal scale: $optimalScale")
            Log.d("DailyWidget", "Current time: ${SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT).format(Date(currentTimeMillis))}")
        }

        internal fun maxLinesForEvent(isToday: Boolean, eventEndTime: Long, nowMillis: Long): Int {
            return if (isToday && eventEndTime < nowMillis) 1 else 0
        }
    }
}
