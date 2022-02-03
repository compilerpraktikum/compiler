package edu.kit.compiler.optimization

import firm.BackEdges
import firm.Graph
import firm.Mode
import firm.nodes.Add
import firm.nodes.Bad
import firm.nodes.Call
import firm.nodes.Const
import firm.nodes.Load
import firm.nodes.Node
import firm.nodes.Phi
import firm.nodes.Proj
import firm.nodes.Return
import firm.nodes.Store
import java.lang.IllegalStateException
import java.util.Stack

val StoreAfterStoreOptimization = Optimization("store after store elimination", ::applyStoreAfterStoreOptimization)

fun applyStoreAfterStoreOptimization(graph: Graph): Boolean {
    return StoreAfterStore(graph).removeDeadStores()
}

/**
 * Collect all relevant store nodes
 * TODO gescheit dokumentieren...
 */
class StoreAfterStoreNodeCollector(
    // TODO
    private val LocalVarStoreStack: Stack<Store>,
    private val localVarStoreToAddressProjNodeMap: HashMap<Store, Proj>,
    private val localVarStoreToAddressProjOffsetMap: HashMap<Store, Int>,
    private val localVarLoadToAddressProjNodeMap: HashMap<Load, Proj>,
    private val localVarLoadToAddressProjOffsetMap: HashMap<Load, Int>,
) : FirmNodeVisitorAdapter() {

    override fun visit(node: Store) {
        // 1 = "memory address"
        val predNode = node.getPred(1)
        if (predNode is Proj) {
            LocalVarStoreStack.push(node)
            localVarStoreToAddressProjNodeMap[node] = predNode
            localVarStoreToAddressProjOffsetMap[node] = 0
        } else if (predNode is Add) {
            // 1 is const 0x8
            val possibleConst = predNode.getPred(1)
            if (possibleConst is Const && possibleConst.mode == Mode.getLs()) {
                val possibleProj = predNode.getPred(0)
                if (possibleProj is Proj) {
                    LocalVarStoreStack.push(node)
                    localVarStoreToAddressProjNodeMap[node] = possibleProj
                    localVarStoreToAddressProjOffsetMap[node] = possibleConst.tarval.asInt()
                }
            }
        }
    }

    override fun visit(node: Load) {
        // 1 = "memory address"
        val predNode = node.getPred(1)
        if (predNode is Proj) {
            localVarLoadToAddressProjNodeMap[node] = predNode
            localVarLoadToAddressProjOffsetMap[node] = 0
        } else if (predNode is Add) {
            // 1 is const 0x8
            val possibleConst = predNode.getPred(1)
            if (possibleConst is Const && possibleConst.mode == Mode.getLs()) {
                val possibleProj = predNode.getPred(0)
                if (possibleProj is Proj) {
                    localVarLoadToAddressProjNodeMap[node] = possibleProj
                    localVarLoadToAddressProjOffsetMap[node] = possibleConst.tarval.asInt()
                }
            }
        }
    }
}

/**
 * Dead store elimination for primitive typed local variables (no arrays!).
 */
class StoreAfterStore(val graph: Graph) {

    // todo if langeweile: die interfaces sind relativ sicher bullshit..
    class Path<T>(val list: MutableList<T>, override val size: Int = 0) : MutableList<T>, Set<T> {
        val nodeHashSet: MutableSet<T> = HashSet(size)

        init {
            nodeHashSet.addAll(list)
        }

        override fun iterator() = list.iterator()
        override fun contains(element: T) = nodeHashSet.contains(element)
        override fun containsAll(elements: Collection<T>) = nodeHashSet.containsAll(elements)
        override fun isEmpty() = nodeHashSet.isEmpty()
        override fun get(index: Int): T = list.get(index)

        override fun indexOf(element: T) = list.indexOf(element)
        override fun lastIndexOf(element: T) = list.lastIndexOf(element)
        override fun listIterator() = list.listIterator()
        override fun listIterator(index: Int) = list.listIterator(index)
        override fun spliterator() = list.spliterator()

        override fun subList(fromIndex: Int, toIndex: Int) = list.subList(fromIndex, toIndex)

        fun cloneAndAdd(element: T): Path<T> {
            val newPath = Path(list.toMutableList())
            newPath.add(element)
            return newPath
        }

