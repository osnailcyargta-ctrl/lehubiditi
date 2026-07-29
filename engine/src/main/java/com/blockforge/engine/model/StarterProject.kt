package com.blockforge.engine.model

import com.blockforge.engine.blocks.BlockCatalog

/**
 * A small but complete platformer used as the default new project.
 *
 * Opening the editor to an empty canvas teaches nothing; opening it to a running game that uses
 * every important block — a frame lane, a branch to the right, a repeat-until, a broadcast and a
 * variable watcher — gives a creator something to take apart.
 */
object StarterProject {

    fun create(name: String = "Petualangan Pertama"): GameProject {
        val skorVar = VariableDef(name = "skor", kind = VariableKind.NUMBER, initial = "0", showOnScreen = true)
        val nyawaVar = VariableDef(name = "nyawa", kind = VariableKind.NUMBER, initial = "3", showOnScreen = true)

        val player = buildPlayer(skorVar, nyawaVar)
        val ground = buildGround()
        val platform = buildPlatform()
        val coin = buildCoin(skorVar)

        val scene = Scene(
            name = "Level 1",
            backgroundColor = 0xFF121A2B.toInt(),
            objects = listOf(ground, platform, coin, player)
        )

        return GameProject(
            name = name,
            packageId = "com.example.petualangan",
            variables = listOf(skorVar, nyawaVar),
            messages = listOf("mulai", "koin diambil", "game over"),
            scenes = listOf(scene),
            startSceneId = scene.id,
            settings = GameSettings(showVirtualPad = true, gravity = 1800f)
        )
    }

    // ---- objects ----------------------------------------------------------------------------

    private fun buildPlayer(skor: VariableDef, nyawa: VariableDef): GameObject {
        val langkah = "5"

        /** setiap frame → tiap tombol membuka satu cabang ke kanan, lalu jaga tetap di dalam layar. */
        val frameScript = hat("event.frame") {
            branch(
                block("control.if") {
                    argBlock("cond", block("sense.key_pressed") { arg("key", "LEFT") })
                    body(block("motion.move_xy") { arg("dx", "-$langkah"); arg("dy", "0") })
                },
                block("control.if") {
                    argBlock("cond", block("sense.key_pressed") { arg("key", "RIGHT") })
                    body(block("motion.move_xy") { arg("dx", langkah); arg("dy", "0") })
                },
                block("control.if") {
                    argBlock("cond", block("sense.key_clicked") { arg("key", "A") })
                    body(block("motion.jump") { arg("power", "760") })
                },
                block("motion.stay_on_screen")
            )
        }

        /** saat menyentuh koin → tambah skor, bunyikan, siarkan pesan. */
        val coinScript = hat("event.collision") {
            arg("tag", "koin")
            branch(
                block("var.change") { arg("var", skor.id); arg("delta", "1") },
                block("control.broadcast") { arg("msg", "koin diambil") },
                block("game.shake") { arg("power", "8"); arg("sec", "0.15") }
            )
        }

        /** saat skor >= 5 → menang: cabang berulang sampai selesai lalu pesan. */
        val winScript = hat("event.var_when") {
            arg("var", skor.id); arg("op", ">="); arg("value", "5")
            branch(
                block("looks.say") { arg("text", "Menang! 🎉"); arg("sec", "2") },
                block("control.if_until") {
                    argBlock(
                        "cond",
                        block("op.compare") {
                            argBlock("a", block("var.get") { arg("var", nyawa.id) })
                            arg("op", ">"); arg("b", "0")
                        }
                    )
                    argBlock(
                        "until",
                        block("op.compare") {
                            argBlock("a", block("sense.self") { arg("prop", "y") })
                            arg("op", "<="); arg("b", "120")
                        }
                    )
                    body(block("motion.move_xy") { arg("dx", "0"); arg("dy", "-4") })
                },
                block("control.broadcast") { arg("msg", "game over") }
            )
        }

        val startScript = hat("event.start") {
            branch(
                block("game.camera_follow") { arg("obj", "") },
                block("var.set") { arg("var", nyawa.id); arg("value", "3") },
                block("looks.say") { arg("text", "Kumpulkan 5 koin!"); arg("sec", "2") }
            )
        }

        return GameObject(
            name = "Pemain",
            tag = "pemain",
            x = 180f, y = 300f,
            width = 64f, height = 84f,
            fallbackColor = 0xFF4FC3F7.toInt(),
            physics = PhysicsBody(enabled = true, static = false, gravityScale = 1f, friction = 0.18f, solid = true),
            scripts = listOf(startScript, frameScript, coinScript, winScript)
        )
    }

    private fun buildGround() = GameObject(
        name = "Tanah",
        tag = "tanah",
        x = 480f, y = 505f,
        width = 960f, height = 70f,
        fallbackColor = 0xFF2E7D5B.toInt(),
        physics = PhysicsBody(enabled = true, static = true, solid = true)
    )

    private fun buildPlatform() = GameObject(
        name = "Platform",
        tag = "tanah",
        x = 660f, y = 350f,
        width = 220f, height = 32f,
        fallbackColor = 0xFF3A6EA5.toInt(),
        physics = PhysicsBody(enabled = true, static = true, solid = true)
    )

    private fun buildCoin(skor: VariableDef): GameObject {
        val spin = hat("event.frame") {
            branch(
                block("motion.turn") { arg("deg", "3") }
            )
        }

        /** saat menerima "koin diambil" → pindah ke posisi acak, itu yang membuat loop permainannya. */
        val respawn = hat("event.message") {
            arg("msg", "koin diambil")
            branch(
                block("looks.hide"),
                block("control.wait") { arg("sec", "0.4") },
                block("motion.set_pos") {
                    argBlock("x", block("sense.random") { arg("min", "120"); arg("max", "840") })
                    argBlock("y", block("sense.random") { arg("min", "150"); arg("max", "420") })
                },
                block("looks.show")
            )
        }

        return GameObject(
            name = "Koin",
            tag = "koin",
            x = 700f, y = 280f,
            width = 44f, height = 44f,
            shape = ObjectShape.CIRCLE,
            fallbackColor = 0xFFFFD166.toInt(),
            scripts = listOf(spin, respawn)
        )
    }

    // ---- tiny builder DSL ---------------------------------------------------------------------

    private class NodeBuilder(type: String) {
        private var node = BlockCatalog.instantiate(type)

        fun arg(key: String, value: String) {
            node = node.withArg(key, Arg.Lit(value))
        }

        fun argBlock(key: String, child: BlockNode) {
            node = node.withArg(key, Arg.Blk(child))
        }

        fun body(vararg children: BlockNode) {
            branch(*children)
        }

        fun branch(vararg children: BlockNode) {
            val lane = (node.branch(0) ?: Lane()).copy(nodes = children.toList())
            node = if (node.branches.isEmpty()) node.copy(branches = listOf(lane))
            else node.withBranch(0, lane)
        }

        fun build(): BlockNode = node
    }

    private fun block(type: String, configure: NodeBuilder.() -> Unit = {}): BlockNode =
        NodeBuilder(type).apply(configure).build()

    private fun hat(type: String, configure: NodeBuilder.() -> Unit): BlockNode =
        NodeBuilder(type).apply(configure).build()
}
