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
package br.com.dafiti.hanger.controller;

import br.com.dafiti.hanger.exception.Message;
import br.com.dafiti.hanger.model.Connection;
import br.com.dafiti.hanger.model.WorkbenchQuery;
import br.com.dafiti.hanger.model.JobCheckup;
import br.com.dafiti.hanger.model.WorkbenchEmail;
import br.com.dafiti.hanger.service.ConfigurationService;
import br.com.dafiti.hanger.service.ConnectionService;
import br.com.dafiti.hanger.service.WorkbenchService;
import br.com.dafiti.hanger.service.ConnectionService.QueryResultSet;
import br.com.dafiti.hanger.service.TemplateService;
import br.com.dafiti.hanger.service.UserService;
import java.security.Principal;
import java.util.List;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author Helio Leal
 * @author Valdiney V GOMES
 */
@Controller
@RequestMapping(path = "/workbench")
public class WorkbenchController {

    private final ConnectionService connectionService;
    private final WorkbenchService workbenchService;
    private final ConfigurationService configurationService;
    private final UserService userService;
    private final TemplateService templateService;

    @Autowired
    public WorkbenchController(
            ConnectionService connectionService,
            WorkbenchService workbenchService,
            ConfigurationService configurationService,
            UserService userService,
            TemplateService templateService) {

        this.connectionService = connectionService;
        this.workbenchService = workbenchService;
        this.configurationService = configurationService;
        this.userService = userService;
        this.templateService = templateService;
    }

    /**
     * Show SQL workbench
     *
     * @param model Model
     * @return SQL workbench template.
     */
    @GetMapping(path = "/studio/")
    public String workbench(Model model) {
        modelDefault(model);
        return "workbench/studio";
    }

    /**
     * Workbench query resultset.
     *
     * @param connection Connection
     * @param query SQL Expression
     * @param parameters
     * @param principal
     * @param model Model
     * @return Query result set fragment.
     */
    @PostMapping(path = "/query/{id}")
    public String query(
            @PathVariable(name = "id") Connection connection,
            @RequestParam(name = "query") String query,
            @RequestParam(name = "parameters", required = false) JSONArray parameters,
            Principal principal,
            Model model) {

        QueryResultSet queryResultSet = connectionService.getQueryResultSet(
                connection,
                templateService.setParameters(query, parameters),
                userService.findByUsername(principal.getName()));

        if (queryResultSet.hasError()) {
            model.addAttribute("errorMessage", queryResultSet.getError());
        } else {
            model.addAttribute("resultset", queryResultSet);
        }

        return "workbench/fragmentQueryResultSet::resultSet";
    }

    /**
     * Cancel
     *
     * @param principal
     * @param model
     * @return
     */
    @PostMapping(path = "/query")
    public ResponseEntity cancel(
            Principal principal,
            Model model) {

        ResponseEntity responseEntity;

        if (connectionService.queryCancel(principal)) {
            responseEntity = new ResponseEntity(HttpStatus.OK);
        } else {
            responseEntity = new ResponseEntity(HttpStatus.PRECONDITION_FAILED);
        }

        return responseEntity;
    }

    /**
     * Show SQL workbench
     *
     * @param query WorkbenchQuery
     * @param model Model
     * @return SQL workbench template.
     */
    @GetMapping(path = "/studio/{id}")
    public String workbench(
            @PathVariable(name = "id") WorkbenchQuery query,
            Model model) {
        modelDefault(model, query.getQuery(), query.getConnection());
        return "workbench/studio";
    }

    /**
     * Job checkup query on workbench
     *
     * @param checkup JobCheckup
     * @param model Model
     * @return SQL workbench template.
     */
    @GetMapping(path = "/job/checkup/{id}")
    public String workbench(
            @PathVariable(name = "id") JobCheckup checkup,
            Model model) {
        modelDefault(model, checkup.getQuery(), checkup.getConnection());
        return "workbench/studio";
    }

    /**
     * Job checkup query on workbench
     *
     * @param email WorkbenchEmail
     * @param model Model
     * @return SQL workbench template.
     */
    @GetMapping(path = "/studio/email/{id}")
    public String workbench(
            @PathVariable(name = "id") WorkbenchEmail email,
            Model model) {
        modelDefault(model, email.getQuery(), email.getConnection());
        return "workbench/studio";
    }

    /**
     *
     * @param model
     * @param query
     * @param connection
     */
    private void modelDefault(Model model, String query, Connection connection) {
        model.addAttribute("query", query);
        model.addAttribute("connection", connection);
        model.addAttribute("connections", connectionService.list());
        model.addAttribute("maxRows", configurationService.getMaxRows());
    }

    /**
     *
     * @param model
     * @param query
     * @param connection
     */
    private void modelDefault(Model model) {
        model.addAttribute("connections", connectionService.list());
        model.addAttribute("maxRows", configurationService.getMaxRows());
    }

    /**
     * Workbench tree list.
     *
     * @param connection Connection
     * @param catalog Catalog
     * @param schema Schema
     * @return List Tree.
     */
    @GetMapping(path = "/tree/{id}")
    @ResponseBody
    public List<WorkbenchService.Tree> workbenchTree(
            @PathVariable(name = "id") Connection connection,
            @RequestParam(name = "catalog") String catalog,
            @RequestParam(name = "schema") String schema) {

        return workbenchService.JSTreeExchange(connection, catalog, schema);
    }

    /**
     * Add fields to workbench.
     *
     * @param connection
     * @param fields
     * @param catalog
     * @param schema
     * @param table
     * @param model Model
     * @return Query result set fragment.
     */
    @PostMapping(path = "/fields")
    public String addFields(
            @RequestParam(name = "id", required = false) Connection connection,
            @RequestParam(name = "fields", required = false) List<String> fields,
            @RequestParam(name = "catalog", required = false) String catalog,
            @RequestParam(name = "schema", required = false) String schema,
            @RequestParam(name = "table", required = false) String table,
            Model model) {

        if (fields != null && fields.size() > 0) {
            model.addAttribute("connection", connection);
            model.addAttribute("query", this.workbenchService.doQuery(fields, catalog, schema, table,connection));
        }

        return this.workbench(model);
    }

    /**
     *
     * @param connection
     * @param query
     * @param model
     * @return
     */
    @PostMapping(path = "/parameter/modal")
    public String parameterModal(
            @RequestParam(name = "connection", required = false) int connection,
            @RequestParam(name = "query", required = false) String query,
            Model model) {

        try {
            model.addAttribute("connection", connection);
            model.addAttribute("query", query);
            model.addAttribute("parameters", templateService.getParameters(query));
        } catch (Exception ex) {
            model.addAttribute("errorMessage", "Fail listing columns " + new Message().getErrorMessage(ex));
        }

        return "workbench/modalQueryParameter::parameter";
    }
}
