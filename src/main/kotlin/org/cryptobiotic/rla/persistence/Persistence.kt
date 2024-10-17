package org.cryptobiotic.rla.persistence

import org.cryptobiotic.rla.model.BallotManifestInfo

object Persistence {

    // apparently have to query the persistence layer
    //   public static List<BallotManifestInfo> BallotManifestInfoQueries.locationFor(final Set<String> uris) {
    /** select ballot_manifest_info where uri in :uris **/
    fun locationFor(uris: List<String>): List<BallotManifestInfo> {
        if (uris.isEmpty()) {
            return emptyList();
        }
        /* val session: Session = Persistence.currentSession();
        final CriteriaBuilder cb = s.getCriteriaBuilder();
        final CriteriaQuery<BallotManifestInfo> cq = cb.createQuery(BallotManifestInfo.class);
        final Root<BallotManifestInfo> root = cq.from(BallotManifestInfo.class);
        cq.where(root.get("uri").in(uris));
        final TypedQuery<BallotManifestInfo> query = s.createQuery(cq);
        return query.getResultList(); */
        return emptyList()
    }

    /**
     * Gets the entity in the current session that has the specified ID and class.
     * This method must be called within a transaction.
     *
     * @param the_id The ID.
     * @param the_class The class.
     * @return the result entity, or null if no such entity exists.
     * @exception IllegalStateException if no database is available or no
     * transaction is running.
     */
    fun  <T> getByID(the_id: Any, the_class: Class<T> ): T? {
        checkForRunningTransaction();

        val result: T? = null;
        /* try {
            result = currentSession().get(the_class, the_id);
        } catch (final PersistenceException e) {
            Main.LOGGER.error("exception when searching for " + the_class + "/" + the_id +
                    ": " + e);
        } */
        return result;
    }

    fun saveOrUpdate(the_object: Any ): Boolean {
        checkForRunningTransaction();

        var result = true;
/*
        try {
            currentSession().saveOrUpdate(the_object);
        } catch (final PersistenceException e) {
            Main.LOGGER.error("could not save/update object " + the_object + ": " + e);
            result = false;
        }

 */

        return result;
    }

    /**
     * Saves the specified object in persistent storage. This will cause an
     * exception if there is already an object in persistent storage with the same
     * class and ID. This method must be called within a transaction.
     *
     * @param the_object The object to save.
     * @exception IllegalStateException if no database is available or no
     * transaction is running.
     * @exception PersistenceException if the object cannot be saved.
     */
    fun save(the_object: Any) {
        checkForRunningTransaction();
       // currentSession().save(the_object);
    }

    fun flush() {}

    fun checkForRunningTransaction() {}
}