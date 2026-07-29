package com.blockforge.engine.blocks

import com.blockforge.engine.model.Arg
import com.blockforge.engine.model.BlockNode
import com.blockforge.engine.model.Lane

/**
 * Pure structural edits on a script forest.
 *
 * Everything here returns a new list rather than mutating, which is what lets the editor keep an
 * undo stack for free and lets Compose diff the tree cheaply. Lanes carry no stored size, so an
 * insert grows the lane and a removal shrinks it with no bookkeeping on either side.
 */
object BlockTree {

    // ---- queries ------------------------------------------------------------------------------

    fun findNode(scripts: List<BlockNode>, id: String): BlockNode? {
        scripts.forEach { node -> findIn(node, id)?.let { return it } }
        return null
    }

    private fun findIn(node: BlockNode, id: String): BlockNode? {
        if (node.id == id) return node
        node.branches.forEach { lane ->
            lane.nodes.forEach { child -> findIn(child, id)?.let { return it } }
        }
        node.args.values.forEach { arg ->
            if (arg is Arg.Blk) findIn(arg.node, id)?.let { return it }
        }
        return null
    }

    fun findLane(scripts: List<BlockNode>, laneId: String): Lane? {
        scripts.forEach { node -> findLaneIn(node, laneId)?.let { return it } }
        return null
    }

    private fun findLaneIn(node: BlockNode, laneId: String): Lane? {
        node.branches.forEach { lane ->
            if (lane.id == laneId) return lane
            lane.nodes.forEach { child -> findLaneIn(child, laneId)?.let { return it } }
        }
        node.args.values.forEach { arg ->
            if (arg is Arg.Blk) findLaneIn(arg.node, laneId)?.let { return it }
        }
        return null
    }

    /** True when [id] is [node] or lives anywhere inside it — the guard against dropping a block into itself. */
    fun contains(node: BlockNode, id: String): Boolean = findIn(node, id) != null

    fun countBlocks(scripts: List<BlockNode>): Int = scripts.sumOf { countIn(it) }

    private fun countIn(node: BlockNode): Int =
        1 + node.branches.sumOf { lane -> lane.nodes.sumOf { countIn(it) } } +
            node.args.values.sumOf { if (it is Arg.Blk) countIn(it.node) else 0 }

    /** The id of the lane holding [blockId], or null when it is a top-level hat or lives in a slot. */
    fun parentLaneId(scripts: List<BlockNode>, blockId: String): String? {
        scripts.forEach { node -> parentLaneIn(node, blockId)?.let { return it } }
        return null
    }

    private fun parentLaneIn(node: BlockNode, blockId: String): String? {
        node.branches.forEach { lane ->
            if (lane.nodes.any { it.id == blockId }) return lane.id
            lane.nodes.forEach { child -> parentLaneIn(child, blockId)?.let { return it } }
        }
        node.args.values.forEach { arg ->
            if (arg is Arg.Blk) parentLaneIn(arg.node, blockId)?.let { return it }
        }
        return null
    }

    // ---- edits --------------------------------------------------------------------------------

    /** Rewrites the node with [id] in place, leaving the rest of the forest untouched. */
    fun updateNode(scripts: List<BlockNode>, id: String, transform: (BlockNode) -> BlockNode): List<BlockNode> =
        scripts.map { updateIn(it, id, transform) }

    private fun updateIn(node: BlockNode, id: String, transform: (BlockNode) -> BlockNode): BlockNode {
        if (node.id == id) return transform(node)
        val branches = node.branches.map { lane ->
            lane.copy(nodes = lane.nodes.map { updateIn(it, id, transform) })
        }
        val args = node.args.mapValues { (_, arg) ->
            if (arg is Arg.Blk) Arg.Blk(updateIn(arg.node, id, transform)) else arg
        }
        return node.copy(branches = branches, args = args)
    }

    /** Inserts [newNode] into lane [laneId] at [index] (clamped). The lane grows by exactly one row. */
    fun insertIntoLane(
        scripts: List<BlockNode>,
        laneId: String,
        index: Int,
        newNode: BlockNode
    ): List<BlockNode> = scripts.map { insertIn(it, laneId, index, newNode) }

