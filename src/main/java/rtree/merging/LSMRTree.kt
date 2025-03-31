package rtree.merging

import kotlinx.coroutines.*
import rtree.base.DataObject
import rtree.base.MBR
import kotlin.math.pow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.system.measureTimeMillis

class LSMRTree(
    private val T: Int,
    private val minRecords: Int,
    private val maxRecords: Int,
    private val memTableSize: Int
) {
    private var memTable: MergeTreeStructure = MergeTreeStructure(2, minRecords, maxRecords)
    var ssTables: ArrayList<MergeTreeStructure> = ArrayList()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val levelMergeLocks: MutableMap<Int, Mutex> = mutableMapOf()  // Per-level locks
    private val mergeJobs: MutableMap<Int, Job> = mutableMapOf()
    private val memTableJobs: MutableList<Job> = mutableListOf()

    init {
        memTable.createEmptyRoot()
    }

    var timeSpentMerging = 0L

    /**
     * Insert a data object into the MemTable of the LSM-tree
     *
     * If the MemTable is full, a merge operation is started
     */
    suspend fun insert(data: DataObject) {
        val low = floatArrayOf(data.lowX, data.lowY)
        val high = floatArrayOf(data.highX, data.highY)
        val record = MergeRecord(MBR(low, high), data)

        withContext(Dispatchers.Default) {
            memTable.insert(record)
            memTable.recordCount += 1
        }

        if (memTable.recordCount >= memTableSize) {
            merge()
        }

    }

    /**
     * Suspended function, running asynchronously, meaning merging the MemTable to the first level in the SSTable
     * is not done in real time, but is added to a queue of memtables waiting to be merged that runs asynchronously.
     * This is done to avoid blocking, in case there is a lock on the first level of the SSTable.
     *
     * After the merge is done, we also check the size of the first level of the SSTable to see if it needs to be merged further.
     * In this case, the handleOverFlow function is called.
     */
    private suspend fun merge() {

        val memTableCopy = memTable.clone()
        memTable = MergeTreeStructure(2, minRecords, maxRecords)
        memTable.createEmptyRoot()


        // Forslag - Ikke gjør dette i en annen coroutine
        val memTableJob = coroutineScope.launch {
            if (ssTables.isEmpty()) {
                ssTables.add(memTableCopy)
            } else if (ssTables[0].recordCount == 0) {
                ssTables[0] = memTableCopy
            } else {
                if (memTableCopy.root.mbr.lowX == 0) {
                    print("Error")
                }
                levelMergeLocks.getOrPut(0) { Mutex() }.withLock {
                    val time = measureTimeMillis {
                        ssTables[0].mergeTrees(memTableCopy)
                    }
                    timeSpentMerging += time
                }

            }
            if (ssTables.size > 0 && ssTables[0].recordCount >= T * memTableSize) {
                handleOverFlow(0)
            }
        }

        memTableJob.join()
        memTableJobs.add(memTableJob)
        memTableJobs.removeAll { it.isCompleted }
    }

    /**
     * Handles merges for a specific level in the SSTable, 0 meaning that the first SSTable is being merged into the second.
     * The Merge operation is done in a coroutine, performed in the performMerge function.
     */
    private suspend fun handleOverFlow(level: Int) {
        mergeJobs[level]?.takeIf { it.isActive }?.join()

        mergeJobs[level] = coroutineScope.launch {
            performMerge(level)
        }
    }

    /**
     * makes a lock on the insert level and the target level, and performs a merge between the two R-trees. If the
     * resulting R-tree larger than the threshold, the handleOverFlow function is called for the next level.
     */
    private suspend fun performMerge(level: Int) {
        val lock = levelMergeLocks.getOrPut(level) { Mutex() }
        val nextLock: Mutex = levelMergeLocks.getOrPut(level + 1) { Mutex() }

        var ssTableCopy: MergeTreeStructure
        lock.withLock {
            ssTableCopy = ssTables[level].clone()
            ssTables[level] = MergeTreeStructure(2, minRecords, maxRecords)
            ssTables[level].createEmptyRoot()
        }
        if (ssTables.size > level + 1) {
            if (ssTables[level + 1].recordCount == 0) {
                ssTables[level + 1] = ssTableCopy
            } else {
                nextLock.withLock {
                    val time = measureTimeMillis {
                        ssTables[level + 1].mergeTrees(ssTableCopy)
                    }
                    timeSpentMerging += time
                }
            }
        } else {
            ssTables.add(ssTableCopy)
        }


        if (ssTables.size > level + 1 && ssTables[level + 1].recordCount >= T.toDouble()
                .pow(level + 2) * memTableSize
        ) {
            handleOverFlow(level + 1)
        }
    }

    suspend fun waitForMerges() {
        mergeJobs.values.forEach { it.join() }
    }

    suspend fun waitForAllMerges() {
        coroutineScope {
            val allJobs = mergeJobs.values + memTableJobs  // Combine both lists
            allJobs.map { async { it.join() } }.awaitAll()  // Wait for all to finish in parallel
        }
    }

    suspend fun waitForMemTableMerges() {
        memTableJobs.removeAll { it.isCompleted }  // Clean up completed jobs
        memTableJobs.forEach { it.join() }         // Ensure all pending jobs finish
    }

    suspend fun waitForMergeJobs() {
        mergeJobs.values.removeAll { it.isCompleted }  // Clean up completed jobs
        mergeJobs.values.forEach { it.join() }         // Ensure all pending jobs finish
    }


    /**
     * Search for all data objects in the LSM-tree that intersect with the given MBR.
     * The search is done in the MemTable and all SSTables.
     */
    fun search(mbr: MBR): Pair<List<DataObject>, Int> {
        val searchResults = mutableListOf<DataObject>()
        var nodesSearched = 0

        for (ssTable in ssTables) {
                val ssTableRes = ssTable.search(mbr)
                nodesSearched += ssTableRes.second
                searchResults.addAll(ssTableRes.first)
        }
        val memTableRes = memTable.search(mbr)
        nodesSearched += memTableRes.second
        searchResults.addAll(memTableRes.first)

        return Pair(searchResults, nodesSearched)
    }

    fun getRecordCount(): Int {
        return memTable.recordCount + ssTables.sumOf { it.recordCount }
    }
}