        override fun toString(): String {
            return "Path[" + list.fold("") { acc, t ->
                "$acc${ if (acc.isEmpty()) "$t" else " --> $t" }"
            } + "]"
        }

        override fun add(element: T): Boolean {
            nodeHashSet.add(element)
            return list.add(element)
        }
        override fun add(index: Int, element: T) {
            list.add(index, element)
            nodeHashSet.add(element)
        }
        override fun addAll(index: Int, elements: Collection<T>): Boolean {
            nodeHashSet.addAll(elements)
            return list.addAll(index, elements)
        }
        override fun addAll(elements: Collection<T>): Boolean {
            nodeHashSet.addAll(elements)
            return list.addAll(elements)
        }
        override fun clear() {
            list.clear()
            nodeHashSet.clear()
        }
        override fun remove(element: T): Boolean {
            while (list.remove(element)) list.remove(element)
            return nodeHashSet.remove(element)
        }
        override fun removeAll(elements: Collection<T>): Boolean {
            nodeHashSet.removeAll(elements.toSet())
            return list.removeAll(elements)
        }
        override fun removeAt(index: Int): T {
            val element = list.removeAt(index)
            nodeHashSet.remove(element)
            return element
        }
        override fun retainAll(elements: Collection<T>): Boolean {
            list.retainAll(elements)
            return nodeHashSet.retainAll(elements)
        }
        override fun set(index: Int, element: T): T {
            val previous = list.set(index, element)
            nodeHashSet.remove(previous)
            nodeHashSet.add(element)
            return previous
        }
    }

    /**
     * A DataRiverClosure represents circles that intersect with at least one other circle in the DataRiverClosure.
     * That means each node in the closure can be reached by each other node in the closure (infinitely often).
     */
    class DataRiverClosure(initialCircle: Path<Node>) {
        val circles = mutableSetOf<StoreAfterStore.Path<Node>>()
        val nodeSet = mutableSetOf<Node>()

        init { addToClosure(initialCircle) }
        /**
         * @return whether the given circle overlaps with this closure
         */
        fun overlaps(circle: Path<Node>): Boolean {
            return circle.nodeHashSet.any { it in nodeSet }
        }

        /**
         * Add to closure
         */
        fun addToClosure(circle: Path<Node>) {
            circles.add(circle)
            nodeSet.addAll(circle.nodeHashSet)
        }

        fun uniteIntoThis(dataRiverClosure: DataRiverClosure): Boolean {
            if (dataRiverClosure === this) return false
            return if (dataRiverClosure.circles.any { this.overlaps(it) }) {
                dataRiverClosure.circles.forEach { this.addToClosure(it) }
                true
            } else false
        }

        fun getAllPossibleBackedges(node: Node): List<Node> {
            // not really edges but returning a pair of node, .. is unnecessary.
            val backEdges = mutableListOf<Node>()
            circles.filter { path -> path.contains(node) }.forEach { path ->
                // Only 2 Options:
                if (path.list.last() == node) {
                    // 1. (node -> ... backedgeNode-> node)
                    backEdges.add(path.list[path.list.size - 2])
                } else {
                    // 2. (... -> backedgeNode -> node -> ...)
                    backEdges.add(path.list[path.list.indexOf(node) - 1])
                }
            }
            return backEdges
        }
    }

    val storePaths: MutableList<Path<Node>> = mutableListOf()
    val circlePaths: MutableList<Path<Node>> = mutableListOf()
    val circleDataRiverClosures: MutableList<DataRiverClosure> = mutableListOf()

    val localVarStoreStack = Stack<Store>()
    val localVarStoreToAddressProjNodeMap = HashMap<Store, Proj>()
    val localVarLoadToAddressProjNodeMap = HashMap<Load, Proj>()
    val localVarStoreToAddressProjOffsetMap = HashMap<Store, Int>()
    val localVarLoadToAddressProjOffsetMap = HashMap<Load, Int>()

    var deletedAnyStoreNode = false

    private fun calculateSpanningForest() = graph.end.block.preds.forEach { calculateSpanningTree(it as Return, Path(mutableListOf(it))) }

