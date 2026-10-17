package theindex.metadata

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ComicFileNameTest {
    private fun parse(name: String) = ComicFileName.parse(name)

    @Test
    fun `series, zero-padded number and year as volume`() {
        parse("Saga 001 (2012).cbz") shouldBe ExtractedMetadata(
            source = "filename",
            title = null,
            series = ExtractedSeries("Saga", position = "1", volume = 2012),
        )
    }

    @Test
    fun `a hash before the number and a title after a dash`() {
        parse("Saga #12 - The War for Phang (2018) (digital) (Son of Ultron-Empire).cbz") shouldBe ExtractedMetadata(
            source = "filename",
            title = "The War for Phang",
            series = ExtractedSeries("Saga", position = "12", volume = 2018),
        )
    }

    @Test
    fun `an explicit volume wins over the year`() {
        parse("Batman_v2_015_(2012).cbz").series shouldBe ExtractedSeries("Batman", position = "15", volume = 2)
    }

    @Test
    fun `a volume without an issue number is a collected volume with no position`() {
        parse("Saga Vol. 3 (2014).cbz").series shouldBe ExtractedSeries("Saga", position = null, volume = 3)
    }

    @Test
    fun `decimal numbers, a trailing of-count and a series ending in a number`() {
        parse("2000 AD 1.5 (of 3).cbz").series shouldBe ExtractedSeries("2000 AD", position = "1.5")
    }

    @Test
    fun `scanner tags in square brackets are ignored`() {
        parse("[Team] Monstress 004 [HQ].cbz").series shouldBe ExtractedSeries("Monstress", position = "4")
    }

    @Test
    fun `a name with no number is the title`() {
        parse("Maus_A_Survivor's_Tale (1991).cbz") shouldBe ExtractedMetadata(
            source = "filename",
            title = "Maus A Survivor's Tale",
        )
    }
}
