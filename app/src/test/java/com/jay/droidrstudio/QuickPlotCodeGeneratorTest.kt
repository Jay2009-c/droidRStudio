package com.jay.droidrstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickPlotCodeGeneratorTest {
    @Test
    fun `scatter plot creates paired vectors and labelled axes`() {
        val code = QuickPlotCodeGenerator.generate(
            "Scatter Plot", "Manual", "1, 2, 3", "4, 5, 6", "Measurements", "steelblue", "plot.png"
        )

        assertTrue(code.contains("df <- data.frame(x = c(1.0, 2.0, 3.0), y = c(4.0, 5.0, 6.0))"))
        assertTrue(code.contains("geom_point(color='steelblue', size=3, alpha=1.0)"))
        assertTrue(code.contains("labs(title='Measurements', x=NULL, y=NULL)"))
    }

    @Test
    fun `manual values reject invalid and mismatched input`() {
        assertEquals("Enter comma- or space-separated finite numbers.", QuickPlotCodeGenerator.validationError("Line Plot", "Manual", "1, nope", ""))
        assertEquals("X and Y must contain the same number of values.", QuickPlotCodeGenerator.validationError("Scatter Plot", "Manual", "1, 2", "3"))
        assertNull(QuickPlotCodeGenerator.validationError("Scatter Plot", "Manual", "1 2", "3 4"))
    }

    @Test
    fun `titles are escaped and output uses high resolution device`() {
        val code = QuickPlotCodeGenerator.generate(
            "Histogram", "Manual", "1, 2", "", "Sam's plot", "darkred", "safe.png"
        )

        assertTrue(code.startsWith("png('safe.png', width=1200, height=800, res=144)"))
        assertTrue(code.contains("labs(title='Sam\\'s plot', x=NULL, y=NULL)"))
    }

    @Test
    fun `data tools accept only safe dataset names`() {
        assertTrue(QuickPlotCodeGenerator.isSafeDatasetName("sales_data"))
        assertFalse(QuickPlotCodeGenerator.isSafeDatasetName("sales_data; system('oops')"))
        assertEquals(
            "Use a dataset name such as mtcars or my_data.",
            QuickPlotCodeGenerator.dataToolValidationError("Data overview", "sales data", "", "")
        )
    }

    @Test
    fun `guided task for color analysis uses farver`() {
        val code = QuickPlotCodeGenerator.generateGuidedTask("Color Analysis", "mtcars")

        assertTrue(code.contains("farver::decode_colour"))
        assertTrue(code.contains("cli::cli_h1"))
    }

    @Test
    fun `guided imported data task loads the selected csv automatically`() {
        val code = QuickPlotCodeGenerator.generateGuidedTask("Understand my data", "my_data")

        assertTrue(code.contains("psych::describe"))
        assertTrue(code.contains("cli::cli_h1"))
    }
}
