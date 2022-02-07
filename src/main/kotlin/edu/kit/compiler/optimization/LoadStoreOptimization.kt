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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.util.NoSuchElementException
import java.util.Stack
import java.util.Vector
import kotlin.IllegalStateException
import kotlin.system.measureTimeMillis

val StoreAfterStoreOptimization = Optimization("store after store elimination", ::applyStoreAfterStoreOptimization)
const val StoreAfterStoreOptimizationTimeoutMilliseconds = 4000L /* 4 seconds */
// const val StoreAfterStoreOptimizationTimeoutMilliseconds = 1000L /* 1 seconds */
const val StoreAfterStoreOptimizationMaxMPhisInGraph = 14 /* number of phis for the optimization to handle in reasonable time (">14" ==(ca.)==> ">4000ms") */
val StoreAfterStoreOptimizationHasTimedOutBefore = mutableMapOf<Graph, Boolean>()

fun applyStoreAfterStoreOptimization(graph: Graph): Boolean {
    StoreAfterStoreOptimizationHasTimedOutBefore.putIfAbsent(graph, false)
    return if (!StoreAfterStoreOptimizationHasTimedOutBefore[graph]!! &&
        PhiCounter(graph).walk().phiCount <= StoreAfterStoreOptimizationMaxMPhisInGraph
    ) {
        val hasChangedHasTimedOutPair = StoreAfterStore(graph).removeDeadStores(StoreAfterStoreOptimizationTimeoutMilliseconds)
        StoreAfterStoreOptimizationHasTimedOutBefore[graph] = hasChangedHasTimedOutPair.second
        hasChangedHasTimedOutPair.first
    } else false
}

class PhiCounter(val graph: Graph, val mode: Mode = Mode.getM()) : FirmNodeVisitorAdapter() {
    var phiCount: Int = 0

    override fun visit(node: Phi) {
        if (node.mode == mode) phiCount++
    }

    fun walk(): PhiCounter {
        graph.walk(this)
        return this
    }
}

/**
 * Collect all relevant store nodes.
 */
