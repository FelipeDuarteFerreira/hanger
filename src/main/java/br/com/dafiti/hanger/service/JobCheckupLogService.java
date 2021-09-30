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

import br.com.dafiti.hanger.model.Job;
import br.com.dafiti.hanger.model.JobCheckup;
import br.com.dafiti.hanger.model.JobCheckupLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import br.com.dafiti.hanger.repository.JobCheckupLogRepository;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class JobCheckupLogService {

    private final JobCheckupLogRepository jobCheckupLogRepository;

    @Autowired
    public JobCheckupLogService(JobCheckupLogRepository jobCheckupLogRepository) {
        this.jobCheckupLogRepository = jobCheckupLogRepository;
    }

    public Iterable<JobCheckupLog> list() {
        return jobCheckupLogRepository.findAll();
    }

    public JobCheckupLog load(Long id) {
        return jobCheckupLogRepository.findById(id).get();
    }

    public JobCheckupLog save(JobCheckupLog jobCheckupLog) {
        return jobCheckupLogRepository.save(jobCheckupLog);
    }

    public void delete(Long id) {
        jobCheckupLogRepository.deleteById(id);
    }

    public void cleaneup(Date expiration) {
        jobCheckupLogRepository.deleteByDateBefore(expiration);
    }

    /**
     * Find a list of checkups and its logs of a job.
     *
     * @param job Job
     * @param from Date from.
     * @param to Date to.
     * @param item Item per page.
     * @return JobCheckupLog map of each JobCheckup.
     */
    public Map<JobCheckup, List<JobCheckupLog>> findByJobCheckupAndDateBetween(Job job, Date from, Date to, int item) {
        Map<JobCheckup, List<JobCheckupLog>> checkupLogs = new LinkedHashMap();

        job.getCheckup().forEach(checkup -> {
            checkupLogs.put(checkup, jobCheckupLogRepository.findByCheckupAndDateBetweenOrderByDateDesc(checkup, from, to, PageRequest.of(0, item)));
        });

        return checkupLogs;
    }
}
