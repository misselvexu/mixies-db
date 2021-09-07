/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.db.mixing.properties;

import sirius.db.jdbc.schema.SQLPropertyInfo;
import sirius.db.jdbc.schema.Table;
import sirius.db.jdbc.schema.TableColumn;
import sirius.db.mixing.AccessPath;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixable;
import sirius.db.mixing.Property;
import sirius.db.mixing.PropertyFactory;
import sirius.db.mixing.annotations.DefaultValue;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;

import java.lang.reflect.Field;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.function.Consumer;

/**
 * Represents an {@link LocalTime} field within a {@link Mixable}.
 */
public class LocalTimeProperty extends Property implements SQLPropertyInfo {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(EntityDescriptor descriptor, Field field) {
            return LocalTime.class.equals(field.getType());
        }

        @Override
        public void create(EntityDescriptor descriptor,
                           AccessPath accessPath,
                           Field field,
                           Consumer<Property> propertyConsumer) {
            propertyConsumer.accept(new LocalTimeProperty(descriptor, accessPath, field));
        }
    }

    LocalTimeProperty(EntityDescriptor descriptor, AccessPath accessPath, Field field) {
        super(descriptor, accessPath, field);
    }

    @Override
    protected Object transformValueFromImport(Value value) {
        if (value.is(LocalTime.class)) {
            return value.get();
        }
        if (value.is(LocalDateTime.class)) {
            return value.get(LocalDateTime.class, null).toLocalTime();
        }
        return NLS.parseMachineString(LocalTime.class, value.asString());
    }

    @Override
    public Object transformValue(Value value) {
        if (value.is(LocalTime.class)) {
            return value.get();
        }
        if (value.is(LocalDateTime.class)) {
            return value.get(LocalDateTime.class, null).toLocalTime();
        }
        return NLS.parseUserString(LocalTime.class, value.asString());
    }

    @Override
    protected Object transformFromJDBC(Value data) {
        Object object = data.get();

        if (object == null) {
            return null;
        }
        return ((Time) object).toLocalTime();
    }

    @Override
    protected Object transformToJDBC(Object object) {
        return object == null ? null : Time.valueOf((LocalTime) object);
    }

    @Override
    public void contributeToTable(Table table) {
        table.getColumns().add(new TableColumn(this, Types.TIME));
    }

    /**
     * Overrides the default behavior, as the initial value of a temporal property is not suited for a default.
     * <p>
     * The initial value will commonly be a temporal value and thus not a constant.
     * Therefore we ignore the initial value here, and only check for a {@link DefaultValue} annotation on the field.
     */
    @Override
    protected void determineDefaultValue() {
        DefaultValue defaultValueAnnotation = field.getAnnotation(DefaultValue.class);
        if (defaultValueAnnotation != null) {
            this.defaultValue = Value.of(transformValueFromImport(Value.of(defaultValueAnnotation.value())));
        }
    }
}
