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

import br.com.dafiti.hanger.service.AuditorService;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author Valdiney V GOMES
 */
@Controller
@RequestMapping(path = "/log")
public class AuditorController {

    private final AuditorService auditorService;

    @Autowired
    public AuditorController(AuditorService auditorService) {
        this.auditorService = auditorService;
    }

    /**
     * Auditor log list.
     *
     * @param model Model
     * @return Template render.
     * @throws java.text.ParseException
     */
    @GetMapping(path = {"/list", "/list/filter"})
    public String listServer(Model model) throws ParseException {
        this.modelDefault(model);

        return "auditor/list";
    }

    /**
     * Auditor with date filter.
     *
     * @param dateFrom Start Date
     * @param dateTo End date
     * @param types Event type
     * @param model Model
     * @return
     */
    @PostMapping(path = "/list/filter")
    public String filter(
            @RequestParam("dateFrom") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date dateFrom,
            @RequestParam("dateTo") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date dateTo,
            @RequestParam(value = "type", required = false) List<String> types,
            Model model) {

        this.modelDefault(model, dateFrom, dateTo, types);
        return "auditor/list";
    }

    /**
     * Model default.
     *
     * @param model Model
     */
    private void modelDefault(Model model) throws ParseException {
        Date dateFrom = Date.from(LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT).atZone(ZoneId.systemDefault()).toInstant());
        Date dateTo = Date.from(LocalDateTime.of(LocalDate.now(), LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant());

        this.modelDefault(model, dateFrom, dateTo, null);
    }

    /**
     * Model default.
     *
     * @param model Model
     * @param dateFrom Start Date
     * @param dateTo End date
     * @param type Event type
     */
    private void modelDefault(
            Model model,
            Date dateFrom,
            Date dateTo,
            List<String> types) {

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        if (types == null) {
            model.addAttribute("events", auditorService.listDateBetween(dateFrom, dateTo));
        } else {
            model.addAttribute("events", auditorService.listDateBetweenAndTypeIn(dateFrom, dateTo, types));
        }

        model.addAttribute("types", auditorService.listDistinctTypesByDateBetween(dateFrom, dateTo));
        model.addAttribute("dateFrom", simpleDateFormat.format(dateFrom));
        model.addAttribute("dateTo", simpleDateFormat.format(dateTo));
    }
}
