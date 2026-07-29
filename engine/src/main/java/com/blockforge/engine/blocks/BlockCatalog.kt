package com.blockforge.engine.blocks

import com.blockforge.engine.blocks.BlockCategory.CONTROL
import com.blockforge.engine.blocks.BlockCategory.EVENT
import com.blockforge.engine.blocks.BlockCategory.GAME
import com.blockforge.engine.blocks.BlockCategory.LOOKS
import com.blockforge.engine.blocks.BlockCategory.MOTION
import com.blockforge.engine.blocks.BlockCategory.OPERATOR
import com.blockforge.engine.blocks.BlockCategory.SENSING
import com.blockforge.engine.blocks.BlockCategory.SOUND
import com.blockforge.engine.blocks.BlockCategory.VARIABLE
import com.blockforge.engine.blocks.BlockShape.BOOLEAN
import com.blockforge.engine.blocks.BlockShape.BRANCH
import com.blockforge.engine.blocks.BlockShape.HAT
import com.blockforge.engine.blocks.BlockShape.REPORTER
import com.blockforge.engine.blocks.BlockShape.STACK
import com.blockforge.engine.blocks.BlockShape.TERMINAL
import com.blockforge.engine.model.Arg
import com.blockforge.engine.model.BlockNode
import com.blockforge.engine.model.Lane

/** Every block the engine knows how to draw and run. Adding a block means adding it here and in the interpreter. */
object BlockCatalog {

    // ---- shared slot vocabularies -------------------------------------------------------------

    val KEYS = listOf(
        Choice("LEFT", "◀ kiri"),
        Choice("RIGHT", "▶ kanan"),
        Choice("UP", "▲ atas"),
        Choice("DOWN", "▼ bawah"),
        Choice("A", "A"),
        Choice("B", "B"),
        Choice("SPACE", "spasi"),
        Choice("ANY", "apa saja")
    )

    val COMPARE_OPS = listOf(
        Choice("<", "<"),
        Choice("<=", "≤"),
        Choice("==", "="),
        Choice("!=", "≠"),
        Choice(">=", "≥"),
        Choice(">", ">")
    )

    val PROPS = listOf(
        Choice("x", "posisi x"),
        Choice("y", "posisi y"),
        Choice("rotation", "arah"),
        Choice("width", "lebar"),
        Choice("height", "tinggi"),
        Choice("scale", "ukuran"),
        Choice("alpha", "transparansi"),
        Choice("vx", "kecepatan x"),
        Choice("vy", "kecepatan y")
    )

    val MATH_FNS = listOf(
        Choice("abs", "nilai mutlak"),
        Choice("round", "bulatkan"),
        Choice("floor", "bulat bawah"),
        Choice("ceil", "bulat atas"),
        Choice("sqrt", "akar"),
        Choice("sin", "sin"),
        Choice("cos", "cos"),
        Choice("tan", "tan"),
        Choice("sign", "tanda")
    )

    val STOP_TARGETS = listOf(
        Choice("this", "skrip ini"),
        Choice("all", "semua skrip"),
        Choice("others", "skrip lain di objek ini")
    )

    // ---- slot builders ------------------------------------------------------------------------

    private fun num(key: String, default: String = "0") = SlotDef(key, SlotKind.NUMBER, default)
    private fun txt(key: String, default: String = "") = SlotDef(key, SlotKind.TEXT, default)
    private fun bool(key: String) = SlotDef(key, SlotKind.BOOLEAN, "false")
    private fun choice(key: String, choices: List<Choice>, default: String = choices.first().value) =
        SlotDef(key, SlotKind.CHOICE, default, choices, acceptsBlock = false)

