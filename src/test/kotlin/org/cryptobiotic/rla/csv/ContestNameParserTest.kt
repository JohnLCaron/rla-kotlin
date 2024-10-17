package org.cryptobiotic.rla.csv


import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ContestNameParserTest {

    val byteOrderMark: String
    val csvWithBOM: String
    val simpleCSV: String
    val dupesInCSV: String
    val malformedCSV: String
    val withChoicesCSV: String

    init {
        simpleCSV = "CountyName,ContestName\n" +
                "boulder,IPA\nboulder,kombucha\nboulder,coffee\n" +
                "denver,IPA\ndenver,stout\ndenver,coffee"
        byteOrderMark = "﻿"
        csvWithBOM = byteOrderMark + "CountyName,ContestName\n" +
                "boulder,IPA\nboulder,kombucha\nboulder,coffee\n" +
                "denver,IPA\ndenver,stout\ndenver,coffee"
        dupesInCSV = "CountyName,ContestName\n" +
                "boulder,IPA\nboulder,kombucha\nboulder,coffee\n" +
                "denver,IPA\ndenver,IPA\ndenver,coffee"
        malformedCSV = "County Name\",Contest Name\t\nboulder,\n"
        withChoicesCSV = "CountyName,ContestName,ContestChoices\n" +
                "boulder,IPA,\"Mojo , Moirai\"\nboulder,kombucha,\"happy leaf,Rowdy Mermaid\"\nboulder,coffee\n" +
                "denver,IPA\ndenver,stout\ndenver,coffee"
    }

    @Test
    fun parseTest() {
        val target = ContestNameParser(simpleCSV)
        val successfulP = target.parse()

        assertEquals(successfulP, true, "A parser returns its successfullness")
        assertEquals(
            6,
            target.contestCount(),
            "Boulder and Denver have six potentially-shared contests"
        )
    }

    /* @Test
     fun parseBOMTest() {
         try {
             val p = ContestNameParser(csvWithBOM)
             val successfulP = p.parse()

             assertEquals(p.contestCount(), OptionalInt.of(6), "A Byte Order Mark isn't a dealbreaker")
         } catch (ioe: IOException) {
             fail("Edge case", ioe)
         }
     }*/

    //         withChoicesCSV = "CountyName,ContestName,ContestChoices\n" +
    //                "boulder,IPA,\"Mojo , Moirai\"\nboulder,kombucha,\"happy leaf,Rowdy Mermaid\"\nboulder,coffee\n" +
    //                "denver,IPA\ndenver,stout\ndenver,coffee"
    @Test
    fun choicesTest() {
        val target = ContestNameParser(withChoicesCSV)
        target.parse()
        assertEquals("{IPA=[Moirai, Mojo], kombucha=[Rowdy Mermaid, happy leaf]}", target.choices().toString())
    }

    @Test
    fun duplicatesTest() {
        val target = ContestNameParser(dupesInCSV)
        val successfulP = target.parse()

        val expectedDupes = TreeMap<String, Set<String>>()
        val duplicates = TreeSet<String>()

        duplicates.add("IPA")
        expectedDupes["denver"] = duplicates

        assertEquals(
            5,
            target.contestCount(),
            "missing stout by way of a double IPA"
        )
        assertEquals(
            target.duplicates(),
            expectedDupes,
            "duplicates are collected by county"
        )
    }

    @Test
    fun errorsTest() {
        val target = ContestNameParser(malformedCSV)
        val successfulP = target.parse()

        assertFalse(successfulP, "A malformed CSV is considered a failure")
        assertFalse(target.errors.isEmpty(), "Parse failure results in errors")
        assertEquals(
            "malformed record: ({Contest Name\t=, County Name\"=boulder}) on line 2",
            target.errors.first().toString(),
            "Line two is missing a contest name value"
        )
    }
}