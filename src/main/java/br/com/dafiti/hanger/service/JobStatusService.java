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

import br.com.dafiti.hanger.model.JobBuild;
import br.com.dafiti.hanger.model.JobStatus;
import br.com.dafiti.hanger.option.Flow;
import br.com.dafiti.hanger.option.Status;
import br.com.dafiti.hanger.repository.JobStatusRepository;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
public class JobStatusService {

    private final JobStatusRepository jobStatusRepository;

    @Autowired
    public JobStatusService(JobStatusRepository jobStatusRepository) {
        this.jobStatusRepository = jobStatusRepository;
    }

    public JobStatus save(JobStatus jobStatus) {
        boolean failure = false;

        switch (jobStatus.getFlow()) {
            case UNHEALTHY:
            case BLOCKED:
            case ERROR:
                failure = true;
                break;
            default:
                JobBuild jobBuild = jobStatus.getBuild();

                if (jobBuild != null) {
                    failure = ((jobBuild.getStatus().equals(Status.FAILURE)) || jobBuild.getStatus().equals(Status.ABORTED));
                }
        }

        //Identifies when the failure happened.
        if (failure) {
            jobStatus.setFailureTimestamp(new Date());
        }

        return jobStatusRepository.save(jobStatus);
    }

    public void delete(Long id) {
        jobStatusRepository.deleteById(id);
    }

    public JobStatus updateFlow(JobStatus jobStatus, Flow flow) {
        jobStatus.setFlow(flow);
        return this.save(jobStatus);
    }
}
