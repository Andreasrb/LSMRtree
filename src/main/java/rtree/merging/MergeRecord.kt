package rtree.merging

import rtree.base.DataObject
import rtree.base.MBR
import rtree.base.RTreeRecord

/**
 * Extended version of the RTreeRecord. Made with the possibility of making additions to the class without changing the
 * original
 */
class MergeRecord : RTreeRecord {
    constructor(mbr: MBR?, data: DataObject?) : super(mbr, data)
    constructor(mbr: MBR?, child: MergeNode) : super(mbr, child)
    var hasBeenReinserted = false;

    val child: MergeNode?
        get() = super.getChild() as? MergeNode

}