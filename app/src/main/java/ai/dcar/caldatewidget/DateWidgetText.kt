package ai.dcar.caldatewidget

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import java.text.CharacterIterator
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the date-widget text and locates the day-of-month field within it so the day number
 * can be styled (bigger + accent color) independently of the rest, for *any* date-format pattern.
 *
 * Finding the day token is done with [SimpleDateFormat.formatToCharacterIterator], which tags each
 * output character with a [DateFormat.Field]; this is locale-correct and pattern-agnostic, so it
 * works for "EEE d", "MM/dd/yyyy", "yyyy-MM-dd", etc. without substring guessing.
 *
 * The two span types applied by [applyDayStyle] ([RelativeSizeSpan], [ForegroundColorSpan]) are
 * both in the set that survives `RemoteViews` parceling, so the styling reaches the launcher.
 */
object DateWidgetText {

    /** How much larger the day-of-month renders vs. the rest of the date text (close to parity). */
    const val DEFAULT_DAY_SIZE_FACTOR = 1.4f

    /** Formatted text plus the character range of the day-of-month field within it (or null). */
    data class Formatted(val text: String, val dayRange: IntRange?)

    fun format(pattern: String, date: Date, locale: Locale): Formatted {
        return try {
            val sdf = SimpleDateFormat(pattern, locale)
            val iter = sdf.formatToCharacterIterator(date)
            val sb = StringBuilder()
            var start = -1
            var end = -1
            var idx = 0
            var c = iter.first()
            while (c != CharacterIterator.DONE) {
                sb.append(c)
                if (iter.getAttribute(DateFormat.Field.DAY_OF_MONTH) != null) {
                    if (start < 0) start = idx
                    end = idx + 1
                }
                idx++
                c = iter.next()
            }
            Formatted(sb.toString(), if (start >= 0) start until end else null)
        } catch (e: Exception) {
            Formatted("Invalid Format", null)
        }
    }

    /**
     * Returns a styled copy of [base] with [dayRange] enlarged by [sizeFactor] and recolored to
     * [dayColor]. The rest of the text keeps the TextView's own size/color, so the day number reads
     * bigger and the weekday/remainder reads smaller by comparison. Returns [base] unchanged when
     * there is no day field to style.
     */
    fun applyDayStyle(base: CharSequence, dayRange: IntRange?, dayColor: Int, sizeFactor: Float): CharSequence {
        if (dayRange == null || dayRange.isEmpty()) return base
        val s = dayRange.first
        val e = dayRange.last + 1
        if (s < 0 || e > base.length) return base
        return SpannableString(base).apply {
            setSpan(RelativeSizeSpan(sizeFactor), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(dayColor), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
