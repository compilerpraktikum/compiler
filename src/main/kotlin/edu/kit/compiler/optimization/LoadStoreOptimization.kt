package edu.kit.compiler.optimization

import firm.BackEdges
import firm.Graph
import firm.nodes.Bad
import firm.nodes.Load
import firm.nodes.Node
import firm.nodes.Phi
import firm.nodes.Proj
import firm.nodes.Return
import firm.nodes.Store
import java.lang.IllegalStateException
import java.util.Stack

/**
 * Collect all relevant store nodes
 */
class StoreAfterStoreNodeCollector(
    // TODO
    private val LocalVarStoreStack: Stack<Store>,
    private val localVarStoreToAddressProjNodeMap: HashMap<Store, Proj>,
    private val localVarLoadToAddressProjNodeMap: HashMap<Load, Proj>,
) : FirmNodeVisitorAdapter() {

    override fun visit(node: Store) {
        // TODO some kind of Erkennung arrayaccess/ local var access etc? Or on-the-fly Erkennung und hier nur store nodes sammeln.
        // TODO Grundblöcke? Worklist muss nach Kontrollfluss sortiert sein! Was leistet der topologische Iterator da?

        // -- case: this.vari = ..; --
        // 1 = "memory address"
        val possibleMemProj = node.getPred(1)
        if (possibleMemProj is Proj) {
            LocalVarStoreStack.push(node)
            localVarStoreToAddressProjNodeMap[node] = possibleMemProj
        }

        // -- case: this.arr[5] = ...; TODO need to traverse much nodes, see testForwardStore.mj
    }

    override fun visit(node: Load) {

        // -- case: this.vari = ..; --
        // 1 = "memory address"
        val possibleMemProj = node.getPred(1)
        if (possibleMemProj is Proj) {
            localVarLoadToAddressProjNodeMap[node] = possibleMemProj
        }
    }
}

fun doStoreAfterStoreOptimizationRIGHT(graph: Graph) {
    println("---------------------[ storeAfterStore($graph) ${" ".repeat(80 - graph.toString().length)}]---------------------")
    StoreAfterStore(graph).removeDeadStores()
}

class StoreAfterStore(val graph: Graph) {

    class Path<T>(val list: MutableList<T>, override val size: Int = 0) : MutableList<T>, Set<T> {
        val nodeHashSet: MutableSet<T> = HashSet(size)
        // TODO add a load-Proj-Map, populate it on each add operation!

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
    }

    val storePaths: MutableList<Path<Node>> = mutableListOf()
    val circlePaths: MutableList<Path<Node>> = mutableListOf()
    val circleDataRiverClosures: MutableList<DataRiverClosure> = mutableListOf()

    val localVarStoreStack = Stack<Store>()
    val localVarStoreToAddressProjNodeMap = HashMap<Store, Proj>()
    val localVarLoadToAddressProjNodeMap = HashMap<Load, Proj>()

    private fun calculateSpanningForest() = graph.end.block.preds.forEach { calculateSpanningTree(it as Return, Path(mutableListOf(it))) }

