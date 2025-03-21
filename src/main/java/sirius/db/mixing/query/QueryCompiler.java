/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.db.mixing.query;

import parsii.tokenizer.LookaheadReader;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Property;
import sirius.db.mixing.properties.BaseEntityRefListProperty;
import sirius.db.mixing.properties.BaseEntityRefProperty;
import sirius.db.mixing.properties.LocalDateProperty;
import sirius.db.mixing.properties.LocalDateTimeProperty;
import sirius.db.mixing.properties.LocalTimeProperty;
import sirius.db.mixing.query.constraints.Constraint;
import sirius.db.mixing.query.constraints.FilterFactory;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a query and compiles it into a {@link Constraint}.
 * <p>
 * By default, the compiler will translate all tokens into appropriate constraints against the
 * given list of {@link #searchFields}. Also it supports boolean operations like AND and OR as
 * well as braces. Additionally operations on fields are compiled:
 * <ul>
 *     <li>
 *         <tt>FIELD:value</tt> is transformed into an equals constraint
 *     </li>
 *     <li>
 *         <tt>!FIELD:value</tt> is transformed into a not equals constraint
 *     </li>
 *     <li>
 *         <tt>FIELD&gt;value</tt> is transformed into a relational constraint
 *        just like the other relational operators.
 *     </li>
 *     <li>
 *         <tt>FIELD:-</tt> is transformed into a null/empty constraint
 *     </li>
 *     <li>
 *         <tt>!FIELD:-</tt> is transformed into an "is filled" constraint
 *     </li>
 * </ul>
 * <p>
 * If the underlying mapper allowes it (e.g. {@link sirius.db.jdbc.OMA}), a field can be a
 * path like <tt>partent.tenant.name</tt> and will be transformed into an appropriate JOIN
 * on the relevant tables in order to create the constraint.
 * <p>
 * Temporal fields like {@link LocalTimeProperty} and {@link LocalDateTime} support a shorthand
 * delta syntax like <tt>-5h</tt> or <tt>3d</tt> where the value is added or subtracted from
 * <tt>now</tt>. Valid unit are <b>m</b> for minutes, <b>h</b> for hours and <b>d</b> for days.
 * Note that the value can also be an expression as supported by the {@link sirius.kernel.commons.AdvancedDateParser}.
 * <p>
 * Values which contain whitespaces or which should be matched exactly, can be put into quotes (<tt>"</tt>).
 * <p>
 * Note that this class can also be subclasses in order to generate custom constraints or to handle virtual
 * fields.
 * <p>
 * This is implemented as a simple recursive descending parser (which forms the upper section of the class). The main
 * task is to determine how to transform tokens into constraints as we're never 100% sure if a user got an operation
 * wrong or if this is simply a complex seach term (e.g. value:XX). Therefore we provide some ways of recovering or
 * fallbacks when trying to produce a meaningful query.
 *
 * @param <C> the type of constraints generated by this compiler
 */
public abstract class QueryCompiler<C extends Constraint> {

    /**
     * Matches delta expressions for temporal fields.
     */
    private static final Pattern DELTA_EXPRESSION = Pattern.compile("([+-]?)(\\d+)([dhm])");

    /**
     * Represents a value parsed for a field.
     */
    protected static class FieldValue {
        Object value;
        boolean exact;

        protected FieldValue(Object value, boolean exact) {
            this.value = value;
            this.exact = exact;
        }

        /**
         * Returns the value itself
         *
         * @return the parsed (and tranformed) value
         */
        public Object getValue() {
            return value;
        }

        /**
         * Determins if the value must not be processed any further.
         *
         * @return <tt>true</tt> if the value was explicitely given (e.g. in quotes), <tt>false</tt> otherwise
         */
        public boolean isExact() {
            return exact;
        }
    }

    protected FilterFactory<C> factory;
    protected EntityDescriptor descriptor;
    protected final List<QueryField> searchFields;
    protected final LookaheadReader reader;
    protected boolean debugging;

    @Part
    protected static GlobalContext ctx;

    /**
     * Creates a new instance for the given factory entity and query.
     *
     * @param factory      the factory used to create constraints
     * @param descriptor   the descriptor of entities being queried
     * @param query        the query to compile
     * @param searchFields the default search fields to query
     */
    protected QueryCompiler(FilterFactory<C> factory,
                            EntityDescriptor descriptor,
                            String query,
                            List<QueryField> searchFields) {
        this.factory = factory;
        this.descriptor = descriptor;
        this.searchFields = Collections.unmodifiableList(searchFields);
        this.reader = new LookaheadReader(new StringReader(query));
    }

    private boolean skipWhitespace() {
        boolean skipped = false;
        while (reader.current().isWhitespace()) {
            reader.consume();
            skipped = true;
        }

        return skipped;
    }

    /**
     * Compiles the query into a constraint.
     *
     * @return the compiled constraint
     */
    @Nullable
    public C compile() {
        skipWhitespace();
        if (reader.current().is('?') && reader.next().is('?')) {
            this.debugging = true;
            reader.consume(2);
        }

        return parseOR();
    }

    /**
     * Determines if the compiler was put into "debug mode".
     * <p>
     * This can switched on by putting "??" in front of a query. Subsequent callers of the compiler can then
     * log the parsed query to help when tracing down problems.
     *
     * @return <tt>true</tt> if the compiler was put into debug mode, <tt>false</tt> otherwise
     */
    public boolean isDebugging() {
        return debugging;
    }

    private C parseOR() {
        List<C> constraints = new ArrayList<>();
        while (!reader.current().isEndOfInput() && !reader.current().is(')')) {
            C inner = parseAND();
            if (inner != null) {
                constraints.add(inner);
            }
            if (!isAtOR()) {
                break;
            } else {
                reader.consume(2);
            }
        }

        if (constraints.isEmpty()) {
            return null;
        } else {
            return factory.or(constraints);
        }
    }

    private boolean isAtOR() {
        return reader.current().is('o', 'O') && reader.next().is('r', 'R');
    }

    private C parseAND() {
        List<C> constraints = new ArrayList<>();
        while (!reader.current().isEndOfInput() && !reader.current().is(')')) {
            C inner = parseExpression();
            if (inner != null) {
                constraints.add(inner);
            }
            skipWhitespace();
            if (isAtOR()) {
                break;
            }
            if (isAtAND()) {
                reader.consume(3);
            }
            if (isAtBinaryAND()) {
                reader.consume(2);
            }
        }

        if (constraints.isEmpty()) {
            return null;
        } else {
            return factory.and(constraints);
        }
    }

    private boolean isAtBinaryAND() {
        return reader.current().is('&') && reader.next().is('&');
    }

    private boolean isAtAND() {
        return reader.current().is('a', 'A') && reader.next().is('n', 'N') && reader.next(2).is('d', 'D');
    }

    private C parseExpression() {
        skipWhitespace();

        if (reader.current().is('!') || reader.current().is('-')) {
            reader.consume();
            return factory.not(parseExpression());
        }

        if (reader.current().is('(')) {
            return parseBrackets();
        }

        if (reader.current().is('|') && reader.next().is('|')) {
            return parseTag();
        }

        while (!reader.current().isEndOfInput() && !continueToken(false)) {
            reader.consume();
        }

        if (reader.current().isEndOfInput()) {
            return null;
        }

        FieldValue token = readToken();
        boolean skipped = skipWhitespace();
        if (isAtOperator()) {
            String field = token.getValue().toString();
            Tuple<Mapping, Property> mappingAndProperty = resolveProperty(field);

            return compileConstraint(mappingAndProperty, token, skipped);
        }

        if (searchFields.isEmpty()) {
            throw new IllegalArgumentException(Strings.apply(
                    "Cannot process token: '%s' (Operator is missing and no search fields are present).",
                    token.getValue()));
        }

        return compileDefaultSearch(searchFields, token);
    }

    private C parseBrackets() {
        reader.consume();
        C inner = parseOR();
        if (reader.current().is(')')) {
            reader.consume();
        }

        return inner;
    }

    private boolean isAtOperator() {
        if (reader.current().is('=', ':')) {
            return true;
        }

        if (reader.current().is('!') && reader.next().is('=')) {
            return true;
        }

        return reader.current().is('<', '>');
    }

    private FieldValue readToken() {
        StringBuilder token = new StringBuilder();
        boolean inQuotes = reader.current().is('"');
        if (inQuotes) {
            reader.consume();
        }
        while (continueToken(inQuotes)) {
            if (reader.current().is('\\')) {
                reader.consume();
            }
            token.append(reader.consume());
        }
        if (inQuotes && reader.current().is('"')) {
            reader.consume();
        }
        return new FieldValue(token.toString(), inQuotes);
    }

    private boolean continueToken(boolean inQuotes) {
        if (reader.current().isEndOfInput()) {
            return false;
        }

        if (inQuotes) {
            return !reader.current().is('"');
        }

        return !reader.current().is(')', ':') && !reader.current().isWhitespace() && !isAtOperator();
    }

    /**
     * Resolves the given path into a property.
     * <p>
     * If the path contains ".", it will also yield an appropriate mapping which represents the necessary joins.
     *
     * @param propertyPath the path to parse
     * @return a tuple consisting of the mapping to use and the target property. Returns <tt>null</tt> if the property
     * cannot be resolved
     */
    @Nullable
    protected Tuple<Mapping, Property> resolveProperty(String propertyPath) {
        return resolveProperty(descriptor, propertyPath, null);
    }

    @Nullable
    protected Tuple<Mapping, Property> resolveProperty(EntityDescriptor effectiveDescriptor,
                                                       String propertyPath,
                                                       Mapping path) {
        Tuple<String, String> splitPath = Strings.split(propertyPath, ".");
        Property property = effectiveDescriptor.findProperty(splitPath.getFirst());
        if (property == null) {
            return null;
        }

        Mapping effectiveMapping = Mapping.named(property.getPropertyName());
        if (path != null) {
            effectiveMapping = path.join(effectiveMapping);
        }

        if (Strings.isEmpty(splitPath.getSecond())) {
            return Tuple.create(effectiveMapping, property);
        }

        return resolvedNestedProperty(property, effectiveMapping, splitPath.getSecond());
    }

    /**
     * Translates a nested property (e.g. "foo.bar") into a proper mapping understood by
     * {@link #compileConstraint(Tuple, FieldValue, boolean)}.
     *
     * @param property   the root property being queried on
     * @param mapping    the mapping to obtain the root property
     * @param nestedPath the nested path within the root property
     * @return a tuple of the effective mapping and the resulting property to access or <tt>null</tt> if the
     * path could not be resolved
     */
    protected Tuple<Mapping, Property> resolvedNestedProperty(Property property, Mapping mapping, String nestedPath) {
        return null;
    }

    /**
     * Compiles a constraint after a property has been resolved and an operator was detected.
     * <p>
     * This expects the tokenizer to look at an operator. The given <tt>mappingAndProperty</tt> will
     * most probably contain a field/property which is addressed by the operation. In this case,
     * an operation is created via {@link #parseOperation(Mapping, Property)}.
     * <p>
     * If the property cannot be resolved, the given <tt>token</tt> is passed into {@link #compileCustomField(String)}
     * so that a sub class of the compiler can generate a constraint for a virtual field. If this also doesn't yield
     * a constraint, a regular search in the default fields is generated. This might be necessarry so that tokens like
     * an:value can be used as search term as long as no property named "an" exists.
     *
     * @param mappingAndProperty the mapping (join path) and the resolved property to address
     * @param token              the token which has been parsed as field / property name
     * @param skipped            <tt>true</tt> if we previously skilled whitespaces. Therefor fore "x=y" we generate
     *                           this as literal term. But for "x =y" we simply generate a search for "x" and leave
     *                           the rest up to the parser
     * @return the effective constraint for the given property and token
     */
    private C compileConstraint(@Nullable Tuple<Mapping, Property> mappingAndProperty,
                                FieldValue token,
                                boolean skipped) {
        if (mappingAndProperty != null) {
            return parseOperation(mappingAndProperty.getFirst(), mappingAndProperty.getSecond());
        }

        C customConstraint = compileCustomField(String.valueOf(token.getValue()));
        if (customConstraint != null) {
            return customConstraint;
        }

        if (searchFields.isEmpty()) {
            throw new IllegalArgumentException(Strings.apply("Unknown property: '%s'", token.getValue()));
        }

        if (!skipped) {
            return compileDefaultSearch(searchFields, treatOperatorAsTokenPart(token));
        }

        return compileDefaultSearch(searchFields, token);
    }

    /**
     * Permits subclasses to transform a custom field into a constraint.
     * <p>
     * The effective value has to be fetched manually like
     * {@link #compileOperation(Mapping, Property, String, FieldValue)}.
     *
     * @param customField the field to process
     * @return the generated constraint or <tt>null</tt> if this field cannot be handled
     */
    @Nullable
    protected C compileCustomField(String customField) {
        return null;
    }

    /**
     * Handles an operator which was found when reading a field token as part of a value, as the field isn't a property
     * (real database field) of the underlying entity).
     * <p>
     * An example would be something like <pre>hello:world</pre> where <b>hello</b> isn't an actual field of the
     * entity being searched. Therefore we have to emit a {@link #compileDefaultSearch(List, FieldValue)} with
     * <pre>hello:world</pre> as token.
     *
     * @param token the partial field/token
     * @return the enhanced field token.
     */
    private FieldValue treatOperatorAsTokenPart(FieldValue token) {
        StringBuilder additionalToken = new StringBuilder();
        while (isAtOperator() || continueToken(false)) {
            if (reader.current().is('\\')) {
                reader.consume();
            }
            additionalToken.append(reader.consume());
        }

        token = new FieldValue(token.getValue().toString() + additionalToken, token.isExact());
        return token;
    }

    /**
     * Generates a constrant which tries to find the given token in the given <tt>searchFields</tt>.
     *
     * @param searchFields the fields to search in
     * @param token        the token to search for
     * @return a constraint which performs the requested search
     */
    protected C compileDefaultSearch(List<QueryField> searchFields, FieldValue token) {
        List<C> constraints = new ArrayList<>();
        if (token.isExact()) {
            List<C> fieldConstraints = new ArrayList<>();
            for (QueryField field : searchFields) {
                fieldConstraints.add(compileSearchToken(field.getField(),
                                                        QueryField.Mode.EQUAL,
                                                        token.getValue().toString()));
            }
            constraints.add(factory.or(fieldConstraints));
        } else {
            for (String word : token.getValue().toString().split("\\s")) {
                List<C> fieldConstraints = new ArrayList<>();
                for (QueryField field : searchFields) {
                    fieldConstraints.add(compileSearchToken(field.getField(), field.getMode(), word));
                }
                constraints.add(factory.or(fieldConstraints));
            }
        }
        return factory.and(constraints);
    }

    /**
     * Permits the subclasses to generate the appropriate search constraint.
     *
     * @param field the field to search in
     * @param mode  the mode to use
     * @param value the value to search for
     * @return an appropriate constraint for the given parameters
     */
    protected abstract C compileSearchToken(Mapping field, QueryField.Mode mode, String value);

    /**
     * Parses an operation on the given field.
     * <p>
     * This expects the tokenizer to look at an operator token and will parse and compile the appropriate
     * operation of the given field and its resolved property.
     *
     * @param field    the field to create an operation for. This might be different from the raw property name in
     *                 case of a JOIN FETCH.
     * @param property the property to create an operation for
     * @return a constraint matching the requested operation
     */
    protected C parseOperation(Mapping field, Property property) {
        String operation = readOp();
        FieldValue value = compileValue(property, parseValue());

        return compileOperation(field, property, operation, value);
    }

    /**
     * Tries to parse a known operator.
     *
     * @return the parsed operator
     * @throws IllegalStateException if the current token doesn't match a known operator
     */
    protected String readOp() {
        if (isNotEqual()) {
            reader.consume(2);
            return "<>";
        }
        if (reader.current().is('<') && reader.next().is('=')) {
            reader.consume(2);
            return "<=";
        }
        if (reader.current().is('>') && reader.next().is('=')) {
            reader.consume(2);
            return ">=";
        }
        if (reader.current().is('=') || reader.current().is(':')) {
            reader.consume();
            return "=";
        }
        if (reader.current().is('>')) {
            reader.consume();
            return ">";
        }
        if (reader.current().is('<')) {
            reader.consume();
            return "<";
        } else {
            throw new IllegalStateException(reader.current().toString());
        }
    }

    private boolean isNotEqual() {
        if (reader.current().is('!') && reader.next().is('=')) {
            return true;
        }

        return reader.current().is('<') && reader.next().is('>');
    }

    /**
     * Parses a potential value to be used for an operation.
     * <p>
     * This supports handling of quotes to represent values containing whitespaces.
     *
     * @return the parsed value to use
     */
    protected FieldValue parseValue() {
        skipWhitespace();
        if (reader.current().is('"')) {
            reader.consume();
            StringBuilder result = new StringBuilder();
            while (!reader.current().isEndOfInput() && !reader.current().is('"')) {
                if (reader.current().is('\\')) {
                    reader.consume();
                }
                result.append(reader.consume());
            }
            reader.consume();
            return new FieldValue(result.toString(), true);
        } else {
            return readValue();
        }
    }

    /**
     * Parses a potential value to be used for an operation.
     * <p>
     * In contrast to {@link #parseValue()} this will only process non-whitespace inputs. Also this will
     * stop if an unbalanced ')' is encountered. Unbalanced means here, that more closing than opening brackets
     * were detected in the token. Therefor <tt>Hello()</tt> is fully parsed but <tt>Hello())</tt> is terminated
     * before the last closing bracket.
     *
     * @return the parsed value to use
     */
    protected FieldValue readValue() {
        StringBuilder token = new StringBuilder();
        AtomicInteger numberOfOpenBrackets = new AtomicInteger(0);
        while (continueValue(numberOfOpenBrackets)) {
            if (reader.current().is('(')) {
                numberOfOpenBrackets.incrementAndGet();
            }
            token.append(reader.consume());
        }

        return new FieldValue(token.toString(), false);
    }

    private boolean continueValue(AtomicInteger numberOfOpenBrackets) {
        if (reader.current().isEndOfInput() || reader.current().isWhitespace()) {
            return false;
        }

        if (reader.current().is(')')) {
            if (numberOfOpenBrackets.get() == 0) {
                return false;
            } else {
                numberOfOpenBrackets.decrementAndGet();
            }
        }

        return true;
    }

    /**
     * Compiles the given operation into a constraint.
     *
     * @param field     the field to query for
     * @param property  the property being hit by the constraint
     * @param operation the operation to perform
     * @param value     the value to query with
     * @return a constraint matching the given parameters
     */
    protected C compileOperation(Mapping field, Property property, String operation, FieldValue value) {
        return switch (operation) {
            case ">" -> factory.gt(field, value.getValue());
            case ">=" -> factory.gte(field, value.getValue());
            case "<=" -> factory.lte(field, value.getValue());
            case "<" -> factory.lt(field, value.getValue());
            case "<>" -> compileNotEquals(field, property, value);
            default -> compileFieldEquals(field, property, value);
        };
    }

    /**
     * Generates a "not equals" constraint for the given field and value.
     *
     * @param field    the field to query for
     * @param property the property being hit by the constraint
     * @param value    the value to query with
     * @return a constraint matching the given parameters
     */
    protected C compileNotEquals(Mapping field, Property property, FieldValue value) {
        return factory.ne(field, value.getValue());
    }

    /**
     * Generates an "equals" constraint for the given field and value.
     *
     * @param field    the field to query for
     * @param property the property being hit by the constraint
     * @param value    the value to query with
     * @return a constraint matching the given parameters
     */
    protected C compileFieldEquals(Mapping field, Property property, FieldValue value) {
        return factory.eq(field, value.getValue());
    }

    /**
     * Transforms the given value into the effective filter object.
     *
     * @param property the property being queried
     * @param value    the value to transform
     * @return the effective value to use in a constraint
     */
    protected FieldValue compileValue(Property property, FieldValue value) {
        if (!value.isExact() && "-".equals(value.getValue())) {
            return new FieldValue(null, value.isExact());
        }

        return new FieldValue(compileStringValue(property, String.valueOf(value.getValue())), value.isExact());
    }

    /**
     * Transforms the given string into the effective filter object.
     *
     * @param property the property being queried
     * @param value    the value to transform
     * @return the effective value to use in a constraint
     */
    protected Object compileStringValue(Property property, String value) {
        LocalDateTime deltaValue = parseDeltaValue(value);
        if (deltaValue != null) {
            if (property instanceof LocalDateTimeProperty) {
                return deltaValue;
            }
            if (property instanceof LocalDateProperty) {
                return deltaValue.toLocalDate();
            }
            if (property instanceof LocalTimeProperty) {
                return deltaValue.toLocalTime();
            }
        }

        // first line of defence: reference types are not processed as transformation triggers a lookup in the
        // referenced database; if that lookup yields no results, the value is considered invalid despite the fact that
        // looking for it in the original database may make sense
        if (property instanceof BaseEntityRefProperty || property instanceof BaseEntityRefListProperty) {
            return Value.of(value);
        }

        try {
            return property.transformValue(Value.of(value));
        } catch (Exception e) {
            // second line of defence: should the transformation fail for whatever reason, we pass the original string
            // as is, leaving it to the database to come up with no results if searching for an invalid value (such as a
            // string in a numeric field)
            Exceptions.ignore(e);
            return Value.of(value);
        }
    }

    private LocalDateTime parseDeltaValue(String value) {
        Matcher matcher = DELTA_EXPRESSION.matcher(value);
        if (!matcher.matches()) {
            return null;
        }

        TemporalUnit unit = ChronoUnit.DAYS;
        if ("h".equals(matcher.group(3))) {
            unit = ChronoUnit.HOURS;
        } else if ("m".equals(matcher.group(3))) {
            unit = ChronoUnit.MINUTES;
        }

        if ("-".equals(matcher.group(1))) {
            return LocalDateTime.now().minus(Integer.parseInt(matcher.group(2)), unit);
        }

        return LocalDateTime.now().plus(Integer.parseInt(matcher.group(2)), unit);
    }

    @SuppressWarnings("unchecked")
    private C parseTag() {
        StringBuilder tag = new StringBuilder();
        tag.append(reader.consume());
        tag.append(reader.consume());
        while (!reader.current().isEndOfInput() && !(reader.current().is('|') && reader.next().is('|'))) {
            tag.append(reader.consume());
        }
        tag.append(reader.consume());
        tag.append(reader.consume());

        QueryTag queryTag = QueryTag.parse(tag.toString());
        if (queryTag.getType() != null && Strings.isFilled(queryTag.getValue())) {
            QueryTagHandler<C> handler = ctx.getPart(queryTag.getType(), QueryTagHandler.class);
            if (handler != null) {
                return handler.generateConstraint(factory, descriptor, queryTag.getValue());
            }
        }

        return null;
    }
}
