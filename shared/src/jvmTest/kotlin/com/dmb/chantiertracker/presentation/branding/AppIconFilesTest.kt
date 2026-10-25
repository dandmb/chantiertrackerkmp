package com.dmb.chantiertracker.presentation.branding

import androidx.compose.ui.graphics.toArgb
import com.dmb.chantiertracker.presentation.theme.Yellow
import org.w3c.dom.Element
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppIconFilesTest {

    private val repository = File(System.getProperty("user.dir")).parentFile
    private val androidRes = File(repository, "androidApp/src/main/res")
    private val androidNamespace = "http://schemas.android.com/apk/res/android"
    private val oldToolsGlyph = "M13.7829,15.1718"

    private class VectorPath(val pathData: String, val color: String, val strokeWidth: Float, val roundCap: Boolean)

    private class Vector(val scale: Float, val translateX: Float, val translateY: Float, val paths: List<VectorPath>)

    private fun xml(path: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(androidRes, path)).documentElement

    private fun Element.android(name: String): String = getAttributeNS(androidNamespace, name)

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun vector(path: String): Vector {
        val root = xml(path)
        assertEquals("108", root.android("viewportWidth"), "$path: viewport of an adaptive layer")
        val group = root.children("group").single()
        return Vector(
            scale = group.android("scaleX").toFloat().also { assertEquals(it, group.android("scaleY").toFloat()) },
            translateX = group.android("translateX").toFloat(),
            translateY = group.android("translateY").toFloat(),
            paths = root.children("path").map {
                val stroke = it.android("strokeColor")
                VectorPath(
                    pathData = it.android("pathData"),
                    color = stroke.ifEmpty { it.android("fillColor") }.uppercase(),
                    strokeWidth = if (stroke.isEmpty()) 0f else it.android("strokeWidth").toFloat(),
                    roundCap = it.android("strokeLineCap") == "round",
                )
            },
        )
    }

    private fun hex(color: androidx.compose.ui.graphics.Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

    private fun png(path: String): BufferedImage = ImageIO.read(File(repository, path))

    private fun BufferedImage.pixels(): Sequence<Triple<Int, Int, Int>> =
        (0 until height).asSequence().flatMap { y -> (0 until width).asSequence().map { x -> Triple(x, y, getRGB(x, y)) } }

    private fun isYellow(argb: Int): Boolean {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return argb ushr 24 > 200 && r > 230 && g in 190..230 && b < 40
    }

    private fun isDrawn(argb: Int): Boolean {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        return argb ushr 24 > 0 && maxOf(r, g) > 40
    }

    private fun assertShowsTheNewLogo(image: BufferedImage, name: String) {
        assertTrue(image.pixels().count { isYellow(it.third) } > image.width * image.height / 400, "$name: the yellow hook and load of the crane are missing")
    }

    private fun assertDrawingInsideTheRoundMask(image: BufferedImage, name: String) {
        val radius = image.width * 33.0 / 108 + 1
        val center = image.width / 2.0
        val outside = image.pixels().filter { (x, y, argb) -> isDrawn(argb) && hypot(x + 0.5 - center, y + 0.5 - center) > radius }.count()
        assertEquals(0, outside, "$name: pixels of the drawing outside the safe zone (circle of 66/108)")
    }

    @Test
    fun both_adaptive_icons_use_the_black_background_the_logo_and_a_dedicated_monochrome_layer() {
        listOf("mipmap-anydpi-v26/ic_launcher.xml", "mipmap-anydpi-v26/ic_launcher_round.xml").forEach { path ->
            val icon = xml(path)
            assertEquals("@color/ic_launcher_background", icon.children("background").single().android("drawable"), path)
            assertEquals("@drawable/ic_launcher_foreground", icon.children("foreground").single().android("drawable"), path)
            assertEquals("@drawable/ic_launcher_monochrome", icon.children("monochrome").single().android("drawable"), path)
        }
        assertEquals("#0A0A0A", xml("color/ic_launcher_background.xml").children("item").single().android("color").uppercase())
    }

    @Test
    fun the_launcher_foreground_draws_the_logo_of_the_app_in_white_and_yellow() {
        val foreground = vector("drawable/ic_launcher_foreground.xml")
        assertFalse(foreground.paths.any { it.pathData.startsWith(oldToolsGlyph) }, "the old tools glyph is still drawn")
        assertEquals(LogoScale, foreground.scale)
        assertEquals(LogoTranslateX, foreground.translateX)
        assertEquals(LogoTranslateY, foreground.translateY)
        assertEquals(LogoPaths.map { it.pathData }, foreground.paths.map { it.pathData })
        assertEquals(LogoPaths.map { hex(it.color) }, foreground.paths.map { it.color })
        assertEquals(LogoPaths.map { it.strokeWidth }, foreground.paths.map { it.strokeWidth })
        assertEquals(LogoPaths.map { it.roundCap }, foreground.paths.map { it.roundCap })
        assertEquals(setOf("#FFFFFF", "#FFD400"), foreground.paths.map { it.color }.toSet())
    }

    @Test
    fun the_monochrome_layer_is_the_same_drawing_in_white_only() {
        val foreground = vector("drawable/ic_launcher_foreground.xml")
        val monochrome = vector("drawable/ic_launcher_monochrome.xml")
        assertEquals(foreground.scale, monochrome.scale)
        assertEquals(foreground.translateX, monochrome.translateX)
        assertEquals(foreground.translateY, monochrome.translateY)
        assertEquals(foreground.paths.map { it.pathData }, monochrome.paths.map { it.pathData })
        assertEquals(foreground.paths.map { it.strokeWidth }, monochrome.paths.map { it.strokeWidth })
        assertEquals(setOf("#FFFFFF"), monochrome.paths.map { it.color }.toSet())
    }

    @Test
    fun the_splash_screen_icon_is_the_launcher_drawing_on_the_black_background() {
        val foreground = vector("drawable/ic_launcher_foreground.xml")
        val splash = vector("drawable/splash_icon.xml")
        assertEquals(foreground.scale, splash.scale)
        assertEquals(foreground.translateX, splash.translateX)
        assertEquals(foreground.translateY, splash.translateY)
        assertEquals(foreground.paths.map { it.pathData to it.color }, splash.paths.map { it.pathData to it.color })
        val theme = File(androidRes, "values/themes.xml").readText()
        assertTrue("<item name=\"windowSplashScreenBackground\">#0A0A0A</item>" in theme)
        assertTrue("<item name=\"windowSplashScreenAnimatedIcon\">@drawable/splash_icon</item>" in theme)
    }

    @Test
    fun the_legacy_launcher_icons_show_the_new_logo_inside_the_round_mask() {
        mapOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192).forEach { (density, size) ->
            listOf("ic_launcher", "ic_launcher_round").forEach { name ->
                val path = "androidApp/src/main/res/mipmap-$density/$name.png"
                val image = png(path)
                assertEquals(size, image.width, path)
                assertEquals(size, image.height, path)
                assertShowsTheNewLogo(image, path)
                assertDrawingInsideTheRoundMask(image, path)
            }
        }
        assertFalse(png("androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png").colorModel.hasAlpha(), "the square legacy icon is opaque")
    }

    @Test
    fun the_play_store_icon_is_512_opaque_with_the_new_logo_inside_the_round_mask() {
        val path = "androidApp/store/play-store-icon-512.png"
        val image = png(path)
        assertEquals(512, image.width)
        assertEquals(512, image.height)
        assertFalse(image.colorModel.hasAlpha(), "the Play Store refuses an icon with transparency")
        assertShowsTheNewLogo(image, path)
        assertDrawingInsideTheRoundMask(image, path)
    }

    @Test
    fun the_ios_icon_and_launch_logo_show_the_new_logo() {
        val iconPath = "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"
        val icon = png(iconPath)
        assertEquals(1024, icon.width)
        assertFalse(icon.colorModel.hasAlpha(), "App Store icons are opaque")
        assertShowsTheNewLogo(icon, iconPath)
        mapOf("logo.png" to 240, "logo@2x.png" to 480, "logo@3x.png" to 720).forEach { (name, size) ->
            val path = "iosApp/iosApp/Assets.xcassets/LaunchLogo.imageset/$name"
            val logo = png(path)
            assertEquals(size, logo.width, path)
            assertShowsTheNewLogo(logo, path)
        }
    }

    @Test
    fun the_desktop_and_window_icons_show_the_new_logo() {
        listOf("desktopApp/icons/icon.png", "shared/src/commonMain/composeResources/drawable/app_icon.png").forEach { path ->
            val image = png(path)
            assertEquals(512, image.width, path)
            assertShowsTheNewLogo(image, path)
        }
        listOf("desktopApp/icons/icon.icns", "desktopApp/icons/icon.ico").forEach { path ->
            val largest = embeddedPngs(File(repository, path).readBytes()).maxBy { it.width }
            assertTrue(largest.width >= 256, "$path: largest image ${largest.width} px")
            assertShowsTheNewLogo(largest, path)
        }
    }

    private fun embeddedPngs(bytes: ByteArray): List<BufferedImage> {
        val signature = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        return (0..bytes.size - signature.size)
            .filter { start -> signature.indices.all { bytes[start + it] == signature[it] } }
            .mapNotNull { start -> ImageIO.read(ByteArrayInputStream(bytes, start, bytes.size - start)) }
    }

    @Test
    fun the_drawing_is_the_logo_file_kept_as_is() {
        assertEquals(
            listOf("#FFFFFF", "#FFD400", "#FFD400", "#FFD400", "#FFD400", "#FFFFFF", "#FFFFFF"),
            LogoPaths.map { hex(it.color) },
        )
        assertEquals("M83,19 V22 a5,5 0 1,1 -8,3", LogoPaths[2].pathData)
        assertEquals(3.5f, LogoPaths[2].strokeWidth)
        assertTrue(LogoPaths[2].roundCap)
        assertEquals("M83,28 L76,35 M83,28 L92,35", LogoPaths[3].pathData)
        assertEquals(2f, LogoPaths[3].strokeWidth)
        assertEquals("M44,18 L76,46 L68,46 L44,25 L20,46 L12,46 Z", LogoPaths[5].pathData)
    }
}
