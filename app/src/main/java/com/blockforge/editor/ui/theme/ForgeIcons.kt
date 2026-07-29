package com.blockforge.editor.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

/**
 * The handful of icons the editor needs that `material-icons-core` does not carry.
 *
 * Pulling in `material-icons-extended` for eleven glyphs costs several megabytes of APK and a large
 * chunk of every build's dexing time, so they are declared here instead. Path data matches the
 * standard Material 24dp glyphs; the black fill is replaced by whatever tint `Icon` applies.
 */
object ForgeIcons {

    private fun icon(name: String, path: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(pathData = PathData(path), fill = SolidColor(Color.Black))
        }.build()

    val Pause: ImageVector by lazy {
        icon("Forge.Pause") {
            moveTo(6f, 19f); horizontalLineToRelative(4f); verticalLineTo(5f)
            horizontalLineTo(6f); verticalLineToRelative(14f); close()
            moveTo(14f, 5f); verticalLineToRelative(14f); horizontalLineToRelative(4f)
            verticalLineTo(5f); horizontalLineToRelative(-4f); close()
        }
    }

    val Stop: ImageVector by lazy {
        icon("Forge.Stop") {
            moveTo(6f, 6f); horizontalLineToRelative(12f); verticalLineToRelative(12f)
            horizontalLineTo(6f); close()
        }
    }

    val Undo: ImageVector by lazy {
        icon("Forge.Undo") {
            moveTo(12.5f, 8f)
            curveToRelative(-2.65f, 0f, -5.05f, 0.99f, -6.9f, 2.6f)
            lineTo(2f, 7f); verticalLineToRelative(9f); horizontalLineToRelative(9f)
            lineToRelative(-3.62f, -3.62f)
            curveToRelative(1.39f, -1.16f, 3.16f, -1.88f, 5.12f, -1.88f)
            curveToRelative(3.54f, 0f, 6.55f, 2.31f, 7.6f, 5.5f)
            lineToRelative(2.37f, -0.78f)
            curveTo(21.08f, 11.03f, 17.15f, 8f, 12.5f, 8f)
            close()
        }
    }

    val Redo: ImageVector by lazy {
        icon("Forge.Redo") {
            moveTo(18.4f, 10.6f)
            curveTo(16.55f, 8.99f, 14.15f, 8f, 11.5f, 8f)
            curveToRelative(-4.65f, 0f, -8.58f, 3.03f, -9.96f, 7.22f)
            lineTo(3.9f, 16f)
            curveToRelative(1.05f, -3.19f, 4.05f, -5.5f, 7.6f, -5.5f)
            curveToRelative(1.95f, 0f, 3.73f, 0.72f, 5.12f, 1.88f)
            lineTo(13f, 16f); horizontalLineToRelative(9f); verticalLineTo(7f)
            lineToRelative(-3.6f, 3.6f)
            close()
        }
    }

    val Copy: ImageVector by lazy {
        icon("Forge.Copy") {
            moveTo(16f, 1f); horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f); verticalLineToRelative(14f)
            horizontalLineToRelative(2f); verticalLineTo(3f); horizontalLineToRelative(12f)
            verticalLineTo(1f); close()
            moveTo(19f, 5f); horizontalLineTo(8f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f); verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f); horizontalLineToRelative(11f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineTo(7f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
            moveTo(19f, 21f); horizontalLineTo(8f); verticalLineTo(7f)
            horizontalLineToRelative(11f); verticalLineToRelative(14f); close()
        }
    }

    val Tune: ImageVector by lazy {
        icon("Forge.Tune") {
            moveTo(3f, 17f); verticalLineToRelative(2f); horizontalLineToRelative(6f)
            verticalLineToRelative(-2f); horizontalLineTo(3f); close()
            moveTo(3f, 5f); verticalLineToRelative(2f); horizontalLineToRelative(10f)
            verticalLineTo(5f); horizontalLineTo(3f); close()
            moveTo(13f, 21f); verticalLineToRelative(-2f); horizontalLineToRelative(8f)
            verticalLineToRelative(-2f); horizontalLineToRelative(-8f); verticalLineToRelative(-2f)
            horizontalLineToRelative(-2f); verticalLineToRelative(6f); horizontalLineToRelative(2f); close()
            moveTo(7f, 9f); verticalLineToRelative(2f); horizontalLineTo(3f)
            verticalLineToRelative(2f); horizontalLineToRelative(4f); verticalLineToRelative(2f)
            horizontalLineToRelative(2f); verticalLineTo(9f); horizontalLineTo(7f); close()
            moveTo(21f, 13f); verticalLineToRelative(-2f); horizontalLineTo(11f)
            verticalLineToRelative(2f); horizontalLineToRelative(10f); close()
            moveTo(15f, 9f); horizontalLineToRelative(2f); verticalLineTo(7f)
            horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineToRelative(-4f)
            verticalLineTo(3f); horizontalLineToRelative(-2f); verticalLineToRelative(6f); close()
        }
    }

