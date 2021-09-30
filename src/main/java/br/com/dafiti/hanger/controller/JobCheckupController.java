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

import br.com.dafiti.hanger.model.CommandLog;
import br.com.dafiti.hanger.model.Job;
import br.com.dafiti.hanger.service.JobApprovalService;
import br.com.dafiti.hanger.service.JobCheckupLogService;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author Valdiney V GOMES
 */
@Controller
@RequestMapping(path = "/checkup")
public class JobCheckupController {

    private final JobApprovalService jobApprovalService;
    private final JobCheckupLogService jobCheckupLogService;

    @Autowired
    public JobCheckupController(
            JobApprovalService jobApprovalService,
            JobCheckupLogService jobCheckupLogService) {

        this.jobApprovalService = jobApprovalService;
        this.jobCheckupLogService = jobCheckupLogService;
    }

    /**
     * Show job checkups and approvals.
     *
     * @param job Job
     * @param model Model
     * @return job checkups and approvals list
     */
    @GetMapping(path = {"/job/{id}/list"})
    public String list(@PathVariable(name = "id") Job job, Model model) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Date dateFrom = Date.from(LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT).atZone(ZoneId.systemDefault()).toInstant());
        Date dateTo = Date.from(LocalDateTime.of(LocalDate.now(), LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant());

        model.addAttribute("job", job);
        model.addAttribute("checkups", jobCheckupLogService.findByJobCheckupAndDateBetween(job, dateFrom, dateTo, 10));
        model.addAttribute("approvals", jobApprovalService.findByJobOrderByCreatedAtDesc(job));
        model.addAttribute("dateFrom", simpleDateFormat.format(dateFrom));
        model.addAttribute("dateTo", simpleDateFormat.format(dateTo));
        model.addAttribute("item", 10);

        return "checkup/list";
    }

    /**
     * Show job checkups and approvals.
     *
     * @param job Job
     * @param dateFrom Date from.
     * @param dateTo Date to.
     * @param item Item per page.
     * @param model Model
     * @return job checkups and approvals list
     */
    @PostMapping(path = "/job/{id}/list")
    public String filter(
            @PathVariable(name = "id") Job job,
            @RequestParam("dateFrom") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date dateFrom,
            @RequestParam("dateTo") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date dateTo,
            @RequestParam(name = "item", defaultValue = "10") int item,
            Model model) {

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        model.addAttribute("job", job);
        model.addAttribute("checkups", jobCheckupLogService.findByJobCheckupAndDateBetween(job, dateFrom, dateTo, item));
        model.addAttribute("approvals", jobApprovalService.findByJobOrderByCreatedAtDesc(job));
        model.addAttribute("dateFrom", simpleDateFormat.format(dateFrom));
        model.addAttribute("dateTo", simpleDateFormat.format(dateTo));
        model.addAttribute("item", item);

        return "checkup/list";
    }

    /**
     * Show the log of a job checkup command.
     *
     * @param commandLog CommandLog
     * @param model Model
     * @return Checkup log
     */
    @GetMapping(path = "/log/{id}")
    public String log(
            @PathVariable(name = "id") CommandLog commandLog,
            Model model) {

        model.addAttribute("log", commandLog);
        return "checkup/log";
    }
}
