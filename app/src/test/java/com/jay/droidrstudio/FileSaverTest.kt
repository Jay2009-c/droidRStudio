package com.jay.droidrstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileSaverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun saveRunnerR_writesCorrectContent() {
        // Given
        val fileSaver = FileSaver()
        val tempDir = tempFolder.newFolder()
        val content = "print('Hello, R!')"

        // When
        fileSaver.saveRunnerR(tempDir, content)

        // Then
        val expectedFile = File(tempDir, "runner.R")
        assertTrue("File should exist", expectedFile.exists())
        assertEquals(content, expectedFile.readText())
    }

    @Test
    fun saveRunnerR_overwritesExistingFile() {
        // Given
        val fileSaver = FileSaver()
        val tempDir = tempFolder.newFolder()
        val initialContent = "old content"
        val newContent = "new content"
        
        val file = File(tempDir, "runner.R")
        file.writeText(initialContent)

        // When
        fileSaver.saveRunnerR(tempDir, newContent)

        // Then
        assertEquals(newContent, file.readText())
    }
}
