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
package br.com.dafiti.hanger.mq;

import br.com.dafiti.hanger.model.Auditor;
import br.com.dafiti.hanger.model.Blueprint;
import br.com.dafiti.hanger.service.AuditorService;
import br.com.dafiti.hanger.service.MailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 *
 * @author Valdiney V GOMES
 */
@Component
public class QueueListener {

    private final AuditorService auditorService;
    private final MailService mailService;

    @Autowired
    public QueueListener(
            AuditorService auditorService,
            MailService mailService) {

        this.auditorService = auditorService;
        this.mailService = mailService;
    }

    @JmsListener(destination = "queue.auditoring")
    public void onMessage(Auditor auditor) {
        auditorService.save(auditor);
    }

    @JmsListener(destination = "queue.mailing")
    public void onMessage(Blueprint blueprint) {
        mailService.send(blueprint);
    }
}
