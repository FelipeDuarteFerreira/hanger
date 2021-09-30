/*
 * Copyright (c) 2019 Dafiti Group
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

import br.com.dafiti.hanger.model.Connection;
import br.com.dafiti.hanger.option.Database;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author Fernando Saga
 */
@Service
public class WorkbenchService {

    private final ConnectionService connectionService;
    private final ConfigurationService configurationService;

    @Autowired
    public WorkbenchService(
            ConnectionService connectionService,
            ConfigurationService configurationService) {

        this.connectionService = connectionService;
        this.configurationService = configurationService;
    }

    /**
     * Identify if should load schema or table list.
     *
     * @param connection Connection
     * @param catalog
     * @param schema
     *
     *
     * @return List Tree
     */
    public List<Tree> JSTreeExchange(
            Connection connection,
            String catalog,
            String schema) {

        List<Tree> tree = new ArrayList();

        //Whether catalog and schema are empty, get catalogs.
        if (catalog.isEmpty() && schema.isEmpty()) {
            tree = JSTreeCatalogList(connection);
        }

        //Whether tree is empty and schema, get schemas.
        if (tree.isEmpty() && schema.isEmpty()) {
            tree = JSTreeSchemaList(connection, catalog);
        }

        //Whether tree is empty, get tables.
        if (tree.isEmpty()) {
            tree = JSTreeTableList(connection, catalog, schema);
        }

        return tree;
    }

    /**
     * Fully qualified catalog list.
     *
     * @param connection Connection
     * @return List Catalogs tree
     */
    public List<Tree> JSTreeCatalogList(Connection connection) {
        List<Tree> tree = new ArrayList();

        connectionService.getCatalogs(connection).forEach((catalogEntity) -> {
            tree.add(
                    new Tree(
                            catalogEntity.getCatalog(),
                            catalogEntity.getCatalog(),
                            "#",
                            "glyphicon glyphicon-th-large",
                            true,
                            new TreeAttribute(catalogEntity.getCatalog())
                    ));
        });

        return tree;
    }

    /**
     * Fully qualified schema list.
     *
     * @param connection Connection
     * @return List schemas tree
     */
    public List<Tree> JSTreeSchemaList(Connection connection) {
        return JSTreeSchemaList(connection, "");
    }

    /**
     * Fully qualified schema list.
     *
     * @param connection Connection
     * @param catalog
     * @return List schemas tree
     */
    public List<Tree> JSTreeSchemaList(Connection connection, String catalog) {
        List<Tree> tree = new ArrayList();

        String parent = ((catalog == null) || (catalog.isEmpty())) ? "#" : catalog;

        connectionService.getSchemas(connection).forEach((schemaEntity) -> {
            tree.add(
                    new Tree(
                            schemaEntity.getSchema(),
                            schemaEntity.getSchema(),
                            parent,
                            "glyphicon glyphicon-book",
                            true,
                            new TreeAttribute(
                                    schemaEntity.getCatalog(),
                                    schemaEntity.getSchema()
                            )
                    ));
        });

        return tree;
    }

    /**
     * Table name list.
     *
     * @param connection Connection
     * @param catalog Catalog
     * @param schema Schema
     * @return Table list
     */
    public List<Tree> JSTreeTableList(
            Connection connection,
            String catalog,
            String schema) {

        List tree = new ArrayList();

        connectionService.getTables(connection, catalog, schema).forEach((tableEntity) -> {
            tree.add(
                    new Tree(
                            tableEntity.getTable(),
                            tableEntity.getTable(),
                            ((tableEntity.getSchema() == null) || (tableEntity.getSchema().isEmpty())) ? tableEntity.getCatalog() : tableEntity.getSchema(),
                            ("EXTERNAL TABLE".equals(tableEntity.getType())) ? "glyphicon glyphicon-file" : "glyphicon glyphicon-list",
                            false,
                            new TreeAttribute(
                                    tableEntity.getCatalog(),
                                    tableEntity.getSchema(),
                                    tableEntity.getTable(),
                                    connection.getTarget()
                            )
                    )
            );
        });

        return tree;
    }

    /**
     * Do a simple query with field list.
     *
     * @param field Fields that will be put on query.
     * @param catalog
     * @param schema
     * @param table
     * @param connection
     * @return Query
     */
    public String doQuery(
            List<String> field,
            String catalog,
            String schema,
            String table,
            Connection connection) {

        List<String> catalogSchema = new ArrayList();

        if (catalog != null && !"null".equals(catalog) && !catalog.isEmpty()) {
            catalogSchema.add(catalog);
        }

        if (schema != null && !"null".equals(schema) && !schema.isEmpty()) {
            catalogSchema.add(schema);
        }

        if (table != null && !"null".equals(table) && !table.isEmpty()) {
            if (connection.getTarget().equals(Database.HANA)) {
                catalogSchema.add('"' + table + '"');
            } else {
                catalogSchema.add(table);
            }
        }

        StringBuilder query = new StringBuilder("SELECT ");

        switch (connection.getTarget()) {
            case MSSQL:
            case JTDS:
                query.append(" TOP ");
                query.append(configurationService.getMaxRows());
                query.append(" ");
                query.append(String.join(",", field));
                query.append(" FROM ");
                query.append(String.join(".", catalogSchema));
                break;
            default:
                query.append(String.join(",", field));
                query.append(" FROM ");
                query.append(String.join(".", catalogSchema));
                query.append(" LIMIT ");
                query.append(configurationService.getMaxRows());
                break;
        }

        return query.toString();
    }

    /**
     * Represents a JSTree.
     */
    public class Tree {

        String id;
        String text;
        String parent;
        String icon;
        boolean children;
        TreeAttribute a_attr;

        public Tree(
                String id,
                String text,
                String parent,
                String icon,
                boolean children,
                TreeAttribute a_attr) {

            this.id = id;
            this.text = text;
            this.parent = parent;
            this.icon = icon;
            this.children = children;
            this.a_attr = a_attr;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getParent() {
            return parent;
        }

        public void setParent(String parent) {
            this.parent = parent;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public boolean isChildren() {
            return children;
        }

        public void setChildren(boolean children) {
            this.children = children;
        }

        public TreeAttribute getA_attr() {
            return a_attr;
        }

        public void setA_attr(TreeAttribute a_attr) {
            this.a_attr = a_attr;
        }
    }

    /**
     * Represents a JSTree node attributes.
     */
    public class TreeAttribute {

        String catalog;
        String schema;
        String table;
        Database target;

        public TreeAttribute(String catalog) {
            this.catalog = catalog;
        }

        public TreeAttribute(String catalog, String schema) {
            this.catalog = catalog;
            this.schema = schema;
        }

        public TreeAttribute(String catalog, String schema, String table, Database target) {
            this.catalog = catalog;
            this.schema = schema;
            this.table = table;
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

        public Database getTarget() {
            return target;
        }

        public void setTarget(Database target) {
            this.target = target;
        }
    }
}
