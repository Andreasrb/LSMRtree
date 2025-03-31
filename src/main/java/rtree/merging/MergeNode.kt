package rtree.merging

import rtree.base.RTreeNode
import rtree.base.RTreeRecord
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.ArrayList

/**
 * A node in the merge tree structure
 * Extends RTreeNode with the purpose of adding queues used while merging two R-trees.
 */
class MergeNode(id: Int, height: Int, records: ArrayList<MergeRecord>) : RTreeNode(id, height, records as ArrayList<RTreeRecord>) {
    val insertionQueue: Queue<MergeRecord> = ConcurrentLinkedQueue()
    val localInsertionQueue: Queue<MergeRecord> = ConcurrentLinkedQueue()

    val mergeRecords: ArrayList<MergeRecord>
        get() = records as ArrayList<MergeRecord>

}