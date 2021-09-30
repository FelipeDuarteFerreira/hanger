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
package br.com.dafiti.hanger.repository;

import br.com.dafiti.hanger.model.Auditor;
import br.com.dafiti.hanger.service.AuditorService;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Repository;

/**
 *
 * @author Valdiney V GOMES
 */
@Repository
public class CustomAuditEventRepository {

    @Autowired
    private AuditorService auditorService;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Bean
    public AuditEventRepository auditEventRepository() {
        return new AuditEventRepository() {

            /**
             * Feed actuact events endpoint.
             *
             * @param principal Principal
             * @param after Period
             * @param type Type
             * @return AuditEvent List
             */
            @Override
            public List<AuditEvent> find(String principal, Instant after, String type) {
                Iterable<Auditor> eventAuditor;

                if (principal == null && after == null) {
                    eventAuditor = auditorService.list();
                } else if (after == null) {
                    eventAuditor = auditorService.listByUsername(principal);
                } else {
                    eventAuditor = auditorService.listByUsernameAndDate(principal, Date.from(after));
                }

                return auditorService.getAuditEvent(eventAuditor);
            }

            /**
             * Send a new event message.
             *
             * @param auditEvent AuditEvent
             */
            @Override
            public void add(AuditEvent auditEvent) {
                if (!"anonymousUser".equals(auditEvent.getPrincipal())) {
                    Auditor auditor = new Auditor();
                    auditor.setUsername(auditEvent.getPrincipal());
                    auditor.setType(auditEvent.getType());
                    auditor.setDate(Date.from(auditEvent.getTimestamp()));
                    auditor.setData(auditEvent.getData());

                    jmsTemplate.convertAndSend("queue.auditoring", auditor);
                }
            }
        };
    }
}