class StoreAfterStoreNodeCollector(
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
 * you MUST call initialize for this to work properly!
 */
class StoreAfterStore(val graph: Graph) {

    class PathTree(val rootFirmNode: Return) {
        lateinit var root: PathTreeNode
        val constructionVisited: MutableSet<Node> = HashSet()
        val constructionCurrentPath: Path<Node> = Path(mutableListOf())
        val circles: MutableList<Path<Node>> = mutableListOf()

        suspend fun initialize() {
            this.root = PathTreeNode(rootFirmNode, this, null)
            this.root.initialize()
        }

        private fun addCircleFromCurrentPath(firmNode: Node): Boolean {
            return if (constructionCurrentPath.contains(firmNode)) {
                circles.add(constructionCurrentPath.newCircleIncluding(firmNode))
                true
            } else false
        }

        class PathTreeNode(val node: Node, val pathTree: PathTree, val parent: PathTreeNode? = null) {
            val children: MutableList<PathTreeNode> = ArrayList(if (node is Phi) node.predCount else 1)

            suspend fun initialize() {
                // visit the node first because of construction recursion!
                if (node != node.graph.start) {
                    pathTree.constructionVisited.add(this.node)
                    pathTree.constructionCurrentPath.add(this.node)
                }
                yield()

                if (node == node.graph.start) {
                    // "return"
                } else if (node is Bad) {
                    error("GOT BAD NODE $node. Abort mission.")
                    // "return"
                } else if (node is Phi) {
                    node.preds.forEach { firmChild ->
                        if (!pathTree.addCircleFromCurrentPath(firmChild)) {
                            val newPathTreeNode = PathTreeNode(firmChild, pathTree, this)
                            yield()
                            newPathTreeNode.initialize()
                            yield()
                            children.add(newPathTreeNode)
                        }
                    }
                } else {
                    val nextNode = node.getPred(0)
                    if (!pathTree.addCircleFromCurrentPath(nextNode)) {
                        val newPathTreeNode = PathTreeNode(nextNode, pathTree, this)
                        yield()
                        newPathTreeNode.initialize()
                        yield()
                        children.add(newPathTreeNode)
                    }
                }

                if (node != node.graph.start) {
                    yield()
                    pathTree.constructionCurrentPath.removeLast()
                    yield()
                }
                yield()
            }

            fun childCount(): Int {
                return node.predCount
            }
        }

        /**
         * This iterator walks through all
         */
        class PathTreeIterator(val pathTree: PathTree, val desiredDestinations: Set<Node>) : Iterator<Iterator<Node>> {
            var currentPathTreeNode = pathTree.root
            var currentPathTreeNodeIterator = PathTreeNodeIterator(this, currentPathTreeNode.node)
            var nextWasAlreadyCalled = true
            var nextPathTreeNodeIterator: PathTreeNodeIterator? = currentPathTreeNodeIterator

            val iteratedDestinations: MutableSet<Node> = HashSet()

            // nodess
            val visited: MutableSet<PathTreeNode> = HashSet()

            override fun hasNext(): Boolean {
                setNextIfNotAlreadyDone()
                return !visited.contains(pathTree.root) && !iteratedDestinations.containsAll(desiredDestinations)
                // the next Iterator
            }

            private fun setNextIfNotAlreadyDone() {
                if (! nextWasAlreadyCalled) {
                    if (currentPathTreeNodeIterator.hasNext()) throw IllegalStateException("Called hasNext while underlying iterator still has elements!")
                    // we know that the underlying iterator is done.
                    nextPathTreeNodeIterator = PathTreeNodeIterator(this, pathTree.root.node)
                }
                nextWasAlreadyCalled = true
            }

            override fun next(): Iterator<Node> {
                // reset currentPathTreeNode
                this.currentPathTreeNode = pathTree.root
                setNextIfNotAlreadyDone()
                currentPathTreeNodeIterator = nextPathTreeNodeIterator ?: throw NoSuchElementException("Iterator has no next element!")
                nextWasAlreadyCalled = false
                return currentPathTreeNodeIterator
            }

            fun dfsNext(): Node? {
                val maybeNextNode = currentPathTreeNode.children.indexOfFirst { !visited.contains(it) }
                return if (maybeNextNode != -1) {
                    // it's a child, visit it
                    visitNode(currentPathTreeNode.children[maybeNextNode])
                    currentPathTreeNode = currentPathTreeNode.children[maybeNextNode]
                    currentPathTreeNode.node
                } else {
                    // TODO the old stuff succz, do sth else:  go back to the root and be done with it.

                    // all children must have been visited, go back to parent and mark this node as visited
                    visitNode(currentPathTreeNode)

                    /* if no children, recursion will give the next that has children until there is none with a parent,
                       which is when nextFirmNode will be null. */
                    while (currentPathTreeNode.parent != null) {
                        currentPathTreeNode = currentPathTreeNode.parent!!
                        visitNode(currentPathTreeNode)
//                        nextFirmNode = dfsNext()
                    }
                    null
                }
            }

            /**
             * Tree nodes are marked as visited, if all their children were visited.
             * A leaf is visited once this method is called on it.
             */
            private fun visitNode(pathTreeNode: PathTreeNode): PathTreeNode {
                if (pathTreeNode.children.all { visited.contains(it) }) {
                    visited.add(pathTreeNode)
                }
                return pathTreeNode
            }
        }

        /**
         * Iterates over paths until not-already-visited T occurs.
         */
        class PathTreeNodeIterator(val pathTreeIterator: PathTreeIterator, rootNode: Node) : Iterator<Node> {
            var currentNode = rootNode
            var nextNode: Node? = currentNode
            var nextWasAlreadyCalled = true

            var overAndOut = false

//            init {
//                if (pathTreeIterator.visited.contains(pathTreeIterator.currentPathTreeNode)) {
//                    nextNode = null
//                    nextWasAlreadyCalled = false
//                }
//            }

            override fun hasNext(): Boolean {
                if (overAndOut) return false
                setNextIfNotAlreadyDone()
                return nextNode != null
            }

            override fun next(): Node {
                if (!hasNext()) throw NoSuchElementException("Iterator has no next element!")

                if (nextNode != null &&
                    pathTreeIterator.desiredDestinations.contains(nextNode) &&
                    !pathTreeIterator.iteratedDestinations.contains(nextNode)
                ) {
                    // we found the next path to a T typed node.
                    overAndOut = true
                    pathTreeIterator.iteratedDestinations.add(nextNode!!)
                }

                currentNode = nextNode ?: throw NoSuchElementException("Iterator has no next element!")
                nextWasAlreadyCalled = false
                return currentNode
            }

            private fun setNextIfNotAlreadyDone() {
                if (! nextWasAlreadyCalled) {
                    nextNode = pathTreeIterator.dfsNext()
                }
                nextWasAlreadyCalled = true
            }
        }

        fun getIterableFor(nodeSet: Set<Node>): Iterable<Iterator<Node>> {
            return Iterable { PathTreeIterator(this, nodeSet) }
        }
    }

// todo if langeweile: die interfaces sind relativ sicher bullshit..
    class Path<T>(var list: MutableList<T>, override val size: Int = 0) : MutableList<T>, Set<T> {
        val nodeHashSet: MutableSet<T> = HashSet(size)

        init {
            nodeHashSet.addAll(list)
        }

        override fun iterator() = list.iterator()
        override fun contains(element: T) = nodeHashSet.contains(element)
        override fun containsAll(elements: Collection<T>) = nodeHashSet.containsAll(elements)
        override fun isEmpty() = nodeHashSet.isEmpty()
        override fun get(index: Int): T = list[index]

        override fun indexOf(element: T) = list.indexOf(element)
        override fun lastIndexOf(element: T) = list.lastIndexOf(element)
        override fun listIterator() = list.listIterator()
        override fun listIterator(index: Int) = list.listIterator(index)
        override fun spliterator() = list.spliterator()

        override fun subList(fromIndex: Int, toIndex: Int) = list.subList(fromIndex, toIndex)

        fun cloneAndAdd(element: T): Path<T> {
            val newPath = Path(Vector(list))
            newPath.add(element)
            return newPath
        }

        fun newCircleIncluding(element: T): Path<T> {
            val newPath = Path(
                Vector(
                    list.subList(
                        list.indexOfFirst { it == element },
                        list.size
                    )
                )
            )
            newPath.add(element)
            return newPath
        }

        override fun toString(): String {
            return "Path[" + list.fold("") { acc, t ->
                "$acc${ if (acc.isEmpty()) "$t" else " -> $t" }"
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

        fun removeLast(): T {
            val element = list.removeLast()
            if (!list.contains(element)) nodeHashSet.remove(element)
            return element
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

//    val storePaths: MutableList<Path<Node>> = mutableListOf()
    var circlePaths: MutableList<Path<Node>> = mutableListOf()
    val circleDataRiverClosures: MutableList<DataRiverClosure> = mutableListOf()

    val storePathIterables: MutableList<Iterable<Iterator<Node>>> = mutableListOf()

    val localVarStoreStack = Stack<Store>()
    val localVarStoreToAddressProjNodeMap = HashMap<Store, Proj>()
    val localVarLoadToAddressProjNodeMap = HashMap<Load, Proj>()
    val localVarStoreToAddressProjOffsetMap = HashMap<Store, Int>()
    val localVarLoadToAddressProjOffsetMap = HashMap<Load, Int>()

    var deletedAnyStoreNode = false

    private suspend fun calculateSpanningForest() = graph.end.block.preds.forEach { returnNode ->
        val pathTree = PathTree(returnNode as Return)
        pathTree.initialize()
        yield()
        // 1. set circlepaths
        circlePaths = pathTree.circles
        // 2. set storepaths
        storePathIterables.add(pathTree.getIterableFor(localVarStoreStack.toSet()))
        yield()
    }

    private suspend fun calculateDataRiverClosures() {
        // 1. Fit every circlePath in a closure
        circlePaths.forEach { circle ->

            // 1.1 if any existing closure fits, add it to that one
            var foundFittingClosure = false
            circleDataRiverClosures.forEach { dataRiverClosure ->
                if (dataRiverClosure.overlaps(circle)) {
                    yield()
                    dataRiverClosure.addToClosure(circle)
                    foundFittingClosure = true
                }
                yield()
            }

            // 1.2 else create own closure
            if (!foundFittingClosure) {
                circleDataRiverClosures.add(DataRiverClosure(circle))
            }
            yield()
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

    private fun debug(string: String) {
    }

    /**
     * @return "Pair(hasChangedAnything, hasExceededTimeout)"
     */
    fun removeDeadStores(timeout: Long): Pair<Boolean, Boolean> {
        /* 1. Collect all stores and populate maps from load and store to their respective proj nodes (invariant for LOCAL VAR ACCESSES, ONLY!)*/
        graph.walk(
            StoreAfterStoreNodeCollector(
                localVarStoreStack,
                localVarStoreToAddressProjNodeMap,
                localVarStoreToAddressProjOffsetMap,
                localVarLoadToAddressProjNodeMap,
                localVarLoadToAddressProjOffsetMap
            )
        )

        if (runBlocking<Boolean?> {
            withTimeoutOrNull<Boolean?>(timeout) {

                    /* 2. For each Return node, run a spanning tree algorithm (DFS) which yields
                        * Each path from each return to each store node
                        * all circles in the graph
                     */
                val elapsed = measureTimeMillis {
                    calculateSpanningForest()
                }
                debug("  - spanning forest calc took $elapsed ms.")

                    /* 3. Calculate DataRiverClosures™ (Def 1.2) which each contain all circles that are "reachability-connected".
                          These Closures are disjunct!
                     */
                calculateDataRiverClosures()
                false
            }
        } == null
        ) {
            return Pair(false, true)
        }

        /* 4. Determine whether store nodes are dead stores (Def 0.0) and delete them. */
        localVarStoreToAddressProjNodeMap.keys
            .filter { isDeadStore(it) }
            .forEach { deleteStoreNode(it) }

        debug("before return")
        return Pair(deletedAnyStoreNode, false)
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
        print("$graph: In isDeadStore for $store: ")
        // 1. find if there is a critical store path
        val criticalityCheck = anyStorePathIsCriticalFor(store)
        if (criticalityCheck.first) { // there exists a path where after the store comes no other similar store or a load.
            println("criticalityCheck FAILED")
            return false
        } else if (criticalityCheck.second.isEmpty()) {
            throw IllegalStateException("Uncritical paths must bear a witness.")
        }
        println("criticalityCheck SUCCEEDED")

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
        debug("In anyStorePathIsCriticalFor for $store")
        val witnesses = mutableSetOf<Store>()
        val projNode = localVarStoreToAddressProjNodeMap[store]
        val projOffset = localVarStoreToAddressProjOffsetMap[store]

        storePathIterables.forEach { treeIterable ->
            var lastLoadOrCallOrStoreBeforeOurStore: Node? = null
            debug("  - TREE_ITERABLE")
            treeIterable.forEach { storePathIterator ->
                debug("    - storePathIterator")
                print("(PATH[")
                var storeFound = false // also used as a soft break because the iterator implementation is unbreakable.
                for (node in storePathIterator) {
                    print("$node->")
                    if (!storeFound) {
                        // loop until reaching store
                        if (node == store) {
                            storeFound = true
                        } else if (node is Store && localVarStoreToAddressProjNodeMap[node] == projNode && localVarStoreToAddressProjOffsetMap[node] == projOffset ||
                            node is Load && localVarLoadToAddressProjNodeMap[node] == projNode && localVarLoadToAddressProjOffsetMap[node] == projOffset ||
                            node is Call
                        ) {
                            lastLoadOrCallOrStoreBeforeOurStore = node
                        }
                    }
                }
                print("], sf=$storeFound, ")
                if (storeFound) {
                    print("($lastLoadOrCallOrStoreBeforeOurStore")
                    when (lastLoadOrCallOrStoreBeforeOurStore) {

                        null -> return Pair(true, witnesses) // no store found
                        is Store -> {
                            witnesses.add(lastLoadOrCallOrStoreBeforeOurStore as Store) // uncritical with witness.
                        }
                        else -> return Pair(true, witnesses) // Load or Call found
                    }
                }
                print(") ")
            }
        }
        return Pair(false, witnesses) // uncritical, no
    }
}
