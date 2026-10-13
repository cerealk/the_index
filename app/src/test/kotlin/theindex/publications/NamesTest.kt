package theindex.publications

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NamesTest {
    @Test
    fun `Contributor names match after case, accents, dots, spaces and name order are normalized`() {
        Names.contributorKey("J. R. R. Tolkien") shouldBe "j r r tolkien"
        Names.contributorKey("Tolkien, J.R.R.") shouldBe "j r r tolkien"
        Names.contributorKey("  GABRIEL  García Márquez ") shouldBe Names.contributorKey("garcia marquez, gabriel")
    }

    @Test
    fun `display names flip Last, First but keep suffixes`() {
        Names.displayName("Tolkien, J.R.R.") shouldBe "J.R.R. Tolkien"
        Names.displayName("King, Jr.") shouldBe "King, Jr."
        Names.displayName("Ursula K. Le Guin") shouldBe "Ursula K. Le Guin"
    }

    @Test
    fun `sort names are derived as Last, First`() {
        Names.deriveSortName("J. R. R. Tolkien") shouldBe "Tolkien, J. R. R."
        Names.deriveSortName("Moebius") shouldBe "Moebius"
    }

    @Test
    fun `sort titles move a leading article by language`() {
        Names.deriveSortTitle("The Hobbit", null) shouldBe "Hobbit, The"
        Names.deriveSortTitle("A Wizard of Earthsea", "en") shouldBe "Wizard of Earthsea, A"
        Names.deriveSortTitle("Il nome della rosa", "it") shouldBe "nome della rosa, Il"
        Names.deriveSortTitle("L'amica geniale", "it") shouldBe "amica geniale, L'"
        Names.deriveSortTitle("The", "en") shouldBe "The"
        Names.deriveSortTitle("Theory of Everything", "en") shouldBe "Theory of Everything"
        Names.deriveSortTitle("The Hobbit", "ja") shouldBe "The Hobbit"
    }

    @Test
    fun `Series positions keep their text and sort by numeric prefix`() {
        Names.positionText("2.0") shouldBe "2"
        Names.positionText("1.50") shouldBe "1.5"
        Names.positionText("Annual 1") shouldBe "Annual 1"
        Names.positionSortKey("1.5") shouldBe "1.5".toBigDecimal()
        Names.positionSortKey("12 (variant)") shouldBe "12".toBigDecimal()
        Names.positionSortKey("Annual 1") shouldBe null
    }
}
