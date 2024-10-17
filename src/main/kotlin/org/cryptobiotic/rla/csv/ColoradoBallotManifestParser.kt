package org.cryptobiotic.rla.csv

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rla.model.BallotManifestInfo
// import us.freeandfair.corla.persistence.Persistence
import java.io.IOException
import java.io.Reader
import java.lang.Exception
import java.util.*

/**
 * The parser for Colorado ballot manifests.
 *
 * @author Daniel M. Zimmerman <dmz@freeandfair.us>
 * @version 1.0.0
 */
class ColoradoBallotManifestParser(val my_parser: CSVParser, val my_county_id: Long) {

    /**
     * The number of ballots represented by the parsed records.
     */
    private var my_ballot_count = -1

    /**
     * The set of parsed ballot manifests that haven't yet been flushed to the
     * database.
     */
    private val my_parsed_manifests: MutableSet<BallotManifestInfo> = HashSet()

    /**
     * Construct a new Colorado ballot manifest parser using the specified Reader.
     *
     * @param the_reader The reader from which to read the CSV to parse.
     * @param the_timestamp The timestamp to apply to the parsed records.
     * @param the_county_id The county ID for the parsed records.
     * @exception IOException if an error occurs while constructing the parser.
     */
    constructor(the_reader: Reader, the_county_id: Long): this(CSVParser(the_reader, CSVFormat.DEFAULT), the_county_id)


    /**
     * Construct a new Colorado ballot manifest parser using the specified String.
     *
     * @param the_string The CSV string to parse.
     * @param the_timestamp The timestamp to apply to the parsed records.
     * @param the_county_id The county ID for the parsed records.
     * @exception IOException if an error occurs while constructing the parser.
     */
    // constructor(the_string: String, the_county_id: Long): this (CSVParser(the_string, CSVFormat.DEFAULT), the_county_id)

    /**
     * Checks to see if the set of parsed manifests needs flushing, and does so
     * if necessary.
     */
    private fun checkForFlush() {
        if (my_parsed_manifests.size % BATCH_SIZE == 0) {
            //us.freeandfair.corla.persistence.Persistence.flush()
            for (bmi in my_parsed_manifests) {
                //us.freeandfair.corla.persistence.Persistence.evict(bmi)
            }
            my_parsed_manifests.clear()
        }
    }

    /**
     * Extracts ballot manifest information from a single CSV line.
     *
     * @param the_line The CSV line.
     * @param the_timestamp The timestamp to apply to the result.
     * @return the extracted information.
     */
    private fun extractBMI(the_line: CSVRecord): BallotManifestInfo {
        val batch_size = the_line.get(NUM_BALLOTS_COLUMN).toInt()
        val sequence_start = if (my_ballot_count == 0) 1L else my_ballot_count.toLong() + 1L
        val sequence_end = sequence_start + batch_size - 1L

        // class BallotManifestInfo(
        //     val countyId: Long,
        //    val scannerId: Int,
        //    val batchId: String,
        //    val batchSize: Int,
        //    val storageLocation: String,
        //    val sequenceStart: Long, // The first sequence number (of all ballots) in this batch
        //    val sequenceEnd: Long, // The last sequence number (of all ballots) in this batch
        //  }
        val bmi = BallotManifestInfo(
            my_county_id,
            the_line.get(SCANNER_ID_COLUMN).toInt(),
            the_line.get(BATCH_NUMBER_COLUMN).toInt(),
            batch_size,
            the_line.get(BATCH_LOCATION_COLUMN),
            sequence_start,
            sequence_end,
        )
        // us.freeandfair.corla.persistence.Persistence.saveOrUpdate(bmi)
        my_parsed_manifests.add(bmi)
        checkForFlush()
        LOGGER.debug("parsed ballot manifest: $bmi")

        return bmi
    }

    /**
     * Parse the supplied data export. If it has already been parsed, this
     * method returns immediately.
     *
     * @return true if the parse was successful, false otherwise
     */
    @Synchronized
    fun parse(): Result {
        val result = Result()
        val records = my_parser.iterator()

        var my_record_count = 0
        my_ballot_count = 0
        var bmi_line: CSVRecord? = null
        var bmi: BallotManifestInfo

        try {
            // we expect the first line to be the headers, which we currently discard
            records.next()
            // subsequent lines contain ballot manifest info

            while (records.hasNext()) {
                bmi_line = records.next()
                bmi = extractBMI(bmi_line)
                my_record_count++
                my_ballot_count = bmi.sequenceEnd.toInt()
            }

            result.success = true
            result.importedCount = my_record_count
        } catch (e: Exception) {
            result.success = false
            result.errorMessage = e.javaClass.toString() + " " + e.message
            result.errorRowNum = my_record_count
            if (bmi_line != null) {
                val values: MutableList<String> = ArrayList()
                bmi_line.iterator().forEachRemaining { values.add(it) }
                result.errorRowContent = values.joinToString(",")
            }
            LOGGER.error("${e.javaClass} ${e.message}\n line number: ${result.errorRowNum} \n content:${result.errorRowContent}")
        }

        return result
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    fun ballotCount(): Int = my_ballot_count

    companion object {
        val LOGGER = KotlinLogging.logger("ColoradoBallotManifestParser")

        /**
         * The size of a batch of ballot manifests to be flushed to the database.
         */
        const val BATCH_SIZE = 50

        /**
         * The column containing the scanner ID.
         */
        const val SCANNER_ID_COLUMN = 1

        /**
         * The column containing the batch number.
         */
        const val BATCH_NUMBER_COLUMN = 2

        /**
         * The column containing the number of ballots in the batch.
         */
        const val NUM_BALLOTS_COLUMN = 3

        /**
         * The column containing the storage location.
         */
        const val BATCH_LOCATION_COLUMN = 4
    }
}