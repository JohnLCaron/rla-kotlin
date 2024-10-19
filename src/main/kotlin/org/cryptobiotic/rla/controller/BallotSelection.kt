package org.cryptobiotic.rla.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rla.model.BallotManifestInfo
import org.cryptobiotic.rla.model.CVRAuditInfo
import org.cryptobiotic.rla.model.CastVoteRecord
import org.cryptobiotic.rla.model.ContestResult
import org.cryptobiotic.rla.model.Tribute
import org.cryptobiotic.rla.persistence.BallotManifestInfoQueries
import org.cryptobiotic.rla.persistence.CastVoteRecordQueries
import org.cryptobiotic.rla.persistence.Persistence
import org.cryptobiotic.rla.util.PseudoRandomNumberGenerator

// probably a singleton
class BallotSelection private constructor() {
    
    class Segment {
        val cvrs = mutableListOf<CastVoteRecord>() // TODO was Set
        val cvrIds = mutableListOf<Long>()
        val tributes = mutableListOf<Tribute>()

        fun addTribute(
            bmi: BallotManifestInfo,
            ballotPosition: Int,
            rand: Int,
            randSequencePosition: Int,
            contestName: String,
        ) {
            val t = Tribute(bmi.countyId, bmi.scannerId, bmi.batchId, ballotPosition, rand, randSequencePosition, contestName)
            tributes.add(t)
        }

        fun addCvrs(newcvrs: Collection<CastVoteRecord>) {
            cvrs.addAll(newcvrs)
        }

        fun addCvrIds(cvrs: Collection<CastVoteRecord>) {
            cvrIds.addAll( cvrs.map { it.id } )
        }

        fun addCvrIds(newcvrIds: List<Long>) {
            cvrIds.addAll(newcvrIds)
        }

        fun auditSequence(): List<Long> {
            return cvrIds
        }

        fun cvrsInBallotSequence(): List<CastVoteRecord> {
            return BallotSequencer.sortAndDeduplicateCVRs(cvrs)
        }

        override fun toString(): String {
            return "[Segment auditSequence=${cvrIds} ballotPositions=${tributes}]"
        }
    }

    class Selection(val contestResult: ContestResult, val domainSize: Int = 0) {
        val segments= mutableMapOf<Long, Segment>()
        val generatedNumbers = mutableListOf<Int>()
        val contestName = contestResult.contestName

        fun initCounty(countyId: Long) {
            if (forCounty(countyId) == null) {
                val segment = Segment()
                segments[countyId] = segment
            }
        }

        fun addBallotPosition(
            bmi: BallotManifestInfo,
            ballotPosition: Int,
            rand: Int,
            randSequencePosition: Int,
            contestName: String,
        ) {
            forCounty(bmi.countyId)!!.addTribute(
                bmi, ballotPosition,
                rand, randSequencePosition, this.contestName
            )
        }

        fun forCounty(countyId: Long): Segment? {
            return segments[countyId]
        }

        val allSegments: Collection<Segment>
            get() = segments.values

        //     public List<Long> contestCVRIds() {
        //      return contestResult.countyIDs().stream()
        //        .map(id -> forCounty(id))
        //        .filter(s -> s != null)
        //        .map(segment -> segment.cvrIds)
        //        .flatMap(List::stream)
        //        .collect(Collectors.toList());
        //    }
        fun contestCVRIds(): List<Long> {
            return contestResult.countyIDs()
                .map { forCounty(it) }
                .filter { it != null}
                .map { it!!.cvrIds }
                .flatten()
        }

        override fun toString(): String {
            return String.format(
                "[Selection contestName=%s generatedNumbers=%s domainSize=%s]",
                contestName, generatedNumbers, domainSize
            )
        }
    }

    class MissingBallotManifestException internal constructor(msg: String?) : java.lang.RuntimeException(msg) {}