    /**
     * @param path includes param node!
     */
    private fun calculateSpanningTree(node: Node, path: Path<Node>) {
        if (node == graph.start) {
            return
        } else if (node is Bad) {
            error("GOT BAD NODE $node. Abort mission.")
            return
        } else if (node is Store) {
            // 1. handle store
            storePaths.add(path)

            // 2. handle children
            val next0Pred = node.getPred(0)
            if (!checkCircleInNewPathAndStoreCirclePath(next0Pred, path)) {
                calculateSpanningTree(next0Pred, path.cloneAndAdd(next0Pred))
            }
        } else if (node is Phi) {
            node.preds.forEach {
                // continue only if not visited (circle)
                if (!checkCircleInNewPathAndStoreCirclePath(it, path)) {
                    calculateSpanningTree(it, path.cloneAndAdd(it))
                }
            }
        } else {
            val nextNode = node.getPred(0)
            if (!checkCircleInNewPathAndStoreCirclePath(nextNode, path)) {
                calculateSpanningTree(nextNode, path.cloneAndAdd(nextNode))
            }
        }
    }

    private fun checkCircleInNewPathAndStoreCirclePath(nextNode: Node, oldPath: Path<Node>): Boolean {
        return if (oldPath.contains(nextNode)) {
            // ( ReturnNode -> ... NodeI -> ... -> NodeI) ==> (NodeI -> ... -> NodeI)
            circlePaths.add(circlify(oldPath.cloneAndAdd(nextNode)))
            true
        } else {
            false
        }
    }

    private fun circlify(pathFromStartToCircleEnd: Path<Node>): Path<Node> {
        return Path(
            pathFromStartToCircleEnd.subList(
                pathFromStartToCircleEnd.list.indexOfFirst {
                    it == pathFromStartToCircleEnd.list.last()
                },
                pathFromStartToCircleEnd.list.size
            )
        )
    }

    private fun calculateDataRiverClosures() {

        // 1. Fit every circlePath in a closure
        circlePaths.forEach { circle ->

            // 1.1 if any existing closure fits, add it to that one
            var foundFittingClosure = false
            circleDataRiverClosures.forEach { dataRiverClosure ->
                if (dataRiverClosure.overlaps(circle)) {
                    dataRiverClosure.addToClosure(circle)
                    foundFittingClosure = true
                }
            }

            // 1.2 else create own closure
            if (!foundFittingClosure) {
                circleDataRiverClosures.add(DataRiverClosure(circle))
            }
        }

        // 2. Unite closures
        var hasChanged = true
        while (hasChanged) {
            hasChanged = false
            for (dataRiverClosure1 in circleDataRiverClosures) {
                val it = circleDataRiverClosures.listIterator()
                while (it.hasNext()) {
                    val dataRiverClosure2 = it.next()
                    if (dataRiverClosure1.uniteIntoThis(dataRiverClosure2)) {
                        hasChanged = true
                        it.remove()
                    }
                }
            }
        }
    }

    fun removeDeadStores(): Boolean {
        // Collect all stores and populate maps from load and store to their respective proj nodes (invariant for LOCAL VAR ACCESSES, ONLY!)
        graph.walk(
            StoreAfterStoreNodeCollector(
                localVarStoreStack, localVarStoreToAddressProjNodeMap,
                localVarStoreToAddressProjOffsetMap, localVarLoadToAddressProjNodeMap, localVarLoadToAddressProjOffsetMap
            )
        )

        /* For each Return node, run a spanning tree algorithm (DFS) which yields
            * Each path from each return to each store node
            * all circles in the graph
         */
        calculateSpanningForest()

        /* Calculate DataRiverClosures™ (Def 1.2) which each contain all circles that are "reachability-connected".
           These Closures are disjunct!
         */
        calculateDataRiverClosures()

        /* Determine whether store nodes are dead stores (Def 0.0) and delete them. */
        localVarStoreToAddressProjNodeMap.keys
            .filter { isDeadStore(it) }
//            .map { println("  - $it"); it }
            .forEach { deleteStoreNode(it) }
        return deletedAnyStoreNode
    }

