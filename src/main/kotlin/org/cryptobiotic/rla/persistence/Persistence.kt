package org.cryptobiotic.rla.persistence

import org.cryptobiotic.rla.model.BallotManifestInfo

object Persistence {
    val db = mutableMapOf<String, HasId>()

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
     * Gets all the entities of the specified class. This method must be called
     * within a transaction.
     *
     * @param the_class The class.
     * @return a list containing all the entities of the_class.
     * @exception IllegalStateException if no database is available or no
     * transaction is running.
     */
    fun <T> getAll(the_class: Class<T>): List<T> {
        checkForRunningTransaction();

        /*
        final List<T> result = new ArrayList<>();

        try {
            final Session s = Persistence.currentSession();
            final CriteriaBuilder cb = s.getCriteriaBuilder();
            final CriteriaQuery<T> cq = cb.createQuery(the_class);
            final Root<T> root = cq.from(the_class);
            cq.select(root);
            final TypedQuery<T> query = s.createQuery(cq);
            result.addAll(query.getResultList());
        } catch (final PersistenceException e) {
            Main.LOGGER.error("could not query database");
        } */

        return emptyList();
    }

    /**
     * Gets the entity in the current session that has the specified ID and class.
     *
     * @param the_id The ID.
     * @param the_class The class.
     * @return the result entity, or null if no such entity exists.
     */
    fun  <T> getByID(the_id: Any, the_class: Class<T> ): T? {
        val key = the_class.name + "#" + the_id
        return db[key] as T?
    }

    fun saveOrUpdate(peristObj: HasId ): Boolean {
        val key = peristObj.javaClass.name + "#" + peristObj.id()
        db[key] = peristObj
        return true
    }

    /**
     * Saves the specified object in persistent storage. This will cause an
     * exception if there is already an object in persistent storage with the same
     * class and ID.
     */
    fun save(peristObj: HasId) {
        val key = peristObj.javaClass.name + "#" + peristObj.id()
        if (db[key] != null) {
            throw RuntimeException("Duplicate key '$key'")
        }
        db[key] = peristObj
    }

    fun flush() {}

    fun checkForRunningTransaction() {}

    /**
     * Deletes the specified object from persistent storage, if it exists. This
     * method must be called within a transaction.
     *
     * @param the_object The object to delete.
     * @return true if the deletion was successful, false otherwise (if
     * the object did not exist, false is returned).
     * @exception IllegalStateException if no database is available or no
     * transaction is running.
     */
    fun delete(the_object: Any): Boolean {
        return true;
    }
}

interface HasId {
    fun id(): Long
}