    companion object {
        val LOGGER = KotlinLogging.logger("BallotSelection")

        /**
         * The total number of ballots across a set of counties
         * @param countyIds a set of counties to count
         * @return the number of ballots in the ballot manifests belonging to
         * countyIds
         **/
        fun ballotsCast(countyIds: Set<Long>): Long {
            // could use voteTotals but that would be impure; using cvr data
            //
            // If a county has only one ballot for a contest, all the ballots from that
            // county are used to get a total number of ballots
            return BallotManifestInfoQueries.totalBallots(countyIds);
        }

        fun combineSegments(segments: Collection<Segment>): Segment {
            return segments.stream()
                .filter { s: Segment? -> s == null }
                .reduce(Segment()) { acc: Segment, s: Segment ->
                    acc.addCvrIds(s.cvrIds)
                    acc.addCvrs(s.cvrs)
                    acc
                }
        }

        fun projectUltimateSequence(bmis: Set<BallotManifestInfo>): Set<BallotManifestInfo> {
            var last: Long = 0
            for (bmi in bmis) {
                bmi.setUltimate(last + 1L)
                last = bmi.ultimateSequenceEnd!!
            }
            return bmis
        }

        fun randomSelection(contestResult: ContestResult , seed: String , minIndex: Int , maxIndex: Int): Selection {
            if (minIndex > maxIndex) {
                return Selection(contestResult)
            }

            val domainSize = ballotsCast(contestResult.countyIDs()).toInt()
            val gen = PseudoRandomNumberGenerator(seed, true, 1, domainSize);
            val generatedNumbers = gen.getRandomNumbers(minIndex, maxIndex);

            val selection = Selection(contestResult, domainSize)
            selection.generatedNumbers.addAll(generatedNumbers)

            // make the theoretical selections (avoiding cvrs)
            selectTributes(selection, contestResult.countyIDs()) { ids -> BallotManifestInfoQueries.getMatching(ids)}

            LOGGER.info(String.format("[randomSelection] selected %s samples for %s ",
                selection.generatedNumbers.size,
                contestResult.contestName));
            LOGGER.debug("randomSelection: selection= " + selection);
            // get the CVRs from the theoretical
            resolveSelection(selection);
            return selection;
        }

        //   public static void selectTributes(final Selection selection,
        //                                    final Set<Long> countyIds,
        //                                    final MATCHINGQ queryMatching) {
        //
        //    final Set<BallotManifestInfo> contestBmis = queryMatching.apply(countyIds);
        //    selectTributes(selection, countyIds, contestBmis);
        //  }
        fun selectTributes(selection: Selection, countyIds: Set<Long>, queryMatching: (Set<Long>) -> Set<BallotManifestInfo> ) {
            val contestBmis = queryMatching(countyIds)
            selectTributes(selection, countyIds, contestBmis)
        }

        fun selectTributes(selection: Selection, countyIds: Set<Long>, contestBmis: Set<BallotManifestInfo>) {
            countyIds.forEach { id -> selection.initCounty(id) }
            var i = 0
            for (rand in selection.generatedNumbers) {
                val bmi = selectCountyId(rand.toLong(), contestBmis)
                selection.addBallotPosition(
                    bmi,
                    bmi.translateRand(rand),
                    rand,
                    i++,
                    selection.contestName!!
                )
            }
        }

        //   /** look for the cvrs, some may be phantom records **/
        fun resolveSelection(selection: Selection): Selection {
            for (segment in selection.segments.values) {
                val cvrs: List<CastVoteRecord> =
                    dedupePhantomBallots( CastVoteRecordQueries.atPosition(segment.tributes))
                segment.addCvrs(cvrs)
                segment.addCvrIds(cvrs)
            }
            LOGGER.debug(
                String.format(
                    "[resolveSelection: selection=%s, combinedSegments=%s]",
                    selection.segments, combineSegments(selection.allSegments).cvrIds
                )
            )
            return selection
        }

        /**
         * When we draw more than one phantom ballot, we need to make sure
         * that the persistence context knows about only one instance of each.
         * (Phantom ballots are POJOs, so every phantom ballot looks identical
         * to the persistence context.)
         *
         * @param county The county.
         * @param cvrs A list of CastVoteRecord objects that might contain phantom ballots
         */
        fun dedupePhantomBallots(cvrs: List<CastVoteRecord> ): List<CastVoteRecord> {
            return cvrs
            /* A map of a CVR to a CVR so we can get a unique persisted entity from the database.
            val phantomCvrs: Map<CastVoteRecord, CastVoteRecord> = cvrs
                .filter { it.recordType == CastVoteRecord.RecordType.PHANTOM_RECORD }
                .associate { it to it }

            // Assign database identifiers to newly-created phantom CVRs.
            phantomCvrs.entrySet().stream()
                .forEach(e -> Persistence.saveOrUpdate(e.getValue()));

            // Use the database-mapped CVR if it exists.
            return cvrs.stream()
                .map(cvr -> phantomCvrs.getOrDefault(cvr, cvr))
            .collect(Collectors.toList()); */
        }

        fun selectCountyId(rand: Long, bmis: Set<BallotManifestInfo>): BallotManifestInfo {
            val holding = projectUltimateSequence(bmis).stream()
                .filter { bmi: BallotManifestInfo -> bmi.isHolding(rand) }
                .findFirst()
            if (holding.isPresent) {
                return holding.get()
            } else {
                val msg = "Could not find BallotManifestInfo holding random number: $rand"
                throw MissingBallotManifestException(msg)
            }
        }

        //   public static Integer auditedPrefixLength(final List<Long> cvrIds) {
        //    // FIXME extract-fn, then use
        //    // Map <Long, Boolean> isAuditedById = checkAudited(cvrIds);
        //
        //    if (cvrIds.isEmpty()) { return 0; }
        //
        //    final Map <Long, Boolean> isAuditedById = new HashMap<>();
        //
        //    for (final Long cvrId: cvrIds) {
        //      final CVRAuditInfo cvrai = Persistence.getByID(cvrId, CVRAuditInfo.class);
        //      // has an acvr
        //      final boolean isAudited = cvrai != null && cvrai.acvr() != null;
        //      isAuditedById.put(cvrId, isAudited);
        //    }
        //
        //    Integer idx = 0;
        //    for (int i=0; i < cvrIds.size(); i++) {
        //      final boolean audited = isAuditedById.get(cvrIds.get(i));
        //      if (audited) {
        //        idx = i + 1;
        //      } else { break; }
        //    }
        //    LOGGER.debug(String.format("[auditedPrefixLength: isAuditedById=%s, apl=%d]",
        //                                isAuditedById, idx));
        //    return idx;
        //  }
        fun auditedPrefixLength(cvrIds: List<Long>): Int {
            val isAuditedById: MutableMap<Long, Boolean> = HashMap()
            if (cvrIds.isEmpty()) {
                return 0
            }
            for (cvrId in cvrIds) {
                // TODO apparently the CVRAuditInfo you are looking for is in the session
                //       final CVRAuditInfo cvrai = Persistence.getByID(cvrId, CVRAuditInfo.class);
                val cvrai: CVRAuditInfo? = Persistence.getByID(cvrId, CVRAuditInfo::class.java)
                val isAudited =  cvrai != null && cvrai.acvr != null
                isAuditedById[cvrId] = isAudited
            }
            var idx = 0
            for (i in cvrIds.indices) {
                val audited = isAuditedById[cvrIds[i]]
                if (audited!!) {
                    idx = i + 1
                } else {
                    break
                }
            }
            LOGGER.debug(String.format("[auditedPrefixLength: isAuditedById=%s, apl=%d]", isAuditedById, idx))
            return idx
        }

        fun toResponseList(cvrs: List<CastVoteRecord>): List<CVRToAuditResponse> {
            val responses = mutableListOf<CVRToAuditResponse>()
            val uris = cvrs.map { it.bmiUri() }
            val bmis: List<BallotManifestInfo> = Persistence.locationFor(uris)
            val uriToLoc : Map<String, String> = bmis.map { it.uri to it.storageLocation }.toMap()

            var idx = 0
            for (cvr in cvrs) {
                val storageLocation = uriToLoc[cvr.bmiUri()]
                if (storageLocation == null) {
                    LOGGER.error("could not find a ballot manifest for cvr: " + cvr.getUri())
                    continue
                }
                responses.add(toResponse(idx, storageLocation, cvr))
                idx++
            }
            return responses
        }

        fun toResponse(idx: Int, storageLocation: String, cvr: CastVoteRecord): CVRToAuditResponse {
            return CVRToAuditResponse(
                idx,
                cvr.scannerId,
                cvr.batchId,
                cvr.recordId,
                cvr.imprintedId,
                cvr.cvrNumber,
                cvr.id,
                cvr.ballotType,
                storageLocation,
                cvr.auditFlag(),
                cvr.previouslyAudited(),
            )
        }
    }
}

