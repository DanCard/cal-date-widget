package ai.dcar.caldatewidget

import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class WidgetSizeSelectionTest {

    // Reported by the Pixel launcher on a 1280x800dp tablet emulator.
    private val tabletPortrait = SizeF(456f, 309f)
    private val tabletLandscape = SizeF(672f, 233f)

    @Test
    fun `tablet in landscape uses the landscape size even when portrait is listed first`() {
        val selected = WidgetColumnRenderer.selectSizeForScreen(
            listOf(tabletPortrait, tabletLandscape), screenWidthDp = 1280, screenHeightDp = 752
        )
        assertEquals(tabletLandscape, selected)
    }

    @Test
    fun `tablet in portrait uses the portrait size even when landscape is listed first`() {
        val selected = WidgetColumnRenderer.selectSizeForScreen(
            listOf(tabletLandscape, tabletPortrait), screenWidthDp = 800, screenHeightDp = 1232
        )
        assertEquals(tabletPortrait, selected)
    }

    @Test
    fun `foldable cover screen ignores sizes that only fit the inner screen`() {
        val cover = SizeF(320f, 200f)
        val inner = SizeF(700f, 400f)
        val selected = WidgetColumnRenderer.selectSizeForScreen(
            listOf(inner, cover), screenWidthDp = 344, screenHeightDp = 780
        )
        assertEquals(cover, selected)
    }

    @Test
    fun `falls back to a reported size when none fit the screen`() {
        val selected = WidgetColumnRenderer.selectSizeForScreen(
            listOf(tabletLandscape), screenWidthDp = 320, screenHeightDp = 480
        )
        assertEquals(tabletLandscape, selected)
    }

    @Test
    fun `reportedSizesDp drops duplicate and empty sizes`() {
        val options = Bundle().apply {
            putParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(tabletPortrait, tabletLandscape, tabletPortrait, SizeF(0f, 0f))
            )
        }
        assertEquals(listOf(tabletPortrait, tabletLandscape), WidgetColumnRenderer.reportedSizesDp(options))
    }

    @Test
    fun `reportedSizesDp is empty when the launcher reports nothing`() {
        assertTrue(WidgetColumnRenderer.reportedSizesDp(Bundle()).isEmpty())
    }
}
