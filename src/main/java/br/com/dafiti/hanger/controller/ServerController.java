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
package br.com.dafiti.hanger.controller;

import br.com.dafiti.hanger.exception.Message;
import br.com.dafiti.hanger.model.Job;
import br.com.dafiti.hanger.model.Server;
import br.com.dafiti.hanger.service.JenkinsService;
import br.com.dafiti.hanger.service.JobService;
import br.com.dafiti.hanger.service.ServerService;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 *
 * @author Valdiney V GOMES
 */
@Controller
@RequestMapping(path = "/server")
public class ServerController {

    private final ServerService serverService;
    private final JenkinsService jenkinsService;
    private final JobService jobService;

    @Autowired
    public ServerController(
            ServerService serverService,
            JenkinsService jenkinsService,
            JobService jobService) {

        this.serverService = serverService;
        this.jenkinsService = jenkinsService;
        this.jobService = jobService;
    }

    /**
     * Save a server.
     *
     * @param server Server
     * @param bindingResult BindingResult
     * @param model Model
     * @return Server edit template.
     */
    @PostMapping(path = "/save")
    public String saveServer(@Valid @ModelAttribute Server server, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("server", server);
            return "server/edit";
        }

        try {
            serverService.save(server);
        } catch (Exception ex) {
            model.addAttribute("errorMessage", new Message().getErrorMessage(ex));
        } finally {
            model.addAttribute("servers", serverService.list());
        }

        return "server/list";
    }

    /**
     * Add a server.
     *
     * @param model Model
     * @return Server edit template.
     */
    @GetMapping(path = "/add")
    public String addServer(Model model) {
        model.addAttribute("server", new Server());
        return "server/edit";
    }

    /**
     * List servers.
     *
     * @param model Model
     * @return Server list template.
     */
    @GetMapping(path = "/list")
    public String listServer(Model model) {
        model.addAttribute("servers", serverService.list());
        return "server/list";
    }

    /**
     * Edit a server.
     *
     * @param model Model
     * @param id ID
     * @return Server edit template.
     */
    @GetMapping(path = "/edit/{id}")
    public String editServer(Model model, @PathVariable(value = "id") Long id) {
        model.addAttribute("server", serverService.load(id));
        return "server/edit";
    }

    /**
     * Delete a server.
     *
     * @param id ID
     * @param model Model
     * @return Server list template
     */
    @GetMapping(path = "/delete/{id}")
    public String deleteServer(@PathVariable(name = "id") Long id, Model model) {
        try {
            serverService.delete(id);
        } catch (Exception ex) {
            if (ex.getClass() == DataIntegrityViolationException.class) {
                model.addAttribute("errorMessage", "This server is being used. Remove the dependencies before deleting the server!");
            } else {
                model.addAttribute("errorMessage", "Fail deleting the server: " + ex.getMessage());
            }
        } finally {
            model.addAttribute("servers", serverService.list());
        }

        return "server/list";
    }

    /**
     * Test server connection.
     *
     * @param server Server
     * @param model Model
     * @return Identify if connected sucessfully.
     */
    @GetMapping(path = "/test/{id}")
    public String serverIsRunning(@PathVariable(name = "id") Server server, Model model) {
        model.addAttribute("servers", serverService.list());

        if (jenkinsService.isRunning(server)) {
            String message = server.getName() + " server is running.";

            if (!jenkinsService.hasNotificationPlugin(server)) {
                model.addAttribute("errorMessage", message + " Notification plugin is not deployed!");
            } else if (!jenkinsService.hasBuildBlockerPlugin(server)) {
                model.addAttribute("errorMessage", message + " Build Blocker plugin is not deployed!");
            } else {
                model.addAttribute("successMessage", message + " Plugins Notification and Build Blocker are deployed!");
            }
        } else {
            model.addAttribute("errorMessage", server.getName() + " server is not running!");
        }

        return "server/list";
    }

    /**
     * Sync job list.
     *
     * @param server Server
     * @param model Model
     * @return Job list
     */
    @GetMapping(path = "/import/{serverID}")
    public String sync(
            @PathVariable(value = "serverID") Server server,
            Model model) {

        model.addAttribute("servers", serverService.list());

        if (server != null) {
            if (jenkinsService.isRunning(server)) {
                try {
                    List<String> jobs = jenkinsService.listJob(server);

                    for (String job : jobs) {
                        if (jenkinsService.isBuildable(job, server)) {
                            if (jobService.findByName(job) == null) {
                                Job jobSynced = new Job(job, server);
                                jobService.save(jobSynced);
                                jenkinsService.updateJob(jobSynced);
                            }
                        }
                    }

                    jobService.refresh();
                    model.addAttribute("successMessage", server.getName() + " is synced!");
                } catch (URISyntaxException | IOException ex) {
                    model.addAttribute("errorMessage", "Fail listing jobs from Jenkins: " + ex.getMessage());
                }
            } else {
                model.addAttribute("errorMessage", server.getName() + " is not connected!");
            }
        }

        return "server/list";
    }
}
