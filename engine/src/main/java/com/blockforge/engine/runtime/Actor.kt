package com.blockforge.engine.runtime

import android.graphics.RectF
import com.blockforge.engine.model.GameObject
import com.blockforge.engine.model.ObjectShape
import com.blockforge.engine.model.PhysicsBody

/**
 * A live instance of a [GameObject]. Positions are centre-based, which keeps rotation, "hadap ke
 * objek" and distance maths free of half-width corrections everywhere.
 */
class Actor(
    val def: GameObject,
    val id: String,
    /** Clones are spawned at runtime and are removed on scene reload; scene actors are not. */
    val isClone: Boolean = false
) {
    var name: String = def.name
    var tag: String = def.tag

    var x: Float = def.x
    var y: Float = def.y
    var width: Float = def.width
    var height: Float = def.height
    var rotation: Float = def.rotation
    var scaleX: Float = def.scaleX
    var scaleY: Float = def.scaleY
    var alpha: Float = def.alpha
    var zIndex: Int = def.zIndex
    var visible: Boolean = def.visible

    var vx: Float = 0f
    var vy: Float = 0f

    var spriteAssetId: String? = def.spriteAssetId
    var spriteFile: String? = null
    var color: Int = def.fallbackColor
    var shape: ObjectShape = def.shape
    var physics: PhysicsBody = def.physics

    /** Set by the collision pass; drives whether "lompat" is allowed. */
    var grounded: Boolean = false
    var alive: Boolean = true

    var sayText: String? = null
    var sayUntil: Double = 0.0

    /** OBJECT-scope variables — every actor keeps its own copy. */
    val locals = HashMap<String, Any?>()

    /** Actor ids overlapped last frame, so collision hats fire once per contact, not once per frame. */
    val touching = HashSet<String>()

    val drawWidth: Float get() = width * scaleX
    val drawHeight: Float get() = height * scaleY

    fun bounds(out: RectF = RectF()): RectF {
        val hw = drawWidth / 2f
        val hh = drawHeight / 2f
        out.set(x - hw, y - hh, x + hw, y + hh)
        return out
    }

    fun overlaps(other: Actor): Boolean {
        val hw = drawWidth / 2f + other.drawWidth / 2f
        val hh = drawHeight / 2f + other.drawHeight / 2f
        return kotlin.math.abs(x - other.x) < hw && kotlin.math.abs(y - other.y) < hh
    }

    fun distanceTo(other: Actor): Float {
        val dx = other.x - x
        val dy = other.y - y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun property(name: String): Any? = when (name) {
        "x" -> x.toDouble()
        "y" -> y.toDouble()
        "rotation" -> rotation.toDouble()
        "width" -> drawWidth.toDouble()
        "height" -> drawHeight.toDouble()
        "scale" -> (scaleX * 100f).toDouble()
        "alpha" -> (alpha * 100f).toDouble()
        "vx" -> vx.toDouble()
        "vy" -> vy.toDouble()
        else -> 0.0
    }

    fun resetToDefinition() {
        x = def.x; y = def.y
        width = def.width; height = def.height
        rotation = def.rotation
        scaleX = def.scaleX; scaleY = def.scaleY
        alpha = def.alpha
        zIndex = def.zIndex
        visible = def.visible
        vx = 0f; vy = 0f
        spriteAssetId = def.spriteAssetId
        color = def.fallbackColor
        physics = def.physics
        grounded = false
        alive = true
        sayText = null
        touching.clear()
        locals.clear()
    }
}
