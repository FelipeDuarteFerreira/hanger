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
package br.com.dafiti.hanger;

import br.com.dafiti.hanger.model.User;
import br.com.dafiti.hanger.service.ConfigurationService;
import br.com.dafiti.hanger.service.JobApprovalService;
import br.com.dafiti.hanger.service.JobCheckupLogService;
import br.com.dafiti.hanger.service.JobNotificationService;
import br.com.dafiti.hanger.service.JobService;
import br.com.dafiti.hanger.service.PrivilegeService;
import br.com.dafiti.hanger.service.RoleService;
import br.com.dafiti.hanger.service.UserService;
import java.util.Date;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 *
 * @author Valdiney V GOMES
 */
@Component
public class Setup implements ApplicationListener<ContextRefreshedEvent> {

    private final UserService userService;
    private final RoleService roleService;
    private final JobCheckupLogService jobCheckupLogService;
    private final JobApprovalService jobApprovalService;
    private final JobService jobService;
    private final JobNotificationService jobNotificationService;
    private final ConfigurationService configurationService;
    private final PrivilegeService privilegeService;

    private static final Logger LOG = LogManager.getLogger(Setup.class.getName());

    private boolean setup = false;

    @Autowired
    public Setup(
            ConfigurationService configurationService,
            UserService userService,
            RoleService roleService,
            JobCheckupLogService jobCheckupLogService,
            JobApprovalService jobApprovalService,
            JobService jobService,
            JobNotificationService jobNotificationService,
            PrivilegeService privilegeService) {

        this.jobNotificationService = jobNotificationService;
        this.userService = userService;
        this.roleService = roleService;
        this.jobCheckupLogService = jobCheckupLogService;
        this.jobApprovalService = jobApprovalService;
        this.jobService = jobService;
        this.configurationService = configurationService;
        this.privilegeService = privilegeService;
    }

    /**
     * Application setup.
     *
     * @param e ContextRefreshedEvent
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent e) {
        int logRetention;
        
        if (!this.setup) {
            //Setup the configuration table. 
            configurationService.createConfigurationIfNotExists();

            //Setup the additional privileges. 
            privilegeService.createPrivilegeIfNotExists("WORKBENCH");
            privilegeService.createPrivilegeIfNotExists("API");

            //Setup the admin role. 
            roleService.createRoleIfNotExists("USER");
            roleService.createRoleIfNotExists("ADMIN");
            roleService.createRoleIfNotExists("HERO");

            //Setup the super user.
            if (userService.findByUsername("hanger.manager") == null) {
                User user = new User();
                user.setEmail(this.configurationService.findByParameter("EMAIL_ADDRESS").getValue());
                user.setFirstName("Hanger");
                user.setLastName("Manager");
                user.setUsername("hanger.manager");
                user.setPassword("hmanager");
                user.addRole(roleService.findByName("HERO"));

                userService.save(user);
            }

            this.setup = true;
        }

        //Flow notification.
        jobService.list().forEach((job) -> {
            LOG.log(Level.INFO, job.getName());
            jobNotificationService.notify(job, false, true);
        });

        logRetention = Integer.valueOf(
                configurationService.findByParameter("LOG_RETENTION_PERIOD").getValue());

        //Run log cleanup on the context refresh.
        jobCheckupLogService.cleaneup(expiration(logRetention));
        jobApprovalService.cleaneup(expiration(logRetention));
    }

    /**
     * Calculate the expiration date.
     *
     * @param days Days gone.
     * @return Date
     */
    private Date expiration(int days) {
        return new DateTime(new Date()).minusDays(days).toDate();
    }
}
