package com.blockforge.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One block in a script.
 *
 * The whole editing model is built on two rules:
 *  - a [Lane] is a straight run of blocks flowing downward, and
 *  - a block that needs a body opens a *new lane to the right* instead of swallowing blocks inside
 *    itself the way a C-block does.
 *
 * So a script is a tree that grows down and to the right, and the lane a block lives in defines what
 * it can see: blocks in a child lane belong to that branch and nothing else.
 */
@Serializable
data class BlockNode(
    val id: String = newId("blk"),
    /** Matches [com.blockforge.engine.blocks.BlockDef.type], e.g. `control.wait`. */
    val type: String,
    val args: Map<String, Arg> = emptyMap(),
    /** One lane per body. `if/else` has two, `if-then-until` has one, plain stack blocks have none. */
    val branches: List<Lane> = emptyList(),
    /** Editor-only position for top-level hat blocks on the script canvas. */
    val canvasX: Float = 0f,
    val canvasY: Float = 0f,
    val collapsed: Boolean = false
) {
    fun arg(key: String): Arg? = args[key]

    fun literal(key: String): String? = (args[key] as? Arg.Lit)?.value

    fun branch(index: Int): Lane? = branches.getOrNull(index)

    fun withArg(key: String, value: Arg): BlockNode = copy(args = args + (key to value))

    fun withBranch(index: Int, lane: Lane): BlockNode =
        copy(branches = branches.mapIndexed { i, b -> if (i == index) lane else b })
}

/**
 * A vertical run of blocks. Lanes auto-size: the editor never stores a height, it just lays out
 * however many blocks are in [nodes], so adding a block grows the lane and deleting one shrinks it.
 */
@Serializable
data class Lane(
    val id: String = newId("lane"),
    val label: String = "",
    val nodes: List<BlockNode> = emptyList()
) {
    val size: Int get() = nodes.size
    fun isEmpty(): Boolean = nodes.isEmpty()
}

/** A value plugged into a block slot: either typed in directly, or another block reporting a value. */
@Serializable
sealed interface Arg {
    @Serializable
    @SerialName("lit")
    data class Lit(val value: String) : Arg

    @Serializable
    @SerialName("blk")
    data class Blk(val node: BlockNode) : Arg
}

fun lit(value: String): Arg.Lit = Arg.Lit(value)
fun lit(value: Number): Arg.Lit = Arg.Lit(value.toString())
fun lit(value: Boolean): Arg.Lit = Arg.Lit(value.toString())
