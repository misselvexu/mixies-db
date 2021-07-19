/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.db.mixing;

import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.Initializable;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Provides a lookup facility to the {@link EntityDescriptor descriptor} for an entity.
 */
@Register(classes = {Mixing.class, Initializable.class})
public class Mixing implements Initializable {

    /**
     * Contains the name of the default realm.
     */
    public static final String DEFAULT_REALM = "mixing";

    @SuppressWarnings("squid:S1192")
    @Explain("Constants have different semantics.")
    public static final Log LOG = Log.get("mixing");

    private static final String UPDATE_SCHEMA_MODE_SAFE = "safe";
    private static final String UPDATE_SCHEMA_MODE_ALL = "all";
    private static final String UPDATE_SCHEMA_MODE_OFF = "off";

    @ConfigValue("mixing.autoUpdateSchema")
    private String autoUpdateSchemaMode;

    private Map<Class<?>, EntityDescriptor> descriptorsByType = new HashMap<>();
    private Map<String, EntityDescriptor> descriptorsByName = new HashMap<>();

    @Part
    private GlobalContext globalContext;

    @Override
    public void initialize() throws Exception {
        descriptorsByType.clear();
        descriptorsByName.clear();
        loadEntities();
        loadNesteds();
        linkSchema();

        checkAutoUpdateSchemaMode();
    }

    private void checkAutoUpdateSchemaMode() {
        if (UPDATE_SCHEMA_MODE_OFF.equals(autoUpdateSchemaMode)) {
            LOG.INFO("Automatic schema updates are disabled on this node...");
            return;
        }

        if (UPDATE_SCHEMA_MODE_SAFE.equals(autoUpdateSchemaMode)) {
            LOG.INFO("Safe schema updates are enabled on this node...");
            return;
        }

        if (UPDATE_SCHEMA_MODE_ALL.equals(autoUpdateSchemaMode)) {
            LOG.WARN("All (including some with possible dataloss) schema updates are enabled on this node...");
            return;
        }

        LOG.WARN(
                "'mixing.autoUpdateSchema' contains an invalid value (%s) - please select either off, safe, all - Using 'off' as fallback...",
                autoUpdateSchemaMode);
    }

    private void linkSchema() {
        descriptorsByType.values().forEach(EntityDescriptor::link);
        descriptorsByType.values().forEach(EntityDescriptor::finishSetup);
    }

    private void loadEntities() {
        for (Class<? extends BaseEntity<?>> mappableType : EntityLoadAction.getMappableClasses()) {
            EntityDescriptor descriptor = new EntityDescriptor(mappableType);
            descriptor.initialize();
            descriptorsByType.put(mappableType, descriptor);
            String typeName = getNameForType(descriptor.getType());
            EntityDescriptor conflictingDescriptor = descriptorsByName.get(typeName);
            if (conflictingDescriptor != null) {
                Exceptions.handle()
                          .to(LOG)
                          .withSystemErrorMessage(
                                  "Cannot register mapping descriptor for '%s' as '%s' as this name is already taken by '%s'",
                                  mappableType.getName(),
                                  typeName,
                                  conflictingDescriptor.getType().getName())
                          .handle();
            } else {
                descriptorsByName.put(typeName, descriptor);
            }
        }
    }

    private void loadNesteds() {
        for (Class<? extends Nested> mappableType : NestedLoadAction.getMappableClasses()) {
            EntityDescriptor descriptor = new EntityDescriptor(mappableType);
            descriptor.initialize();
            descriptorsByType.put(mappableType, descriptor);
        }
    }

    /**
     * Each entity type can be addressed by its class or by a unique name, which is its simple class name in upper
     * case.
     *
     * @param type the entity class to generate the type name for
     * @return the type name of the given type
     */
    @Nonnull
    public static String getNameForType(@Nonnull Class<?> type) {
        return type.getSimpleName().toUpperCase();
    }

