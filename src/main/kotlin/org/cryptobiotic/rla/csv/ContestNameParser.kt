package org.cryptobiotic.rla.csv

import org.apache.commons.io.input.BOMInputStream
import org.apache.commons.io.input.ReaderInputStream
import org.apache.commons.io.IOUtils
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.lang.Exception
import java.util.*
import kotlin.text.split

class ContestNameParser(val parser: CSVParser) {
    companion object {
        private val csvFormat: CSVFormat = CSVFormat.DEFAULT.withHeader()
    }

    // TODO org uses TreeSet to keep sorted. is that a good idea for choices?
    val contests = mutableMapOf<String, MutableSet<String>>()
    private val choices = mutableMapOf<String, MutableSet<String>>()
    val duplicates = mutableMapOf<String, MutableSet<String>>()
    val errors: SortedSet<ParseError> = TreeSet()

    @Throws(IOException::class)
    constructor(r: Reader): this(
        CSVParser(InputStreamReader(BOMInputStream(ReaderInputStream(r)), "UTF-8") , csvFormat))

    @Throws(IOException::class)
    constructor(string: String): this(
        CSVParser(InputStreamReader( BOMInputStream(IOUtils.toInputStream(string, "UTF-8")), "UTF-8"), csvFormat))

    override fun toString(): String {
        return String.format("[contests=%s; duplicates=%s; errors=%s]", contests(), duplicates(), errors)
    }

    fun contestCount(): Int? {
        return contests.values.map{ it.size }.sum()
    }

    fun contests(): Map<String, Set<String>?> {
        return contests
    }

    fun addContest(countyName: String, contestName: String) {
        val v = contests.getOrPut(countyName) { mutableSetOf<String>() }
        val newElement = v.add(contestName)
        if (!newElement) {
            addDuplicateContest(countyName, contestName)
        }
        contests[countyName] = v
    }

    fun choices(): Map<String, Set<String>> {
        return choices.map { (key, value) -> key to value.toSortedSet() }.toMap()
    }

    //   public void addChoices(final String contestName, final String... splitResult) {
    //    final Set<String> choiceNames = new HashSet();
    //    Collections.addAll(choiceNames, splitResult);
    //
    //    this.choices.merge(contestName, choiceNames,
    //                       (s1,s2) -> {
    //                         s1.addAll(s2);
    //                         return s1;
    //                       });
    //  }
    fun addChoices(contestName: String, splitResult: List<String>) {
        val choiceNames = HashSet(splitResult)
        choices.merge(contestName, choiceNames) { s1, s2 -> s1.addAll(s2); s1 }
    }

    fun addDuplicateContest(countyName: String, contestName: String) {
        val v = duplicates.getOrPut(countyName) { mutableSetOf<String>() }
        v.add(contestName)
        duplicates[countyName] = v
    }

    fun duplicates(): Map<String, Set<String>?> {
        return duplicates
    }

    fun isSuccess(): Boolean {
        return errors.isEmpty() && duplicates.isEmpty()
    }

    //   public synchronized boolean parse() {
    //    final Iterable<CSVRecord> records = parser;
    //
    //    try {
    //      for (final CSVRecord r : records) {
    //        final Map<String,String> record = r.toMap();
    //        final String countyName = record.getOrDefault("CountyName", "");
    //        final String contestName = record.getOrDefault("ContestName", "");
    //        final String choiceNames = record.getOrDefault("ContestChoices", "");
    //
    //        if (!choiceNames.isEmpty()){
    //          addChoices(contestName, choiceNames.split("\\s*,\\s*"));
    //        }
    //
    //        if (countyName.isEmpty() || contestName.isEmpty()) {
    //          errors.add(new ParseError("malformed record: (" + record + ")",
    //                                    parser.getCurrentLineNumber()));
    //          break;
    //        } else {
    //          addContest(countyName, contestName);
    //        }
    //      }
    //    } catch (final NoSuchElementException e) {
    //      errors.add(new ParseError("Could not parse contests file",
    //                                parser.getCurrentLineNumber(), e));
    //    }
    //
    //    return this.isSuccess();
    //  }
    @Synchronized
    fun parse(): Boolean {
        val records : List<CSVRecord> = parser.records
        for (r in records) {
            val record: Map<String, String> = r.toMap()
            val countyName = record["CountyName"] ?: ""
            val contestName = record["ContestName"] ?:  ""
            val choiceNames = record["ContestChoices"] ?: ""
            if (!choiceNames.isEmpty()) {
                val wtf = choiceNames.split(",").map { it.trim() } // TODO LOOK
                addChoices(contestName, wtf)
            }
            if (countyName.isEmpty() || contestName.isEmpty()) {
                errors.add(ParseError("malformed record: ($record)", parser.currentLineNumber))
                break
            } else {
                addContest(countyName, contestName)
            }
        }
        return isSuccess()
    }

    inner class ParseError(private val msg: String, private val line: Long, val e: Exception?) : Comparable<ParseError> {

        constructor(msg: String, n: Long) : this(msg, n, null)

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val pe = o as ParseError
            return line == pe.line
        }

        override fun hashCode(): Int {
            return Objects.hash(line, msg)
        }

        override fun compareTo(pe: ParseError): Int {
            return compareBy(ParseError::line).thenComparing(ParseError::msg).compare(this, pe)
        }

        fun getException(): Exception? {
            return e
        }

        fun getLine(): Long {
            return line
        }

        override fun toString(): String {
            return if (e != null) "$msg on line $line. Exception: $e"
                    else "$msg on line $line"
        }
    }
}