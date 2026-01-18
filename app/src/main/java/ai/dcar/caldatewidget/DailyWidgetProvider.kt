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

                val paints = WidgetRenderingHelper.createPaints(settings)

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

                    // Debug logging
                    if (i == todayIndex || i == todayIndex + 1) {
                         val (pastEventsOnToday, currentFutureEvents) = if (i == todayIndex) {
                            dayEvents.partition { it.endTime < now }
                        } else {
                            Pair(emptyList(), dayEvents)
                        }
                        
                        val estimatedLineHeight = (48f * optimalFontScale) * 1.2f
                        val measuredHeight = WidgetRenderingHelper.measureTotalHeightForScale(
                            optimalFontScale,
                            dayEvents,
                            textWidth,
                            paints.textPaint,
                            settings,
                            timeFormat,
                            now,
                            todayIndex,
                            i,
                            compressDeclined,
                            0.8f
                        )
                        WidgetRenderingHelper.logCurrentDayDebug(
                            "DailyWidget",
                            dayEvents,
                            pastEventsOnToday,
                            currentFutureEvents,
                            availableHeight,
                            measuredHeight,
                            optimalFontScale,
                            now,
                            estimatedLineHeight,
                            compressDeclined
                        )
                    }

                    var remainingHeightForCurrentDay = availableHeight

                    var yPos = contentTop
                    for (event in dayEvents) {
                        canvas.save()
                        canvas.clipRect(currentX, contentTop, currentX + colWidth, height.toFloat())

                        paints.textPaint.color = settings.textColor

                        val isDeclinedOrInvited = event.selfStatus == CalendarContract.Attendees.ATTENDEE_STATUS_INVITED || event.isDeclined

                        var eventScale = if (i == todayIndex && event.endTime < now) {
                            optimalFontScale * 0.8f
                        } else if (isDeclinedOrInvited) {
                            val invScale = (0.8f - 0.2f * optimalFontScale).coerceIn(0.5f, 0.7f)
                            optimalFontScale * invScale
                        } else {
                            optimalFontScale
                        }

                        // Enforce minimum sizes
                        val minEventScale = if (isDeclinedOrInvited) 0.5f else 0.7f
                        if (eventScale < minEventScale) eventScale = minEventScale

                        paints.textPaint.textSize = 48f * eventScale

                        val isPastTodayEvent = (i == todayIndex && event.endTime < now)
                        val forceOneLine = isPastTodayEvent || (compressDeclined && isDeclinedOrInvited)
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

        internal fun maxLinesForEvent(isToday: Boolean, eventEndTime: Long, nowMillis: Long): Int {
            return if (isToday && eventEndTime < nowMillis) 1 else 0
        }
    }
}