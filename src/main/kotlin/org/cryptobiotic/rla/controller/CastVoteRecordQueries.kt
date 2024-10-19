package org.cryptobiotic.rla.controller

import org.cryptobiotic.rla.model.CastVoteRecord

object CastVoteRecordQueries {

    fun activityReport(contestCVRIds: List<Long>): List<CastVoteRecord> {
        return emptyList()
    }

    fun resultsReport(contestCVRIds: List<Long>): List<CastVoteRecord> {
        return emptyList()
    }


    fun get(the_ids: List<Long>): List<CastVoteRecord> {
        /*

    List<CastVoteRecord> result = new ArrayList<>();

    if (the_ids.isEmpty()) {
        return result;
    }

    try {
        final Session s = Persistence.currentSession();
        final CriteriaBuilder cb = s.getCriteriaBuilder();
        final CriteriaQuery<CastVoteRecord> cq = cb.createQuery(CastVoteRecord.class);
        final Root<CastVoteRecord> root = cq.from(CastVoteRecord.class);
        final List<Predicate> conjuncts = new ArrayList<>();
        conjuncts.add(root.get("my_id").in(the_ids));
        cq.select(root).where(cb.and(conjuncts.toArray(new Predicate[conjuncts.size()])));
        final TypedQuery<CastVoteRecord> query = s.createQuery(cq);
        result = query.getResultList();
    } catch (final PersistenceException e) {
        Main.LOGGER.error(COULD_NOT_QUERY_DATABASE);
    }
    if (result.isEmpty()) {
        Main.LOGGER.debug("found no CVRs with ids " + the_ids);
        return new ArrayList<>();
    } else {
        Main.LOGGER.debug("found " + result.size() + "CVRs ");
    }

     */

        return emptyList();
    }

    /**
     * Find max revision looks for RCVRs that are old versions of a given CVR or ACVR
     **/
    fun maxRevision(cvr: CastVoteRecord): Long {
        /* final Session s = Persistence.currentSession();
        final Query q =
            s.createQuery("select max(revision) from CastVoteRecord cvr " +
                    " where revision is not null" + " and my_county_id = :countyId" +
                    " and my_imprinted_id = :imprintedId");

        q.setLong("countyId", cvr.countyID());
        q.setString("imprintedId", cvr.imprintedID());

        try {
            // Make sure not to return null, which causes errors later. 0L is the correct return value if
            // there are no prior revisions in the database.
            // Some documentation says that getSingleResult() never returns null, but it definitely does.
            final Long result = (Long) q.getSingleResult();
            return result == null ? 0L : result;
        } catch (final PersistenceException e) {
            // the DB had a problem!
            // TODO: Technically this should probably be an error code?
            // TODO: Otherwise there's no way to discern this from a CVR with no revisions?
            // VT: Agree. Since 0L is used for "valid; no prior revisions", suggest using a different value
            // here (-1?) or throwing an exception.
            return 0L;
        }

         */
        return 0L;
    }

    fun forceUpdate(cvr: CastVoteRecord): Long {
        /* final Session s = Persistence.currentSession();
        final Query q =
            s.createNativeQuery("update cast_vote_record " + "set record_type = :recordType, " +
                    " revision = :revision, " + " uri = :uri " + " where id = :id ");
        q.setParameter("recordType", cvr.recordType().toString());
        q.setParameter("revision", cvr.getRevision());
        q.setParameter("uri", cvr.getUri());
        q.setParameter("id", cvr.id());
        final int result = q.executeUpdate();
        return Long.valueOf(result);
         */
        return 0L
    }
}