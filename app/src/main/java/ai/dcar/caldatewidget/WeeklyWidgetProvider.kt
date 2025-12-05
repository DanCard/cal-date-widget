package ai.dcar.caldatewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.provider.CalendarContract
import android.util.Log
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.graphics.Typeface
import java.util.Date
import android.text.style.ForegroundColorSpan
import android.text.style.CharacterStyle

class WeeklyWidgetProvider : AppWidgetProvider() {

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
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            // 1. Create RemoteViews
            val views = RemoteViews(context.packageName, R.layout.widget_weekly)

            // 2. Draw Bitmap
            val bitmap = drawWeeklyCalendar(context, appWidgetManager, appWidgetId)
            views.setImageViewBitmap(R.id.weekly_canvas, bitmap)

            // 3. Config Intent
            val configIntent = Intent(context, WeeklyConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            val configPendingIntent = PendingIntent.getActivity(
                context, appWidgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.weekly_config_btn, configPendingIntent)

            // 4. Calendar Intent (Tap anywhere else)
            val calendarUri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
            val calendarIntent = Intent(Intent.ACTION_VIEW).apply {
                data = calendarUri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, calendarIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.weekly_canvas, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun drawWeeklyCalendar(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Bitmap {
            try {
                Log.d("WeeklyWidget", "Starting drawWeeklyCalendar for ID: $appWidgetId")
                val prefsManager = PrefsManager(context)
                val settings = prefsManager.loadSettings(appWidgetId)
                
                // Get dimensions in Dp
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                
                Log.d("WeeklyWidget", "Widget Dp: minWidthDp=$minWidthDp, minHeightDp=$minHeightDp")

                // Convert Dp to Pixels to create 1:1 bitmap
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
                
                Log.d("WeeklyWidget", "Bitmap Px: widthPx=$widthPx, heightPx=$heightPx (Density: ${dm.density})")
                
                // Use actual pixel dimensions for the bitmap
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                
                // Use widthPx/heightPx for logic
                val width = widthPx
                val height = heightPx

            // Paints
            val textPaint = android.text.TextPaint().apply {
                color = settings.textColor
                textSize = 48f * settings.textSizeScale // Scaled
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
                setShadowLayer(2f, 1f, 1f, settings.shadowColor)
            }
            val dayHeaderPaint = Paint().apply {
                color = Color.LTGRAY
                textSize = 30f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val linePaint = Paint().apply {
                color = Color.DKGRAY
                strokeWidth = 2f
            }

            // ... (Logic Start Date section remains same) ...
            val calendar = Calendar.getInstance()
            val todayCal = Calendar.getInstance()
            todayCal.set(Calendar.HOUR_OF_DAY, 0)
            todayCal.set(Calendar.MINUTE, 0)
            todayCal.set(Calendar.SECOND, 0)
            todayCal.set(Calendar.MILLISECOND, 0)
            val todayMillis = todayCal.timeInMillis
            
            val startMillis = WeeklyDisplayLogic.getStartDate(calendar, settings.weekStartDay)
            
            // 2. Fetch Data
            val repo = CalendarRepository(context)
            var events = repo.getEventsForWeek(startMillis)
            
            if (!settings.showDeclinedEvents) {
                events = events.filter { !it.isDeclined }
            }
            
            // ... (Weights section) ...
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
            
            for (i in validStart..validEnd) {
                val colWidth = (width * (allWeights[i] / totalWeight))
                
                // Draw Divider
                if (i > validStart) {
                    canvas.drawLine(currentX, 0f, currentX, height.toFloat(), linePaint)
                }
                
                // Header
                val dayMillis = startMillis + (i * 24 * 60 * 60 * 1000)
                val dayName = if (i < todayIndex) {
                    dayFormatEEE.format(dayMillis)
                } else {
                    dayFormatEEEd.format(dayMillis)
                }
                
                if (i == todayIndex) {
                    dayHeaderPaint.color = Color.YELLOW 
                    dayHeaderPaint.isFakeBoldText = true
                    dayHeaderPaint.textSize = 90f 
                } else {
                    dayHeaderPaint.color = Color.LTGRAY
                    dayHeaderPaint.isFakeBoldText = false
                    dayHeaderPaint.textSize = 45f
                }
                
                canvas.drawText(dayName, currentX + colWidth / 2, 100f, dayHeaderPaint)
                
                // Column Scale
                val columnScale = when {
                    i < todayIndex -> 1.0f 
                    i > todayIndex -> 1.10f 
                    else -> settings.textSizeScale 
                }
                
                // Events
                val dayEnd = dayMillis + (24 * 60 * 60 * 1000)
                val dayEvents = events.filter { WeeklyDisplayLogic.shouldDisplayEventOnDay(it, dayMillis, dayEnd) }

                // Determine if we should show start times based on whether events will be clipped
                val availableHeight = height - 130f // yPos starts at 110f, clip at height - 20
                val estimatedScale = if (i == todayIndex) settings.textSizeScale else columnScale
                val estimatedTextSize = 48f * estimatedScale
                val estimatedLineHeight = estimatedTextSize * 1.2f
                val showStartTimes = WeeklyDisplayLogic.shouldShowStartTimes(
                    dayEvents.size,
                    availableHeight,
                    estimatedLineHeight
                )

                // Calculate equitable line distribution
                val currentTime = System.currentTimeMillis()

                // For current day, separate past and current/future events
                val (pastEventsOnToday, currentFutureEvents) = if (i == todayIndex) {
                    dayEvents.partition { it.endTime < currentTime }
                } else {
                    Pair(emptyList(), dayEvents)
                }

                // Calculate if we're clipping (opposite of showStartTimes)
                val isClipping = !showStartTimes

                // Debug logging
                if (i == todayIndex) {
                    Log.d("WeeklyWidget", "=== Current Day Debug ===")
                    Log.d("WeeklyWidget", "Total events: ${dayEvents.size}, Past events: ${pastEventsOnToday.size}, Future events: ${currentFutureEvents.size}")
                    Log.d("WeeklyWidget", "Available height: $availableHeight, Line height: $estimatedLineHeight")
                    Log.d("WeeklyWidget", "Show start times: $showStartTimes, Is clipping: $isClipping")
                    Log.d("WeeklyWidget", "Current time: ${SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT).format(Date(currentTime))}")
                }

                // Calculate max lines per event
                val getMaxLinesForEvent: (CalendarEvent) -> Int = { event ->
                    val maxLines = when {
                        !isClipping -> 10 // No clipping, allow generous lines
                        i == todayIndex && event.endTime < currentTime -> 1 // Past events on current day: 1 row
                        i == todayIndex -> {
                            // Current/future events on current day: equitable distribution of remaining space
                            val pastEventsHeight = pastEventsOnToday.size * estimatedLineHeight
                            val remainingHeight = availableHeight - pastEventsHeight
                            if (currentFutureEvents.isEmpty()) {
                                1
                            } else {
                                val maxLinesForCurrentFuture = (remainingHeight / (currentFutureEvents.size * estimatedLineHeight)).toInt().coerceAtLeast(1)
                                maxLinesForCurrentFuture
                            }
                        }
                        else -> {
                            // Past or future days: equitable distribution among all events
                            val maxLines = (availableHeight / (dayEvents.size * estimatedLineHeight)).toInt().coerceAtLeast(1)
                            maxLines
                        }
                    }

                    // Debug logging for each event
                    if (i == todayIndex) {
                        val isPast = event.endTime < currentTime
                        val eventTime = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT).format(Date(event.startTime))
                        val eventEndTime = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT).format(Date(event.endTime))
                        Log.d("WeeklyWidget", "Event: '${event.title}' | Start: $eventTime, End: $eventEndTime | Past: $isPast | MaxLines: $maxLines")
                    }

                    maxLines
                }

                // Debug log showStartTimes
                if (i == todayIndex) {
                    Log.d("WeeklyWidget", "showStartTimes: $showStartTimes")
                }

                var yPos = 110f
                for (event in dayEvents) {
                    canvas.save()
                    canvas.clipRect(currentX, 110f, currentX + colWidth, height.toFloat())

                    textPaint.color = settings.textColor

                    val eventScale = if (i == todayIndex && event.endTime < System.currentTimeMillis()) 0.8f else columnScale
                    textPaint.textSize = 48f * eventScale

                    val lineHeight = textPaint.textSize * 1.2f
                    val dynamicMaxLines = getMaxLinesForEvent(event)

                    val textWidth = (colWidth - 10f).toInt()
                    if (textWidth > 0) {
                        // Check for null title specifically
                        val safeTitle = event.title ?: "No Title"

                        // Add Start Time if not all day and if we have room to show all events
                        val finalText: CharSequence = if (showStartTimes && !event.isAllDay) {
                                                          val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)
                                                          val timeString = timeFormat.format(Date(event.startTime))
                                                          val spannable = SpannableString("$timeString $safeTitle")

                                                          spannable.setSpan(
                                                              ForegroundColorSpan(settings.startTimeColor),
                                                              0,
                                                              timeString.length,
                                                              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                                          )

                                                          spannable.setSpan(object : CharacterStyle() {
                                                              override fun updateDrawState(tp: android.text.TextPaint) {
                                                                  tp.setShadowLayer(4f, 2f, 2f, settings.startTimeShadowColor)
                                                              }
                                                          }, 0, timeString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                                                          spannable
                        } else {
                            safeTitle
                        }

                        try {
                            val builder = android.text.StaticLayout.Builder.obtain(
                                finalText, 0, finalText.length, textPaint, textWidth
                            )
                            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                            .setLineSpacing(0f, 1f)
                            .setIncludePad(false)
                            .setMaxLines(dynamicMaxLines)
                            .setEllipsize(android.text.TextUtils.TruncateAt.END)

                            val layout = builder.build()

                            // Debug logging for layout
                            if (i == todayIndex) {
                                Log.d("WeeklyWidget", "Layout for '${event.title}': text='$finalText', textWidth=$textWidth, maxLines=$dynamicMaxLines, actualLines=${layout.lineCount}, height=${layout.height}")
                            }

                            canvas.translate(currentX + 5f, yPos)
                            layout.draw(canvas)
                            canvas.translate(-(currentX + 5f), -yPos)

                            yPos += layout.height + (textPaint.textSize * 0.2f)
                        } catch (e: Exception) {
                            Log.e("WeeklyWidget", "StaticLayout crash: title='$safeTitle', width=$textWidth", e)
                        }
                    }
                    
                    canvas.restore()
                }
                
                currentX += colWidth
            }
            
            return bitmap
            } catch (e: Exception) {
                Log.e("WeeklyWidgetProvider", "Error drawing widget", e)
                // Return a blank error bitmap
                val errorBitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
                val c = Canvas(errorBitmap)
                val p = Paint().apply { color = Color.RED; textSize = 40f }
                c.drawText("Error: ${e.localizedMessage}", 50f, 200f, p)
                return errorBitmap
            }
        }
    }
}