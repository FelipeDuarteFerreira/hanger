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
import br.com.dafiti.hanger.model.JobBuildGanttFilter;
import br.com.dafiti.hanger.model.JobBuildMetric;
import br.com.dafiti.hanger.model.JobBuildMetricFilter;
import br.com.dafiti.hanger.option.Phase;
import br.com.dafiti.hanger.service.ConfigurationService;
import br.com.dafiti.hanger.service.JobBuildGraphService;
import br.com.dafiti.hanger.service.JobService;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 *
 * @author Valdiney V GOMES
 */
@Controller
@RequestMapping("/build")
public class JobBuildController {

    private final JobService jobService;
    private final JobBuildGraphService jobBuildGraphService;
    private final ConfigurationService configurationService;

    @Autowired
    public JobBuildController(
            JobBuildGraphService jobBuildGraphService,
            JobService jobService,
            ConfigurationService configurationService) {

        this.jobBuildGraphService = jobBuildGraphService;
        this.jobService = jobService;
        this.configurationService = configurationService;
    }

    /**
     * Build heatmap.
     *
     * @param model Model
     * @return Job build heatmap
     */
    @GetMapping(path = {"/heatmap", "/heatmap/filtered"})
    public String heatmap(Model model) {
        model.addAttribute("filter",
                new JobBuildMetricFilter(
                        Phase.STARTED,
                        new Date(),
                        new Date()
                )
        );

        model.addAttribute("detail",
                jobBuildGraphService.getJobBuildDetail(
                        Phase.STARTED,
                        new Date(),
                        new Date()
                )
        );

        model.addAttribute("total",
                jobBuildGraphService.getJobBuildTotal(
                        Phase.STARTED,
                        new Date(),
                        new Date()
                )
        );

        return "build/heatmap";
    }

    /**
     * Build heatmap filtered.
     *
     * @param model Model
     * @param dateFrom Date from
     * @param dateTo Date to
     * @param phase Phase
     * @return Job build heatmap
     */
    @PostMapping(path = "/heatmap/filtered")
    public String heatmapFilter(
            @RequestParam("phase") Phase phase,
            @RequestParam("dateFrom") @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam("dateTo") @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo,
            Model model) {

        model.addAttribute("filter",
                new JobBuildMetricFilter(
                        phase,
                        dateFrom,
                        dateTo
                )
        );

        model.addAttribute("detail",
                jobBuildGraphService.getJobBuildDetail(
                        phase,
                        dateFrom,
                        dateTo
                )
        );

        model.addAttribute("total",
                jobBuildGraphService.getJobBuildTotal(
                        phase,
                        dateFrom,
                        dateTo
                )
        );

        return "build/heatmap";
    }

    /**
     * Build gantt.
     *
     * @param model Model
     * @return Job build gantt
     */
    @GetMapping({"/gantt"})
    public String gantt(Model model) {
        model.addAttribute("filter",
                new JobBuildMetricFilter(
                        Phase.STARTED,
                        new Date(),
                        new Date()
                )
        );
        model.addAttribute("jobs", jobService.listFromCache());
        return "build/gantt";
    }

    /**
     * Build gantt date in a DHTMLXGantt fashion.
     *
     * @param filter
     * @param model Model
     * @return Gantt data in a DHTMLXGantt fashion.
     */
    @ResponseBody
    @PostMapping(path = "/gantt/filtered", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, List> ganttFilter(
            @RequestBody JobBuildGanttFilter filter,
            Model model) {

        Map<String, List> data = new HashMap<>();
        data.put("data",
                jobBuildGraphService.getDHTMLXGanttData(
                        filter.getJobs(),
                        filter.getFrom(),
                        filter.getTo()
                )
        );

        return data;
    }

    /**
     * Build history modal.
     *
     * @param job Job
     * @param model Model
     * @return Build history modal
     */
    @GetMapping({"/history/{id}"})
    public String history(
            @PathVariable(value = "id") Job job,
            Model model) {

        //Identifies the retention period. 
        String retentionPeriod = configurationService
                .findByParameter("LOG_RETENTION_PERIOD")
                .getValue();

        model.addAttribute("job", job);
        model.addAttribute("history",
                jobBuildGraphService.findBuildHistory(
                        Arrays.asList(job),
                        LocalDate
                                .now()
                                .minusDays(Integer.valueOf(retentionPeriod))
                                .toDate(),
                        new Date()
                ).stream()
                        .sorted(Comparator.comparing(JobBuildMetric::getStartDate).reversed())
                        .limit(Integer.valueOf(retentionPeriod))
                        .collect(Collectors.toList())
        );

        return "build/modalBuildHistory::history";
    }
}