    private fun varSlot(key: String = "var") = SlotDef(key, SlotKind.VARIABLE, "", acceptsBlock = false)
    private fun msgSlot(key: String = "msg") = SlotDef(key, SlotKind.MESSAGE, "mulai", acceptsBlock = false)
    private fun objSlot(key: String = "obj") = SlotDef(key, SlotKind.OBJECT, "", acceptsBlock = false)
    private fun imgSlot(key: String = "image") = SlotDef(key, SlotKind.IMAGE, "", acceptsBlock = false)
    private fun sndSlot(key: String = "audio") = SlotDef(key, SlotKind.AUDIO, "", acceptsBlock = false)
    private fun sceneSlot(key: String = "scene") = SlotDef(key, SlotKind.SCENE, "", acceptsBlock = false)
    private fun keySlot(key: String = "key") = choice(key, KEYS, "RIGHT")
    private fun color(key: String = "color", default: String = "#4FC3F7") =
        SlotDef(key, SlotKind.COLOR, default, acceptsBlock = false)

    private fun body(label: String = "lakukan") = listOf(BranchDef(label))

    // ---- catalog ------------------------------------------------------------------------------

    val all: List<BlockDef> = listOf(

        // ============================== KEJADIAN (hat blocks) ==============================
        BlockDef(
            "event.start", EVENT, HAT, "saat game dimulai",
            branches = body("lane utama"),
            help = "Berjalan sekali, tepat setelah scene dimuat."
        ),
        BlockDef(
            "event.frame", EVENT, HAT, "setiap frame",
            branches = body("lane utama"),
            help = "Lane ini diulang sekali setiap frame — tempat logika gerak dan input."
        ),
        BlockDef(
            "event.message", EVENT, HAT, "saat menerima pesan {msg}",
            slots = listOf(msgSlot()),
            branches = body("lane utama"),
            help = "Dipicu oleh blok 'siarkan pesan'."
        ),
        BlockDef(
            "event.key_down", EVENT, HAT, "saat tombol {key} ditekan",
            slots = listOf(keySlot()),
            branches = body("lane utama")
        ),
        BlockDef(
            "event.key_up", EVENT, HAT, "saat tombol {key} dilepas",
            slots = listOf(keySlot()),
            branches = body("lane utama")
        ),
        BlockDef(
            "event.tap", EVENT, HAT, "saat objek ini disentuh",
            branches = body("lane utama")
        ),
        BlockDef(
            "event.var_when", EVENT, HAT, "saat {var} {op} {value}",
            slots = listOf(varSlot(), choice("op", COMPARE_OPS, ">="), num("value", "10")),
            branches = body("lane utama"),
            help = "Dipicu sekali setiap kali perbandingan berubah dari salah menjadi benar."
        ),
        BlockDef(
            "event.collision", EVENT, HAT, "saat menyentuh objek bertag {tag}",
            slots = listOf(txt("tag", "musuh")),
            branches = body("lane utama")
        ),
        BlockDef(
            "event.spawned", EVENT, HAT, "saat salinan objek ini dibuat",
            branches = body("lane utama")
        ),

        // ============================== KONTROL ==============================
        BlockDef(
            "control.wait", CONTROL, STACK, "tunggu {sec} detik",
            slots = listOf(num("sec", "1"))
        ),
        BlockDef(
            "control.if", CONTROL, BRANCH, "jika {cond} maka",
            slots = listOf(bool("cond")),
            branches = body("maka"),
            help = "Membuka satu cabang baru ke kanan."
        ),
        BlockDef(
            "control.if_else", CONTROL, BRANCH, "jika {cond} maka",
            slots = listOf(bool("cond")),
            branches = listOf(BranchDef("maka"), BranchDef("kalau tidak")),
            help = "Dua cabang ke kanan: yang atas jalan kalau benar, yang bawah kalau salah."
        ),
        BlockDef(
            "control.if_until", CONTROL, BRANCH, "jika {cond} maka ulangi sampai {until}",
            slots = listOf(bool("cond"), bool("until")),
            branches = body("lakukan"),
            help = "Kalau syarat pertama benar, cabang diulang tiap frame sampai syarat kedua benar."
        ),
        BlockDef(
            "control.repeat", CONTROL, BRANCH, "ulangi {times} kali",
            slots = listOf(num("times", "10")),
            branches = body("lakukan")
        ),
        BlockDef(
            "control.forever", CONTROL, BRANCH, "selamanya",
            branches = body("lakukan"),
            help = "Satu putaran per frame, jadi tidak akan membekukan game."
        ),
        BlockDef(
            "control.repeat_until", CONTROL, BRANCH, "ulangi sampai {cond}",
            slots = listOf(bool("cond")),
            branches = body("lakukan")
        ),
        BlockDef(
            "control.while", CONTROL, BRANCH, "selama {cond}",
            slots = listOf(bool("cond")),
            branches = body("lakukan")
        ),
        BlockDef(
            "control.branch", CONTROL, BRANCH, "cabang {name}",
            slots = listOf(txt("name", "cabang baru")),
            branches = body("isi cabang"),
            help = "Cabang polos: memanjangkan lane ke kanan untuk mengelompokkan kode."
        ),
        BlockDef(
            "control.wait_until", CONTROL, STACK, "tunggu sampai {cond}",
            slots = listOf(bool("cond"))
        ),
        BlockDef(
            "control.broadcast", CONTROL, STACK, "siarkan pesan {msg}",
            slots = listOf(msgSlot())
        ),
        BlockDef(
            "control.broadcast_wait", CONTROL, STACK, "siarkan {msg} dan tunggu selesai",
            slots = listOf(msgSlot())
        ),
        BlockDef(
            "control.stop", CONTROL, TERMINAL, "hentikan {target}",
            slots = listOf(choice("target", STOP_TARGETS))
        ),

        // ============================== GERAK ==============================
        BlockDef("motion.move", MOTION, STACK, "maju {dist} piksel", slots = listOf(num("dist", "10"))),
        BlockDef(
            "motion.move_xy", MOTION, STACK, "geser x {dx} y {dy}",
            slots = listOf(num("dx", "10"), num("dy", "0"))
        ),
        BlockDef(
            "motion.set_pos", MOTION, STACK, "pergi ke x {x} y {y}",
            slots = listOf(num("x"), num("y"))
        ),
        BlockDef("motion.set_x", MOTION, STACK, "atur x ke {x}", slots = listOf(num("x"))),
        BlockDef("motion.set_y", MOTION, STACK, "atur y ke {y}", slots = listOf(num("y"))),
        BlockDef("motion.turn", MOTION, STACK, "putar {deg} derajat", slots = listOf(num("deg", "15"))),
        BlockDef("motion.point_dir", MOTION, STACK, "hadap ke arah {deg}", slots = listOf(num("deg", "90"))),
        BlockDef("motion.point_to", MOTION, STACK, "hadap ke {obj}", slots = listOf(objSlot())),
        BlockDef(
            "motion.set_velocity", MOTION, STACK, "atur kecepatan x {vx} y {vy}",
            slots = listOf(num("vx", "0"), num("vy", "0"))
        ),
        BlockDef(
            "motion.add_velocity", MOTION, STACK, "tambah kecepatan x {vx} y {vy}",
            slots = listOf(num("vx", "0"), num("vy", "0"))
        ),
        BlockDef(
            "motion.jump", MOTION, STACK, "lompat dengan kekuatan {power}",
            slots = listOf(num("power", "600")),
            help = "Hanya bekerja kalau objek sedang menapak pada objek solid."
        ),
        BlockDef("motion.bounce_edge", MOTION, STACK, "pantul kalau menyentuh tepi"),
        BlockDef("motion.stay_on_screen", MOTION, STACK, "jangan keluar layar"),

        // ============================== TAMPILAN ==============================
        BlockDef("looks.set_sprite", LOOKS, STACK, "ganti gambar ke {image}", slots = listOf(imgSlot())),
        BlockDef("looks.show", LOOKS, STACK, "tampilkan"),
        BlockDef("looks.hide", LOOKS, STACK, "sembunyikan"),
        BlockDef("looks.set_size", LOOKS, STACK, "atur ukuran ke {pct} %", slots = listOf(num("pct", "100"))),
        BlockDef("looks.change_size", LOOKS, STACK, "ubah ukuran sebanyak {pct} %", slots = listOf(num("pct", "10"))),
        BlockDef("looks.set_alpha", LOOKS, STACK, "atur transparansi ke {pct} %", slots = listOf(num("pct", "100"))),
        BlockDef("looks.set_color", LOOKS, STACK, "atur warna ke {color}", slots = listOf(color())),
        BlockDef(
            "looks.say", LOOKS, STACK, "katakan {text} selama {sec} detik",
            slots = listOf(txt("text", "Halo!"), num("sec", "2"))
        ),
        BlockDef("looks.say_now", LOOKS, STACK, "katakan {text}", slots = listOf(txt("text", "Halo!"))),
        BlockDef("looks.set_z", LOOKS, STACK, "atur lapisan ke {z}", slots = listOf(num("z", "0"))),

        // ============================== SUARA ==============================
        BlockDef("sound.play", SOUND, STACK, "mainkan efek suara {audio}", slots = listOf(sndSlot())),
        BlockDef("sound.play_wait", SOUND, STACK, "mainkan efek {audio} sampai selesai", slots = listOf(sndSlot())),
        BlockDef("sound.music", SOUND, STACK, "mainkan musik latar {audio}", slots = listOf(sndSlot())),
        BlockDef("sound.stop_music", SOUND, STACK, "hentikan musik latar"),
        BlockDef("sound.volume", SOUND, STACK, "atur volume ke {pct} %", slots = listOf(num("pct", "80"))),

        // ============================== VARIABEL ==============================
        BlockDef(
            "var.set", VARIABLE, STACK, "atur {var} ke {value}",
            slots = listOf(varSlot(), txt("value", "0"))
        ),
        BlockDef(
            "var.change", VARIABLE, STACK, "ubah {var} sebanyak {delta}",
            slots = listOf(varSlot(), num("delta", "1"))
        ),
        BlockDef("var.show", VARIABLE, STACK, "tampilkan variabel {var}", slots = listOf(varSlot())),
        BlockDef("var.hide", VARIABLE, STACK, "sembunyikan variabel {var}", slots = listOf(varSlot())),
        BlockDef("var.get", VARIABLE, REPORTER, "{var}", slots = listOf(varSlot())),

        // ============================== GAME ==============================
        BlockDef(
            "game.spawn", GAME, STACK, "buat salinan {obj} di x {x} y {y}",
            slots = listOf(objSlot(), num("x"), num("y"))
        ),
        BlockDef("game.destroy", GAME, TERMINAL, "hapus objek ini"),
        BlockDef("game.destroy_tag", GAME, STACK, "hapus semua objek bertag {tag}", slots = listOf(txt("tag", "peluru"))),
        BlockDef("game.goto_scene", GAME, TERMINAL, "pindah ke scene {scene}", slots = listOf(sceneSlot())),
        BlockDef("game.restart", GAME, TERMINAL, "ulangi scene ini"),
        BlockDef("game.camera_follow", GAME, STACK, "kamera ikuti {obj}", slots = listOf(objSlot())),
        BlockDef(
            "game.camera_to", GAME, STACK, "kamera ke x {x} y {y}",
            slots = listOf(num("x"), num("y"))
        ),
        BlockDef(
            "game.shake", GAME, STACK, "guncang kamera {power} selama {sec} detik",
            slots = listOf(num("power", "12"), num("sec", "0.3"))
        ),
        BlockDef("game.quit", GAME, TERMINAL, "keluar dari game"),

        // ============================== SENSOR ==============================
        BlockDef("sense.key_pressed", SENSING, BOOLEAN, "tombol {key} sedang ditekan", slots = listOf(keySlot())),
        BlockDef(
            "sense.key_clicked", SENSING, BOOLEAN, "tombol {key} baru diklik",
            slots = listOf(keySlot()),
            help = "Benar hanya pada frame pertama tombol ditekan."
        ),
        BlockDef("sense.touch_tag", SENSING, BOOLEAN, "menyentuh objek bertag {tag}", slots = listOf(txt("tag", "musuh"))),
        BlockDef("sense.touch_object", SENSING, BOOLEAN, "menyentuh {obj}", slots = listOf(objSlot())),
        BlockDef("sense.touch_edge", SENSING, BOOLEAN, "menyentuh tepi layar"),
        BlockDef("sense.pointer_down", SENSING, BOOLEAN, "layar sedang disentuh"),
        BlockDef("sense.pointer_x", SENSING, REPORTER, "sentuhan x"),
        BlockDef("sense.pointer_y", SENSING, REPORTER, "sentuhan y"),
        BlockDef("sense.self", SENSING, REPORTER, "{prop} objek ini", slots = listOf(choice("prop", PROPS))),
        BlockDef(
            "sense.of_object", SENSING, REPORTER, "{prop} dari {obj}",
            slots = listOf(choice("prop", PROPS), objSlot())
        ),
        BlockDef("sense.distance_to", SENSING, REPORTER, "jarak ke {obj}", slots = listOf(objSlot())),
        BlockDef("sense.timer", SENSING, REPORTER, "waktu berjalan"),
        BlockDef("sense.delta", SENSING, REPORTER, "delta waktu"),
        BlockDef(
            "sense.random", SENSING, REPORTER, "acak {min} sampai {max}",
            slots = listOf(num("min", "1"), num("max", "10"))
        ),
        BlockDef("sense.count_tag", SENSING, REPORTER, "jumlah objek bertag {tag}", slots = listOf(txt("tag", "musuh"))),

        // ============================== OPERATOR ==============================
        BlockDef("op.add", OPERATOR, REPORTER, "{a} + {b}", slots = listOf(num("a"), num("b"))),
        BlockDef("op.sub", OPERATOR, REPORTER, "{a} − {b}", slots = listOf(num("a"), num("b"))),
        BlockDef("op.mul", OPERATOR, REPORTER, "{a} × {b}", slots = listOf(num("a"), num("b"))),
        BlockDef("op.div", OPERATOR, REPORTER, "{a} ÷ {b}", slots = listOf(num("a"), num("b", "1"))),
        BlockDef("op.mod", OPERATOR, REPORTER, "sisa bagi {a} ÷ {b}", slots = listOf(num("a"), num("b", "1"))),
        BlockDef(
            "op.compare", OPERATOR, BOOLEAN, "{a} {op} {b}",
            slots = listOf(txt("a", "0"), choice("op", COMPARE_OPS, ">="), txt("b", "0"))
        ),
        BlockDef("op.and", OPERATOR, BOOLEAN, "{a} dan {b}", slots = listOf(bool("a"), bool("b"))),
        BlockDef("op.or", OPERATOR, BOOLEAN, "{a} atau {b}", slots = listOf(bool("a"), bool("b"))),
        BlockDef("op.not", OPERATOR, BOOLEAN, "tidak {a}", slots = listOf(bool("a"))),
        BlockDef(
            "op.math", OPERATOR, REPORTER, "{fn} dari {n}",
            slots = listOf(choice("fn", MATH_FNS), num("n"))
        ),
        BlockDef("op.min", OPERATOR, REPORTER, "terkecil dari {a} dan {b}", slots = listOf(num("a"), num("b"))),
        BlockDef("op.max", OPERATOR, REPORTER, "terbesar dari {a} dan {b}", slots = listOf(num("a"), num("b"))),
        BlockDef(
            "op.clamp", OPERATOR, REPORTER, "batasi {n} antara {min} dan {max}",
            slots = listOf(num("n"), num("min", "0"), num("max", "100"))
        ),
        BlockDef("op.join", OPERATOR, REPORTER, "gabung {a} {b}", slots = listOf(txt("a", "skor "), txt("b", "0")))
    )

    private val byType: Map<String, BlockDef> = all.associateBy { it.type }

    operator fun get(type: String): BlockDef? = byType[type]

    fun require(type: String): BlockDef =
        byType[type] ?: error("Blok tidak dikenal: $type")

    fun byCategory(category: BlockCategory): List<BlockDef> = all.filter { it.category == category }

    val categories: List<BlockCategory> = BlockCategory.entries.toList()

    fun search(query: String): List<BlockDef> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter { it.plainLabel.lowercase().contains(q) || it.type.contains(q) }
    }

    /** Builds a node with every slot pre-filled and every branch present, so it is runnable at once. */
    fun instantiate(type: String): BlockNode {
        val def = require(type)
        return BlockNode(
            type = type,
            args = def.slots.associate { it.key to (Arg.Lit(it.default) as Arg) },
            branches = def.branches.map { Lane(label = it.label) }
        )
    }
}