    private fun insertIn(node: BlockNode, laneId: String, index: Int, newNode: BlockNode): BlockNode {
        val branches = node.branches.map { lane ->
            val updated = if (lane.id == laneId) {
                val at = index.coerceIn(0, lane.nodes.size)
                lane.copy(nodes = lane.nodes.toMutableList().apply { add(at, newNode) })
            } else {
                lane
            }
            updated.copy(nodes = updated.nodes.map { child ->
                if (child.id == newNode.id) child else insertIn(child, laneId, index, newNode)
            })
        }
        val args = node.args.mapValues { (_, arg) ->
            if (arg is Arg.Blk) Arg.Blk(insertIn(arg.node, laneId, index, newNode)) else arg
        }
        return node.copy(branches = branches, args = args)
    }

    /**
     * Detaches the block with [id] (and its whole subtree) from wherever it sits — a lane row, a
     * value slot, or the top level. Slots fall back to the block definition's default so a hole in
     * the middle of a label can never happen.
     */
    fun removeNode(scripts: List<BlockNode>, id: String): Pair<List<BlockNode>, BlockNode?> {
        val removed = findNode(scripts, id) ?: return scripts to null
        val top = scripts.filterNot { it.id == id }
        if (top.size != scripts.size) return top to removed
        return scripts.map { removeIn(it, id) } to removed
    }

    private fun removeIn(node: BlockNode, id: String): BlockNode {
        val branches = node.branches.map { lane ->
            lane.copy(nodes = lane.nodes.filterNot { it.id == id }.map { removeIn(it, id) })
        }
        val def = BlockCatalog[node.type]
        val args = node.args.mapValues { (key, arg) ->
            when {
                arg is Arg.Blk && arg.node.id == id ->
                    Arg.Lit(def?.slot(key)?.default ?: "")
                arg is Arg.Blk -> Arg.Blk(removeIn(arg.node, id))
                else -> arg
            }
        }
        return node.copy(branches = branches, args = args)
    }

    fun setArg(scripts: List<BlockNode>, blockId: String, key: String, arg: Arg): List<BlockNode> =
        updateNode(scripts, blockId) { it.withArg(key, arg) }

    fun clearArg(scripts: List<BlockNode>, blockId: String, key: String): List<BlockNode> =
        updateNode(scripts, blockId) { node ->
            val fallback = BlockCatalog[node.type]?.slot(key)?.default ?: ""
            node.withArg(key, Arg.Lit(fallback))
        }

    /** Appends an extra body lane to a branch block — the "buat cabang baru" action. */
    fun addBranch(scripts: List<BlockNode>, blockId: String, label: String): List<BlockNode> =
        updateNode(scripts, blockId) { it.copy(branches = it.branches + Lane(label = label)) }

    fun removeBranch(scripts: List<BlockNode>, blockId: String, index: Int): List<BlockNode> =
        updateNode(scripts, blockId) { node ->
            if (node.branches.size <= 1) node
            else node.copy(branches = node.branches.filterIndexed { i, _ -> i != index })
        }

    /**
     * Moves [blockId] into lane [targetLaneId] at [index]. Refuses moves that would put a block
     * inside its own subtree, which would otherwise produce an unrenderable cycle.
     */
    fun moveNode(
        scripts: List<BlockNode>,
        blockId: String,
        targetLaneId: String,
        index: Int
    ): List<BlockNode> {
        val moving = findNode(scripts, blockId) ?: return scripts
        if (laneIsInside(moving, targetLaneId)) return scripts

        val sourceLane = parentLaneId(scripts, blockId)
        val (detached, removed) = removeNode(scripts, blockId)
        if (removed == null) return scripts

        // Removing a row above the drop point shifts everything below it up by one.
        val laneAfter = findLane(detached, targetLaneId)
        val originalIndex = findLane(scripts, targetLaneId)?.nodes?.indexOfFirst { it.id == blockId } ?: -1
        val adjusted = if (sourceLane == targetLaneId && originalIndex in 0 until index) index - 1 else index
        val safeIndex = adjusted.coerceIn(0, laneAfter?.nodes?.size ?: 0)
        return insertIntoLane(detached, targetLaneId, safeIndex, removed)
    }

    private fun laneIsInside(node: BlockNode, laneId: String): Boolean = findLaneIn(node, laneId) != null

    /** Regenerates every id in a subtree so a copied block never collides with its original. */
    fun regenerateIds(node: BlockNode): BlockNode = node.copy(
        id = com.blockforge.engine.model.newId("blk"),
        branches = node.branches.map { lane ->
            lane.copy(
                id = com.blockforge.engine.model.newId("lane"),
                nodes = lane.nodes.map { regenerateIds(it) }
            )
        },
        args = node.args.mapValues { (_, arg) ->
            if (arg is Arg.Blk) Arg.Blk(regenerateIds(arg.node)) else arg
        }
    )
}
