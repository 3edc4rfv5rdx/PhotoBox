package xx.photobox.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoZoomTest {
    private val size = IntSize(1000, 2000)
    private val center = Offset(500f, 1000f)

    /** Where a spot of the unzoomed photo, [at] on screen, lands with [zoom] applied about the middle. */
    private fun shown(zoom: PhotoZoom, at: Offset): Offset = center + zoom.offset + (at - center) * zoom.scale

    @Test
    fun theSpotBetweenTheFingersStaysUnderThem() {
        val zoom = PhotoZoom()
        val fingers = Offset(700f, 600f)
        zoom.transform(2f, Offset.Zero, fingers, size)
        assertEquals(2f, zoom.scale)
        assertEquals(fingers, shown(zoom, fingers))
    }

    @Test
    fun aSecondStepKeepsTheSameSpotToo() {
        val zoom = PhotoZoom()
        val fingers = Offset(300f, 1500f)
        zoom.transform(1.5f, Offset.Zero, fingers, size)
        zoom.transform(1.5f, Offset.Zero, fingers, size)
        assertEquals(2.25f, zoom.scale, 1e-4f)
        val spot = shown(zoom, fingers)
        assertEquals(fingers.x, spot.x, 1e-2f)
        assertEquals(fingers.y, spot.y, 1e-2f)
    }

    @Test
    fun zoomStopsAtItsEndsAndFullyOutIsCentred() {
        val zoom = PhotoZoom()
        zoom.transform(100f, Offset.Zero, Offset(10f, 10f), size)
        assertEquals(5f, zoom.scale)
        zoom.transform(0.01f, Offset(50f, 50f), Offset(10f, 10f), size)
        assertEquals(1f, zoom.scale)
        assertEquals(Offset.Zero, zoom.offset)
    }

    @Test
    fun panCannotDragThePhotoOffTheScreen() {
        val zoom = PhotoZoom()
        zoom.transform(2f, Offset.Zero, center, size)
        zoom.transform(1f, Offset(10_000f, -10_000f), center, size)
        assertEquals(Offset(500f, -1000f), zoom.offset)
    }
}
