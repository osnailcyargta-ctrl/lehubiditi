package com.blockforge.engine.runtime

import com.blockforge.engine.blocks.BlockCatalog
import com.blockforge.engine.model.AssetKind
import com.blockforge.engine.model.GameObject
import com.blockforge.engine.model.GameProject
import com.blockforge.engine.model.VariableScope
import com.blockforge.engine.model.newId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Lets the hosting Activity react to things only it can do, like closing the game. */
interface GameHost {
    fun onQuit() {}
    fun onSceneChanged(sceneId: String) {}
    fun onError(message: String) {}
}

/**
 * The live scene: actors, variables, camera, physics and the event dispatch that turns input and
 * collisions into running fibers. One instance per loaded scene; changing scene rebuilds it.
 */
class GameWorld(
    val project: GameProject,
    val resources: ResourceProvider,
    val audio: AudioBus,
    val bitmaps: BitmapCache,
    val input: InputState,
    var host: GameHost? = null
) {
    var sceneId: String = project.startSceneId
        private set

    var time: Double = 0.0
        private set
    var deltaTime: Float = 0f
        private set

    val actors = mutableListOf<Actor>()
    private val fibers = LinkedHashMap<String, Fiber>()
    private val spawnQueue = mutableListOf<Actor>()
    private val destroyQueue = mutableListOf<Actor>()

    private val globals = HashMap<String, Any?>()
    private val visibleVars = HashSet<String>()
    private val watcherState = HashMap<String, Boolean>()

    /** Block ids executed this frame — the editor draws a running-glow from this. */
    private val activeBlocks = HashSet<String>()
    private val activeSnapshot = HashSet<String>()

    var cameraX: Float = project.settings.designWidth / 2f
    var cameraY: Float = project.settings.designHeight / 2f
    var cameraTargetId: String? = null
    private var shakePower = 0f
    private var shakeUntil = 0.0
    var shakeOffsetX = 0f
        private set
    var shakeOffsetY = 0f
        private set

    private var pendingScene: String? = null

    val viewWidth: Float get() = project.settings.designWidth
    val viewHeight: Float get() = project.settings.designHeight

    val left: Float get() = cameraX - viewWidth / 2f
    val right: Float get() = cameraX + viewWidth / 2f
    val top: Float get() = cameraY - viewHeight / 2f
    val bottom: Float get() = cameraY + viewHeight / 2f

    init {
        loadScene(project.startSceneId)
    }

    // ---- scene lifecycle ----------------------------------------------------------------------

    fun loadScene(id: String) {
        val scene = project.scene(id) ?: project.scenes.firstOrNull() ?: return
        sceneId = scene.id
        actors.clear()
        fibers.clear()
        spawnQueue.clear()
        destroyQueue.clear()
        watcherState.clear()
        time = 0.0
        cameraX = viewWidth / 2f
        cameraY = viewHeight / 2f
        cameraTargetId = null
        shakePower = 0f

        globals.clear()
        visibleVars.clear()
        project.variables.filter { it.scope == VariableScope.GLOBAL }.forEach { v ->
            globals[v.id] = initialValue(v.initial, v.kind.name)
            if (v.showOnScreen) visibleVars.add(v.id)
        }

        scene.objects.forEach { def -> actors.add(createActor(def, def.id, isClone = false)) }
        preloadAudio()

        actors.toList().forEach { actor -> triggerHats(actor, "event.start") }
        host?.onSceneChanged(sceneId)
    }

    private fun initialValue(raw: String, kind: String): Any? = when (kind) {
        "NUMBER" -> Val.num(raw)
        "BOOLEAN" -> Val.bool(raw)
        else -> raw
    }

    private fun createActor(def: GameObject, id: String, isClone: Boolean): Actor {
        val actor = Actor(def, id, isClone)
        actor.spriteFile = fileNameOf(def.spriteAssetId)
        project.variables.filter { it.scope == VariableScope.OBJECT }.forEach { v ->
            actor.locals[v.id] = initialValue(v.initial, v.kind.name)
        }
        return actor
    }

    private fun preloadAudio() {
        audio.preload(project.assets.filter { it.kind == AssetKind.AUDIO }.map { it.fileName })
    }

    fun requestScene(id: String) {
        pendingScene = id.ifEmpty { sceneId }
    }

    fun requestQuit() {
        host?.onQuit()
    }

    // ---- main tick ----------------------------------------------------------------------------

    fun update(dt: Float) {
        deltaTime = dt
        time += dt
        activeBlocks.clear()

        dispatchInputEvents()
        dispatchVariableWatchers()
        restartFrameHats()

        runFibers()

        integrate(dt)
        resolveCollisions()
        dispatchCollisionEvents()

        flushQueues()
        updateCamera(dt)
        expireSpeech()

        activeSnapshot.clear()
        activeSnapshot.addAll(activeBlocks)
        input.endFrame()

        pendingScene?.let { next ->
            pendingScene = null
            loadScene(next)
        }
    }

    private fun runFibers() {
        var batch = fibers.values.toList()
        var pass = 0
        // A broadcast started mid-pass should still run this frame, so drain new fibers a few times.
        while (batch.isNotEmpty() && pass < 4) {
            val before = fibers.keys.toHashSet()
            batch.forEach { it.tick() }
            batch = fibers.entries.filter { it.key !in before }.map { it.value }
            pass++
        }
        fibers.entries.removeAll { (_, fiber) -> fiber.finished && !isFrameHat(fiber) }
    }

    private fun isFrameHat(fiber: Fiber) = fiber.script.type == "event.frame"

    fun markActive(blockId: String) {
        activeBlocks.add(blockId)
    }

    /** Snapshot of the blocks that ran last frame; safe to read from the UI thread. */
    fun activeBlockIds(): Set<String> = activeSnapshot.toSet()

    // ---- event dispatch -----------------------------------------------------------------------

    private fun hatsOf(actor: Actor, type: String) = actor.def.scripts.filter { it.type == type }

    private fun triggerHats(actor: Actor, type: String, match: (com.blockforge.engine.model.BlockNode) -> Boolean = { true }) {
        hatsOf(actor, type).filter(match).forEach { script -> start(actor, script) }
    }

    private fun start(actor: Actor, script: com.blockforge.engine.model.BlockNode) {
        val key = "${actor.id}#${script.id}"
        val existing = fibers[key]
        if (existing != null) {
            existing.restart()
        } else {
            fibers[key] = Fiber(this, actor, script, key)
        }
    }

    private fun restartFrameHats() {
        actors.forEach { actor ->
            hatsOf(actor, "event.frame").forEach { script ->
                val key = "${actor.id}#${script.id}"
                val fiber = fibers[key]
                if (fiber == null) fibers[key] = Fiber(this, actor, script, key)
                else if (fiber.finished) fiber.restart()
            }
        }
    }

    private fun dispatchInputEvents() {
        val pressed = input.pressedKeys()
        val released = input.releasedKeys()
        val tapped = input.pointerJustDown

        if (pressed.isEmpty() && released.isEmpty() && !tapped) return

        actors.toList().forEach { actor ->
            if (pressed.isNotEmpty()) {
                triggerHats(actor, "event.key_down") { node ->
                    val k = node.literal("key") ?: "ANY"
                    k == "ANY" || pressed.contains(k)
                }
            }
            if (released.isNotEmpty()) {
                triggerHats(actor, "event.key_up") { node ->
                    val k = node.literal("key") ?: "ANY"
                    k == "ANY" || released.contains(k)
                }
            }
            if (tapped && actor.visible && pointerInside(actor)) {
                triggerHats(actor, "event.tap")
            }
        }
    }

    private fun pointerInside(actor: Actor): Boolean {
        val hw = actor.drawWidth / 2f
        val hh = actor.drawHeight / 2f
        return abs(input.pointerX - actor.x) <= hw && abs(input.pointerY - actor.y) <= hh
    }

    /**
     * `saat {var} >= {n}` fires on the rising edge only, so holding a score above the threshold does
     * not re-run the script every frame.
     */
    private fun dispatchVariableWatchers() {
        actors.toList().forEach { actor ->
            hatsOf(actor, "event.var_when").forEach { node ->
                val varId = node.literal("var").orEmpty()
                val op = node.literal("op") ?: ">="
                val threshold = node.literal("value") ?: "0"
                val current = getVariable(varId, actor)
                val cmp = Val.compare(current, threshold)
                val now = when (op) {
                    "<" -> cmp < 0
                    "<=" -> cmp <= 0
                    "==" -> cmp == 0
                    "!=" -> cmp != 0
                    ">=" -> cmp >= 0
                    ">" -> cmp > 0
                    else -> false
                }
                val key = "${actor.id}#${node.id}"
                val was = watcherState[key] ?: false
                watcherState[key] = now
                if (now && !was) start(actor, node)
            }
        }
    }

    private fun dispatchCollisionEvents() {
        val withHats = actors.filter { it.alive && it.def.scripts.any { s -> s.type == "event.collision" } }
        if (withHats.isEmpty()) {
            actors.forEach { it.touching.clear() }
            return
        }
        withHats.forEach { actor ->
            val nowTouching = HashSet<String>()
            actors.forEach { other ->
                if (other !== actor && other.alive && actor.overlaps(other)) nowTouching.add(other.id)
            }
            val entered = nowTouching - actor.touching
            actor.touching.clear()
            actor.touching.addAll(nowTouching)
            if (entered.isEmpty()) return@forEach
            val enteredTags = entered.mapNotNull { id -> actors.firstOrNull { it.id == id }?.tag }
            triggerHats(actor, "event.collision") { node ->
                val tag = node.literal("tag").orEmpty()
                tag.isEmpty() || enteredTags.any { it.equals(tag, ignoreCase = true) }
            }
        }
    }

    fun broadcast(message: String): List<Fiber> {
        val started = mutableListOf<Fiber>()
        actors.toList().forEach { actor ->
            hatsOf(actor, "event.message")
                .filter { (it.literal("msg") ?: "").equals(message, ignoreCase = true) }
                .forEach { script ->
                    start(actor, script)
                    fibers["${actor.id}#${script.id}"]?.let { started.add(it) }
                }
        }
        return started
    }

    fun stopAllScripts() {
        fibers.values.forEach { it.kill() }
    }

    fun stopOtherScripts(actor: Actor, except: Fiber) {
        fibers.values.filter { it.actor === actor && it !== except }.forEach { it.kill() }
    }

    // ---- variables ----------------------------------------------------------------------------

    fun getVariable(varId: String, actor: Actor?): Any? {
        val def = project.variable(varId) ?: return 0.0
        return if (def.scope == VariableScope.OBJECT) {
            actor?.locals?.get(varId) ?: initialValue(def.initial, def.kind.name)
        } else {
            globals[varId] ?: initialValue(def.initial, def.kind.name)
        }
    }

    fun setVariable(varId: String, actor: Actor?, value: Any?) {
        val def = project.variable(varId) ?: return
        if (def.scope == VariableScope.OBJECT) actor?.locals?.put(varId, value)
        else globals[varId] = value
    }

    fun setVariableVisible(varId: String, visible: Boolean) {
        if (visible) visibleVars.add(varId) else visibleVars.remove(varId)
    }

    /** Name/value pairs the renderer draws in the corner. */
    fun watchedVariables(): List<Pair<String, String>> =
        project.variables.filter { visibleVars.contains(it.id) }
            .map { it.name to Val.str(getVariable(it.id, null)) }

    // ---- actor helpers ------------------------------------------------------------------------

    fun actorsByTag(tag: String): List<Actor> =
        if (tag.isEmpty()) emptyList() else actors.filter { it.alive && it.tag.equals(tag, ignoreCase = true) }

    /** Resolves an OBJECT slot. Empty means "this object", which is the useful default in the editor. */
    fun actorForSlot(id: String, self: Actor?): Actor? {
        if (id.isEmpty()) return self
        actors.firstOrNull { it.id == id && it.alive }?.let { return it }
        return actors.firstOrNull { it.def.id == id && it.alive }
    }

    fun fileNameOf(assetId: String?): String? = project.asset(assetId)?.fileName

    fun spawnClone(objectDefId: String, x: Float, y: Float) {
        if (actors.size + spawnQueue.size >= MAX_ACTORS) return
        val scene = project.scene(sceneId) ?: return
        val def = scene.obj(objectDefId)
            ?: project.scenes.firstNotNullOfOrNull { it.obj(objectDefId) }
            ?: return
        val clone = createActor(def, newId("clone"), isClone = true)
        clone.x = x
        clone.y = y
        spawnQueue.add(clone)
    }

    fun destroy(actor: Actor) {
        if (!actor.alive) return
        actor.alive = false
        destroyQueue.add(actor)
    }

    fun destroyByTag(tag: String) {
        actorsByTag(tag).forEach { destroy(it) }
    }

    private fun flushQueues() {
        if (spawnQueue.isNotEmpty()) {
            val added = spawnQueue.toList()
            spawnQueue.clear()
            actors.addAll(added)
            added.forEach { triggerHats(it, "event.spawned") }
        }
        if (destroyQueue.isNotEmpty()) {
            val removed = destroyQueue.toList()
            destroyQueue.clear()
            removed.forEach { actor ->
                actors.remove(actor)
                fibers.entries.removeAll { (_, f) -> f.actor === actor }
            }
        }
    }

    // ---- physics ------------------------------------------------------------------------------

    private fun integrate(dt: Float) {
        val gravity = project.settings.gravity
        actors.forEach { actor ->
            val body = actor.physics
            if (body.enabled && !body.static) {
                actor.vy += gravity * body.gravityScale * dt
                if (body.friction > 0f) {
                    val keep = (1f - body.friction).coerceIn(0f, 1f)
                    actor.vx *= Math.pow(keep.toDouble(), (dt * 60f).toDouble()).toFloat()
                }
            }
        }
    }

    private fun resolveCollisions() {
        val dt = deltaTime
        val solids = actors.filter { it.alive && it.physics.enabled && it.physics.solid }
        actors.forEach { actor ->
            if (!actor.alive) return@forEach
            if (actor.vx == 0f && actor.vy == 0f) {
                if (actor.physics.enabled && !actor.physics.static) actor.grounded = groundedCheck(actor, solids)
                return@forEach
            }
            val movable = actor.physics.enabled && !actor.physics.static && actor.physics.solid

            actor.x += actor.vx * dt
            if (movable) separateAxis(actor, solids, horizontal = true)

            actor.y += actor.vy * dt
            if (movable) {
                actor.grounded = false
                separateAxis(actor, solids, horizontal = false)
            }
        }
    }

    private fun groundedCheck(actor: Actor, solids: List<Actor>): Boolean {
        val probeY = actor.y + 2f
        return solids.any { other ->
            other !== actor && overlapsAt(actor, actor.x, probeY, other) && other.y > actor.y
        }
    }

    private fun separateAxis(actor: Actor, solids: List<Actor>, horizontal: Boolean) {
        solids.forEach { other ->
            if (other === actor || !other.alive) return@forEach
            if (!actor.overlaps(other)) return@forEach

            val overlapX = (actor.drawWidth + other.drawWidth) / 2f - abs(actor.x - other.x)
            val overlapY = (actor.drawHeight + other.drawHeight) / 2f - abs(actor.y - other.y)
            if (overlapX <= 0f || overlapY <= 0f) return@forEach

            if (horizontal) {
                actor.x += if (actor.x < other.x) -overlapX else overlapX
                if (abs(actor.vx) > 0f) actor.vx = -actor.vx * actor.physics.bounce
            } else {
                val fromAbove = actor.y < other.y
                actor.y += if (fromAbove) -overlapY else overlapY
                if (fromAbove) actor.grounded = true
                actor.vy = if (actor.physics.bounce > 0f) -actor.vy * actor.physics.bounce else 0f
                if (abs(actor.vy) < 20f) actor.vy = 0f
            }
        }
    }

    private fun overlapsAt(actor: Actor, x: Float, y: Float, other: Actor): Boolean {
        val hw = actor.drawWidth / 2f + other.drawWidth / 2f
        val hh = actor.drawHeight / 2f + other.drawHeight / 2f
        return abs(x - other.x) < hw && abs(y - other.y) < hh
    }

    // ---- screen edges -------------------------------------------------------------------------

    fun touchesEdge(actor: Actor): Boolean {
        val hw = actor.drawWidth / 2f
        val hh = actor.drawHeight / 2f
        return actor.x - hw <= left || actor.x + hw >= right || actor.y - hh <= top || actor.y + hh >= bottom
    }

    fun clampToScreen(actor: Actor) {
        val hw = actor.drawWidth / 2f
        val hh = actor.drawHeight / 2f
        actor.x = actor.x.coerceIn(left + hw, max(left + hw, right - hw))
        actor.y = actor.y.coerceIn(top + hh, max(top + hh, bottom - hh))
    }

    fun bounceOnEdge(actor: Actor) {
        val hw = actor.drawWidth / 2f
        val hh = actor.drawHeight / 2f
        var bounced = false
        if (actor.x - hw < left) {
            actor.x = left + hw; actor.vx = abs(actor.vx); bounced = true
        } else if (actor.x + hw > right) {
            actor.x = right - hw; actor.vx = -abs(actor.vx); bounced = true
        }
        if (actor.y - hh < top) {
            actor.y = top + hh; actor.vy = abs(actor.vy); bounced = true
        } else if (actor.y + hh > bottom) {
            actor.y = bottom - hh; actor.vy = -abs(actor.vy); bounced = true
        }
        if (bounced) actor.rotation = ((180f - actor.rotation) % 360f + 360f) % 360f
    }

    // ---- camera -------------------------------------------------------------------------------

    fun shake(power: Float, seconds: Float) {
        shakePower = power
        shakeUntil = time + seconds
    }

    private fun updateCamera(dt: Float) {
        cameraTargetId?.let { id ->
            actors.firstOrNull { it.id == id && it.alive }?.let { target ->
                val k = min(1f, dt * 8f)
                cameraX += (target.x - cameraX) * k
                cameraY += (target.y - cameraY) * k
            }
        }
        if (time < shakeUntil && shakePower > 0f) {
            shakeOffsetX = ((Math.random() - 0.5) * 2 * shakePower).toFloat()
            shakeOffsetY = ((Math.random() - 0.5) * 2 * shakePower).toFloat()
        } else {
            shakeOffsetX = 0f
            shakeOffsetY = 0f
            shakePower = 0f
        }
    }

    private fun expireSpeech() {
        actors.forEach { actor ->
            if (actor.sayText != null && time > actor.sayUntil) actor.sayText = null
        }
    }

    /** Validates a project up-front so a typo in a saved file shows as a message, not a crash. */
    fun diagnostics(): List<String> {
        val problems = mutableListOf<String>()
        project.scenes.forEach { scene ->
            scene.objects.forEach { obj ->
                obj.scripts.forEach { script ->
                    if (BlockCatalog[script.type] == null) {
                        problems += "${scene.name} / ${obj.name}: blok tidak dikenal '${script.type}'"
                    }
                }
            }
        }
        return problems
    }

    private companion object {
        /** Keeps a runaway spawner from exhausting memory on a phone. */
        const val MAX_ACTORS = 600
    }
}
