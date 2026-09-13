package Config

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigrationFailureTest
{
    private lateinit var directory: File
    private lateinit var source: File

    @BeforeTest
    fun setUp()
    {
        directory = Files.createTempDirectory("ubuild-migration-failure").toFile()
        source = File(directory, "config.json")
    }

    @AfterTest
    fun tearDown()
    {
        directory.deleteRecursively()
    }

    @Test
    fun malformedV1ShapeLeavesOriginalBytesUntouched()
    {
        val original = """{"engineConfigs": []}"""
        source.writeText(original)

        assertFails { Migration.migrateIfNeeded(source) }

        assertEquals(original, source.readText())
        assertTrue(File(directory, "config.json.v1.bak").isFile)
    }

    @Test
    fun invalidJsonDoesNotCreateBackupOrRewriteSource()
    {
        val original = "not json"
        source.writeText(original)

        assertFails { Migration.migrateIfNeeded(source) }

        assertEquals(original, source.readText())
        assertFalse(File(directory, "config.json.v1.bak").exists())
    }
}
