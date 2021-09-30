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
import br.com.dafiti.hanger.model.AuditorData;
import br.com.dafiti.hanger.model.Subject;
import br.com.dafiti.hanger.model.User;
import br.com.dafiti.hanger.model.JobDetails;
import br.com.dafiti.hanger.service.AuditorService;
import br.com.dafiti.hanger.service.SlackService;
import br.com.dafiti.hanger.service.SubjectService;
import br.com.dafiti.hanger.service.UserService;
import br.com.dafiti.hanger.service.JobDetailsService;
import br.com.dafiti.hanger.service.JobService;
import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 *
 * @author Valdiney V GOMES
 */
@Controller
@RequestMapping(path = "/subject")
public class SubjectController {

    private final SubjectService subjectService;
    private final SlackService slackService;
    private final UserService userService;
    private final JobService jobService;
    private final JobDetailsService jobDetailsService;
    private final AuditorService auditorService;

    @Autowired
    public SubjectController(
            SubjectService subjectService,
            SlackService slackService,
            UserService userService,
            JobService jobService,
            JobDetailsService jobDetailsService,
            AuditorService auditorService) {

        this.subjectService = subjectService;
        this.slackService = slackService;
        this.userService = userService;
        this.jobService = jobService;
        this.jobDetailsService = jobDetailsService;
        this.auditorService = auditorService;
    }