class CVRToAuditResponse(
    val idx: Int,
    val scannerId: Int,
    val batchId: String,
    val recordId: Int,
    val imprintedId: String,
    val cvrNumber: Int,
    val dbId: Long,
    val ballotType: String,
    val storageLocation: String,
    val cvrAuditFlag: Boolean,
    val cvrPreviouslyAudited: Boolean,
) : Comparable<CVRToAuditResponse> {

    override fun compareTo(other: CVRToAuditResponse): Int {
        TODO("Not yet implemented")
    }

    //   /**
    //   * Compares this object to another.
    //   *
    //   * The sorting happens by the tuple
    //   * (storageLocation(), scannerID(), batchID(), recordID()) and will return a
    //   * negative, positive, or 0-valued result if this should come before, after,
    //   * or at the same point as the other object, respectively.
    //   *
    //   * @return int
    //   */
    //  @Override
    //  public int compareTo(final CVRToAuditResponse other) {
    //    final int storageLocation = NaturalOrderComparator.INSTANCE.compare(
    //        this.storageLocation(), other.storageLocation());
    //
    //    if (storageLocation != 0) {
    //      return storageLocation;
    //    }
    //
    //    final int scanner = this.scannerID() - other.scannerID();
    //
    //    if (scanner != 0) {
    //      return scanner;
    //    }
    //
    //    final int batch = NaturalOrderComparator.INSTANCE.compare(
    //        this.batchID(), other.batchID());
    //
    //    if (batch != 0) {
    //      return batch;
    //    }
    //
    //    return this.recordID() - other.recordID();
    //  }

}