    val Picture: ImageVector by lazy {
        icon("Forge.Picture") {
            moveTo(21f, 19f); verticalLineTo(5f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); horizontalLineTo(5f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f); verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f); horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); close()
            moveTo(8.5f, 13.5f); lineToRelative(2.5f, 3.01f); lineTo(14.5f, 12f)
            lineToRelative(4.5f, 6f); horizontalLineTo(5f); lineToRelative(3.5f, -4.5f); close()
        }
    }

    val AudioTrack: ImageVector by lazy {
        icon("Forge.AudioTrack") {
            moveTo(12f, 3f); verticalLineToRelative(10.55f)
            curveToRelative(-0.59f, -0.34f, -1.27f, -0.55f, -2f, -0.55f)
            curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
            reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
            reflectiveCurveToRelative(4f, -1.79f, 4f, -4f); verticalLineTo(7f)
            horizontalLineToRelative(4f); verticalLineTo(3f); horizontalLineToRelative(-6f); close()
        }
    }

    val Folder: ImageVector by lazy {
        icon("Forge.Folder") {
            moveTo(10f, 4f); horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f); lineTo(2f, 18f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f); horizontalLineToRelative(16f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineTo(8f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); horizontalLineToRelative(-8f)
            lineToRelative(-2f, -2f); close()
        }
    }

    /** Marks "ekspor proyek Android" — a robot head is the clearest signal for that action. */
    val Android: ImageVector by lazy {
        icon("Forge.Android") {
            moveTo(6f, 18f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f); horizontalLineToRelative(1f)
            verticalLineToRelative(3.5f)
            curveToRelative(0f, 0.83f, 0.67f, 1.5f, 1.5f, 1.5f)
            reflectiveCurveToRelative(1.5f, -0.67f, 1.5f, -1.5f); verticalLineTo(19f)
            horizontalLineToRelative(2f); verticalLineToRelative(3.5f)
            curveToRelative(0f, 0.83f, 0.67f, 1.5f, 1.5f, 1.5f)
            reflectiveCurveToRelative(1.5f, -0.67f, 1.5f, -1.5f); verticalLineTo(19f)
            horizontalLineToRelative(1f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f); verticalLineTo(8f)
            horizontalLineTo(6f); verticalLineToRelative(10f); close()
            moveTo(3.5f, 8f)
            curveTo(2.67f, 8f, 2f, 8.67f, 2f, 9.5f); verticalLineToRelative(7f)
            curveToRelative(0f, 0.83f, 0.67f, 1.5f, 1.5f, 1.5f)
            reflectiveCurveTo(5f, 17.33f, 5f, 16.5f); verticalLineToRelative(-7f)
            curveTo(5f, 8.67f, 4.33f, 8f, 3.5f, 8f); close()
            moveTo(20.5f, 8f)
            curveToRelative(-0.83f, 0f, -1.5f, 0.67f, -1.5f, 1.5f); verticalLineToRelative(7f)
            curveToRelative(0f, 0.83f, 0.67f, 1.5f, 1.5f, 1.5f)
            reflectiveCurveToRelative(1.5f, -0.67f, 1.5f, -1.5f); verticalLineToRelative(-7f)
            curveToRelative(0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f); close()
            moveTo(15.53f, 2.16f); lineToRelative(1.3f, -1.3f)
            curveToRelative(0.2f, -0.2f, 0.2f, -0.51f, 0f, -0.71f)
            curveToRelative(-0.2f, -0.2f, -0.51f, -0.2f, -0.71f, 0f)
            lineToRelative(-1.48f, 1.48f)
            curveTo(13.85f, 1.23f, 12.95f, 1f, 12f, 1f)
            curveToRelative(-0.96f, 0f, -1.86f, 0.23f, -2.66f, 0.63f)
            lineTo(7.85f, 0.15f)
            curveToRelative(-0.2f, -0.2f, -0.51f, -0.2f, -0.71f, 0f)
            curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0f, 0.71f)
            lineToRelative(1.31f, 1.31f)
            curveTo(6.97f, 3.26f, 6f, 5.01f, 6f, 7f); horizontalLineToRelative(12f)
            curveToRelative(0f, -1.99f, -0.97f, -3.75f, -2.47f, -4.84f); close()
        }
    }

    /** Two paths splitting to the right — the shape of a branch lane opening off a block. */
    val Branch: ImageVector by lazy {
        icon("Forge.Branch") {
            moveTo(14f, 4f); lineToRelative(2.29f, 2.29f); lineToRelative(-2.88f, 2.88f)
            lineToRelative(1.42f, 1.42f); lineToRelative(2.88f, -2.88f); lineTo(20f, 10f)
            verticalLineTo(4f); horizontalLineToRelative(-6f); close()
            moveTo(10f, 4f); horizontalLineTo(4f); verticalLineToRelative(6f)
            lineToRelative(2.29f, -2.29f); lineToRelative(4.71f, 4.7f); verticalLineTo(20f)
            horizontalLineToRelative(2f); verticalLineToRelative(-8.41f)
            lineToRelative(-5.29f, -5.3f); lineTo(10f, 4f); close()
        }
    }
}
