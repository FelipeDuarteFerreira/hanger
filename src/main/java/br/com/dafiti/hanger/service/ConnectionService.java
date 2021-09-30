/*
 * Copyright (c) 2018 Dafiti Group
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 */
package br.com.dafiti.hanger.service;

import br.com.dafiti.hanger.exception.Message;
import br.com.dafiti.hanger.model.AuditorData;
import br.com.dafiti.hanger.model.Connection;
import br.com.dafiti.hanger.model.User;
import br.com.dafiti.hanger.option.Database;
import br.com.dafiti.hanger.option.Status;
import br.com.dafiti.hanger.repository.ConnectionRepository;
import br.com.dafiti.hanger.security.PasswordCryptor;
import java.security.Principal;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.persistence.Transient;
import javax.sql.DataSource;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final PasswordCryptor passwordCryptor;
    private final JdbcTemplate jdbcTemplate;
    private final ConfigurationService configurationService;
    private final Map<String, PreparedStatement> inflight;
    private final AuditorService auditorService;

    private static final Logger LOG = LogManager.getLogger(ConnectionService.class.getName());

    @Autowired
    public ConnectionService(
            ConnectionRepository connectionRepository,
            PasswordCryptor passwordCryptor,
            JdbcTemplate jdbcTemplate,
            AuditorService auditorService,
            ConfigurationService configurationService) {

        this.connectionRepository = connectionRepository;
        this.passwordCryptor = passwordCryptor;
        this.jdbcTemplate = jdbcTemplate;
        this.auditorService = auditorService;
        this.configurationService = configurationService;
        this.inflight = new HashMap();
    }

    @Cacheable(value = "connections")
    public Iterable<Connection> list() {
        return connectionRepository.findAll();
    }

    /**
     * List all connections and their status.
     *
     * @return list of all connections and their status,
     */
    public List<ConnectionStatus> listConnectionStatus() {
        List<ConnectionStatus> connectionStatus = new ArrayList<>();

        for (Connection connection : this.list()) {
            ConnectionStatus status = new ConnectionStatus();
            status.setConnection(connection);
            status.setStatus(this.testConnection(connection).isEmpty() ? Status.SUCCESS : Status.FAILURE);
            connectionStatus.add(status);
        }

        return connectionStatus;
    }

    public Connection load(Long id) {
        return connectionRepository.findById(id).get();
    }

    @Caching(evict = {
        @CacheEvict(value = "connections", allEntries = true)})
    public void save(Connection connection) {
        String password = connection.getPassword();

        password = passwordCryptor.decrypt(password);
        connection.setPassword(passwordCryptor.encrypt(password));
        connectionRepository.save(connection);
    }

    @Caching(evict = {
        @CacheEvict(value = "connections", allEntries = true)})
    public void delete(Long id) {
        connectionRepository.deleteById(id);
    }

    /**
     * Get a DataSource.
     *
     * @param connection Connection
     * @return Return a DataSource.
     */
    public DataSource getDataSource(Connection connection) {
        SimpleDriverDataSource dataSource = null;

        if (connection != null) {
            try {
                Driver driver = null;
                Properties properties = new Properties();

                switch (connection.getTarget()) {
                    case MYSQL:
                        driver = new com.mysql.cj.jdbc.Driver();
                        properties.setProperty("loginTimeout", "5000");
                        properties.setProperty("connectTimeout", "5000");
                        break;
                    case MSSQL:
                        driver = new com.microsoft.sqlserver.jdbc.SQLServerDriver();
                        properties.setProperty("loginTimeout", "5");
                        break;
                    case POSTGRES:
                        driver = new org.postgresql.Driver();
                        properties.setProperty("loginTimeout", "5");
                        properties.setProperty("connectTimeout", "5");
                        break;
                    case REDSHIFT:
                        driver = new com.amazon.redshift.jdbc42.Driver();
                        properties.setProperty("loginTimeout", "5");
                        properties.setProperty("connectTimeout", "5");
                        break;
                    case ATHENA:
                        driver = new com.simba.athena.jdbc42.Driver();
                        properties.setProperty("MaxErrorRetry", "10");
                        properties.setProperty("ConnectionTimeout", "5");
                        properties.setProperty("MetadataRetrievalMethod", "Query");
                        break;
                    case HANA:
                        driver = new com.sap.db.jdbc.Driver();
                        properties.setProperty("loginTimeout", "5000");
                        properties.setProperty("connectTimeout", "5000");
                        break;
                    case JTDS:
                        driver = new net.sourceforge.jtds.jdbc.Driver();
                        properties.setProperty("loginTimeout", "5");
                        break;
                    case GENERIC:
                        driver = (Driver) Class.forName(connection.getClassName()).newInstance();
                        break;
                    default:
                        break;
                }

                dataSource = new SimpleDriverDataSource(
                        driver,
                        connection.getUrl(),
                        connection.getUsername(),
                        passwordCryptor.decrypt(connection.getPassword()));

                dataSource.setConnectionProperties(properties);
            } catch (SQLException
                    | ClassNotFoundException
                    | InstantiationException
                    | IllegalAccessException ex) {

                LOG.log(Level.ERROR, "Fail getting datasource " + connection.getName(), ex);
            }
        }

        return dataSource;
    }

    /**
     * Test a connection.
     *
     * @param connection Connection
     * @return Identifies a connection status.
     */
    public String testConnection(Connection connection) {
        String status = "";
        DataSource datasource = this.getDataSource(connection);

        if (datasource != null) {
            try {
                if (datasource.getConnection() != null) {
                }
            } catch (SQLException ex) {
                status = ex.getMessage();
                LOG.log(Level.ERROR, "Fail testing connection to " + connection.getName(), status);
            } finally {
                try {
                    if (datasource.getConnection() != null) {
                        datasource.getConnection().close();
                    }
                } catch (SQLException ex) {
                    LOG.log(Level.ERROR, "Fail closing connection", ex.getMessage());
                }
            }
        }

        return status;
    }

    /**
     * Get catalogs.
     *
     * @param connection Connection
     * @return Database metadata
     */
    @Cacheable(value = "catalogs", key = "#connection")
    public List<Entity> getCatalogs(Connection connection) {
        List catalog = new ArrayList();
        DataSource datasource = this.getDataSource(connection);

        try {
            ResultSet catalogs = datasource.getConnection()
                    .getMetaData()
                    .getCatalogs();

            while (catalogs.next()) {
                String catalogName = catalogs.getString("TABLE_CAT");

                if (catalogName != null && !catalogName.isEmpty()) {

                    catalog.add(
                            new Entity(
                                    catalogName,
                                    "",
                                    "",
                                    "",
                                    connection.getTarget()));
                }
            }
        } catch (SQLException ex) {
            LOG.log(Level.ERROR, "Fail getting metadata of " + connection.getName(), ex);
        } finally {
            try {
                datasource.getConnection().close();
            } catch (SQLException ex) {
                LOG.log(Level.ERROR, "Fail closing connection", ex.getMessage());
            }
        }

        return catalog;
    }

    /**
     * Get schemas.
     *
     * @param connection Connection
     * @return Database metadata
     */
    @Cacheable(value = "schemas", key = "#connection")
    public List<Entity> getSchemas(Connection connection) {
        List schema = new ArrayList();
        DataSource datasource = this.getDataSource(connection);

        try {
            ResultSet schemas = datasource.getConnection()
                    .getMetaData()
                    .getSchemas();

            while (schemas.next()) {
                String catalogName = schemas.getString("TABLE_CATALOG");
                String schemaName = schemas.getString("TABLE_SCHEM");

                if ((catalogName != null && !catalogName.isEmpty())
                        || (schemaName != null && !schemaName.isEmpty())) {

                    schema.add(
                            new Entity(
                                    catalogName,
                                    schemaName,
                                    "",
                                    "",
                                    connection.getTarget()));
                }
            }

        } catch (SQLException ex) {
            LOG.log(Level.ERROR, "Fail getting metadata of " + connection.getName(), ex);
        } finally {
            try {
                datasource.getConnection().close();
            } catch (SQLException ex) {
                LOG.log(Level.ERROR, "Fail closing connection", ex.getMessage());
            }
        }

        return schema;
    }

    /**
     * Get tables.
     *
     * @param connection Connection
     * @param catalog String
     * @param schema String
     * @return Database metadata
     */
    @Cacheable(value = "tables", key = "{#connection, #catalog, #schema}")
    public List<Entity> getTables(Connection connection, String catalog, String schema) {
        List table = new ArrayList();
        DataSource datasource = this.getDataSource(connection);

        try {
            ResultSet tables = datasource.getConnection()
                    .getMetaData()
                    .getTables(
                            ("null".equals(catalog) || catalog.isEmpty()) ? null : catalog,
                            ("null".equals(schema) || schema.isEmpty()) ? null : schema,
                            "%",
                            new String[]{"TABLE", "EXTERNAL TABLE"});

            while (tables.next()) {
                if (this.isDisplayLimit(table.size())) {
                    break;
                } else {
                    table.add(
                            new Entity(
                                    tables.getString("TABLE_CAT"),
                                    tables.getString("TABLE_SCHEM"),
                                    tables.getString("TABLE_NAME"),
                                    tables.getString("TABLE_TYPE"),
                                    connection.getTarget()));
                }
            }
        } catch (SQLException ex) {
            LOG.log(Level.ERROR, "Fail getting tables of " + connection.getName(), ex);
        } finally {
            try {
                datasource.getConnection().close();
            } catch (SQLException ex) {
                LOG.log(Level.ERROR, "Fail closing connection", ex.getMessage());
            }
        }

        return table;
    }

    /**
     * Get column.
     *
     * @param connection Connection
     * @param catalog Caralog
     * @param schema Schema
     * @param table Table
     * @return Table columns
     */
    public List<Column> getColumns(
            Connection connection,
            String catalog,
            String schema,
            String table) {

        List column = new ArrayList();
        DataSource datasource = this.getDataSource(connection);

        try {
            ResultSet columns = datasource.getConnection()
                    .getMetaData()
                    .getColumns(
                            ("null".equals(catalog) || catalog.isEmpty()) ? null : catalog,
                            ("null".equals(schema) || schema.isEmpty()) ? null : schema,
                            table,
                            null
                    );

            while (columns.next()) {
                column.add(
                        new Column(
                                columns.getInt("ORDINAL_POSITION"),
                                columns.getString("COLUMN_NAME"),
                                columns.getString("TYPE_NAME"),
                                columns.getInt("COLUMN_SIZE"),
                                columns.getInt("DECIMAL_DIGITS"),
                                columns.getString("REMARKS")));
            }
        } catch (SQLException ex) {
            LOG.log(Level.ERROR, "Fail getting columns of " + connection.getName(), ex);
        } finally {
            try {
                datasource.getConnection().close();
            } catch (SQLException ex) {
                LOG.log(Level.ERROR, "Fail closing connection", ex.getMessage());
            }
        }

        return column;
    }

    /**
     * Get primary key.
     *
     * @param connection Connection
     * @param catalog Catalog
     * @param schema Schema
     * @param table Table
     * @return Table primary key
     */
    public List<Column> getPrimaryKey(
            Connection connection,
            String catalog,
            String schema,
            String table) {

        List columns = new ArrayList();
        DataSource datasource = this.getDataSource(connection);

        try {
            ResultSet tables = datasource.getConnection()
                    .getMetaData()
                    .getPrimaryKeys(catalog, schema, table);

            while (tables.next()) {
                columns.add(
                        new Column(
                                tables.getInt("KEY_SEQ"),
                                tables.getString("COLUMN_NAME")));
            }
        } catch (SQLException ex) {
            LOG.log(Level.ERROR, "Fail getting primary key of " + connection.getName(), ex);
        } finally {
            try {
                datasource.getConnection().close();
            } catch (SQLException ex) {
                LOG.log(Level.ERROR, "Fail closing connection", ex.getMessage());
            }
        }

        return columns;
    }

    /**
     * Executes a query and returns a QueryResultSet instance.
     *
     * @param connection Connection.
     * @param query Query.
     * @param user User.
     * @return QueryResultSet instance.
     */
    public QueryResultSet getQueryResultSet(
            Connection connection,
            String query,
            User user) {

        QueryResultSet queryResultSet = new QueryResultSet();
        DataSource datasource = this.getDataSource(connection);

        try {
            //Sets a connection to target.
            jdbcTemplate.setDataSource(datasource);
            jdbcTemplate.setMaxRows(this.configurationService.getMaxRows());

            //Sets default security behavior. 
            if (connection.getTarget().equals(Database.POSTGRES)
                    || connection.getTarget().equals(Database.ATHENA)) {
                query = this.evaluate(query);
            }

            //Executes a query. 
            StopWatch watch = new StopWatch();

            //Starts the query metter.
            watch.start();

            //Defines a final statement to be excecuted. 
            final String statement = query;

            jdbcTemplate.query((java.sql.Connection conn) -> {
                //Creates READ_ONLY and TYPE_FORWARD_ONLY prepared statement.
                PreparedStatement preparedStatement = conn.prepareStatement(
                        statement,
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY);

                //Salves the statement being executed.
                inflight.put(user.getUsername(), preparedStatement);

                return preparedStatement;
            }, (ResultSet resultSet) -> {
                QueryResultSetRow resultSetRow = new QueryResultSetRow();

                //Identifies if should retrieve metadata. 
                if (queryResultSet.getHeader().isEmpty()) {
                    //Retrives the metadata. 
                    ResultSetMetaData metaData = resultSet.getMetaData();

                    for (int i = 1; i <= metaData.getColumnCount(); i++) {
                        //Extracts columns name.
                        queryResultSet
                                .getHeader()
                                .add(metaData.getColumnName(i));

                        //Extracts columns data type. 
                        queryResultSet
                                .getType()
                                .put(metaData.getColumnName(i), metaData.getColumnTypeName(i));

                        //Extracts columns class name. 
                        queryResultSet
                                .getClassName()
                                .put(metaData.getColumnName(i), metaData.getColumnClassName(i));
                    }
                }

                //Sets columns value to the row. 
                for (String column : queryResultSet.getHeader()) {
                    resultSetRow
                            .getColumn()
                            .add(resultSet.getString(column));
                }

                //Sets the row to the resultset. 
                queryResultSet
                        .getRow()
                        .add(resultSetRow);

                //Removes the statement from inflight when a query finishes.
                inflight.remove(user.getUsername());
            });

            //Log.
            auditorService.publish(
                    "QUERY",
                    new AuditorData()
                            .addData("connection", connection.getName())
                            .addData("sql", query)
                            .getData());

            //Gets query elapsed time.
            queryResultSet.setElapsedTime(watch.getTotalTimeMillis());

            //Stops the query metter.
            watch.stop();
        } catch (DataAccessException ex) {
            queryResultSet.setError(new Message().getErrorMessage(ex));

            LOG.log(Level.ERROR, "Query error: ", ex);
        } finally {
            try {
                //Close the connection. 
                jdbcTemplate.getDataSource().getConnection().close();
            } catch (SQLException ex) {
                LOG.log(Level.ERROR, "Fail closing connection: ", ex);
            }
        }

        return queryResultSet;
    }

    /**
     * Cancels a query being executed.
     *
     * @param principal Principal
     * @return Identifies if a query was canceled.
     */
    public boolean queryCancel(Principal principal) {
        boolean canceled = false;
        PreparedStatement preparedStatement = inflight.get(principal.getName());

        if (preparedStatement != null) {
            try {
                //Try cancel a query.
                preparedStatement.cancel();

                //Identifies that the cancel command was sent sucessfully.
                canceled = true;

                //Removes the statements from inflight when a cancel commend is sent.
                inflight.remove(principal.getName());
            } catch (SQLException ex) {
                LOG.log(Level.ERROR, "Fail aborting query ", ex);
            }
        }

        return canceled;
    }

    /**
     * Refresh all connections cache.
     *
     */
    @Caching(evict = {
        @CacheEvict(value = "tables", allEntries = true)
    })
    public void refresh() {
    }

    /**
     * Refresh connection cache.
     *
     * @param connection Connection.
     */
    @Caching(evict = {
        @CacheEvict(value = "tables", key = "#connection")
    })
    public void refreshConnection(Connection connection) {
    }

    /**
     * Identifies if the table quantity excedeed the configuration limit.
     *
     * @param tables Table quantity.
     * @return table quantity excedeed the configuration limit
     */
    public boolean isDisplayLimit(int tables) {
        return tables >= Integer
                .valueOf(configurationService
                        .findByParameter("WORKBENCH_MAX_ENTITY_NUMBER")
                        .getValue());
    }

    /**
     * Evaluate query.
     *
     * @param query
     * @return table quantity excedeed the configuration limit
     */
    public String evaluate(String query) {
        String limit = " limit "
                + String.valueOf(this.configurationService.getMaxRows());

        if (!query.toLowerCase().contains("limit")) {
            if (query.endsWith(";")) {
                query = query.replaceAll(";", limit);
            } else {
                query = query.concat(limit);
            }
        } else {
            //Identifies if query has "limit <number>"
            String regex = "limit\\s[0-9]+";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(query.toLowerCase());

            while (matcher.find()) {
                String found = query.substring(
                        matcher.start(),
                        matcher.end());

                //Identifies if query limit exceeds configuration allowed
                if (Integer.valueOf(found.substring(6))
                        > this.configurationService.getMaxRows()) {
                    query = query.replace(found, limit);
                }
            }
        }
        return query;
    }

    /**
     * Get indexes.
     *
     * @param connection Connection
     * @param catalog Caralog
     * @param schema Schema
     * @param table Table
     * @return Table columns
     */
    public List<Column> getIndexes(
            Connection connection,
            String catalog,
            String schema,
            String table) {

        List index = new ArrayList();
        DataSource datasource = this.getDataSource(connection);

        try {
            ResultSet indexes = datasource.getConnection()
                    .getMetaData()
                    .getIndexInfo(
                            ("null".equals(catalog) || catalog.isEmpty()) ? null : catalog,
                            ("null".equals(schema) || schema.isEmpty()) ? null : schema,
                            table,
                            false,
                            true
                    );

            while (indexes.next()) {
                index.add(
                        new Index(
                                indexes.getBoolean("NON_UNIQUE"),
                                indexes.getString("INDEX_QUALIFIER"),
                                indexes.getString("INDEX_NAME"),
                                indexes.getString("TYPE"),
                                indexes.getInt("ORDINAL_POSITION"),
                                indexes.getString("COLUMN_NAME"),
                                indexes.getString("ASC_OR_DESC"),
                                indexes.getInt("CARDINALITY")));
            }
        } catch (SQLException ex) {
            LOG.log(Level.ERROR, "Fail getting indexes of " + connection.getName(), ex);
        } finally {
            try {
                datasource.getConnection().close();
            } catch (SQLException ex) {
                LOG.log(Level.ERROR, "Fail closing connection", ex.getMessage());
            }
        }

        return index;
    }

    /**
     * Represents a target entity.
     */
    public class Entity {

        private String catalog;
        private String schema;
        private String table;
        private String type;
        private Database target;

        public Entity(
                String catalog,
                String schema,
                String table,
                String type,
                Database target) {

            this.catalog = catalog;
            this.schema = schema;
            this.table = table;
            this.type = type;
            this.target = target;
        }

        public String getCatalog() {
            return catalog;
        }

        public void setCatalog(String catalog) {
            this.catalog = catalog;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Database getTarget() {
            return target;
        }

        public void setTarget(Database target) {
            this.target = target;
        }

        @Transient
        public String getCatalogSchema() {
            List<String> catalogSchema = new ArrayList();

            if (catalog != null) {
                catalogSchema.add(catalog);
            }

            if (schema != null) {
                catalogSchema.add(schema);
            }

            return String.join(".", catalogSchema);
        }
    }

    /**
     * Represents a target entity column.
     */
    public class Column {

        int position;
        String name;
        String type;
        int size;
        int decimal;
        String remark;

        public Column(
                int position,
                String name) {

            this.position = position;
            this.name = name;
        }

        public Column(
                int position,
                String name,
                String type,
                int size,
                int decimal,
                String remark) {

            this.position = position;
            this.name = name;
            this.type = type;
            this.size = size;
            this.decimal = decimal;
            this.remark = remark;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getDecimal() {
            return decimal;
        }

        public void setDecimal(int decimal) {
            this.decimal = decimal;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    /**
     * Represents a query resultset.
     */
    public class QueryResultSet {

        String error = new String();
        List<String> header = new ArrayList();
        Map<String, String> type = new HashMap();
        Map<String, String> className = new HashMap();
        List<QueryResultSetRow> row = new ArrayList();
        long elapsedTime = 0;

        public List<String> getHeader() {
            return header;
        }

        public void setHeader(List<String> header) {
            this.header = header;
        }

        public Map<String, String> getType() {
            return type;
        }

        public void setType(Map<String, String> type) {
            this.type = type;
        }

        public Map<String, String> getClassName() {
            return className;
        }

        public void setClassName(Map<String, String> className) {
            this.className = className;
        }

        public List<QueryResultSetRow> getRow() {
            return row;
        }

        public void setRow(List<QueryResultSetRow> row) {
            this.row = row;
        }

        public long getElapsedTime() {
            return elapsedTime;
        }

        public void setElapsedTime(long elapsedTime) {
            this.elapsedTime = elapsedTime;
        }

        public boolean hasError() {
            return !error.isEmpty();
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        /**
         * Get the query resultset as a JSON Object ;
         *
         * @return Query resultset JSON Object
         */
        public Map toJSONObject() {
            JSONObject object = new JSONObject();

            for (int i = 0; i < header.size(); i++) {
                for (int j = 0; j < row.size(); j++) {

                    if (object.has(header.get(i))) {
                        object.put(
                                header.get(i),
                                ((JSONArray) object.get(header.get(i)))
                                        .put(row.get(j)
                                                .getColumn()
                                                .get(i)));
                    } else {
                        object.put(header.get(i), new JSONArray()
                                .put(row.get(j)
                                        .getColumn()
                                        .get(i)));
                    }
                }
            }

            return object.toMap();
        }
    }

    /**
     * Represents a query resultset row.
     */
    public class QueryResultSetRow {

        List<Object> column = new ArrayList();

        public List<Object> getColumn() {
            return column;
        }

        public void setColumn(List<Object> column) {
            this.column = column;
        }
    }

    /**
     * Represents status of a target connection.
     */
    public class ConnectionStatus {

        Status status;
        Connection connection;

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public Connection getConnection() {
            return connection;
        }

        public void setConnection(Connection connection) {
            this.connection = connection;
        }
    }

    /**
     * Represents a target entity index.
     */
    public class Index {

        boolean nonUnique;
        String qualifier;
        String name;
        String type;
        int position;
        String columnName;
        String ascOrDesc;
        int cardinality;

        public Index(
                boolean nonUnique,
                String qualifier,
                String name,
                String type,
                int position,
                String columnName,
                String ascOrDesc,
                int cardinality) {

            this.nonUnique = nonUnique;
            this.qualifier = qualifier;
            this.name = name;
            this.type = type;
            this.position = position;
            this.columnName = columnName;
            this.ascOrDesc = ascOrDesc;
            this.cardinality = cardinality;
        }

        public boolean isNonUnique() {
            return nonUnique;
        }

        public void setNonUnique(boolean nonUnique) {
            this.nonUnique = nonUnique;
        }

        public String getQualifier() {
            return qualifier;
        }

        public void setQualifier(String qualifier) {
            this.qualifier = qualifier;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }

        public String getAscOrDesc() {
            return ascOrDesc;
        }

        public void setAscOrDesc(String ascOrDesc) {
            this.ascOrDesc = ascOrDesc;
        }

        public int getCardinality() {
            return cardinality;
        }

        public void setCardinality(int cardinality) {
            this.cardinality = cardinality;
        }

    }
}
