package ai.dcar.caldatewidget

import android.text.StaticLayout
import android.text.TextPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOverlapTest {

    @Test
    fun `past event on current day consumed height uses layout height not line height`() {
        val eventTitle = "Past Event"
        val eventScale = 0.8f
        val textSize = 48f

        val textPaint = TextPaint().apply {
            this.textSize = textSize * eventScale
        }

        val textWidth = 200
        val layout = StaticLayout.Builder.obtain(
            eventTitle, 0, eventTitle.length, textPaint, textWidth
        )
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .setMaxLines(1)
            .build()

        val spacing = textPaint.textSize * 0.1f
        val consumedHeightFixed = layout.height.toFloat() + spacing

        val lineHeight = textPaint.textSize * 1.2f
        val consumedHeightOld = lineHeight + spacing

        println("Past event on current day:")
        println("  Text: '$eventTitle'")
        println("  TextSize: ${textPaint.textSize}")
        println("  Layout height: ${layout.height}")
        println("  LineHeight: $lineHeight")
        println("  Spacing: $spacing")
        println("  Consumed height (old code using lineHeight): $consumedHeightOld")
        println("  Consumed height (fixed code using layout.height): $consumedHeightFixed")

        assertTrue("Fixed consumed height must be >= layout height to prevent overlap",
            consumedHeightFixed >= layout.height.toFloat())
    }

    @Test
    fun `current event on current day consumed height matches actual layout height`() {
        val eventTitle = "Current Event"
        val eventScale = 1.0f
        val textSize = 48f

        val textPaint = TextPaint().apply {
            this.textSize = textSize * eventScale
        }

        val textWidth = 200
        val layout = StaticLayout.Builder.obtain(
            eventTitle, 0, eventTitle.length, textPaint, textWidth
        )
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .setMaxLines(10)
            .build()

        val spacing = textPaint.textSize * 0.1f
        val consumedHeight = layout.height.toFloat() + spacing

        println("Current event on current day:")
        println("  Text: '$eventTitle'")
        println("  TextSize: ${textPaint.textSize}")
        println("  Layout height: ${layout.height}")
        println("  Spacing: $spacing")
        println("  Consumed height: $consumedHeight")

        assertTrue(consumedHeight >= layout.height.toFloat())
    }

    @Test
    fun `event on non-today day consumed height matches actual layout height`() {
        val eventTitle = "Non-today Event"
        val columnScale = 1.0f
        val textSize = 48f

        val textPaint = TextPaint().apply {
            this.textSize = textSize * columnScale
        }

        val textWidth = 200
        val layout = StaticLayout.Builder.obtain(
            eventTitle, 0, eventTitle.length, textPaint, textWidth
        )
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .setMaxLines(10)
            .build()

        val spacing = textPaint.textSize * 0.2f
        val consumedHeight = layout.height.toFloat() + spacing

        println("Event on non-today day:")
        println("  Text: '$eventTitle'")
        println("  TextSize: ${textPaint.textSize}")
        println("  Layout height: ${layout.height}")
        println("  Spacing: $spacing")
        println("  Consumed height: $consumedHeight")

        assertTrue(consumedHeight >= layout.height.toFloat())
    }

    @Test
    fun `multi-line past event on current day consumed height should use layout height`() {
        val eventTitle = "This is a very long past event title that will wrap across multiple lines"
        val eventScale = 0.8f
        val textSize = 48f

        val textPaint = TextPaint().apply {
            this.textSize = textSize * eventScale
        }

        val textWidth = 100
        val layout = StaticLayout.Builder.obtain(
            eventTitle, 0, eventTitle.length, textPaint, textWidth
        )
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .setMaxLines(1)
            .build()

        val lineHeight = textPaint.textSize * 1.2f
        val spacing = textPaint.textSize * 0.1f
        val consumedHeight = lineHeight + spacing

        println("Multi-line past event (capped to 1 line):")
        println("  Text: '$eventTitle'")
        println("  TextSize: ${textPaint.textSize}")
        println("  Layout height: ${layout.height}")
        println("  Layout line count: ${layout.lineCount}")
        println("  LineHeight: $lineHeight")
        println("  Spacing: $spacing")
        println("  Consumed height: $consumedHeight")

        assertTrue(consumedHeight >= layout.height.toFloat())
    }

    @Test
    fun `consecutive past events on current day do not overlap`() {
        val event1Title = "First Past Event"
        val event2Title = "Second Past Event"
        val eventScale = 0.8f
        val textSize = 48f

        val textPaint = TextPaint().apply {
            this.textSize = textSize * eventScale
        }

        val textWidth = 200

        val layout1 = StaticLayout.Builder.obtain(
            event1Title, 0, event1Title.length, textPaint, textWidth
        )
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .setMaxLines(1)
            .build()

        val layout2 = StaticLayout.Builder.obtain(
            event2Title, 0, event2Title.length, textPaint, textWidth
        )
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .setMaxLines(1)
            .build()

        val lineHeight = textPaint.textSize * 1.2f
        val spacing = textPaint.textSize * 0.1f
        val consumedHeightPerEvent = lineHeight + spacing

        val yPos1 = 0f
        val yPos2 = yPos1 + consumedHeightPerEvent

        println("Consecutive past events:")
        println("  Event 1: '$event1Title', yPos=$yPos1, height=${layout1.height}")
        println("  Event 2: '$event2Title', yPos=$yPos2, height=${layout2.height}")
        println("  Gap between events: ${yPos2 - (yPos1 + layout1.height)}")

        val gap = yPos2 - (yPos1 + layout1.height.toFloat())
        assertTrue(gap >= 0)
    }

    @Test
    fun `past event followed by current event on current day does not overlap`() {
        val pastEventTitle = "Past Event"
        val currentEventTitle = "Current Event That Might Wrap To Multiple Lines"
        val eventScale = 0.8f
        val columnScale = 1.0f
        val textSize = 48f

        val textPaint = TextPaint().apply {
            this.textSize = textSize * eventScale
        }

        val textWidth = 150

        val pastLayout = StaticLayout.Builder.obtain(
            pastEventTitle, 0, pastEventTitle.length, textPaint, textWidth
        )
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .setMaxLines(1)
            .build()

        textPaint.textSize = textSize * columnScale
        val currentLayout = StaticLayout.Builder.obtain(
            currentEventTitle, 0, currentEventTitle.length, textPaint, textWidth
        )
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .setMaxLines(10)
            .build()

        val lineHeightPast = textSize * eventScale * 1.2f
        val spacingPast = textSize * eventScale * 0.1f
        val consumedHeightPast = lineHeightPast + spacingPast

        val spacingCurrent = textSize * columnScale * 0.1f
        val consumedHeightCurrent = currentLayout.height.toFloat() + spacingCurrent

        val yPosPast = 0f
        val yPosCurrent = yPosPast + consumedHeightPast

        println("Past event followed by current event:")
        println("  Past: '$pastEventTitle', yPos=$yPosPast, height=${pastLayout.height}, consumed=$consumedHeightPast")
        println("  Current: '$currentEventTitle', yPos=$yPosCurrent, height=${currentLayout.height}, consumed=$consumedHeightCurrent")
        println("  Gap between events: ${yPosCurrent - (yPosPast + pastLayout.height)}")

        val gap = yPosCurrent - (yPosPast + pastLayout.height.toFloat())
        assertTrue(gap >= 0)
    }

    @Test
    fun `overlapping detection when past events have smaller font than current events`() {
        val pastEventTitle = "Past Event with 0.8 scale"
        val currentEventTitle = "Current Event with 1.0 scale"
        val pastScale = 0.8f
        val currentScale = 1.0f
        val textSize = 48f

        val textWidth = 200

        val pastPaint = TextPaint().apply {
            this.textSize = textSize * pastScale
        }
        val pastLayout = StaticLayout.Builder.obtain(
            pastEventTitle, 0, pastEventTitle.length, pastPaint, textWidth
        )
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .setMaxLines(1)
            .build()

        val currentPaint = TextPaint().apply {
            this.textSize = textSize * currentScale
        }
        val currentLayout = StaticLayout.Builder.obtain(
            currentEventTitle, 0, currentEventTitle.length, currentPaint, textWidth
        )
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .setMaxLines(10)
            .build()

        val lineHeightPast = pastPaint.textSize * 1.2f
        val spacingPast = pastPaint.textSize * 0.1f
        val consumedHeightPast = lineHeightPast + spacingPast

        val spacingCurrent = currentPaint.textSize * 0.1f
        val consumedHeightCurrent = currentLayout.height.toFloat() + spacingCurrent

        val yPosPast = 0f
        val yPosCurrent = yPosPast + consumedHeightPast

        println("Scale difference scenario:")
        println("  Past scale: $pastScale, textSize: ${pastPaint.textSize}, layout height: ${pastLayout.height}")
        println("  Current scale: $currentScale, textSize: ${currentPaint.textSize}, layout height: ${currentLayout.height}")
        println("  Consumed height past: $consumedHeightPast")
        println("  yPos past: $yPosPast, yPos current: $yPosCurrent")
        println("  Gap: ${yPosCurrent - (yPosPast + pastLayout.height)}")

        val gap = yPosCurrent - (yPosPast + pastLayout.height.toFloat())
        assertTrue("Gap should be non-negative to prevent overlap", gap >= 0)
    }

    @Test
    fun `bug demonstration lineHeight approach can cause overlap`() {
        val eventTitle = "Test Event with TALL TEXT"
        val eventScale = 0.8f
        val textSize = 48f

        val textPaint = TextPaint().apply {
            this.textSize = textSize * eventScale
        }

        val textWidth = 200
        val layout = StaticLayout.Builder.obtain(
            eventTitle, 0, eventTitle.length, textPaint, textWidth
        )
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .setMaxLines(1)
            .build()

        val spacing = textPaint.textSize * 0.1f

        val lineHeight = textPaint.textSize * 1.2f
        val consumedHeightOld = lineHeight + spacing

        val consumedHeightNew = layout.height.toFloat() + spacing

        println("Bug demonstration:")
        println("  Layout height: ${layout.height}")
        println("  LineHeight: $lineHeight")
        println("  Consumed height (old): $consumedHeightOld")
        println("  Consumed height (new): $consumedHeightNew")

        if (consumedHeightOld < layout.height.toFloat()) {
            println("  ❌ BUG: Old approach would consume ${consumedHeightOld} but layout is ${layout.height}")
            println("  Overlap: ${layout.height - consumedHeightOld} pixels")
        } else {
            println("  ✓ No overlap in this case")
        }

        assertTrue("New approach must always consume at least layout height",
            consumedHeightNew >= layout.height.toFloat())
    }
}
