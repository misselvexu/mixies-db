/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.db.es;

import com.alibaba.fastjson.JSONObject;
import sirius.db.es.annotations.ESOption;
import sirius.db.es.annotations.IndexMode;

import java.util.Optional;
import java.util.function.Function;

/**
 * Describes the mapping to use for a {@link sirius.db.mixing.Property}.
 * <p>
 * Note that all properties used with {@link Elastic} must implement this interface to ensure proper mapping generation.
 */
public interface ESPropertyInfo {

    /**
     * Transfers the given option which (via {@link IndexMode}) to the mapping.
     *
     * @param key          the mapping key to use
     * @param annotation   the annotation wrapped as optional
     * @param mapper       the lambda which selects the value to transfer
     * @param mapping      the target mapping to fill
     */
    default void transferOption(String key,
                                Optional<IndexMode> annotation,
                                Function<IndexMode, ESOption> mapper,
                                JSONObject mapping) {
        ESOption option = annotation.map(mapper).orElse(ESOption.ES_DEFAULT);

        if (option != ESOption.ES_DEFAULT) {
            mapping.put(key, option.toString());
        }
    }

    /**
     * Creates the mapping description for this property into the given JSON.
     *
     * @param description the target JSON to fill
     */
    void describeProperty(JSONObject description);
}
