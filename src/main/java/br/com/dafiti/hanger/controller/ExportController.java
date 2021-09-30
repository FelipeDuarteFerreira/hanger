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

import br.com.dafiti.hanger.model.Connection;
import br.com.dafiti.hanger.option.ExportType;
import br.com.dafiti.hanger.service.ExportService;
import java.io.IOException;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author Helio Leal
 */
@Controller
@RequestMapping(path = "/export")
public class ExportController {

    private final ExportService exportService;

    @Autowired
    public ExportController(
            ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * Export a query to CSV format.
     *
     * @param connection Connection
     * @param query String query of the user.
     * @param principal Logged User.
     * @return String file name
     * @throws IOException
     */
    @PostMapping(path = "/query/{id}")
    @ResponseBody
    public String export(
            @PathVariable(name = "id") Connection connection,
            @RequestBody(required = false) String query,
            Principal principal)
            throws IOException {

        String fileName = this.exportService.export(
                connection,
                query,
                principal,
                ExportType.CSV);

        return fileName;
    }

    /**
     * Donwload a file.
     *
     * @param file
     * @return
     * @throws java.io.IOException
     */
    @GetMapping(value = "/download/{file}")
    public HttpEntity<?> dowload(
            @PathVariable(name = "file") String file) throws IOException {

        return this.exportService.download(file);
    }
}
