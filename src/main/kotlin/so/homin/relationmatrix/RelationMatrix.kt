package so.homin.relationmatrix

import me.lemire.integercompression.Composition
import me.lemire.integercompression.FastPFOR
import me.lemire.integercompression.IntWrapper
import me.lemire.integercompression.VariableByte
import org.roaringbitmap.buffer.ImmutableRoaringBitmap
import org.roaringbitmap.buffer.MutableRoaringBitmap
import java.io.DataInput
import java.io.DataOutput
import java.io.EOFException
import java.nio.ByteBuffer

/**
 * Relation Matrix
 * @author YoonSeop Choe
 */
class RelationMatrix {

    private val startIndexesOfRelations: IntArray
    private val relationValues: IntArray

    /**
     * When there are relations as follows,
     *
     * 0 -> [1, 4]
     * 1 -> [2]
     * 2 -> [3, 4, 5]
     *
     * It can be expressed in two arrays like this:
     *
     *  0  1  2             (= index of array)
     * [0, 2, 3]            (= startIndexesOfRelations) ... (1)
     *  |    \  \
     * [1, 4, 2, 3, 4, 5]   (= relationValues) ... (2)
     *
     * Now, two arrays can be compressed in FastPFOR.
     * Furthermore, if a domain is given in a bitset(ex: RoaringBitmap), unique relations can be achieved fastly.
     *
     *  1  0  1             (= domain, bitset)
     * [0  X  3]
     *  |    \  \
     * [1, 4, X, 3, 4, 5]   (X = ignore)
     *
     *  0  1  0  1  1  1    (one hot encodings of 1, 4, 3, 4, 5)
     */
    constructor(startIndexesOfRelations: IntArray, relationValues: IntArray) {
        assert(startIndexesOfRelations.last() <= relationValues.size)
        this.startIndexesOfRelations = startIndexesOfRelations
        this.relationValues = relationValues
    }

    constructor(input: DataInput) {
        this.startIndexesOfRelations = read(input)
        this.relationValues = read(input)
    }

    fun serialize(output: DataOutput) {
        write(this.startIndexesOfRelations, output)
        write(this.relationValues, output)
    }

    fun getUniqueRelations(domain: ImmutableRoaringBitmap): MutableRoaringBitmap {
        val result = MutableRoaringBitmap()
        for (offset in domain) {
            val startIndex = startIndexesOfRelations[offset]
            val size = if (offset < startIndexesOfRelations.size - 1) {
                startIndexesOfRelations[offset + 1] - startIndex
            } else {
                relationValues.size - startIndexesOfRelations[offset]
            }
            for (index in startIndex until startIndex + size) {
                result.add(relationValues[index])
            }
        }
        return result
    }

    class Builder {
        private val buffer = mutableMapOf<Int, IntArray>()

        constructor()

        constructor(input: DataInput) {
            while (true) {
                try {
                    val domain = input.readInt()
                    val size = input.readInt()
                    val relation = IntArray(size)
                    for (i in 0 until size) {
                        relation[i] = input.readInt()
                    }
                    buffer[domain] = relation
                } catch (e: EOFException) {
                    break
                }
            }
        }

        fun add(domain: Int, relation: IntArray): Builder {
            buffer[domain] = relation
            return this
        }

        fun merge(other: Builder) {
            other.buffer.forEach { entry ->
                this.buffer.compute(entry.key) { _, relation ->
                    relation?.plus(entry.value) ?: entry.value
                }
            }
        }

        fun build(): RelationMatrix {
            val entries = buffer.toList().sortedBy { it.first }

            val indexMax = entries.maxOf { it.first }
            val startIndexesOfRelations = IntArray(indexMax + 1) { 0 }
            val relationValues = IntArray(entries.sumBy { it.second.size }) { 0 }

            var cursor = 0
            entries.forEach { pair ->
                val domain = pair.first
                val relation = pair.second
                startIndexesOfRelations[domain] = cursor
                relation.forEach {
                    relationValues[cursor] = it
                    cursor++
                }
            }

            return RelationMatrix(startIndexesOfRelations, relationValues)
        }

        fun serialize(output: DataOutput) {
            buffer.entries.forEach { entry ->
                output.writeInt(entry.key)
                output.writeInt(entry.value.size)
                for (i in entry.value.indices) {
                    output.writeInt(entry.value[i])
                }
            }
        }
    }

    companion object {
        private const val EXTRA_COMPRESS_BUFFER_SIZE = 2048

        private fun read(input: DataInput): IntArray {
            val compressedSize = input.readInt()
            val originalSize = input.readInt()

            val bytes = ByteArray(compressedSize * Int.SIZE_BYTES / Byte.SIZE_BYTES)
            input.readFully(bytes)
            val byteBuffer = ByteBuffer.wrap(bytes)
            val intBuffer = byteBuffer.asIntBuffer()

            val compressed = IntArray(compressedSize)
            intBuffer.get(compressed)

            val uncompressed = IntArray(originalSize)
            val codec = Composition(FastPFOR(), VariableByte())
            val recOffset = IntWrapper(0)
            codec.uncompress(compressed, IntWrapper(0), compressed.size, uncompressed, recOffset)
            return uncompressed
        }

        private fun write(array: IntArray, output: DataOutput) {
            val originalSize = array.size
            val codec = Composition(FastPFOR(), VariableByte())
            val compressed = IntArray(originalSize + EXTRA_COMPRESS_BUFFER_SIZE)
            val outputOffset = IntWrapper(0)
            codec.compress(array, IntWrapper(0), originalSize, compressed, outputOffset)

            val compressedSize = outputOffset.get()
            output.writeInt(compressedSize)
            output.writeInt(originalSize)

            val bytes = ByteArray(compressedSize * Int.SIZE_BYTES / Byte.SIZE_BYTES)
            val byteBuffer = ByteBuffer.wrap(bytes)
            val intBuffer = byteBuffer.asIntBuffer()
            intBuffer.put(compressed, 0, compressedSize)
            output.write(bytes)
        }
    }
}
