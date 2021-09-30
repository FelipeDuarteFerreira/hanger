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
import br.com.dafiti.hanger.model.User;
import br.com.dafiti.hanger.service.WorkbenchQueryService;
import br.com.dafiti.hanger.service.UserService;
import br.com.dafiti.hanger.service.ConnectionService;
import java.security.Principal;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 *
 * @author Helio Leal
 * @author Fernando Saga
 */
@Controller
@RequestMapping(path = "/query")
public class WorkbenchQueryController {

    private final WorkbenchQueryService workbenchQueryService;
    private final UserService userService;
    private final ConnectionService connectionService;

    @Autowired
    public WorkbenchQueryController(
            WorkbenchQueryService workbenchQueryService,
            UserService userService,
            ConnectionService connectionService) {

        this.workbenchQueryService = workbenchQueryService;
        this.userService = userService;
        this.connectionService = connectionService;
    }

    /**
     * WorkbenchQuery modal.
     *
     * @param connection
     * @param query
     * @param model Model
     * @param principal
     *
     * @return Test connections modal
     */
    @PostMapping(path = "/modal/{id}")
    public String queryModal(
            @PathVariable(name = "id") Connection connection,
            @RequestBody(required = false) String query,
            Model model,
            Principal principal) {

        User user = userService.findByUsername(principal.getName());

        if (user != null) {
            WorkbenchQuery workbenchQuery
                    = new WorkbenchQuery(connection, user);

            if (query != null && !query.isEmpty()) {
                workbenchQuery.setQuery(query);
            }
            model.addAttribute("workbenchQuery", workbenchQuery);
        }

        return "workbench/query/modalQuery::query";
    }

    /**
     * Save a WorkbenchQuery.
     *
     * @param redirectAttributes
     * @param workbenchQuery
     * @param model
     * @param principal
     *
     * @return redirect to a page.
     */
    @PostMapping(path = "/save")
    public String save(
            RedirectAttributes redirectAttributes,
            @Valid @ModelAttribute WorkbenchQuery workbenchQuery,
            Model model,
            Principal principal) {
        boolean update = workbenchQuery.getId() != null;
        String redirect = "redirect:/query/stored/";

        try {
            workbenchQueryService.save(workbenchQuery);

            if (update) {
                redirect = "redirect:/query/view/" + workbenchQuery.getId();
            }
        } catch (Exception ex) {
            model.addAttribute("workbenchQuery", workbenchQuery);
            redirectAttributes.addFlashAttribute("errorMessage", new Message().getErrorMessage(ex));
        }

        return redirect;
    }

    /**
     * List queries.
     *
     * @param model
     * @param principal
     * @return
     */
    @GetMapping(path = "/stored")
    public String list(
            Model model,
            Principal principal) {

        User user = userService.findByUsername(principal.getName());

        if (user != null) {
            model.addAttribute("workbenchQueryList",
                    this.workbenchQueryService.findByUserOrSharedTrue(user));
        }

        return "workbench/query/list";
    }

    /**
     * Call WorkbenchQuery modal.
     *
     * @param workbenchQuery
     * @param model Model
     * @param principal
     *
     * @return Test connections modal
     */
    @GetMapping(path = "/modal/{id}")
    public String queryLoadModal(
            @PathVariable(name = "id") WorkbenchQuery workbenchQuery,
            Model model,
            Principal principal) {

        User user = userService.findByUsername(principal.getName());

        if (user != null) {
            model.addAttribute("workbenchQuery", workbenchQuery);
        }

        return "workbench/query/modalQuery::query";
    }

    /**
     * Delete a WorkbenchQuery.
     *
     * @param id
     * @param model
     * @param principal
     * @return
     */
    @GetMapping(path = "/delete/{id}")
    public String delete(
            @PathVariable(name = "id") Long id,
            Model model,
            Principal principal) {

        try {
            workbenchQueryService.delete(id);
        } catch (Exception ex) {
            model.addAttribute("errorMessage",
                    "Fail deleting the connection query: " + ex.getMessage());
        }

        return this.list(model, principal);
    }

    /**
     * Edit a WorkbenchQuery.
     *
     * @param model Model
     * @param workbenchQuery WorkbenchQuery
     * @return String edit
     */
    @GetMapping(path = "/edit/{id}")
    public String edit(
            Model model,
            @PathVariable(value = "id") WorkbenchQuery workbenchQuery) {

        modelDefault(model, workbenchQuery);
        return "workbench/query/edit";
    }

    /**
     * Add a WorkbenchQuery.
     *
     * @param model Model
     * @param principal Principal
     * @return
     */
    @GetMapping(path = "/add")
    public String add(Model model, Principal principal) {
        User user = userService.findByUsername(principal.getName());

        if (user != null) {
            WorkbenchQuery workbenchQuery = new WorkbenchQuery();
            workbenchQuery.setUser(user);
            modelDefault(model, workbenchQuery);
        }

        return "workbench/query/edit";
    }

    /**
     * View a WorkbenchQuery.
     *
     * @param model Model
     * @param workbenchQuery WorkbenchQuery
     * @return String view
     */
    @GetMapping(path = "/view/{id}")
    public String view(
            Model model,
            @PathVariable(value = "id") WorkbenchQuery workbenchQuery) {

        modelDefault(model, workbenchQuery);
        return "workbench/query/view";
    }

    /**
     * Default model
     *
     * @param model Model
     * @param workbenchQuery WorkbenchQuery
     */
    private void modelDefault(Model model, WorkbenchQuery workbenchQuery) {
        model.addAttribute("workbenchQuery", workbenchQuery);
        model.addAttribute("connections", connectionService.list());
    }
}