    /**
     * Save a subject.
     *
     * @param subject
     * @param bindingResult
     * @param model
     * @param principal
     * @return
     */
    @PostMapping(path = "/save")
    public String saveSubject(@Valid @ModelAttribute Subject subject,
            BindingResult bindingResult,
            Model model,
            Principal principal) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("subject", subject);
            return "subject/edit";
        }

        try {
            //Set subject creator as subscriber. 
            if (subject.getId() == null) {
                User user = userService.findByUsername(principal.getName());

                if (user != null) {
                    subject.addUser(user);
                }
            }

            subjectService.save(subject);
        } catch (Exception ex) {
            model.addAttribute("errorMessage", new Message().getErrorMessage(ex));
        } finally {
            modelDefault(model);
        }

        return "subject/list";
    }

    /**
     * Add a subject.
     *
     * @param model
     * @return
     */
    @GetMapping(path = "/add")
    public String addSubject(Model model) {
        model.addAttribute("subject", new Subject());
        return "subject/edit";
    }

    /**
     * List subjects.
     *
     * @param model
     * @param authentication
     * @return
     */
    @GetMapping(path = "/list")
    public String listSubject(Model model, Authentication authentication) {
        modelDefault(model);
        return "subject/list";
    }

    /**
     * Edit a subject.
     *
     * @param model
     * @param id
     * @return
     */
    @GetMapping(path = "/edit/{id}")
    public String editSubject(Model model, @PathVariable(value = "id") Long id) {
        model.addAttribute("subject", subjectService.load(id));
        return "subject/edit";
    }

    /**
     * Delete a subject.
     *
     * @param id
     * @param model
     * @return
     */
    @GetMapping(path = "/delete/{id}")
    public String deleteSubject(@PathVariable(name = "id") Long id, Model model) {
        try {
            subjectService.delete(id);
        } catch (Exception ex) {
            if (ex.getClass() == DataIntegrityViolationException.class) {
                model.addAttribute("errorMessage", "This subject is being used. Remove the dependencies before deleting the subject!");
            } else {
                model.addAttribute("errorMessage", "Fail deleting the subject: " + ex.getMessage());
            }
        } finally {
            modelDefault(model);
        }

        return "subject/list";
    }

    /**
     * Model default attribute.
     *
     * @param model Model
     */
    private void modelDefault(Model model) {
        model.addAttribute("subjects", subjectService.list());
        model.addAttribute("loggedIn", userService.getLoggedIn());
    }

    /**
     * Slack channel list modal.
     *
     * @param model Model
     * @return Slack channel modal
     */
    @GetMapping(path = "/modal/channel")
    public String slackChannelListModal(Model model) {
        model.addAttribute("channels", slackService.getChannels());
        return "subject/modalSlackChannel::channel";
    }

    /**
     * Add Slack channels.
     *
     * @param subject Subject
     * @param slackChannelList Slack channel list
     * @param bindingResult BindingResult
     * @param model Model
     * @return Subject edit
     */
    @PostMapping(path = "/save", params = {"partial_add_slack_channel"})
    public String addSlackChannel(
            @Valid @ModelAttribute Subject subject,
            @RequestParam(value = "slackChannelList", required = false) Set<String> slackChannelList,
            BindingResult bindingResult,
            Model model) {

        try {
            subject.getChannel().addAll(slackChannelList);
        } catch (Exception ex) {
            model.addAttribute("errorMessage", new Message().getErrorMessage(ex));
        } finally {
            model.addAttribute("subject", subject);
        }

        return "subject/edit";
    }

    /**
     * Remove a Slack Channel.
     *
     * @param subject Subject
     * @param slackChannel Slack channel
     * @param bindingResult BindingResult
     * @param model Model
     * @return Subject edit
     */
    @PostMapping(path = "/save", params = {"partial_remove_slack_channel"})
    public String removeSlackChannel(
            @ModelAttribute Subject subject,
            @RequestParam(value = "partial_remove_slack_channel", required = false) String slackChannel,
            BindingResult bindingResult,
            Model model) {

        subject.getChannel().remove(slackChannel);
        model.addAttribute("subject", subject);

        return "subject/edit";
    }

    /**
     *
     * @param subject Subject.
     * @param key Swimlane name.
     * @param value Criteria expression.
     * @param bindingResult
     * @param model
     * @return Subject edit template.
     */
    @PostMapping(path = "/save", params = {"partial_add_swimlane"})
    public String addSwimlane(
            @Valid @ModelAttribute Subject subject,
            @RequestParam(value = "key", required = false) String key,
            @RequestParam(value = "value", required = false) String value,
            BindingResult bindingResult,
            Model model) {

        try {
            //Identifies if the criteria is a valid regexp.
            "DUMMY".matches(value);
            subject.getSwimlane().put(key.toUpperCase(), value);
        } catch (Exception ex) {
            model.addAttribute("errorMessage", new Message().getErrorMessage(ex));
        } finally {
            model.addAttribute("subject", subject);
        }

        return "subject/edit";
    }

    /**
     *
     * @param subject Subject
     * @param key Swimlane name.
     * @param bindingResult
     * @param model
     * @return Subject edit template.
     */
    @PostMapping(path = "/save", params = {"partial_remove_swimlane"})
    public String removeSwimlane(
            @ModelAttribute Subject subject,
            @RequestParam(value = "partial_remove_swimlane", required = false) String key,
            BindingResult bindingResult,
            Model model) {

        subject.getSwimlane().remove(key);
        model.addAttribute("subject", subject);
        return "subject/edit";
    }

    /**
     * Build all jobs in a swimlane.
     *
     * @param model Model
     * @param subject Subject
     * @param swimlane Swimlane name
     * @return Identifies if the job was built successfully.
     */
    @GetMapping(path = "/build/swimlane/{id}/{swimlane}")
    @ResponseBody
    public boolean buildSwimlane(
            Model model,
            @PathVariable(value = "id") Subject subject,
            @PathVariable(value = "swimlane") String swimlane) {

        auditorService.publish("BUILD_SWIMLANE",
                new AuditorData()
                        .addData("subject", "Subject: " + subject.getName())
                        .addData("swimlane", "Swimlane: " + swimlane)
                        .getData());

        subjectService.buildSwimline(subject, swimlane);

        return true;
    }

    /**
     * Subscribe logged user in a subject.
     *
     * @param response
     * @param request
     * @param model
     * @param id
     */
    @PostMapping("/subscribe/")
    public void subscribe(
            HttpServletResponse response,
            HttpServletRequest request,
            Model model,
            @RequestBody String id) {

        Subject subject = subjectService.load(Long.parseLong(id));

        if (subject != null) {
            User user = userService.getLoggedIn();

            if (user != null) {
                subject.addUser(user);
            }
        }

        subjectService.save(subject);
    }

    /**
     * Unsubscribe logged user in a subject.
     *
     * @param response
     * @param request
     * @param model
     * @param id
     */
    @PostMapping("/unsubscribe/")
    public void unsubscribe(
            HttpServletResponse response,
            HttpServletRequest request,
            Model model,
            @RequestBody String id) {

        Subject subject = subjectService.load(Long.parseLong(id));

        if (subject != null) {
            User user = userService.getLoggedIn();

            if (user != null) {
                subject.getUser().remove(user);
            }
        }

        subjectService.save(subject);
    }

    /**
     * Subject details modal.
     *
     * @param subject Subject
     * @param model Model
     * @return Subject detail modal
     */
    @GetMapping(path = "/modal/detail/{id}")
    public String subjectDetailModal(
            @PathVariable("id") Subject subject,
            Model model) {
        subject = subjectService.load(subject.getId());

        if (subject != null) {
            List<JobDetails> jobDetails = jobDetailsService.getDetailsOf(jobService.findBySubjectOrderByName(subject));
            model.addAttribute("jobDetails", jobDetails
                    .stream()
                    .sorted((a, b) -> (a.getJob().getName().compareTo(b.getJob().getName())))
                    .collect(Collectors.toList()));
            model.addAttribute("subject", subject);
        }

        return "subject/modalJobDetails::detail";
    }
}
