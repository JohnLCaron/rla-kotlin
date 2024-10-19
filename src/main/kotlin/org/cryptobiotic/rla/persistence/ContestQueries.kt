package org.cryptobiotic.rla.persistence

import org.cryptobiotic.rla.model.Contest
import org.cryptobiotic.rla.model.County
import org.cryptobiotic.rla.persistence.Persistence.flush
import java.util.ArrayList
import java.util.HashSet

object ContestQueries {
    /**
     * Gets contests that are in the specified set of counties.
     *
     * @param the_counties The counties.
     * @return the matching contests, or null if the query fails.
     */
    fun forCounties(the_counties: Set<County>): List<Contest> {
        var result: MutableList<Contest?>? = null

        /*
        try {
            val s: Session = Persistence.currentSession()
            val cb: CriteriaBuilder = s.getCriteriaBuilder()
            val cq: CriteriaQuery<Contest?> = cb.createQuery(Contest::class.java)
            val root: Root<Contest?> = cq.from(Contest::class.java)
            val disjuncts: MutableList<Predicate?> = ArrayList<Predicate?>()
            for (county in the_counties) {
                disjuncts.add(cb.equal(root.get("my_county"), county))
            }
            cq.select(root)
            cq.where(cb.or(disjuncts.toArray(kotlin.arrayOfNulls<Predicate>(disjuncts.size))))
            cq.orderBy(
                cb.asc(root.get("my_county").get("my_id")),
                cb.asc(root.get("my_sequence_number"))
            )
            val query: TypedQuery<Contest?> = s.createQuery(cq)
            result = query.getResultList()
        } catch (e: PersistenceException) {
            Main.LOGGER.error("Exception when reading contests from database: " + e)
        }
*/
        return emptyList()
    }

    /**
     * Gets contests that are in the specified county.
     *
     * @param the_county The county.
     * @return the matching contests, or null if the query fails.
     */
    fun forCounty(the_county: County): Set<Contest> {
        var result = mutableSetOf<Contest>()
/*
        try {
            val s: Session = Persistence.currentSession()
            val cb: CriteriaBuilder = s.getCriteriaBuilder()
            val cq: CriteriaQuery<Contest?> = cb.createQuery(Contest::class.java)
            val root: Root<Contest?> = cq.from(Contest::class.java)
            cq.select(root)
            cq.where(cb.equal(root.get("my_county"), the_county))
            cq.orderBy(cb.asc(root.get("my_sequence_number")))
            val query: TypedQuery<Contest?> = s.createQuery(cq)
            result = HashSet<Contest?>(query.getResultList())
        } catch (e: PersistenceException) {
            Main.LOGGER.error("Exception when reading contests from database: " + e)
        }

 */

        return result
    }

    /**
     * Deletes all the contests for the county with the specified ID.
     *
     * @param the_county_id The county ID.
     * @return the number of contests that were deleted, or -1 if an error occured.
     */
    fun deleteForCounty(the_county_id: Long): Int {
        val wtf: County? = Persistence.getByID(the_county_id, County::class.java)
        val contests  = forCounty(wtf!!)

        var retval = -1
        if (contests != null) {
            retval = contests.size
            for (c in contests) {
                Persistence.delete(c)
            }
        }
        flush()
        return retval
    }
}

