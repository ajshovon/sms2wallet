package me.shovon.sms2wallet.nlp

import java.time.LocalDateTime
import me.shovon.sms2wallet.domain.nlp.NlPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompt is the exact text that leaves the device, so these tests are really about privacy:
 * a name must not appear in it unless the user chose to share that kind of name.
 */
class NlPromptTest {

    private val now = LocalDateTime.of(2026, 9, 3, 14, 30)

    @Test
    fun `shared category names are listed for the model to choose from`() {
        val prompt = NlPrompt.systemInstruction(
            categoryNames = listOf("Groceries", "Transport"),
            accountNames = emptyList(),
            now = now,
        )

        assertTrue(prompt.contains("Groceries, Transport"))
    }

    @Test
    fun `withholding categories removes the category section entirely`() {
        val prompt = NlPrompt.systemInstruction(
            categoryNames = emptyList(),
            accountNames = emptyList(),
            now = now,
        )

        // Not merely absent from a list: the model is never told a category field exists, so it
        // cannot ask for one or guess at one.
        assertFalse(prompt.contains("category:"))
    }

    @Test
    fun `withholding accounts removes the account section entirely`() {
        val prompt = NlPrompt.systemInstruction(
            categoryNames = listOf("Groceries"),
            accountNames = emptyList(),
            now = now,
        )

        assertFalse(prompt.contains("account:"))
        assertTrue(prompt.contains("Groceries"))
    }

    @Test
    fun `the current time is included so relative phrases resolve`() {
        val prompt = NlPrompt.systemInstruction(emptyList(), emptyList(), now = now)

        assertTrue(prompt.contains("2026-09-03 14:30"))
    }

    @Test
    fun `expense is the default reading`() {
        val prompt = NlPrompt.systemInstruction(emptyList(), emptyList(), now = now)

        assertTrue(prompt.contains("Default to money going out"))
    }
}