    /**
     * @param path includes param node!
     */
    private fun calculateSpanningTree(node: Node, path: Path<Node>) {
        if (node == graph.start) {
            return
        } else if (node is Bad) {
            println("GOT BAD NODE $node. Abort mission.")
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
        // TODO check if visitedSet is irrelevant!
//        return if (visitedSet.contains(nextNode)) {
        return if (oldPath.contains(nextNode)) {
            // ( ReturnNode -> ... NodeI -> ... -> NodeI) ==> (NodeI -> ... -> NodeI)
            circlePaths.add(circlify(oldPath.cloneAndAdd(nextNode)))
            true
        } else {
            false
        }
    }

    private fun circlify(pathFromStartToCircleEnd: Path<Node>): Path<Node> {
        return Path<Node>(
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

        // todo while (sich in der letzten Iteration noch was an den Mengen geändert hat)
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
        // TODO("implement")
    }

    fun removeDeadStores() {
        // Collect all stores and populate maps from load and store to their respective proj nodes (invariant for LOCAL VAR ACCESSES, ONLY!)
        graph.walk(StoreAfterStoreNodeCollector(localVarStoreStack, localVarStoreToAddressProjNodeMap, localVarLoadToAddressProjNodeMap))

        /* For each Return node, run a spanning tree algorithm (DFS) which yields
            * Each path from each return to each store node
            * all circles in the graph
         */
        calculateSpanningForest()

        println("--[ storePaths ]--")
        storePaths.forEach {
            println("  - $it")
        }
        println("--[ circlePaths ]--")
        circlePaths.forEach {
            println("  - $it")
        }

        /* Calculate DataRiverClosures™ (Def 1.2) which each contain all circles that are "reachability-connected".
           These Closures are disjunct!
         */
        calculateDataRiverClosures()
        println("--[ circleDataRiverClosures ]--")
        circleDataRiverClosures.forEach {
            println("  - $it")
            it.circles.forEach { itit -> println("    - $itit") }
        }

        /* Determine whether store nodes are dead stores (Def 0.0) and delete them. */
        print("--[ dead stores ")
        localVarStoreToAddressProjNodeMap.keys.forEach { print(", $it") }
        println(" ]--")
        localVarStoreStack.forEach { if (isDeadStore(it)) println("  - $it") }

        // TODO implement deleting
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

        // 2. interrogate the witnesses
//        println("  - dirty witnesses for $store")
        criticalityCheck.second.forEach { witness ->
            val witnessProj = localVarStoreToAddressProjNodeMap[witness]
            circleDataRiverClosures
                .filter { it.nodeSet.contains(witness) }
                .forEach { circleDataRiverClosure ->
                    if (circleDataRiverClosure.nodeSet
                        .filterIsInstance<Load>()
                        .any { load -> localVarLoadToAddressProjNodeMap[load] == witnessProj }
                    ) {
                        // we got a hidden load within a circle, so we don't have a dead store
//                        println("    - $witness")
                        return@isDeadStore false
                    }
                }
        }
        // TODO rmv DEBUG
//        println("  - clean witnesses for $store")
//        criticalityCheck.second.forEach { println("    - $it") }

        return true
    }

    /**
     * Test whether a path is critical for a given store node and maybe return a witness for uncritical
     */
    private fun anyStorePathIsCriticalFor(store: Store): Pair<Boolean, Set<Store>> {
        val witnesses = mutableSetOf<Store>()
        var critical = false
        val projNode = localVarStoreToAddressProjNodeMap[store]
        storePaths
            .filter { storePath -> storePath.contains(store) } // we want the exact store node!
            .forEach { storePath ->
                val loadsAndStoresAfterOurStore = storePath.list
                    .subList(0, storePath.indexOf(store)) /* There can be only one occurence of the given store node,
                        since there ain't no circles, here! Further, this specifically excludes the given store node!*/
                    .reversed() // walk backwards
                    .filter { node ->
                        node is Store && localVarStoreToAddressProjNodeMap[node] == projNode || node is Load && localVarLoadToAddressProjNodeMap[node] == projNode
                    } /* Now we have only relevant loads and stores! If the first element is a load,
                        we got a CRITICAL path, else (Store) we have a potential dead store.*/
                    .toList()
                if (loadsAndStoresAfterOurStore.isEmpty()) {
                    println("------------------------------------------------------DEBuG $store: no store found")
                    critical = true // no store found!
                } else if (loadsAndStoresAfterOurStore.first() is Store) {
                    witnesses.add(loadsAndStoresAfterOurStore.first() as Store) // uncritical with witness.
                } else {
                    println("------------------------------------------------------DEBuG $store: load found")
                    critical = true // load found!
                }
            }

        return Pair(critical, witnesses) // uncritical, no
    }
}

/**
 * Perform store after store optimization on the given [method graph][Graph].
 */
fun doStoreAfterStoreOptimization(graph: Graph) {
    val LocalVarStoreStack: Stack<Store> = Stack()
    val localVarStoreToAddressProjNodeMap = HashMap<Store, Proj>()
    val localVarLoadToAddressProjNodeMap = HashMap<Load, Proj>()

    // firm meckert sonst.
    BackEdges.enable(graph)

    println("---------------------[ storeAfterStore($graph) ${" ".repeat(80 - graph.toString().length)}]---------------------")
    graph.walkTopological(StoreAfterStoreNodeCollector(LocalVarStoreStack, localVarStoreToAddressProjNodeMap, localVarLoadToAddressProjNodeMap))

    println("--[ LocalVarStoreStack ]--")
    LocalVarStoreStack.forEach {
        println("  - $it ${" ".repeat(35 - it.toString().length)}")
    }
    println("--[ localVarStoreToAddressProjNodeMap ]--")
    localVarStoreToAddressProjNodeMap.forEach {
        println("  - $it ${" ".repeat(35 - it.toString().length)}")
    }
    println("--[ localVarLoadToAddressProjNodeMap ]--")
    localVarLoadToAddressProjNodeMap.forEach {
        println("  - $it ${" ".repeat(35 - it.toString().length)}")
    }

    // walk along memory edges from end to start
    // store nodes of type store and whether they should be deleted at the end.
    val nodesToBeDeleted = HashSet<Store>().toMutableSet()
    val projsVisited = HashMap<Proj, Store>()

    var currentNode: Node = graph.end.block
    println("--[[ Backwardswalk on $currentNode ]]-- ")
    while (currentNode != graph.start) {
        println("  $currentNode")

        if (currentNode is Load) {
            val loadProj = localVarLoadToAddressProjNodeMap[currentNode]
            projsVisited.remove(loadProj)
        }

        // contains check: e.g. array store nodes do have Conv nodes as predecessors. Only looking at basic var stores, here.
        if (currentNode is Store && localVarStoreToAddressProjNodeMap.contains(currentNode)) {
            val storeProj = localVarStoreToAddressProjNodeMap[currentNode]
//            println("    - $storeProj")
//            println("    - projsVisited:")
//            projsVisited.forEach {
//                println("      - $it ${it.key == storeProj}")
//            }

            if (projsVisited.contains(storeProj)) {
                println("    - contains!")
                localVarStoreToAddressProjNodeMap[currentNode]
                // between the current node (store) and the next store node exists no load node. ==> current node is dead store.
                nodesToBeDeleted += currentNode
            }

            projsVisited[localVarStoreToAddressProjNodeMap[currentNode]!!] = currentNode
        }

        // todo currently ignoring phi control flow by always taking 0 (which leads to start, presumably?)
        // TODO problem with that: need to find possible Loads in Phi control flow

        // TODO bei jedem Store -after- Store: Einzigartigkeit des Pfades prüfen.
        // Algorithmus:
        /*
            1. Berechne für jedes potenzielle DeadStore s0 alle Rückwärts-Kreise (s0 -> ... -> s0) per Tiefensuche.
               Rekursives Abbruchkriterium ==> Zweifaches Auftreten von s0 im Gesamtpfad.
               todo einfach nen Algorithmus für Kreisdetektion googeln..
         */
        if (currentNode is Phi) {
            currentNode = currentNode.getPred(0)
        } else {
            currentNode = currentNode.getPred(0)
        }
    }

    println("--[[ nodesToBeDeleted ]]-- ")
    nodesToBeDeleted.forEach {
        println("  - $it")
    }

    nodesToBeDeleted.forEach {
        // rewire nodes
        val pred0 = it.getPred(0)

        // pred0(Proj) <-- it(Store) <--[backEdge]-- node(Proj) <--[backEdgeOfProj]-- node(?)
        // ===after_deletion===>
        // pred0(Proj) <-- node(?)
        BackEdges.getOuts(it).forEach { backEdge ->
            BackEdges.getOuts(backEdge.node).forEach { backEdgeOfProj ->
                backEdgeOfProj.node.setPred(backEdgeOfProj.pos, pred0)
            }
        }

        // not sure if removeAll is necessary TODO test!
//        it.preds.removeAll { true }
        Graph.killNode(it)
    }

//    ConstantPropagationAndFoldingTransformationVisitor(
//        graph,
//        ConstantPropagationAndFoldingAnalysisVisitor(graph)
//            .doConstantPropagationAndFoldingAnalysis()
//    ).transform()

    BackEdges.disable(graph)
}
