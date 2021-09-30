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

import br.com.dafiti.hanger.model.Job;
import br.com.dafiti.hanger.model.JobDetails;
import br.com.dafiti.hanger.model.Subject;
import br.com.dafiti.hanger.service.JobDetailsService;
import br.com.dafiti.hanger.service.JobService;
import br.com.dafiti.hanger.service.SubjectDetailsService;
import br.com.dafiti.hanger.service.SubjectService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import java.util.stream.Collectors;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 *
 * @author Valdiney V GOMES
 */
@Controller
@RequestMapping("/search")
public class JobSearchController {

    private final JobService jobService;
    private final JobDetailsService jobDetailsService;
    private final String SEARCH_COOKIE = "a212aa8752164cfa1bde02b00c6af44a";

    private static final Logger LOG = LogManager.getLogger(JobSearchController.class.getName());

    @Autowired
    public JobSearchController(
            JobService jobService,
            SubjectService subjectService,
            JobDetailsService jobDetails,
            SubjectDetailsService subjectDetailsService) {

        this.jobService = jobService;
        this.jobDetailsService = jobDetails;
    }

    /**
     * Show the search page.
     *
     * @param model Model
     * @param request Request
     * @return Search template
     */
    @GetMapping("/search")
    public String search(
            Model model,
            HttpServletRequest request) {
        List<String> searches = new ArrayList();

        try {
            Cookie[] cookies = request.getCookies();

            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookie.getName().equals(SEARCH_COOKIE)) {
                        searches = new ArrayList<>(Arrays.asList(cookie.getValue().split("#")));
                    }
                }
            }
        } catch (Exception ex) {
            LOG.log(Level.ERROR, "Fail reading cookies", ex);
        }

        model.addAttribute("searches", searches);
        return "search/jobSearch";
    }

    /**
     * Search job details.
     *
     * @param response HttpServletResponse
     * @param request HttpServletRequest
     * @param model Model
     * @param search Search expression.
     * @return Job details details fragment
     */
    @PostMapping("/details/")
    public String details(
            HttpServletResponse response,
            HttpServletRequest request,
            Model model,
            @RequestBody String search) {

        List<Job> jobs = (List) jobService.findByNameContainingOrAliasContaining(search);
        List<String> searches = new ArrayList();

        if (!jobs.isEmpty()) {
            try {
                Cookie[] cookies = request.getCookies();

                if (cookies != null) {
                    for (Cookie cookie : cookies) {
                        if (cookie.getName().equals(SEARCH_COOKIE)) {
                            searches = new ArrayList<>(Arrays.asList(cookie.getValue().split("#")));
                        }
                    }
                }

                if (!searches.contains(search)) {
                    if (searches.size() >= 5) {
                        searches.remove(searches.get(0));
                    }

                    searches.add(search);
                }

                Cookie cookie = new Cookie(SEARCH_COOKIE, String.join("#", searches));
                cookie.setMaxAge(7 * 24 * 60 * 60);
                cookie.setPath("/");
                response.addCookie(cookie);

            } catch (Exception ex) {
                LOG.log(Level.ERROR, "Fail managing cookies", ex);
            }
        }

        request.getCookies();

        List<JobDetails> jobDetails = jobDetailsService.getDetailsOf(jobs);
        //Required parameter for template.
        Map<String, List<JobDetails>> swimlanes = new TreeMap();
        swimlanes.put("ALL", jobDetails
                .stream()
                .sorted((a, b) -> (a.getJob().getName().compareTo(b.getJob().getName())))
                .sorted((a, b) -> a.getStatus().toString().compareTo(b.getStatus().toString())).collect(Collectors.toList()));

        model.addAttribute("swimlanes", swimlanes);
        model.addAttribute("currentSubject", new Subject());

        return "monitor/fragmentJobDetails::jobDetails";
    }
}
