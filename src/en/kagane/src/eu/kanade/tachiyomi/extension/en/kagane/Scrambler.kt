package eu.kanade.tachiyomi.extension.en.kagane

import java.math.BigInteger

class Scrambler(private val seed: BigInteger, private val gridSize: Int) {

    private val totalPieces: Int = gridSize * gridSize
    private val randomizer: Randomizer = Randomizer(seed, gridSize)
    private val dependencyGraph: DependencyGraph
    private val scramblePath: List<Int>

    init {
        dependencyGraph = buildDependencyGraph()
        scramblePath = generateScramblePath()
    }

    private data class DependencyGraph(
        val graph: MutableMap<Int, MutableList<Int>>,
        val inDegree: MutableMap<Int, Int>,
    )

    private fun buildDependencyGraph(): DependencyGraph {
        val graph = mutableMapOf<Int, MutableList<Int>>()
        val inDegree = mutableMapOf<Int, Int>()

        for (n in 0 until totalPieces) {
            inDegree[n] = 0
            graph[n] = mutableListOf()
        }

        val rng = Randomizer(seed, gridSize)

        for (r in 0 until totalPieces) {
            val i = (rng.prng() % BigInteger.valueOf(3) + BigInteger.valueOf(2)).toInt()
            repeat(i) {
                val j = (rng.prng() % BigInteger.valueOf(totalPieces.toLong())).toInt()
                if (j != r && !wouldCreateCycle(graph, j, r)) {
                    graph[j]!!.add(r)
                    inDegree[r] = inDegree[r]!! + 1
                }
            }
        }

        for (r in 0 until totalPieces) {
            if (inDegree[r] == 0) {
                var tries = 0
                while (tries < 10) {
                    val s = (rng.prng() % BigInteger.valueOf(totalPieces.toLong())).toInt()
                    if (s != r && !wouldCreateCycle(graph, s, r)) {
                        graph[s]!!.add(r)
                        inDegree[r] = inDegree[r]!! + 1
                        break
                    }
                    tries++
                }
            }
        }

        return DependencyGraph(graph, inDegree)
    }

    private fun wouldCreateCycle(graph: Map<Int, List<Int>>, target: Int, start: Int): Boolean {
        val visited = mutableSetOf<Int>()
        val stack = ArrayDeque<Int>()
        stack.add(start)

        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n == target) return true
            if (!visited.add(n)) continue
            graph[n]?.let { stack.addAll(it) }
        }
        return false
    }

    private fun generateScramblePath(): List<Int> {
        val graphCopy = dependencyGraph.graph.mapValues { it.value.toMutableList() }.toMutableMap()
        val inDegreeCopy = dependencyGraph.inDegree.toMutableMap()

        val queue = ArrayDeque<Int>()
        for (n in 0 until totalPieces) {
            if (inDegreeCopy[n] == 0) {
                queue.add(n)
            }
        }

        val order = mutableListOf<Int>()
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            order.add(i)
            val neighbors = graphCopy[i]
            if (neighbors != null) {
                for (e in neighbors) {
                    inDegreeCopy[e] = inDegreeCopy[e]!! - 1
                    if (inDegreeCopy[e] == 0) {
                        queue.add(e)
                    }
                }
            }
        }
        return order
    }

    fun getScrambleMapping(): List<Pair<Int, Int>> {
        var e = randomizer.order.toMutableList()

        if (scramblePath.size == totalPieces) {
            val t = Array(totalPieces) { 0 }
            for (i in scramblePath.indices) {
                t[i] = scramblePath[i]
            }
            val n = Array(totalPieces) { 0 }
            for (r in 0 until totalPieces) {
                n[r] = e[t[r]]
            }
            e = n.toMutableList()
        }

        val result = mutableListOf<Pair<Int, Int>>()
        for (n in 0 until totalPieces) {
            result.add(n to e[n])
        }
        return result
    }
}
