package org.cryptobiotic.rla.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rla.model.BallotManifestInfo
import org.cryptobiotic.rla.model.CVRAuditInfo
import org.cryptobiotic.rla.model.CVRContestInfo
import org.cryptobiotic.rla.model.CastVoteRecord
import org.cryptobiotic.rla.model.ContestResult
import org.cryptobiotic.rla.model.Tribute
import org.cryptobiotic.rla.persistence.Persistence

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

    class Selection {
        val segments: MutableMap<Long, Segment> = HashMap()
        var domainSize = Int.MIN_VALUE
        val generatedNumbers: MutableList<Int> = ArrayList()
        var contestName: String? = null
        var contestResult: ContestResult? = null

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
                rand, randSequencePosition, this.contestName!!
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
            return contestResult!!.countyIDs()
                .map { forCounty(it) }
                .filter { it != null}
                .map { it!!.cvrIds }
                .flatten()
        }

        override fun toString(): String {
            return kotlin.String.format(
                "[Selection contestName=%s generatedNumbers=%s domainSize=%s]",
                contestName, generatedNumbers, domainSize
            )
        }
    }

    class MissingBallotManifestException internal constructor(msg: String?) : java.lang.RuntimeException(msg) {}

    companion object {
        val LOGGER = KotlinLogging.logger("BallotSelection")

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

        //   public static void selectTributes(final Selection selection,
        //                                    final Set<Long> countyIds,
        //                                    final MATCHINGQ queryMatching) {
        //
        //    final Set<BallotManifestInfo> contestBmis = queryMatching.apply(countyIds);
        //    selectTributes(selection, countyIds, contestBmis);
        //  }
        fun selectTributes(selection: Selection, countyIds: Set<Long>,  queryMatching: (Set<Long>) -> Set<BallotManifestInfo> ) {
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
        //  public static Selection resolveSelection(final Selection selection) {
        //
        //    selection.allSegments().forEach(segment -> {
        //        final List<CastVoteRecord> cvrs =
        //          dedupePhantomBallots(CastVoteRecordQueries.atPosition(segment.tributes));
        //
        //        segment.addCvrs(cvrs);
        //        segment.addCvrIds(cvrs); // keep raw data separate
        //      });
        //    LOGGER.debug(String.format("[resolveSelection: selection=%s, combinedSegments=%s]",
        //                               selection.segments,
        //                               Selection.combineSegments(selection.allSegments()).cvrIds));
        //    return selection;
        //  }
        /*
        fun resolveSelection(selection: Selection): Selection {
            for (segment in selection.segments.values) {
                val cvrs: List<CastVoteRecord> =
                    dedupePhantomBallots(CastVoteRecordQueries.atPosition(segment.tributes))
                segment.addCvrs(cvrs)
                segment.addCvrIds(cvrs)
            }
            LOGGER.debug(
                kotlin.String.format(
                    "[resolveSelection: selection=%s, combinedSegments=%s]",
                    selection.segments, combineSegments(selection.allSegments()).cvrIds
                )
            )
            return selection
        }

         */

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
