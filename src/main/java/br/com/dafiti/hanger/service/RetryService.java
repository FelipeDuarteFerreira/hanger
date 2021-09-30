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
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 *
 * @author Valdiney V GOMES
 */
@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class RetryService {

    private final ConcurrentHashMap<Job, Integer> retry;

    /**
     * Constructor.
     */
    public RetryService() {
        retry = new ConcurrentHashMap<>();
    }

    /**
     * Identify if exists a job.
     *
     * @param job JOB
     * @return Identify if exists a key
     */
    public boolean exists(Job job) {
        return retry.containsKey(job);
    }

    /**
     * Increse the retry count.
     *
     * @param job Job
     */
    public void increase(Job job) {
        if (retry.containsKey(job)) {
            retry.put(job, retry.get(job) + 1);
        } else {
            retry.put(job, 1);
        }
    }

    /**
     * Get the retry count.
     *
     * @param job Job
     * @return Retry count
     */
    public int get(Job job) {
        int value = 0;

        if (retry.containsKey(job)) {
            value = retry.get(job);
        } else {
            retry.put(job, value);
        }
        
        return value;
    }

    /**
     * Remove the retry job.
     *
     * @param job Job
     */
    public void remove(Job job) {
        retry.remove(job);
    }
}