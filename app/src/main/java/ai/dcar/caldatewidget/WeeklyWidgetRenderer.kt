package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar

object WeeklyWidgetRenderer {

    private const val TAG = "CalendarImageGenerator"

    fun draw(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Bitmap {
        val size = WidgetColumnRenderer.calculateWidgetSize(context, appWidgetManager, appWidgetId)
        try {
            Log.d(TAG, "Starting drawWeeklyCalendar for ID: $appWidgetId")
            val prefsManager = PrefsManager(context)
            val settings = prefsManager.loadSettings(appWidgetId)

            val bitmap = Bitmap.createBitmap(size.widthPx, size.heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            WidgetColumnRenderer.drawBackground(canvas, settings)

            if (!WidgetColumnRenderer.hasCalendarPermission(context)) {
                WidgetColumnRenderer.drawPermissionRequired(canvas, size, settings, context)
                return bitmap
            }

            val paints = WidgetRenderingHelper.createPaints(settings)

            val todayMillis = WidgetColumnRenderer.startOfDayMillis()
            val startMillis = WeeklyDisplayLogic.getStartDate(Calendar.getInstance(), settings.weekStartDay)

            val dm = context.resources.displayMetrics
            val heightDp = size.heightPx / dm.density
            val weeks = WeeklyDisplayLogic.weeksToShow(settings.twoLineModeEnabled, heightDp)

            val totalDays = 7 * weeks
            val todayIndexInWeek = WidgetColumnRenderer.daysBetween(startMillis, todayMillis).coerceIn(0, 6)

            val globalDayMillisList = (0 until totalDays).map { globalIdx ->
                val w = globalIdx / 7
                val i = globalIdx % 7
                val baseEffective = WeeklyDisplayLogic.getEffectiveDayMillis(startMillis, i, todayIndexInWeek)
                baseEffective + (w * 7 * WidgetColumnRenderer.DAY_MILLIS)
            }

            val allEvents = WidgetColumnRenderer.fetchAndFilterEvents(context, settings, startMillis, totalDays + 7)
            val bandHeight = size.heightPx / weeks
            val now = System.currentTimeMillis()
            val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)

            for (w in 0 until weeks) {
                val rowDayMillisList = globalDayMillisList.subList(w * 7, (w + 1) * 7)

                val rowTodayIndex = if (w == 0) todayIndexInWeek else -1

                val baseRowWeights = WeeklyDisplayLogic.getColumnWeights(rowTodayIndex, 7)
                val rowWeights = WidgetColumnRenderer.computeAdjustedWeights(allEvents, baseRowWeights, rowDayMillisList)

                val config = WidgetColumnRenderer.ColumnRenderConfig(
                    pastEventScaleFactor = 0.7f,
                    useCompressDeclinedMaxLines = true,
                    useTimeScaleForPast = false,
                    widgetTag = "Weekly"
                )

                WidgetColumnRenderer.renderDayColumns(
                    canvas, size.widthPx, bandHeight, paints, settings, allEvents,
                    rowWeights, rowTodayIndex, rowDayMillisList, config, timeFormat, now,
                    topOffset = (w * bandHeight).toFloat()
                ) { _, dayMillis, isToday, currentX, colWidth, top ->
                    drawDayHeader(canvas, paints, dayMillis, isToday, currentX, colWidth, top)
                }
            }

            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing weekly widget", e)
            return WidgetColumnRenderer.createErrorBitmap(e, size.widthPx, size.heightPx, context)
        }
    }

    internal fun drawDayHeader(
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
            WidgetColumnRenderer.verticallyCenterBaseline(paints.dayHeaderPaint, topOffset + WidgetColumnRenderer.CONTENT_TOP / 2f),
            paints.dayHeaderPaint
        )
    }
}
