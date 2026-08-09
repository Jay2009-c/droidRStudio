package com.jay.droidrstudio

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class AssetExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `extractAsset extracts files correctly`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val assetManager = mockk<AssetManager>(relaxed = true)
        val targetDir = tempFolder.newFolder("extracted")

        // Create a dummy tar.gz in memory
        val baos = ByteArrayOutputStream()
        GzipCompressorOutputStream(baos).use { gzos ->
            TarArchiveOutputStream(gzos).use { tos ->
                val entry = TarArchiveEntry("hello.txt")
                val content = "hello world".toByteArray()
                entry.size = content.size.toLong()
                tos.putArchiveEntry(entry)
                tos.write(content)
                tos.closeArchiveEntry()

                val dirEntry = TarArchiveEntry("subdir/")
                tos.putArchiveEntry(dirEntry)
                tos.closeArchiveEntry()

                val fileInDirEntry = TarArchiveEntry("subdir/inner.txt")
                val innerContent = "inner".toByteArray()
                fileInDirEntry.size = innerContent.size.toLong()
                tos.putArchiveEntry(fileInDirEntry)
                tos.write(innerContent)
                tos.closeArchiveEntry()
            }
        }
        val tarGzBytes = baos.toByteArray()

        every { context.assets } returns assetManager
        every { assetManager.open(any()) } returns ByteArrayInputStream(tarGzBytes)
        // openFd will throw exception in test environment for ByteArrayInputStream
        every { assetManager.openFd(any()) } throws Exception("Mocked FD failure")

        val extractor = AssetExtractor(context)
        extractor.extractAsset(targetDir)

        val state = extractor.state.value
        assertTrue("State should be Success but was $state", state is ExtractionState.Success)

        val extractedFile = File(targetDir, "hello.txt")
        assertTrue("hello.txt should exist", extractedFile.exists())
        assertEquals("hello world", extractedFile.readText())

        val extractedSubdir = File(targetDir, "subdir")
        assertTrue("subdir should exist", extractedSubdir.isDirectory)

        val extractedInnerFile = File(targetDir, "subdir/inner.txt")
        assertTrue("inner.txt should exist", extractedInnerFile.exists())
        assertEquals("inner", extractedInnerFile.readText())
    }

    @Test
    fun `extractAsset handles errors and cleans up`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val assetManager = mockk<AssetManager>(relaxed = true)
        val targetDir = tempFolder.newFolder("error_test")

        // Create a corrupt tar.gz
        every { context.assets } returns assetManager
        every { assetManager.open(any()) } returns ByteArrayInputStream("corrupt data".toByteArray())

        val extractor = AssetExtractor(context)
        extractor.extractAsset(targetDir)

        val state = extractor.state.value
        assertTrue("State should be Error but was $state", state is ExtractionState.Error)
        assertTrue("Target directory should be deleted", !targetDir.exists())
    }
}
