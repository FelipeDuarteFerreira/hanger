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
package br.com.dafiti.hanger.service;

import br.com.dafiti.hanger.model.Auditor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import br.com.dafiti.hanger.repository.AuditorRepository;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class AuditorService {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final AuditorRepository eventAuditorRepository;

    @Autowired
    public AuditorService(
            AuditorRepository eventLogRepository,
            ApplicationEventPublisher applicationEventPublisher) {

        this.eventAuditorRepository = eventLogRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public Auditor load(Long id) {
        return eventAuditorRepository.findById(id).get();
    }

    public Iterable<Auditor> list() {
        return eventAuditorRepository.findAll();
    }

    public Iterable<Auditor> listDateBetween(Date dateFrom, Date dateTo) {
        return eventAuditorRepository.findAllByDateBetweenOrderByDateDesc(dateFrom, dateTo);
    }

    public Iterable<Auditor> listDateBetweenAndTypeIn(Date dateFrom, Date dateTo, List<String> types) {
        return eventAuditorRepository.findAllByDateBetweenAndTypeInOrderByDateDesc(dateFrom, dateTo, types);
    }

    public Iterable<Auditor> listByUsername(String username) {
        return eventAuditorRepository.findByUsername(username);
    }

    public Iterable<Auditor> listByUsernameAndDate(String username, Date dateFrom) {
        return eventAuditorRepository.findByUsernameAndDate(username, dateFrom);
    }

    public Iterable<String> listDistinctTypesByDateBetween(Date dateFrom, Date dateTo) {
        return eventAuditorRepository.findDistinctTypesByDateBetween(dateFrom, dateTo);
    }

    public void save(Auditor logger) {
        eventAuditorRepository.save(logger);
    }

    public void delete(Long id) {
        eventAuditorRepository.deleteById(id);
    }

    /**
     * Publishes an event.
     *
     * @param type Event type
     */
    public void publish(String type) {
        this.publish(type, new HashMap<>());
    }

    /**
     * Publishes an event.
     *
     * @param type Event type
     * @param data Event payload
     */
    public void publish(String type, Map<String, Object> data) {
        String username = "";
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication != null) {
            username = authentication.getName();
        }

        applicationEventPublisher.publishEvent(
                new AuditApplicationEvent(
                        new AuditEvent(
                                username,
                                type,
                                data
                        )
                )
        );
    }

    /**
     * Cast an Auditor to AuditEvent.
     *
     * @param auditor Auditor
     * @return AuditEvent list
     */
    public List<AuditEvent> getAuditEvent(Iterable<Auditor> auditor) {
        List<AuditEvent> auditEvent = new ArrayList();

        for (Auditor event : auditor) {
            Map<String, Object> data = new HashMap();

            event.getData().entrySet().forEach(entry -> {
                data.put(entry.getKey(), entry.getValue());
            });

            auditEvent.add(new AuditEvent(
                    event.getDate().toInstant(),
                    event.getUsername(),
                    event.getType(),
                    data)
            );
        }

        return auditEvent;
    }
}
