package vn.cgnn.admin.util

/**
 * Pure-Kotlin ISO/IEC 18004 QR Code encoder (Byte mode, ECC Level M, Versions 1..6).
 * Capacity at ECC M: V1=14, V2=26, V3=42, V4=62, V5=84, V6=106 UTF-8 bytes.
 * Easily covers CGNN activation URLs (https://cgnn.vn/t/<20..64 chars> <= 82 bytes).
 */
object QrCodeSvgGenerator {
    private data class VersionSpec(
        val version: Int,
        val size: Int,
        val dataCodewords: Int,
        val ecPerBlock: Int,
        val blocks: Int,
        val alignCenters: IntArray,
    )

    private val SPECS = listOf(
        VersionSpec(1, 21, 16, 10, 1, intArrayOf()),
        VersionSpec(2, 25, 28, 16, 1, intArrayOf(6, 18)),
        VersionSpec(3, 29, 44, 26, 1, intArrayOf(6, 22)),
        VersionSpec(4, 33, 64, 18, 2, intArrayOf(6, 26)),
        VersionSpec(5, 37, 86, 24, 2, intArrayOf(6, 30)),
        VersionSpec(6, 41, 108, 16, 4, intArrayOf(6, 34)),
    )

    // Precomputed 15-bit format info for ECC Level M (00) and masks 0..7 (with BCH + XOR 0x5412)
    private val FORMAT_INFO_M = intArrayOf(
        0x5412, 0x5125, 0x5E7C, 0x5B4B, 0x45F9, 0x40CE, 0x4F97, 0x4AA0,
    )

    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) {
            GF_EXP[i] = GF_EXP[i - 255]
        }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[GF_LOG[a] + GF_LOG[b]]
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val factor = GF_EXP[i]
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], factor)
            }
            poly = next
        }
        return poly
    }

    private fun rsComputeRemainder(data: IntArray, ecLen: Int): IntArray {
        val gen = rsGeneratorPoly(ecLen)
        val result = IntArray(ecLen)
        for (b in data) {
            val factor = b xor result[0]
            System.arraycopy(result, 1, result, 0, ecLen - 1)
            result[ecLen - 1] = 0
            for (j in 0 until ecLen) {
                result[j] = result[j] xor gfMul(gen[j + 1], factor)
            }
        }
        return result
    }

    fun encode(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val spec = SPECS.firstOrNull { bytes.size + 2 <= it.dataCodewords }
            ?: error("Dữ liệu quá dài cho mã QR (${bytes.size} bytes)")

        // 1. Build bit stream: Mode 0100 (Byte), 8-bit length, payload, terminator, pad
        val bits = ArrayList<Int>(spec.dataCodewords * 8)
        fun pushBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits.add((value ushr i) and 1)
        }
        pushBits(0b0100, 4)
        pushBits(bytes.size, 8)
        for (b in bytes) pushBits(b.toInt() and 0xFF, 8)
        val capacityBits = spec.dataCodewords * 8
        val term = minOf(4, capacityBits - bits.size)
        repeat(term) { bits.add(0) }
        while (bits.size % 8 != 0) bits.add(0)

        val dataCw = IntArray(spec.dataCodewords)
        var cwIdx = 0
        var i = 0
        while (i < bits.size && cwIdx < dataCw.size) {
            var v = 0
            for (k in 0 until 8) v = (v shl 1) or bits[i + k]
            dataCw[cwIdx++] = v
            i += 8
        }
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (cwIdx < dataCw.size) {
            dataCw[cwIdx++] = padBytes[padIdx % 2]
            padIdx++
        }

        // 2. Split into blocks and compute RS ECC, then interleave
        val blockSize = spec.dataCodewords / spec.blocks
        val dataBlocks = Array(spec.blocks) { b ->
            dataCw.copyOfRange(b * blockSize, (b + 1) * blockSize)
        }
        val ecBlocks = Array(spec.blocks) { b ->
            rsComputeRemainder(dataBlocks[b], spec.ecPerBlock)
        }

        val finalBits = ArrayList<Int>((spec.dataCodewords + spec.blocks * spec.ecPerBlock) * 8)
        for (col in 0 until blockSize) {
            for (b in 0 until spec.blocks) {
                val v = dataBlocks[b][col]
                for (bit in 7 downTo 0) finalBits.add((v ushr bit) and 1)
            }
        }
        for (col in 0 until spec.ecPerBlock) {
            for (b in 0 until spec.blocks) {
                val v = ecBlocks[b][col]
                for (bit in 7 downTo 0) finalBits.add((v ushr bit) and 1)
            }
        }

        // 3. Build function patterns matrix
        val n = spec.size
        val modules = Array(n) { BooleanArray(n) }
        val isFunction = Array(n) { BooleanArray(n) }

        fun setFn(r: Int, c: Int, dark: Boolean) {
            if (r in 0 until n && c in 0 until n) {
                modules[r][c] = dark
                isFunction[r][c] = true
            }
        }

        fun drawFinder(top: Int, left: Int) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val r = top + dr
                    val c = left + dc
                    if (r !in 0 until n || c !in 0 until n) continue
                    val dark = (dr in 0..6 && dc in 0..6) && (
                        dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4)
                        )
                    setFn(r, c, dark)
                }
            }
        }
        drawFinder(0, 0)
        drawFinder(0, n - 7)
        drawFinder(n - 7, 0)

        // Timing patterns
        for (k in 8 until n - 8) {
            setFn(6, k, k % 2 == 0)
            setFn(k, 6, k % 2 == 0)
        }

        // Alignment patterns
        for (rCenter in spec.alignCenters) {
            for (cCenter in spec.alignCenters) {
                if ((rCenter == 6 && cCenter == 6) ||
                    (rCenter == 6 && cCenter == n - 7) ||
                    (rCenter == n - 7 && cCenter == 6)
                ) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val dark = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc)) != 1
                        setFn(rCenter + dr, cCenter + dc, dark)
                    }
                }
            }
        }

        // Reserve format info areas & dark module
        for (k in 0..8) {
            if (k != 6) {
                setFn(8, k, false)
                setFn(k, 8, false)
            }
        }
        for (k in 0..7) {
            setFn(8, n - 1 - k, false)
            setFn(n - 1 - k, 8, false)
        }
        setFn(n - 8, 8, true)

        // 4. Place data bits in zig-zag columns
        var bitIdx = 0
        var col = n - 1
        var upward = true
        while (col > 0) {
            if (col == 6) col--
            for (step in 0 until n) {
                val r = if (upward) n - 1 - step else step
                for (dc in 0..1) {
                    val c = col - dc
                    if (!isFunction[r][c]) {
                        modules[r][c] = if (bitIdx < finalBits.size) finalBits[bitIdx++] == 1 else false
                    }
                }
            }
            upward = !upward
            col -= 2
        }

        // 5. Evaluate masks 0..7 and choose lowest penalty
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestMatrix: Array<BooleanArray> = modules

        for (mask in 0..7) {
            val candidate = Array(n) { r ->
                BooleanArray(n) { c ->
                    val invert = !isFunction[r][c] && maskCondition(mask, r, c)
                    if (invert) !modules[r][c] else modules[r][c]
                }
            }
            applyFormatBits(candidate, FORMAT_INFO_M[mask])
            val penalty = computePenalty(candidate)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestMatrix = candidate
            }
        }
        return bestMatrix
    }

    private fun maskCondition(mask: Int, r: Int, c: Int): Boolean = when (mask) {
        0 -> (r + c) % 2 == 0
        1 -> r % 2 == 0
        2 -> c % 3 == 0
        3 -> (r + c) % 3 == 0
        4 -> (r / 2 + c / 3) % 2 == 0
        5 -> (r * c) % 2 + (r * c) % 3 == 0
        6 -> ((r * c) % 2 + (r * c) % 3) % 2 == 0
        7 -> ((r + c) % 2 + (r * c) % 3) % 2 == 0
        else -> false
    }

    private fun applyFormatBits(m: Array<BooleanArray>, bits: Int) {
        val n = m.size
        fun bit(i: Int) = ((bits ushr i) and 1) == 1
        // Copy 1: around top-left
        for (i in 0..5) m[8][i] = bit(14 - i)
        m[8][7] = bit(8)
        m[8][8] = bit(7)
        m[7][8] = bit(6)
        for (i in 0..5) m[5 - i][8] = bit(5 - i)
        // Copy 2: bottom-left & top-right
        for (i in 0..6) m[n - 1 - i][8] = bit(14 - i)
        for (i in 0..7) m[8][n - 8 + i] = bit(7 - i)
        m[n - 8][8] = true
    }

    private fun computePenalty(m: Array<BooleanArray>): Int {
        val n = m.size
        var penalty = 0
        // Rule 1: runs of 5+
        for (r in 0 until n) {
            var runLen = 1
            for (c in 1 until n) {
                if (m[r][c] == m[r][c - 1]) runLen++ else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }
        for (c in 0 until n) {
            var runLen = 1
            for (r in 1 until n) {
                if (m[r][c] == m[r - 1][c]) runLen++ else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }
        // Rule 2: 2x2 blocks
        for (r in 0 until n - 1) {
            for (c in 0 until n - 1) {
                val v = m[r][c]
                if (m[r][c + 1] == v && m[r + 1][c] == v && m[r + 1][c + 1] == v) penalty += 3
            }
        }
        return penalty
    }

    fun toSvg(text: String): String {
        val matrix = encode(text)
        val n = matrix.size
        val path = StringBuilder(n * n * 6)
        for (r in 0 until n) {
            for (c in 0 until n) {
                if (matrix[r][c]) {
                    path.append('M').append(c).append(',').append(r).append("h1v1h-1z")
                }
            }
        }
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $n $n" shape-rendering="crispEdges"><rect width="$n" height="$n" fill="#fff"/><path d="$path" fill="#000"/></svg>"""
    }
}
