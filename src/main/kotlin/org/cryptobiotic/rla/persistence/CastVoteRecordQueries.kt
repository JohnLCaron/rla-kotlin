package org.cryptobiotic.rla.persistence

import org.cryptobiotic.rla.model.CastVoteRecord
import org.cryptobiotic.rla.model.Tribute

object CastVoteRecordQueries {

    /** select cast_vote_record where uri in :uris **/
    fun atPosition(tributes: List<Tribute> ): List<CastVoteRecord> {
        if (tributes.isEmpty()) {
            return emptyList()
        }

        // TODO persistence
        // final List<String> uris = tributes.stream().map(Persistence::persist).map(t -> t.getUri())
        val uris = tributes.map { t -> t.uri }

        /*
        final Session s = Persistence.currentSession();
        final Query<CastVoteRecord> q = s.createQuery("select cvr from CastVoteRecord cvr " + " where uri in (:uris) ");
        java.util.Spliterator<String> split = uris.stream().spliterator();

        final List<CastVoteRecord> results = new ArrayList<>(uris.size());

        while (true) {

            List<String> chunk = new ArrayList<>(_chunkOf1000);
            for (int i = 0; i < _chunkOf1000 && split.tryAdvance(chunk::add); i++)
            ;
            if (chunk.isEmpty())
                break;
            q.setParameter("uris", chunk);
            final List<CastVoteRecord> tempResults = q.getResultList();
            results.addAll(tempResults);
            Main.LOGGER.info(MessageFormat
                .format("Total URIs {0} chunk size {1} tempResults size {2} results size {3}",
                    uris.size(), chunk.size(), tempResults.size(), results.size()));
        }

        final Set<String> foundUris =
        results.stream().map(cvr -> (String) cvr.getUri()).collect(Collectors.toSet());

        final Set<CastVoteRecord> phantomRecords =
        tributes.stream().filter(distinctByKey((Tribute t) -> {
            return t.getUri();
        }))
        // is it faster to let the db do this with an except query?
        .filter(t -> !foundUris.contains(t.getUri())).map(CastVoteRecordQueries::phantomRecord)
        .map(Persistence::persist).collect(Collectors.toSet());

        results.addAll(phantomRecords);

        // this is a dummy list so we can add a cvr at a particular position(that of
        // the tributes uris)
        final List<CastVoteRecord> randomOrder =
        new ArrayList<CastVoteRecord>(Collections.nCopies(uris.size(), null));

        // line the cvrs back up into the random order
        for (final CastVoteRecord cvr : results) {
            int index = 0;
            for (final String uri : uris) {
            if (uri.equals(cvr.getUri())) {
                randomOrder.add(index, cvr);
            }
            index++;
        }
        }

        final List<CastVoteRecord> returnList =
        randomOrder.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (returnList.size() != uris.size()) {
            // TODO: I'm pretty sure this code is unreachable, since any time |URIs| < |return|, we
            // TODO: make phantoms until they equal. Maybe take this out?
            // we got a problem here
            Main.LOGGER
                .error("something went wrong with atPosition - returnList.size() != uris.size()");
        }
        return returnList;
         */
        return emptyList()

    }

}