    private fun deleteStoreNode(store: Store) {
        BackEdges.enable(graph)
        // rewire nodes
        val pred0 = store.getPred(0)

        // pred0(Proj) <-- it(Store) <--[backEdge]-- node(Proj) <--[backEdgeOfProj]-- node(?)
        // ===after_deletion===>
        // pred0(Proj) <-- node(?)
        BackEdges.getOuts(store).forEach { backEdge ->
            BackEdges.getOuts(backEdge.node).forEach { backEdgeOfProj ->
                backEdgeOfProj.node.setPred(backEdgeOfProj.pos, pred0)
            }
        }

        Graph.killNode(store)
        BackEdges.disable(graph)

        deletedAnyStoreNode = true
    }

    private fun isDeadStore(store: Store): Boolean {
        // TODO überlegen if man nicht vielleicht  ALLE paths von return zu Start anlooken müsste... (Glaub eher net)
        // 1. find if there is a critical store path
        val criticalityCheck = anyStorePathIsCriticalFor(store)
        if (criticalityCheck.first) { // there exists a path where after the store comes no other similar store or a load.
            return false
        } else if (criticalityCheck.second.isEmpty()) {
            throw IllegalStateException("Uncritical paths must bear a witness.")
        }

        // 2. Walk backwards through the closure in every possible way and halt at the first load or store occurrence, ...
        return circleDataRiverClosures
            .filter { it.nodeSet.contains(store) }
            .none { circleDataRiverClosure -> checkCirclesForDirectLoads(store, circleDataRiverClosure, localVarStoreToAddressProjNodeMap[store]!!, localVarStoreToAddressProjOffsetMap[store]!!) }
    }

    /**
     * @return true if there is a load "directly before" the store (meaning there is NO dead store)
     */
    private fun checkCirclesForDirectLoads(node: Node, circleDataRiverClosure: DataRiverClosure, relevantProj: Proj, relevantProjOffset: Int): Boolean {
        // at the first occurrence of either a load, a call or a store, this recursion path's contribution to the total decision is made.
        if (node is Load && localVarLoadToAddressProjNodeMap[node] == relevantProj && localVarLoadToAddressProjOffsetMap[node] == relevantProjOffset) {
            return true
        } else if (node is Call) {
            return true
        } else if (node is Store && localVarStoreToAddressProjNodeMap[node] == relevantProj && localVarStoreToAddressProjOffsetMap[node] == relevantProjOffset) {
            return false
        }

        // recursion. A single load/ call found is needed.
        var foundALoadOrACall = false
        circleDataRiverClosure.getAllPossibleBackedges(node).forEach {
            if (checkCirclesForDirectLoads(it, circleDataRiverClosure, relevantProj, relevantProjOffset)) foundALoadOrACall = true
        }
        return foundALoadOrACall
    }

    /**
     * Test whether a path is critical for a given store node and maybe return a witness for uncritical
     */
    private fun anyStorePathIsCriticalFor(store: Store): Pair<Boolean, Set<Store>> {
        val witnesses = mutableSetOf<Store>()
        var critical = false
        val projNode = localVarStoreToAddressProjNodeMap[store]
        val projOffset = localVarStoreToAddressProjOffsetMap[store]
        storePaths
            .filter { storePath -> storePath.contains(store) } // we want the exact store node!
            .forEach { storePath ->
                val loadsAndCallsAndStoresAfterOurStore = storePath.list
                    .subList(0, storePath.indexOf(store)) /* There can be only one occurence of the given store node,
                        since there ain't no circles, here! Further, this specifically excludes the given store node!*/
                    .reversed() // walk backwards
                    .filter { node ->
                        node is Store && localVarStoreToAddressProjNodeMap[node] == projNode && localVarStoreToAddressProjOffsetMap[node] == projOffset ||
                            node is Load && localVarLoadToAddressProjNodeMap[node] == projNode && localVarLoadToAddressProjOffsetMap[node] == projOffset ||
                            node is Call
                    } /* Now we have only relevant loads/ calls and stores! If the first element is a load,
                        we got a CRITICAL path, else (Store) we have a potential dead store.*/
                    .toList()
                if (loadsAndCallsAndStoresAfterOurStore.isEmpty()) {
                    critical = true // no store found!
                } else if (loadsAndCallsAndStoresAfterOurStore.first() is Store) {
                    witnesses.add(loadsAndCallsAndStoresAfterOurStore.first() as Store) // uncritical with witness.
                } else {
                    critical = true // load or call found!
                }
            }

        return Pair(critical, witnesses) // uncritical, no
    }
}
