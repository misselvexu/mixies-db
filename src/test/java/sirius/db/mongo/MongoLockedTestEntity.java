/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.db.mongo;

import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Versioned;

@Versioned
public class MongoLockedTestEntity extends MongoEntity {

    public static final Mapping VALUE = Mapping.named("value");
    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
