package org.cryptobiotic.rla.persistence

import org.cryptobiotic.rla.model.BallotManifestInfo

object BallotManifestInfoQueries {
    /** Get the number of ballots for a given set of counties. */
    fun totalBallots(countyIds: Set<Long>): Long = 0L

    fun getMatching(the_county_ids: Set<Long>): Set<BallotManifestInfo> {
        return emptySet()

        /*

        try {
            final Session s = Persistence.currentSession();
            final CriteriaBuilder cb = s.getCriteriaBuilder();
            final CriteriaQuery<BallotManifestInfo> cq =
            cb.createQuery(BallotManifestInfo.class);
            final Root<BallotManifestInfo> root = cq.from(BallotManifestInfo.class);
            final List<Predicate> disjuncts = new ArrayList<Predicate>();
            for (final Long county_id : the_county_ids) {
                disjuncts.add(cb.equal(root.get(COUNTY_ID), county_id));
            }
            cq.select(root).where(cb.or(disjuncts.toArray(new Predicate[disjuncts.size()])));
            final TypedQuery<BallotManifestInfo> query = s.createQuery(cq);
            result.addAll(query.getResultList());
        } catch (final PersistenceException e) {
            Main.LOGGER.error("Exception when reading ballot manifests from database: " + e);
        }

        return result;

         */
    }
}