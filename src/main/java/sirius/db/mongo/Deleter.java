/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.db.mongo;

import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.result.DeleteResult;
import sirius.kernel.commons.Watch;
import sirius.kernel.health.Microtiming;

/**
 * Fluent builder to build a delete statement.
 */
public class Deleter extends QueryBuilder<Deleter> {

    protected Deleter(Mongo mongo, String database) {
        super(mongo, database);
    }

    /**
     * Executes the delete statement on the given collection.
     *
     * @param type the type of entities to delete
     * @return the result of the delete operation
     */
    public DeleteResult singleFrom(Class<?> type) {
        return singleFrom(getRelationName(type));
    }

    /**
     * Executes the delete statement on the given collection.
     *
     * @param collection the name of the collection to delete documents from
     * @return the result of the delete operation
     */
    public DeleteResult singleFrom(String collection) {
        Watch w = Watch.start();
        try {
            if (Mongo.LOG.isFINE()) {
                Mongo.LOG.FINE("DELETE: %s\nFilter: %s", collection, filterObject);
            }

            return mongo.db(database)
                        .getCollection(collection)
                        .deleteOne(filterObject, new DeleteOptions().collation(mongo.determineCollation()));
        } finally {
            mongo.callDuration.addValue(w.elapsedMillis());
            if (Microtiming.isEnabled()) {
                w.submitMicroTiming("mongo", "DELETE - " + collection + ": " + filterObject.keySet());
            }
            traceIfRequired(collection, w);
        }
    }

    /**
     * Executes the delete statement on the given collection.
     *
     * @param type the type of entities to delete
     * @return the result of the delete operation
     */
    public DeleteResult manyFrom(Class<?> type) {
        return manyFrom(getRelationName(type));
    }

    /**
     * Executes the delete statement on the given collection.
     *
     * @param collection the name of the collection to delete documents from
     * @return the result of the delete operation
     */
    public DeleteResult manyFrom(String collection) {
        Watch w = Watch.start();
        try {
            if (Mongo.LOG.isFINE()) {
                Mongo.LOG.FINE("DELETE: %s\nFilter: %s", collection, filterObject);
            }

            return mongo.db(database)
                        .getCollection(collection)
                        .deleteMany(filterObject, new DeleteOptions().collation(mongo.determineCollation()));
        } finally {
            mongo.callDuration.addValue(w.elapsedMillis());
            if (Microtiming.isEnabled()) {
                w.submitMicroTiming("mongo", "DELETE - " + collection + ": " + filterObject.keySet());
            }
            traceIfRequired(collection, w);
        }
    }
}
