package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DailyWidgetRenderer {

    internal data class TomorrowIndicatorHeaderLayout(
        val dayName: String,
        val indicator: String,
        val rainbowTextSize: Float,
        val spacing: Float
    )

    internal const val TOMORROW_INDICATOR = "\uD83C\uDF08"

    private const val RAINBOW_INITIAL_SCALE = 0.62f
    private const val RAINBOW_MIN_SCALE = 0.35f
    private const val RAINBOW_SCALE_STEP = 0.05f
    private const val RAINBOW_MIN_TEXT_SIZE = 18f
    private const val RAINBOW_MAX_TEXT_SIZE = 44f
    private const val RAINBOW_SPACING_FACTOR = 0.12f
    private const val RAINBOW_MIN_SPACING = 2f
    private const val RAINBOW_HORIZONTAL_PADDING = 10f
    private const val HEADER_SCALE_DOWN_FACTOR = 0.9f
    private const val HEADER_MIN_TEXT_SIZE = 10f
    private const val HEADER_MAX_SCALE_ITERATIONS = 20
    private const val DAILY_CELL_WIDTH_DP = 92f
    private const val TAG = "CalendarImageGenerator"

    fun draw(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Bitmap {
        val size = WidgetColumnRenderer.calculateWidgetSize(context, appWidgetManager, appWidgetId)
        try {
            Log.d(TAG, "Starting drawDailyCalendar for ID: $appWidgetId")
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
            val cols = calculateNumberOfDays(context, size.widthPx)

            val startCalendar = Calendar.getInstance()
            var didAutoAdvance = false

            val repo = CalendarRepository(context)
            val selectedIds = settings.selectedCalendarIds.ifEmpty { repo.getDefaultCalendarIds() }
            val selection = selectedIds.ifEmpty { null }

            val checkStart = WidgetColumnRenderer.calculateStartDate(startCalendar)
            val todayEnd = checkStart + WidgetColumnRenderer.DAY_MILLIS
            val todayEvents = repo.getEvents(checkStart, 1, selection).filter {
                WeeklyDisplayLogic.shouldDisplayEventOnDay(it, checkStart, todayEnd)
            }
            val autoAdvanceNow = System.currentTimeMillis()

            if (DailyDisplayLogic.shouldAutoAdvance(
                    todayEvents,
                    autoAdvanceNow,
                    settings.dailyAutoAdvanceCutoffMinuteOfDay
                )
            ) {
                startCalendar.add(Calendar.DAY_OF_YEAR, 1)
                didAutoAdvance = true
                Log.d(TAG, "All events for today are in the past. Auto-advancing to tomorrow.")
            }

            val startMillis = WidgetColumnRenderer.calculateStartDate(startCalendar)
            val dm = context.resources.displayMetrics
            val heightDp = size.heightPx / dm.density
            val grid = DailyDisplayLogic.computeGridLayout(cols, heightDp, settings.twoLineModeEnabled)

            val events = WidgetColumnRenderer.fetchAndFilterEvents(context, settings, startMillis, grid.days)

            val globalDayMillisList = (0 until grid.days).map { startMillis + it * WidgetColumnRenderer.DAY_MILLIS }

            val bandHeight = size.heightPx / grid.rows
            val config = WidgetColumnRenderer.ColumnRenderConfig(
                pastEventScaleFactor = 0.8f,
                useCompressDeclinedMaxLines = true,
                useTimeScaleForPast = false,
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
                    WidgetColumnRenderer.daysBetween(startMillis, todayMillis).coerceIn(0, rowDayMillisList.size - 1)
                } else {
                    -1
                }

                val rowWeights = WidgetColumnRenderer.computeAdjustedWeights(
                    events,
                    DailyDisplayLogic.getColumnWeights(rowTodayIndex, rowDayMillisList.size),
                    rowDayMillisList
                )

                WidgetColumnRenderer.renderDayColumns(
                    canvas, size.widthPx, bandHeight, paints, settings, events,
                    rowWeights, rowTodayIndex, rowDayMillisList, config, timeFormat, now,
                    topOffset = (r * bandHeight).toFloat()
                ) { colIndex, dayMillis, isToday, currentX, colWidth, top ->
                    val globalIndex = rowStart + colIndex
                    val dayName = if (globalIndex < WidgetColumnRenderer.daysBetween(startMillis, todayMillis)) {
                        dayFormatEEE.format(dayMillis)
                    } else {
                        dayFormatEEEd.format(dayMillis)
                    }
                    drawDayHeader(
                        canvas, paints, appWidgetId, globalIndex, isToday, currentX, colWidth,
                        didAutoAdvance, dayName, top
                    )
                }
            }

            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing daily widget", e)
            return WidgetColumnRenderer.createErrorBitmap(e, size.widthPx, size.heightPx, context)
        }
    }

    private fun calculateNumberOfDays(context: Context, widthPx: Int): Int {
        val dm = context.resources.displayMetrics
        val widthDp = widthPx / dm.density

        val cellWidthDp = DAILY_CELL_WIDTH_DP
        val numDays = ((widthDp / cellWidthDp) + 0.5f).toInt().coerceIn(1, 7)
        Log.d(TAG, "calculateNumberOfDays: px=$widthPx, density=${dm.density}, dp=$widthDp, cellWidth=$cellWidthDp -> numDays=$numDays")
        return numDays
    }

    internal fun drawDayHeader(
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

        val headerBaseline = WidgetColumnRenderer.verticallyCenterBaseline(paints.dayHeaderPaint, topOffset + WidgetColumnRenderer.CONTENT_TOP / 2f)
        val centerX = currentX + colWidth / 2f
        if (shouldDrawTomorrowIndicator(didAutoAdvance, colIndex)) {
            val indicatorDrawn = drawHeaderWithTomorrowIndicator(
                canvas = canvas,
                dayName = dayName,
                centerX = centerX,
                centerY = topOffset + WidgetColumnRenderer.CONTENT_TOP / 2f,
                colWidth = colWidth,
                basePaint = paints.dayHeaderPaint
            )
            if (indicatorDrawn) {
                Log.d(TAG, "Drawing tomorrow indicator for daily widget $appWidgetId")
            } else {
                canvas.drawText(dayName, centerX, headerBaseline, paints.dayHeaderPaint)
                Log.d(TAG, "Skipped tomorrow indicator due to narrow column for widget $appWidgetId")
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
        val paint = Paint(basePaint)
        val horizontalPadding = RAINBOW_HORIZONTAL_PADDING
        val availableWidth = (colWidth - horizontalPadding).coerceAtLeast(1f)

        var baseTextWidth = paint.measureText(dayName)
        var layout = buildTomorrowIndicatorHeaderLayout(
            dayName = dayName,
            dayNameWidth = baseTextWidth,
            baseTextSize = paint.textSize,
            availableWidth = availableWidth
        ) { candidateTextSize ->
            val rainbowPaint = Paint(paint).apply { textSize = candidateTextSize }
            rainbowPaint.measureText(TOMORROW_INDICATOR)
        }

        var scaleDownCount = 0
        while ((layout == null || baseTextWidth >= availableWidth) && paint.textSize > HEADER_MIN_TEXT_SIZE && scaleDownCount < HEADER_MAX_SCALE_ITERATIONS) {
            paint.textSize *= HEADER_SCALE_DOWN_FACTOR
            baseTextWidth = paint.measureText(dayName)
            layout = buildTomorrowIndicatorHeaderLayout(
                dayName = dayName,
                dayNameWidth = baseTextWidth,
                baseTextSize = paint.textSize,
                availableWidth = availableWidth
            ) { candidateTextSize ->
                val rainbowPaint = Paint(paint).apply { textSize = candidateTextSize }
                rainbowPaint.measureText(TOMORROW_INDICATOR)
            }
            scaleDownCount++
        }

        if (layout == null || baseTextWidth >= availableWidth) {
            return false
        }

        val rainbowPaint = Paint(paint).apply { textSize = layout.rainbowTextSize }
        val rainbowWidth = rainbowPaint.measureText(layout.indicator)
        val combinedWidth = baseTextWidth + layout.spacing + rainbowWidth
        val leftX = centerX - (combinedWidth / 2f)
        val baseCenterX = leftX + (baseTextWidth / 2f)
        val rainbowCenterX = leftX + baseTextWidth + layout.spacing + (rainbowWidth / 2f)

        val baseBaseline = WidgetColumnRenderer.verticallyCenterBaseline(paint, centerY)
        val rainbowBaseline = WidgetColumnRenderer.verticallyCenterBaseline(rainbowPaint, centerY)

        canvas.drawText(dayName, baseCenterX, baseBaseline, paint)
        canvas.drawText(layout.indicator, rainbowCenterX, rainbowBaseline, rainbowPaint)
        return true
    }

    internal fun calculateRainbowIndicatorTextSize(
        dayNameWidth: Float,
        baseTextSize: Float,
        availableWidth: Float,
        rainbowWidthAtTextSize: (Float) -> Float
    ): Float? {
        val spacing = (baseTextSize * RAINBOW_SPACING_FACTOR).coerceAtLeast(RAINBOW_MIN_SPACING)
        var scale = RAINBOW_INITIAL_SCALE

        while (scale >= RAINBOW_MIN_SCALE) {
            val candidateTextSize = (baseTextSize * scale).coerceIn(RAINBOW_MIN_TEXT_SIZE, RAINBOW_MAX_TEXT_SIZE)
            val rainbowWidth = rainbowWidthAtTextSize(candidateTextSize)
            val combinedWidth = dayNameWidth + spacing + rainbowWidth
            if (combinedWidth <= availableWidth) {
                return candidateTextSize
            }
            scale -= RAINBOW_SCALE_STEP
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
        val spacing = (baseTextSize * RAINBOW_SPACING_FACTOR).coerceAtLeast(RAINBOW_MIN_SPACING)
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
}
