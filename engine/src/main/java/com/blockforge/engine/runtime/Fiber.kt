package com.blockforge.engine.runtime

import com.blockforge.engine.blocks.BlockCatalog
import com.blockforge.engine.model.Arg
import com.blockforge.engine.model.BlockNode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * One running script.
 *
 * Scripts are executed as resumable state machines rather than threads: each frame a fiber runs
 * blocks until it hits something that has to wait (a `tunggu`, one loop iteration, a blocked
 * `tunggu sampai`), then parks. That is what makes `selamanya` safe — a forever loop yields once per
 * iteration, so it can never lock the frame up.
 */
class Fiber(
    private val world: GameWorld,
    val actor: Actor,
    val script: BlockNode,
    /** Identifies the script so re-triggering the same hat restarts it instead of stacking copies. */
    val key: String
) {
    internal enum class LoopKind { REPEAT, FOREVER, UNTIL, WHILE, IF_UNTIL }

    internal class Frame(
        val nodes: List<BlockNode>,
        var pc: Int = 0,
        val loopKind: LoopKind? = null,
        val owner: BlockNode? = null,
        var remaining: Int = 0
    )

    private enum class Step {
        /** The caller advances the program counter. */
        NEXT,

        /** The block already moved the counter (and possibly pushed a frame). */
        ENTERED,

        /** Park until the next frame; the counter is left wherever the block put it. */
        YIELD,

        /** Kill the whole fiber. */
        STOP
    }

    private val stack = ArrayDeque<Frame>()
    private var sleepUntil = 0.0
    private var waitingOn: List<Fiber>? = null

    var finished = false
        private set

    /** Block currently under the cursor — the editor draws a glow on it while the game runs. */
    var currentBlockId: String? = null
        private set

    init {
        restart()
    }

    fun restart() {
        stack.clear()
        val main = script.branch(0)
        if (main != null && main.nodes.isNotEmpty()) stack.addLast(Frame(main.nodes))
        sleepUntil = 0.0
        waitingOn = null
        finished = stack.isEmpty()
        currentBlockId = null
    }

    fun kill() {
        stack.clear()
        finished = true
        currentBlockId = null
    }

    fun tick() {
        if (finished) return
        if (!actor.alive) {
            kill(); return
        }
        if (world.time < sleepUntil) return

        var budget = MAX_STEPS_PER_FRAME
        while (budget-- > 0) {
            val frame = stack.lastOrNull()
            if (frame == null) {
                finished = true
                currentBlockId = null
                return
            }
            if (frame.pc >= frame.nodes.size) {
                if (repeatFrame(frame)) return // one loop iteration per frame
                stack.removeLast()
                continue
            }
            val node = frame.nodes[frame.pc]
            currentBlockId = node.id
            world.markActive(node.id)
            when (execute(node, frame)) {
                Step.NEXT -> frame.pc++
                Step.ENTERED -> Unit
                Step.YIELD -> return
                Step.STOP -> {
                    kill(); return
                }
            }
        }
        // Budget spent. Park rather than spin; a runaway script slows down, it does not hang.
    }

    /** Decides what happens when a frame runs off its end. Returns true when the loop goes round again. */
    private fun repeatFrame(frame: Frame): Boolean {
        val kind = frame.loopKind ?: return false
        val owner = frame.owner ?: return false
        return when (kind) {
            LoopKind.REPEAT -> if (frame.remaining > 0) {
                frame.remaining--; frame.pc = 0; true
            } else false

            LoopKind.FOREVER -> {
                frame.pc = 0; true
            }

            LoopKind.UNTIL -> if (!bool(owner, "cond")) {
                frame.pc = 0; true
            } else false

            LoopKind.WHILE -> if (bool(owner, "cond")) {
                frame.pc = 0; true
            } else false

            LoopKind.IF_UNTIL -> if (!bool(owner, "until")) {
                frame.pc = 0; true
            } else false
        }
    }

    // ---- statement execution ------------------------------------------------------------------

    private fun execute(node: BlockNode, frame: Frame): Step {
        val a = actor
        when (node.type) {

            // ---------------- kontrol ----------------
            "control.wait" -> {
                frame.pc++
                sleepUntil = world.time + num(node, "sec").coerceAtLeast(0.0)
                return Step.YIELD
            }

            "control.if" -> {
                if (!bool(node, "cond")) return Step.NEXT
                return enter(node, frame, 0)
            }

            "control.if_else" -> {
                val index = if (bool(node, "cond")) 0 else 1
                return enter(node, frame, index)
            }

            "control.if_until" -> {
                if (!bool(node, "cond")) return Step.NEXT
                if (bool(node, "until")) return Step.NEXT
                return enter(node, frame, 0, LoopKind.IF_UNTIL)
            }

            "control.repeat" -> {
                val times = int(node, "times")
                if (times <= 0) return Step.NEXT
                return enter(node, frame, 0, LoopKind.REPEAT, times - 1)
            }

            "control.forever" -> return enter(node, frame, 0, LoopKind.FOREVER)

            "control.repeat_until" -> {
                if (bool(node, "cond")) return Step.NEXT
                return enter(node, frame, 0, LoopKind.UNTIL)
            }

            "control.while" -> {
                if (!bool(node, "cond")) return Step.NEXT
                return enter(node, frame, 0, LoopKind.WHILE)
            }

            "control.branch" -> return enter(node, frame, 0)

            "control.wait_until" -> return if (bool(node, "cond")) Step.NEXT else Step.YIELD

            "control.broadcast" -> {
                world.broadcast(str(node, "msg"))
                return Step.NEXT
            }

            "control.broadcast_wait" -> {
                val pending = waitingOn
                if (pending == null) {
                    waitingOn = world.broadcast(str(node, "msg")).filter { it !== this }
                    return Step.YIELD
                }
                if (pending.all { it.finished }) {
                    waitingOn = null
                    return Step.NEXT
                }
                return Step.YIELD
            }

            "control.stop" -> when (str(node, "target")) {
                "all" -> {
                    world.stopAllScripts(); return Step.STOP
                }

                "others" -> {
                    world.stopOtherScripts(a, this); return Step.NEXT
                }

                else -> return Step.STOP
            }

            // ---------------- gerak ----------------
            "motion.move" -> {
                val d = float(node, "dist")
                val rad = Math.toRadians(a.rotation.toDouble())
                a.x += (cos(rad) * d).toFloat()
                a.y += (sin(rad) * d).toFloat()
            }

            "motion.move_xy" -> {
                a.x += float(node, "dx"); a.y += float(node, "dy")
            }

            "motion.set_pos" -> {
                a.x = float(node, "x"); a.y = float(node, "y")
            }

            "motion.set_x" -> a.x = float(node, "x")
            "motion.set_y" -> a.y = float(node, "y")
            "motion.turn" -> a.rotation = wrapAngle(a.rotation + float(node, "deg"))
            "motion.point_dir" -> a.rotation = wrapAngle(float(node, "deg"))

            "motion.point_to" -> {
                world.actorForSlot(str(node, "obj"), a)?.let { target ->
                    a.rotation = wrapAngle(
                        Math.toDegrees(atan2((target.y - a.y).toDouble(), (target.x - a.x).toDouble())).toFloat()
                    )
                }
            }

            "motion.set_velocity" -> {
                a.vx = float(node, "vx"); a.vy = float(node, "vy")
            }

            "motion.add_velocity" -> {
                a.vx += float(node, "vx"); a.vy += float(node, "vy")
            }

            "motion.jump" -> if (a.grounded) {
                a.vy = -float(node, "power")
                a.grounded = false
            }

            "motion.bounce_edge" -> world.bounceOnEdge(a)
            "motion.stay_on_screen" -> world.clampToScreen(a)

            // ---------------- tampilan ----------------
            "looks.set_sprite" -> {
                val assetId = str(node, "image")
                a.spriteAssetId = assetId.ifEmpty { null }
                a.spriteFile = world.fileNameOf(assetId)
            }

            "looks.show" -> a.visible = true
            "looks.hide" -> a.visible = false

            "looks.set_size" -> {
                val s = float(node, "pct") / 100f
                a.scaleX = s; a.scaleY = s
            }

            "looks.change_size" -> {
                val s = float(node, "pct") / 100f
                a.scaleX += s; a.scaleY += s
            }

            "looks.set_alpha" -> a.alpha = (float(node, "pct") / 100f).coerceIn(0f, 1f)
            "looks.set_color" -> a.color = Val.color(str(node, "color"), a.color)

            "looks.say" -> {
                a.sayText = str(node, "text")
                val secs = num(node, "sec").coerceAtLeast(0.0)
                a.sayUntil = world.time + secs
                frame.pc++
                sleepUntil = world.time + secs
                return Step.YIELD
            }

            "looks.say_now" -> {
                a.sayText = str(node, "text")
                a.sayUntil = Double.MAX_VALUE
            }

            "looks.set_z" -> a.zIndex = int(node, "z")

            // ---------------- suara ----------------
            "sound.play" -> world.audio.playSfx(world.fileNameOf(str(node, "audio")))

            "sound.play_wait" -> {
                val file = world.fileNameOf(str(node, "audio"))
                world.audio.playSfx(file)
                frame.pc++
                sleepUntil = world.time + world.audio.durationOf(file)
                return Step.YIELD
            }

            "sound.music" -> world.audio.playMusic(world.fileNameOf(str(node, "audio")))
            "sound.stop_music" -> world.audio.stopMusic()
            "sound.volume" -> world.audio.volume = float(node, "pct") / 100f

            // ---------------- variabel ----------------
            "var.set" -> world.setVariable(str(node, "var"), a, value(node, "value"))

            "var.change" -> {
                val id = str(node, "var")
                world.setVariable(id, a, Val.num(world.getVariable(id, a)) + num(node, "delta"))
            }

            "var.show" -> world.setVariableVisible(str(node, "var"), true)
            "var.hide" -> world.setVariableVisible(str(node, "var"), false)

            // ---------------- game ----------------
            "game.spawn" -> world.spawnClone(str(node, "obj"), float(node, "x"), float(node, "y"))

            "game.destroy" -> {
                world.destroy(a); return Step.STOP
            }

            "game.destroy_tag" -> world.destroyByTag(str(node, "tag"))

            "game.goto_scene" -> {
                world.requestScene(str(node, "scene")); return Step.STOP
            }

            "game.restart" -> {
                world.requestScene(world.sceneId); return Step.STOP
            }

            "game.camera_follow" -> world.cameraTargetId = world.actorForSlot(str(node, "obj"), a)?.id

            "game.camera_to" -> {
                world.cameraTargetId = null
                world.cameraX = float(node, "x")
                world.cameraY = float(node, "y")
            }

            "game.shake" -> world.shake(float(node, "power"), float(node, "sec"))

            "game.quit" -> {
                world.requestQuit(); return Step.STOP
            }

            else -> Unit // reporters never appear as statements; unknown types are skipped safely
        }
        return if (BlockCatalog[node.type]?.endsLane == true) Step.STOP else Step.NEXT
    }

    private fun enter(
        node: BlockNode,
        frame: Frame,
        branchIndex: Int,
        loop: LoopKind? = null,
        remaining: Int = 0
    ): Step {
        frame.pc++
        val lane = node.branch(branchIndex)
        if (lane == null || lane.nodes.isEmpty()) {
            // An empty branch still has to loop, otherwise `selamanya` with nothing in it would exit.
            if (loop == null) return Step.ENTERED
            stack.addLast(Frame(emptyList(), 0, loop, node, remaining))
            return Step.ENTERED
        }
        stack.addLast(Frame(lane.nodes, 0, loop, node, remaining))
        return Step.ENTERED
    }

    // ---- value evaluation ---------------------------------------------------------------------

    private fun value(node: BlockNode, key: String, depth: Int = 0): Any? =
        when (val arg = node.args[key]) {
            is Arg.Lit -> arg.value
            is Arg.Blk -> if (depth > MAX_EXPR_DEPTH) "" else evaluate(arg.node, depth + 1)
            null -> BlockCatalog[node.type]?.slot(key)?.default ?: ""
        }

    private fun num(node: BlockNode, key: String): Double = Val.num(value(node, key))
    private fun float(node: BlockNode, key: String): Float = Val.float(value(node, key))
    private fun int(node: BlockNode, key: String): Int = Val.int(value(node, key))
    private fun str(node: BlockNode, key: String): String = Val.str(value(node, key))
    private fun bool(node: BlockNode, key: String): Boolean = Val.bool(value(node, key))

    /** Evaluates a reporter or boolean block. Public so hat-block watchers can reuse the same maths. */
    fun evaluate(node: BlockNode, depth: Int = 0): Any? {
        val a = actor
        fun v(key: String) = value(node, key, depth)
        fun n(key: String) = Val.num(v(key))
        fun b(key: String) = Val.bool(v(key))
        fun s(key: String) = Val.str(v(key))

        return when (node.type) {
            // ---------------- sensor ----------------
            "sense.key_pressed" -> world.input.isHeld(s("key"))
            "sense.key_clicked" -> world.input.justPressed(s("key"))
            "sense.touch_tag" -> world.actorsByTag(s("tag")).any { it !== a && a.overlaps(it) }
            "sense.touch_object" -> world.actorForSlot(s("obj"), a)?.let { it !== a && a.overlaps(it) } ?: false
            "sense.touch_edge" -> world.touchesEdge(a)
            "sense.pointer_down" -> world.input.pointerDown
            "sense.pointer_x" -> world.input.pointerX.toDouble()
            "sense.pointer_y" -> world.input.pointerY.toDouble()
            "sense.self" -> a.property(s("prop"))
            "sense.of_object" -> world.actorForSlot(s("obj"), a)?.property(s("prop")) ?: 0.0
            "sense.distance_to" -> world.actorForSlot(s("obj"), a)?.let { a.distanceTo(it).toDouble() } ?: 0.0
            "sense.timer" -> world.time
            "sense.delta" -> world.deltaTime.toDouble()
            "sense.random" -> {
                val lo = n("min")
                val hi = n("max")
                val min = minOf(lo, hi)
                val max = maxOf(lo, hi)
                if (isWhole(lo) && isWhole(hi)) {
                    (min.toLong()..max.toLong()).random().toDouble()
                } else {
                    min + Math.random() * (max - min)
                }
            }

            "sense.count_tag" -> world.actorsByTag(s("tag")).size.toDouble()

            // ---------------- variabel ----------------
            "var.get" -> world.getVariable(s("var"), a)

            // ---------------- operator ----------------
            "op.add" -> n("a") + n("b")
            "op.sub" -> n("a") - n("b")
            "op.mul" -> n("a") * n("b")
            "op.div" -> n("b").let { if (it == 0.0) 0.0 else n("a") / it }
            "op.mod" -> n("b").let { if (it == 0.0) 0.0 else n("a").mod(it) }
            "op.compare" -> compare(v("a"), s("op"), v("b"))
            "op.and" -> b("a") && b("b")
            "op.or" -> b("a") || b("b")
            "op.not" -> !b("a")
            "op.math" -> applyMath(s("fn"), n("n"))
            "op.min" -> minOf(n("a"), n("b"))
            "op.max" -> maxOf(n("a"), n("b"))
            "op.clamp" -> n("n").coerceIn(minOf(n("min"), n("max")), maxOf(n("min"), n("max")))
            "op.join" -> s("a") + s("b")

            else -> ""
        }
    }

    private fun compare(left: Any?, op: String, right: Any?): Boolean {
        val c = Val.compare(left, right)
        return when (op) {
            "<" -> c < 0
            "<=" -> c <= 0
            "==" -> c == 0
            "!=" -> c != 0
            ">=" -> c >= 0
            ">" -> c > 0
            else -> false
        }
    }

    private fun applyMath(fn: String, x: Double): Double = when (fn) {
        "abs" -> abs(x)
        "round" -> Math.round(x).toDouble()
        "floor" -> floor(x)
        "ceil" -> ceil(x)
        "sqrt" -> if (x < 0) 0.0 else sqrt(x)
        "sin" -> sin(Math.toRadians(x))
        "cos" -> cos(Math.toRadians(x))
        "tan" -> tan(Math.toRadians(x))
        "sign" -> sign(x)
        else -> x
    }

    private fun isWhole(v: Double) = v == floor(v) && !v.isInfinite()

    private fun wrapAngle(deg: Float): Float {
        var d = deg % 360f
        if (d < 0) d += 360f
        return d
    }

    private companion object {
        /** Backstop against pathological nesting; a fiber that hits this simply resumes next frame. */
        const val MAX_STEPS_PER_FRAME = 2048

        const val MAX_EXPR_DEPTH = 32
    }
}
