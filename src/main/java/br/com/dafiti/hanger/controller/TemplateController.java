/*
 * Copyright (c) 2020 Dafiti Group
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
import br.com.dafiti.hanger.model.Template;
import br.com.dafiti.hanger.service.TemplateService;
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
@RequestMapping(path = "/template")
public class TemplateController {

    private final TemplateService templateService;

    @Autowired
    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * Save a template.
     *
     * @param template Template
     * @param bindingResult BindingResult
     * @param model Model
     * @return Template edit template.
     */
    @PostMapping(path = "/save")
    public String saveTemplate(
            @Valid @ModelAttribute Template template,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("template", template);
            return "template/edit";
        }

        try {
            templateService.save(template);
        } catch (Exception ex) {
            model.addAttribute("errorMessage", new Message().getErrorMessage(ex));
            model.addAttribute("template", template);
            return "template/edit";
        } finally {
            model.addAttribute("templates", templateService.list());
        }

        return "redirect:/template/view/" + template.getId();
    }

    /**
     * Add a template.
     *
     * @param model Model
     * @return Template edit template.
     */
    @GetMapping(path = "/add")
    public String addTemplate(Model model) {
        model.addAttribute("template", new Template());
        return "template/edit";
    }

    /**
     * List templates.
     *
     * @param model Model
     * @return Template list template.
     */
    @GetMapping(path = "/list")
    public String listTemplate(Model model) {
        model.addAttribute("templates", templateService.list());
        return "template/list";
    }
    
    /**
     * View a template.
     *
     * @param model Model
     * @param id ID
     * @return Template view template.
     */
    @GetMapping(path = "/view/{id}")
    public String viewTemplate(Model model, @PathVariable(value = "id") Long id) {
        model.addAttribute("template", templateService.load(id));
        return "template/view";
    }    

    /**
     * Edit a template.
     *
     * @param model Model
     * @param id ID
     * @return Template edit template.
     */
    @GetMapping(path = "/edit/{id}")
    public String editTemplate(Model model, @PathVariable(value = "id") Long id) {
        model.addAttribute("template", templateService.load(id));
        return "template/edit";
    }

    /**
     * Delete a template.
     *
     * @param id ID
     * @param model Model
     * @return Template list template
     */
    @GetMapping(path = "/delete/{id}")
    public String deleteTemplate(@PathVariable(name = "id") Long id, Model model) {
        try {
            templateService.delete(id);
        } catch (Exception ex) {
            if (ex.getClass() == DataIntegrityViolationException.class) {
                model.addAttribute("errorMessage", "This template is being used. Remove the dependencies before deleting the template!");
            } else {
                model.addAttribute("errorMessage", "Fail deleting the template: " + ex.getMessage());
            }
        } finally {
            model.addAttribute("templates", templateService.list());
        }

        return "template/list";
    }
}