    /**
     * Computes the unique name of an entity based on its descriptor type and id.
     *
     * @param typeName the name of the entity type
     * @param id       the id of the entity
     * @return a unique name consisting of the typeName and id
     */
    @Nonnull
    public static String getUniqueName(@Nonnull String typeName, Object id) {
        return typeName + "-" + id;
    }

    /**
     * Computes the unique name of an entity based on its type and id.
     *
     * @param type the entity class to generate the type name for
     * @param id   the id of the entity
     * @return a unique name consisting of the typeName and id
     */
    @Nonnull
    public static String getUniqueName(@Nonnull Class<?> type, Object id) {
        return getNameForType(type) + "-" + id;
    }

    /**
     * Splits a unique name into the descriptor type and id.
     *
     * @param uniqueName the unique name of an entity.
     * @return the type and id of the entity as tuple
     * @see #getUniqueName(String, Object)
     */
    @Nonnull
    public static Tuple<String, String> splitUniqueName(@Nullable String uniqueName) {
        return Strings.split(uniqueName, "-");
    }

    /**
     * Returns the descriptor of the given entity class.
     *
     * @param aClass the entity class
     * @return the descriptor of the given entity class
     */
    public EntityDescriptor getDescriptor(Class<?> aClass) {
        EntityDescriptor ed = descriptorsByType.get(aClass);
        if (ed == null) {
            throw Exceptions.handle()
                            .to(LOG)
                            .withSystemErrorMessage("The class '%s' is not a managed entity!", aClass.getName())
                            .handle();
        }

        return ed;
    }

    /**
     * Returns the descriptor for the given entity type.
     *
     * @param aTypeName a {@link #getNameForType(Class)} of an entity
     * @return the descriptor for the given type name
     * @throws sirius.kernel.health.HandledException if no matching descriptor exists
     */
    public EntityDescriptor getDescriptor(String aTypeName) {
        Optional<EntityDescriptor> ed = findDescriptor(aTypeName);
        if (ed.isEmpty()) {
            throw Exceptions.handle()
                            .to(LOG)
                            .withSystemErrorMessage("The name '%s' is not a known entity!", aTypeName)
                            .handle();
        }

        return ed.get();
    }

    /**
     * Returns the descriptor for the given entity type.
     *
     * @param aTypeName a {@link #getNameForType(Class)} of an entity
     * @return the descriptor for the given type name as optional
     */
    public Optional<EntityDescriptor> findDescriptor(String aTypeName) {
        return Optional.ofNullable(descriptorsByName.get(aTypeName));
    }

    /**
     * Returns the descriptor of the given entity class.
     *
     * @param aClass the entity class
     * @return the descriptor of the given entity class as optional
     */
    public Optional<EntityDescriptor> findDescriptor(Class<?> aClass) {
        return Optional.ofNullable(descriptorsByType.get(aClass));
    }

    /**
     * Returns all known descriptors.
     *
     * @return an unmodifyable list of all known descriptors
     */
    public Collection<EntityDescriptor> getDescriptors() {
        return descriptorsByType.values();
    }

    /**
     * Determines if the database specific schema synchronization should execute safe updates during the startup.
     *
     * @return <tt>true</tt> if actions with no dataloss should be executed during startup, <tt>false</tt> otherwise
     */
    public boolean shouldExecuteSafeSchemaChanges() {
        return UPDATE_SCHEMA_MODE_SAFE.equals(autoUpdateSchemaMode) || shouldExecuteUnsafeSchemaChanges();
    }

    /**
     * Determines if the database specific schema synchronization should execute ALL updates during the startup.
     *
     * @return <tt>true</tt> if actions, even ones with dataloss, should be executed during startup,
     * <tt>false</tt> otherwise
     */
    public boolean shouldExecuteUnsafeSchemaChanges() {
        return UPDATE_SCHEMA_MODE_ALL.equals(autoUpdateSchemaMode);
    }
}
