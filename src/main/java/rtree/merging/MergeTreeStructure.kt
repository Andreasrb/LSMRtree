package rtree.merging

import rtree.base.*
import rtree.utilities.ImportRealData
import kotlin.math.floor
import kotlin.system.measureTimeMillis
import kotlin.math.pow

class MergeTreeStructure(dimensions: Int, m: Int, M: Int) :
    RTreeStructure(dimensions, m, M) {
    var recordCount = 0
    private var nodesAccessed = 0
    var axisChosen = Pair(0, 0)

    override fun createNodeWithoutRecords(height: Int): MergeNode {
        return MergeNode(createNodeId(), height, ArrayList())
    }

    override fun createRecord(node: RTreeNode): RTreeRecord {
        return MergeRecord(node.mbr, node as MergeNode)
    }

    override fun createNodeFromExistingNode(insertionNode: RTreeNode?): RTreeNode {
        return insertionNode as MergeNode
    }

    /**
     * Implementation of creating new root node from RTreeStructure, used in quadratic split.
     */
    override fun createNewRootNode(nodes: ArrayList<RTreeNode>) {
        val newRoot = createNodeWithoutRecords(nodes[0].height + 1)
        for (node in nodes) {
            val record = MergeRecord(node.mbr, node as MergeNode)
            newRoot.addRecord(record)
        }
        newRoot.calculateAndUpdateMBR()
        this.root = newRoot
    }

    /**
     * Used in the case of multiple split, where the root node is split and a new root node is created.
     */
    private fun replaceRootNode(nodes: ArrayList<MergeNode>) {
        hasNewRoot = true
        val records = ArrayList<MergeRecord>()
        for (node in nodes) {
            records.add(createRecord(node) as MergeRecord)
        }
        val newRoot = MergeNode(createNodeId(), nodes[0].height + 1, records)
        newRoot.calculateAndUpdateMBR()
        this.root = newRoot
    }

    /**
     * Initializes new R-tree
     */
    fun createEmptyRoot() {
        this.root = MergeNode(createNodeId(), 0, ArrayList())
    }

    /**
     * Insert data into the R-tree
     */
    fun insertData(data: ArrayList<DataObject>) {
        for (d in data) {
            val low = floatArrayOf(d.lowX, d.lowY)
            val high = floatArrayOf(d.highX, d.highY)
            val record = MergeRecord(MBR(low, high), d)
            insert(record)
            this.recordCount += 1
        }
    }

    override fun getRoot(): MergeNode {
        return super.getRoot() as MergeNode
    }

    private var hasNewRoot: Boolean = false
    /**
     * Quadratic split equal to the one in RTreeStructure, amended to work with splitting multiple times at once.
     */
    private fun quadraticSplit(node: MergeNode): ArrayList<MergeNode> {
        incrementSplitCount()


        val node1: MergeNode = createNodeWithoutRecords(node.height)
        val node2: MergeNode = createNodeWithoutRecords(node.height)

        findAndAssignFirstRecords(node, node1, node2)

        while (node.recordCount > 0) {
            if (node1.recordCount + node.recordCount == this.getm()) {
                node1.records.addAll(node.records)
                node.clearRecords()
            } else if (node2.recordCount + node.recordCount == this.getm()) {
                node2.records.addAll(node.records)
                node.clearRecords()
            } else {
                findAndAssignNextRecord(node, node1, node2)
            }
        }

        node1.calculateAndUpdateMBR()
        node2.calculateAndUpdateMBR()

        val result = ArrayList<MergeNode>()

        if (node1.records.size > getM()) {
            result.addAll(quadraticSplit(node1))
        } else {
            result.add(node1)
        }
        if (node2.records.size > getM()) {
            result.addAll(quadraticSplit(node2))
        } else {
            result.add(node2)
        }

        return result
    }


    /**
     *
     * Sorts entries based on axises, currently limited to 2 axises, and finds which one to perform the
     * split on based on the goodness values area, margin and overlap.
     *
     *
     * Determines if the node should be split along the x or y axis, and then sorts the entries based on the axis.
     * We then find the best split between l and L-l entries, where l is the minimum number of entries in a node and L
     * is the maximum.
     *
     * l is given by l = (L * m) / (M + 1)
     */
    private fun chooseSplitAxis(node: MergeNode, parent: MergeNode?, prevSplitAxis: Int = 0): ArrayList<MergeNode> {
        incrementSplitCount()
        var bestSplit: Pair<Int, Int>? = null
        //var bestGoodness = Double.MAX_VALUE
        var bestGoodness: Triple<Double, Double, Double> = Triple(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)

        val entries = node.mergeRecords
        val m = getm()
        val M = getM()
        val l: Int = floor(((entries.size * m) / (M + 1)).toDouble()).toInt()

        val sortedEntriesX: List<MergeRecord>
        val sortedEntriesY: List<MergeRecord>
        if (prevSplitAxis == 0) {
            sortedEntriesX = entries.sortedBy { it.mbr.low.x }
            sortedEntriesY = entries.sortedBy { it.mbr.low.y }
        } else {
            sortedEntriesY = entries.sortedBy { it.mbr.low.y }
            sortedEntriesX = entries.sortedBy { it.mbr.low.x }
        }



        for (axis in 0..1) {
            val sortedEntries = if (axis == 0) sortedEntriesX else sortedEntriesY

            var leftMBR = boundingBox(ArrayList(sortedEntries.subList(0, l)))
            var rightMBR = boundingBox(ArrayList(sortedEntries.subList(l, sortedEntries.size)))

            for (k in (l until (entries.size - l))) {
                if (k > l) {
                    leftMBR = computeMBR(listOf(leftMBR, sortedEntries[k - 1].mbr))
                    val rightEntries = ArrayList(sortedEntries.subList(k + 1, sortedEntries.size))
                    rightMBR = boundingBox(rightEntries)
                }

                val areaValue = leftMBR.area + rightMBR.area
                val marginValue = (leftMBR.margin + rightMBR.margin).toDouble()
                val overlapValue = overlap(leftMBR, rightMBR).toDouble()
                //val goodness = areaValue - overlapValue.toDouble() - marginValue.toDouble()
                val goodness = Triple(overlapValue, areaValue, marginValue)

                /*if (goodness < bestGoodness) {
                    bestGoodness = goodness
                    bestSplit = Pair(axis, k)
                }*/
                if (goodness.first < bestGoodness.first ||
                    (goodness.first == bestGoodness.first && goodness.second < bestGoodness.second) ||
                    (goodness.first == bestGoodness.first && goodness.second == bestGoodness.second && goodness.third < bestGoodness.third)) {
                    bestGoodness = goodness
                    bestSplit = Pair(axis, k)
                }

            }
        }

        val axis = bestSplit!!.first
        axisChosen = if (axis == 0) {
            Pair(axisChosen.first + 1, axisChosen.second)
        } else {
            Pair(axisChosen.first, axisChosen.second + 1)
        }
        val k = bestSplit.second
        val finalSortedEntries = if (axis == 0) sortedEntriesX else sortedEntriesY
        val leftEntries = ArrayList(finalSortedEntries.subList(0, k))
        val rightEntries = ArrayList(finalSortedEntries.subList(k, finalSortedEntries.size))

        val node1: MergeNode = createNodeWithoutRecords(node.height)
        val node2: MergeNode = createNodeWithoutRecords(node.height)
        node1.records.addAll(leftEntries)
        node2.records.addAll(rightEntries)
        node1.calculateAndUpdateMBR()
        node2.calculateAndUpdateMBR()

        val nodesAfterSplit = ArrayList<MergeNode>()
        if (node1.records.size > M) {
            nodesAfterSplit.addAll(chooseSplitAxis(node1, parent, axis))
        } else {
            nodesAfterSplit.add(node1)
        }
        if (node2.records.size > M) {
            nodesAfterSplit.addAll(chooseSplitAxis(node2, parent, axis))
        } else {
            nodesAfterSplit.add(node2)
        }
        return nodesAfterSplit
    }

    /**
     * Splitting method for multiple split. If the node has more than M entries, it is split into two nodes.
     * The split axis is determined by the chooseSplitAxis method, giving us two nodes to split further if needed.
     * The chooseSplitAxis method is called recursively until all nodes have less than M entries. The resulting nodes
     * are then added to the parent nodes local insertion queue.
     */
    private fun multipleSplit(node: MergeNode, parent: MergeNode? = null) {
        val numEntries = node.records.size

        if (numEntries <= getM()) {
            val resultingNode = ArrayList<MergeNode>()
            resultingNode.add(node)
            return
        }
        val nodesAfterSplit = chooseSplitAxis(node, parent)

        if (parent == null) {
            replaceRootNode(nodesAfterSplit)
            return
        } else {
            for (newNode in nodesAfterSplit) {
                val newRecord = createRecord(newNode) as MergeRecord
                parent.localInsertionQueue.add(newRecord)
            }
            parent.records.removeIf { it.child?.id == node.id }
        }

    }

    /**
     * Finds and returns all leaf records at the bottom of a subtree
     */
    private fun findLeafRecords(node: MergeNode): ArrayList<MergeRecord> {
        val leafRecords = ArrayList<MergeRecord>()
        for (record in node.mergeRecords) {
            if (record.isLeaf) {
                leafRecords.add(record)
                record.hasBeenReinserted = true
            } else {
                leafRecords.addAll(findLeafRecords(record.child as MergeNode))
            }
        }
        return leafRecords
    }

    /**
     * Creates a bounding box based on a list of entries
     * */
    /*private fun boundingBox(entries: ArrayList<MergeRecord>): MBR {
        val lowX = entries.minOf { it.mbr.low.x }
        val lowY = entries.minOf { it.mbr.low.y }
        val highX = entries.maxOf { it.mbr.high.x }
        val highY = entries.maxOf { it.mbr.high.y }

        return MBR(floatArrayOf(lowX, lowY), floatArrayOf(highX, highY))
    }*/

    private fun boundingBox(entries: List<MergeRecord>): MBR {
        if (entries.isEmpty()) {
            throw IllegalArgumentException("Cannot compute bounding box of empty list")
        }

        var lowX = entries[0].mbr.low.x
        var lowY = entries[0].mbr.low.y
        var highX = entries[0].mbr.high.x
        var highY = entries[0].mbr.high.y

        for (i in 1 until entries.size) {
            val mbr = entries[i].mbr
            lowX = minOf(lowX, mbr.low.x)
            lowY = minOf(lowY, mbr.low.y)
            highX = maxOf(highX, mbr.high.x)
            highY = maxOf(highY, mbr.high.y)
        }

        return MBR(floatArrayOf(lowX, lowY), floatArrayOf(highX, highY))
    }



    /**
     * Calculates the overlap between two MBRs
     */
    private fun overlap(leftMBR: MBR, rightMBR: MBR): Float {
        val xOverlap = maxOf(0f, minOf(leftMBR.high.x, rightMBR.high.x) - maxOf(leftMBR.low.x, rightMBR.low.x))
        val yOverlap = maxOf(0f, minOf(leftMBR.high.y, rightMBR.high.y) - maxOf(leftMBR.low.y, rightMBR.low.y))

        return xOverlap * yOverlap
    }

    /**
     * Top level method of merging two R-trees. The records of the insert trees root node are
     * added to the insertion queue of the target tree. The records in the
     * insertion queue are then inserted into the tree.
     * If the root node of the target tree is split, a new root node is created.
     *
     * TODO: Sjekk om dette gir mening, kjører insertTrees nok ganger om jeg splitter root node mer enn en gang?
     */
    fun mergeTrees(insertTree: MergeTreeStructure, reinsertion: Boolean = false) {

        for (record in insertTree.root.mergeRecords) {
            this.root.insertionQueue.add(record)
        }
        insertTrees(this.root, null)

        if (hasNewRoot) {
            hasNewRoot = false
            insertTrees(root, null)
        }

        this.recordCount += insertTree.recordCount
        this.splitCount += insertTree.splitCount
    }

    /**
     * Recursive method that inserts records from the insertion queue into the tree.
     * Items in the insertion queue of a leaf node are inserted directly into the node.
     * For subtrees, we check whether it is better to insert the whole subtree into this node, or direct it
     * further down the tree
     *
     */
    private fun insertTrees(root: MergeNode, parent: MergeNode? = null) {

        if (root.height == 0) {
            while (!root.insertionQueue.isEmpty()) {
                val record = root.insertionQueue.poll()
                root.addRecord(record)
            }
            if (root.records.size > 0) {
                root.calculateAndUpdateMBR()
            }
        }

        if (root.height != 0) {
            while (!root.insertionQueue.isEmpty()) {
                val record = root.insertionQueue.poll()
                if (record.isLeaf) {
                    val child = findInsertionNode(root, record).first
                    child.insertionQueue.add(record)
                }
                if (record.child !== null) {
                    var mergeNode: MergeNode? = null
                    var overlapCriterion = false
                    if ((record.child!!.height + 1) < root.height) {
                            mergeNode = areaCriterion(root, record)?.child
                            mergeNode?.insertionQueue?.add(record)
                    } else if ((record.child!!.height + 1) == root.height) {
                            overlapCriterion = overlapCriterion(root, record)
                            if (overlapCriterion) {
                                root.localInsertionQueue.add(record)
                        }
                    }

                    if ((record.child!!.height + 1) > root.height || record.child!!.records.size < getm() || (mergeNode == null && !overlapCriterion)) {
                        for (entry in record.child?.mergeRecords!!) {
                            root.insertionQueue.add(entry)
                        }
                    }
                }
            }

            val recordsToProcess = root.records.filter { it.child is MergeNode }
            for (record in recordsToProcess) {
                val child = record.child as MergeNode
                if (child.insertionQueue.isNotEmpty()) {
                    insertTrees(child, root)
                }
            }
        }
        while (!root.localInsertionQueue.isEmpty()) {
            val record = root.localInsertionQueue.poll()
            root.addRecord(record)
        }

        if (root.records.size > 0) {
            root.calculateAndUpdateMBR()
        }
        multipleSplit(root, parent)
    }

    /**
     * Checks if the overlap enlargement of inserting a whole subtree into any child node of currentNode is smaller or
     * equal to inserting each individual entry of the subtree in a child node. If this is true, we can insert a whole
     * subtree into a node.
     */
    private fun overlapCriterion(currentNode: MergeNode, insertionEntry: MergeRecord): Boolean {
        var wholeSubtreeEnlargement = 0.0
        var singleEntryEnlargement = 0.0
        for (record in currentNode.mergeRecords) {
            val overlap = record.mbr.calculateOverlap(insertionEntry.mbr)
            if (overlap > 0) {
                wholeSubtreeEnlargement += overlap
            }
        }
        for (entry in insertionEntry.child?.mergeRecords!!) {
            singleEntryEnlargement += singleEntryOverlapEnlargement(currentNode, entry)
        }
        return wholeSubtreeEnlargement <= singleEntryEnlargement
    }

    /**
     * Calculates the minimum overlap enlargement of inserting a single entry into a child of currentNode.
     */
    private fun singleEntryOverlapEnlargement(currentNode: MergeNode, insertionEntry: MergeRecord): Double {
        var minimumAreaIncrease = Double.MAX_VALUE
        var minimumAreaIncreaseRecord: MergeRecord? = null
        for (record in currentNode.mergeRecords) {
            val potentialIncrease = record.mbr.getAreaEnlargement(insertionEntry.mbr)
            if (potentialIncrease < minimumAreaIncrease) {
                minimumAreaIncrease = potentialIncrease
                minimumAreaIncreaseRecord = record
            }
        }
        return if (minimumAreaIncrease == 0.0) {
            0.0
        } else {
            if (minimumAreaIncreaseRecord == null) {
                minimumAreaIncreaseRecord = currentNode.mergeRecords.first()
            }
            /*
            val mbr = boundingBox(arrayListOf(minimumAreaIncreaseRecord, insertionEntry))
            var overlapIncrease = 0.0
            for (record in currentNode.mergeRecords) {
                if (record.mbr.isOverlapping(mbr) && record.mbr != minimumAreaIncreaseRecord.mbr) {
                    overlapIncrease += record.mbr.calculateOverlap(mbr)
                }
            }*/
            minimumAreaIncreaseRecord.mbr.calculateOverlap(insertionEntry.mbr)
        }
    }

    /*private fun areaCriterion(currentNode: MergeNode, insertionEntry: MergeRecord): MergeRecord? {
        var wholeSubtreeEnlargement = Double.MAX_VALUE
        var selectedRecord: MergeRecord? = null

        val newMBRs = mutableMapOf<MergeRecord, MBR>()
        var singleEntryTotalAreaEnlargement = 0.0
        for (entry in insertionEntry.child?.mergeRecords!!) {
            var minEnlargement = Double.MAX_VALUE
            var selectedSingleRecord: MergeRecord? = null
            var hasCheckedWholeSubtree = false
            for (record in currentNode.mergeRecords) {
                if (!hasCheckedWholeSubtree) {
                    val areaEnlargement = record.mbr.getAreaEnlargement(insertionEntry.mbr)
                    if (areaEnlargement < wholeSubtreeEnlargement) {
                        wholeSubtreeEnlargement = areaEnlargement
                        selectedRecord = record
                        hasCheckedWholeSubtree = true
                    }
                }

                val enlargement = record.mbr.getAreaEnlargement(entry.mbr)
                if (enlargement < minEnlargement) {
                    minEnlargement = enlargement
                    selectedSingleRecord = record
                }
            }

            if (selectedSingleRecord != null) {
                val currentMBR = newMBRs[selectedSingleRecord] ?: selectedSingleRecord.mbr
                val updatedMBR = computeMBR(listOf(currentMBR, entry.mbr))
                newMBRs[selectedSingleRecord] = updatedMBR
            }
        }
        for ((record, newMBR) in newMBRs) {
            val originalArea = record.mbr.area
            val newArea = newMBR.area
            singleEntryTotalAreaEnlargement += newArea - originalArea
        }

        if (wholeSubtreeEnlargement <= singleEntryTotalAreaEnlargement) {
            return selectedRecord
        }
        return null
    }*/

    /**
     * Checks if the area enlargement of inserting a whole subtree into any child node of currentNode is smaller or
     * equal to th area enlargement of inserting each individual entry of the subtree in a child node. If this is true,
     * we can insert a whole subtree into a node.
     */
    private fun areaCriterion(currentNode: MergeNode, insertionEntry: MergeRecord): MergeRecord? {
        val childRecords = currentNode.mergeRecords
        val subtreeEntries = insertionEntry.child?.mergeRecords.orEmpty()

        // Step 1: Find best record for inserting the whole subtree
        val bestWholeSubtreeRecord = childRecords.minByOrNull {
            it.mbr.getAreaEnlargement(insertionEntry.mbr)
        }
        val wholeSubtreeEnlargement = bestWholeSubtreeRecord?.mbr?.getAreaEnlargement(insertionEntry.mbr) ?: Double.MAX_VALUE

        // Step 2: Simulate inserting individual entries
        val newMBRs = mutableMapOf<MergeRecord, MBR>()

        for (entry in subtreeEntries) {
            val bestRecord = childRecords.minByOrNull { it.mbr.getAreaEnlargement(entry.mbr) } ?: continue
            val currentMBR = newMBRs[bestRecord] ?: bestRecord.mbr
            val updatedMBR = computeMBR(listOf(currentMBR, entry.mbr))
            newMBRs[bestRecord] = updatedMBR
        }

        // Step 3: Calculate total area enlargement for individual insertions
        val singleEntryTotalAreaEnlargement = newMBRs.entries.sumOf { (record, newMBR) ->
            newMBR.area - record.mbr.area
        }

        // Step 4: Decide based on area criterion
        return if (wholeSubtreeEnlargement <= singleEntryTotalAreaEnlargement) {
            bestWholeSubtreeRecord
        } else {
            null
        }
    }

    private fun computeMBR(mbrs: List<MBR>): MBR {
        val lowX = mbrs.minOf { it.low.x }
        val lowY = mbrs.minOf { it.low.y }
        val highX = mbrs.maxOf { it.high.x }
        val highY = mbrs.maxOf { it.high.y }
        return MBR(floatArrayOf(lowX, lowY), floatArrayOf(highX, highY))
    }


    /**
     * Determines in which child of currentNode we want to place a leaf node based on area enlargement of the selected
     * child
     */
    private fun findInsertionNode(currentNode: MergeNode, insertionEntry: MergeRecord): Pair<MergeNode, Double> {
        var minEnlargement = Double.MAX_VALUE
        var selectedNode: MergeNode? = null

        for (record in currentNode.records) {
            if (!record.isLeaf) {
                val enlargement = record.mbr.getAreaEnlargement(insertionEntry.mbr)
                if (enlargement <= minEnlargement) {
                    minEnlargement = enlargement
                    selectedNode = record.child as MergeNode
                }
            }
        }
        if (selectedNode == null) {
            selectedNode = currentNode
            minEnlargement = Double.MAX_VALUE
        }
        return Pair(selectedNode, minEnlargement)
    }

    /**
     * Simple search function: Traverses all nodes in the tree that overlap with the search MBR and returns the leaf
     * nodes that match with the search MBR.
     */
    fun search(mbr: MBR): Pair<java.util.ArrayList<DataObject>, Int> {
        nodesAccessed = 0
        val results = java.util.ArrayList<DataObject>()
        searchRecursive(root, mbr, results, nodesAccessed)
        return Pair(results, nodesAccessed)
    }

    private fun searchRecursive(
        node: RTreeNode,
        mbr: MBR,
        results: java.util.ArrayList<DataObject>,
        nodesAccessed: Int
    ) {
        this.nodesAccessed += 1
        for (record in node.records) {
            if (record.mbr.isOverlapping(mbr)) {
                if (node.height == 0) {
                    results.add(record.data)
                } else {
                    searchRecursive(record.child, mbr, results, nodesAccessed)
                }
            }
        }
    }

    /**
     * Checks amount of leaf records. Used to see that tree hasn't lost any data during merge.
     *
     * TODO: Expand to see that all leaf nodes are on the same level
     */
    fun countLeafRecords(node: MergeNode): Int {
        var count = 0
        for (record in node.records) {
            if (record.isLeaf) {
                count++
            } else {
                count += countLeafRecords(record.child as MergeNode)
            }
        }
        return count
    }


    /**
     *
     */
    fun checkInvalidMBR(node: MergeNode): Boolean {
        if (node.mbr.low.x.toDouble() == -1.0 && node.mbr.low.y.toDouble() == -1.0 && node.mbr.high.x.toDouble() == -1.0 && node.mbr.high.y.toDouble() == -1.0) {
            return false
        }
        for (record in node.records) {
            if (record.mbr.low.x.toDouble() == -1.0 && record.mbr.low.y.toDouble() == -1.0 && record.mbr.high.x.toDouble() == -1.0 && record.mbr.high.y.toDouble() == -1.0) {
                return false
            }
            if (!record.isLeaf) {
                if (!checkInvalidMBR(record.child as MergeNode)) {
                    return false
                }
            }
        }
        return true
    }


    /**
     * Checks for duplicate nodes in the R-tree, uses collectNodes to collect all nodes in the R-tree
     */
    fun checkForDuplicateNodes(node: MergeNode): Boolean {
        val nodeList = mutableListOf<MergeNode>()
        collectNodes(node, nodeList)
        val nodeIds = nodeList.map { it.id }
        val duplicates = nodeIds.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        if (duplicates.isNotEmpty()) {
            println("Duplicate node IDs: $duplicates")
        }
        return nodeIds.size != nodeIds.distinct().size
    }

    /**
     * Collects all nodes from R-tree into a simple list
     */
    private fun collectNodes(node: MergeNode, nodeList: MutableList<MergeNode>) {
        nodeList.add(node)
        for (record in node.records) {
            if (!record.isLeaf) {
                collectNodes(record.child as MergeNode, nodeList)
            }
        }
    }

    /**
     * Checks the validity of the R-tree in terms of the tree adhering to having a minimum of m and maximum of M entries in each node except for the root.
     *
     * TODO: Should still check if root node has more than M records
     */
    fun checkValidity(node: MergeNode): Boolean {
        if ((node.records.size > getM() || node.records.size < getm()) && node.height != this.height) {
            return false
        } else if (node.height == this.height && node.records.size > getM()) {
            return false
        }
        for (record in node.records) {
            if (!record.isLeaf) {
                if (!checkValidity(record.child as MergeNode)) {
                    return false
                }
            }
        }
        return true
    }

    fun checkOverlapRatio(): Double {
        val overlaps = checkOverlap(root)
        return overlaps.average()
    }

    private fun checkOverlap(node: MergeNode): ArrayList<Double> {
        var totalOverlap = 0.0
        val overlaps = ArrayList<Double>()
        val records = node.records
        for (i in records) {
            for (j in records) {
                if (i != j) {
                    totalOverlap += overlap(i.mbr, j.mbr)
                }
            }
            if (!i.isLeaf) {
                overlaps.addAll(checkOverlap(i.child as MergeNode))
            }
        }
        val totalArea = records.sumOf { it.mbr.area }
        if (totalArea != 0.0 && !totalArea.isNaN()) {
            overlaps.add(totalOverlap / totalArea)
        }
        return overlaps
    }

    private fun checkCoverage(node: MergeNode): ArrayList<Double> {
        var totalCoverage = 0.0
        val coverages = ArrayList<Double>()
        val records = node.records
        val nodeArea = node.mbr.area

        for (i in records) {
            totalCoverage += i.mbr.area
            if (!i.isLeaf) {
                coverages.addAll(checkCoverage(i.child as MergeNode))
            }
        }
        val coverageRatio = totalCoverage / nodeArea
        coverages.add(coverageRatio)
        return coverages
    }

    fun checkCoverageRatio(): Double {
        val coverages: ArrayList<Double> = ArrayList();
        coverages.addAll(checkCoverage(root))
        return coverages.average()
    }

    fun checkFillFactor(): Double {
        val fillFactors = checkTotalFillFactor(root)
        return fillFactors.average()
    }

    private fun checkTotalFillFactor(node: MergeNode): ArrayList<Double> {
        val listOfFillFactors: ArrayList<Double> = arrayListOf()
        if (node.height == 0) {
            listOfFillFactors.add(node.records.size / getM().toDouble() * 100)
        }
        for (record in node.records) {
            if (!record.isLeaf) {
                listOfFillFactors.addAll(checkTotalFillFactor(record.child as MergeNode))
            }
        }
        return listOfFillFactors
    }
}

fun MergeTreeStructure.clone(): MergeTreeStructure {
    val newTree = MergeTreeStructure(this.dimensions, this.getm(), this.getM())
    newTree.root = this.root
    newTree.recordCount = this.recordCount
    return newTree
}