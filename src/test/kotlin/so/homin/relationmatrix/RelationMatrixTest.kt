package so.homin.relationmatrix

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.roaringbitmap.buffer.MutableRoaringBitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream


internal class RelationMatrixTest {

    @Test
    fun basicUsage() {
        val matrix = RelationMatrix.Builder()
            .add(0, intArrayOf(1, 4))
            .add(1, intArrayOf(2))
            .add(2, intArrayOf(3, 4, 5))
            .build()

        val domain = MutableRoaringBitmap()
        domain.add(0)
        domain.add(2)

        val relations = matrix.getUniqueRelations(domain)

        assertEquals(4, relations.cardinality)
        assertTrue(relations.contains(1))
        assertTrue(relations.contains(3))
        assertTrue(relations.contains(4))
        assertTrue(relations.contains(5))
    }

    @Test
    fun mergeBuilders() {
        val builder1 = RelationMatrix.Builder()
            .add(0, intArrayOf(1, 4))
            .add(2, intArrayOf(3))

        val builder2 = RelationMatrix.Builder()
            .add(1, intArrayOf(2))
            .add(2, intArrayOf(4, 5))

        builder1.merge(builder2)
        val matrix = builder1.build()

        val domain = MutableRoaringBitmap()
        domain.add(0)
        domain.add(2)

        val relations = matrix.getUniqueRelations(domain)

        assertEquals(4, relations.cardinality)
        assertTrue(relations.contains(1))
        assertTrue(relations.contains(3))
        assertTrue(relations.contains(4))
        assertTrue(relations.contains(5))
    }

    @Test
    fun serde() {
        val serialized = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { output ->
                RelationMatrix.Builder()
                    .add(0, intArrayOf(1, 4))
                    .add(1, intArrayOf(3))
                    .build()
                    .serialize(output)
                output.flush()
            }
            baos.toByteArray()
        }

        val deserialized = ByteArrayInputStream(serialized).use { bais ->
            DataInputStream(bais).use { input ->
                RelationMatrix(input)
            }
        }
        val domain = MutableRoaringBitmap()
        domain.add(0)
        domain.add(1)

        val relations = deserialized.getUniqueRelations(domain)
        assertTrue(relations.contains(1))
        assertTrue(relations.contains(3))
        assertTrue(relations.contains(4))
        assertEquals(3, relations.cardinality)
    }

    @Test
    fun serdeBuilder() {
        val serialized = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { output ->
                RelationMatrix.Builder()
                    .add(0, intArrayOf(1, 4))
                    .add(1, intArrayOf(3))
                    .serialize(output)
                output.flush()
            }
            baos.toByteArray()
        }

        val deserialized = ByteArrayInputStream(serialized).use { bais ->
            DataInputStream(bais).use { input ->
                RelationMatrix.Builder(input)
            }
        }
        val domain = MutableRoaringBitmap()
        domain.add(0)
        domain.add(1)

        val relations = deserialized.build().getUniqueRelations(domain)
        assertTrue(relations.contains(1))
        assertTrue(relations.contains(3))
        assertTrue(relations.contains(4))
        assertEquals(3, relations.cardinality)
    }
}
