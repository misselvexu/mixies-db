/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.db.jdbc;

import sirius.db.DB;
import sirius.kernel.async.ExecutionPoint;
import sirius.kernel.async.Operation;
import sirius.kernel.commons.Watch;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.time.Duration;

/**
 * Wrapper for {@link Statement} to add microtiming.
 */
class WrappedStatement implements Statement {

    protected Statement stmt;

    WrappedStatement(Statement stmt) {
        super();
        this.stmt = stmt;
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        if (Databases.LOG.isFINE()) {
            Databases.LOG.FINE(sql);
        }
        Watch w = Watch.start();
        try (Operation op = new Operation(() -> sql, Duration.ofSeconds(30))) {
            return stmt.executeQuery(sql);
        } finally {
            updateStatistics(sql, w);
        }
    }

    protected void updateStatistics(String sql, Watch w) {
        w.submitMicroTiming("SQL", "Statement: " + sql);
        Databases.numQueries.inc();
        Databases.queryDuration.addValue(w.elapsedMillis());
        if (w.elapsedMillis() > Databases.getLogQueryThresholdMillis()) {
            Databases.numSlowQueries.inc();
            DB.SLOW_DB_LOG.INFO("A slow JDBC query was executed (%s): %s\n%s",
                                w.duration(),
                                sql,
                                ExecutionPoint.snapshot().toString());
        }
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return stmt.unwrap(iface);
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        if (Databases.LOG.isFINE()) {
            Databases.LOG.FINE(sql);
        }
        Watch w = Watch.start();
        try (Operation op = new Operation(() -> sql, Duration.ofSeconds(30))) {
            return stmt.executeUpdate(sql);
        } finally {
            updateStatistics(sql, w);
        }
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return stmt.isWrapperFor(iface);
    }

    @Override
    public void close() throws SQLException {
        stmt.close();
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return stmt.getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        stmt.setMaxFieldSize(max);
    }

    @Override
    public int getMaxRows() throws SQLException {
        return stmt.getMaxRows();
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        stmt.setMaxRows(max);
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        stmt.setEscapeProcessing(enable);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return stmt.getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        stmt.setQueryTimeout(seconds);
    }

    @Override
    public void cancel() throws SQLException {
        stmt.cancel();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return stmt.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        stmt.clearWarnings();
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        stmt.setCursorName(name);
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        if (Databases.LOG.isFINE()) {
            Databases.LOG.FINE(sql);
        }
        Watch w = Watch.start();
        try (Operation op = new Operation(() -> sql, Duration.ofSeconds(30))) {
            return stmt.execute(sql);
        } finally {
            updateStatistics(sql, w);
        }
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return stmt.getResultSet();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return stmt.getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return stmt.getMoreResults();
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        stmt.setFetchDirection(direction);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return stmt.getFetchDirection();
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        stmt.setFetchSize(rows);
    }

    @Override
    public int getFetchSize() throws SQLException {
        return stmt.getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return stmt.getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return stmt.getResultSetType();
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        stmt.addBatch(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        stmt.clearBatch();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        return stmt.executeBatch();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return stmt.getConnection();
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return stmt.getMoreResults(current);
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return stmt.getGeneratedKeys();
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        if (Databases.LOG.isFINE()) {
            Databases.LOG.FINE(sql);
        }
        Watch w = Watch.start();

        try (Operation op = new Operation(() -> sql, Duration.ofSeconds(30))) {
            return stmt.executeUpdate(sql, autoGeneratedKeys);
        } finally {
            updateStatistics(sql, w);
        }
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        if (Databases.LOG.isFINE()) {
            Databases.LOG.FINE(sql);
        }
        Watch w = Watch.start();
        try (Operation op = new Operation(() -> sql, Duration.ofSeconds(30))) {
            return stmt.executeUpdate(sql, columnIndexes);
        } finally {
            updateStatistics(sql, w);
        }
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        if (Databases.LOG.isFINE()) {
            Databases.LOG.FINE(sql);
        }
        Watch w = Watch.start();
        try (Operation op = new Operation(() -> sql, Duration.ofSeconds(30))) {
            return stmt.executeUpdate(sql, columnNames);
        } finally {
            updateStatistics(sql, w);
        }
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        if (Databases.LOG.isFINE()) {
            Databases.LOG.FINE(sql);
        }
        Watch w = Watch.start();
        try (Operation op = new Operation(() -> sql, Duration.ofSeconds(30))) {
            return stmt.execute(sql, autoGeneratedKeys);
        } finally {
            updateStatistics(sql, w);
        }
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        if (Databases.LOG.isFINE()) {
            Databases.LOG.FINE(sql);
        }
        Watch w = Watch.start();

        try (Operation op = new Operation(() -> sql, Duration.ofSeconds(30))) {
            return stmt.execute(sql, columnIndexes);
        } finally {
            updateStatistics(sql, w);
        }
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        if (Databases.LOG.isFINE()) {
            Databases.LOG.FINE(sql);
        }
        Watch w = Watch.start();
        try (Operation op = new Operation(() -> sql, Duration.ofSeconds(30))) {
            return stmt.execute(sql, columnNames);
        } finally {
            updateStatistics(sql, w);
        }
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return stmt.getResultSetHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return stmt.isClosed();
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        stmt.setPoolable(poolable);
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return stmt.isPoolable();
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        stmt.closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return stmt.isCloseOnCompletion();
    }
}
