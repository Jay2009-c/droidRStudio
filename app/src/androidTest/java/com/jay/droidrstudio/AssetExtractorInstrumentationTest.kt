package com.jay.droidrstudio

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AssetExtractorInstrumentationTest {

    @Test
    fun testExtractionOfRealAsset() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val extractor = AssetExtractor(appContext)
        val targetDir = File(appContext.filesDir, "test_alpine_r")
        
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        
        extractor.extractAsset("alpine_r.tar.gz", targetDir)
        
        val state = extractor.state.value
        assertTrue("Extraction should be successful, but was $state", state is ExtractionState.Success)
        assertTrue("Target directory should exist", targetDir.exists())
        assertTrue("Target directory should not be empty", targetDir.list()?.isNotEmpty() == true)
        
        // Clean up
        targetDir.deleteRecursively()
    }
}
