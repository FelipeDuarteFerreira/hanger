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

import br.com.dafiti.hanger.model.AuditorData;
import br.com.dafiti.hanger.model.Blueprint;
import java.util.HashMap;
import java.util.Locale;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

/**
 *
 * @author Guilherme OLIVEIRA
 * @author Valdiney V GOMES
 */
@Service
public class MailService {

    private final ConfigurationService configurationService;
    private final ApplicationContext applicationContext;
    private final AuditorService auditorService;

    private static final Logger LOG = LogManager.getLogger(MailService.class.getName());

    @Autowired
    public MailService(
            ConfigurationService configurationService,
            ApplicationContext applicationContext,
            AuditorService auditorService) {

        this.configurationService = configurationService;
        this.applicationContext = applicationContext;
        this.auditorService = auditorService;
    }

    /**
     * Send mail with a HTML blueprint.
     *
     * @param blueprint blueprint
     */
    @Async
    public void send(Blueprint blueprint) {
        this.send(blueprint, null);
    }

    /**
     * Send mail with a HTML blueprint and extra log.
     *
     * @param blueprint blueprint
     * @param log Extra log information.
     */
    public void send(Blueprint blueprint, String log) {

        try {
            HtmlEmail mail = new HtmlEmail();

            if (blueprint.getRecipients().size() > 0) {
                for (String recipient : blueprint.getRecipients()) {
                    mail.addBcc(recipient);
                }
            }

            this.send(blueprint, mail, log);
        } catch (EmailException ex) {
            LOG.log(Level.ERROR, "Fail sending e-mail", ex);
        }
    }

    /**
     * Send a mail with a HTML blueprint.
     *
     * @param blueprint Blueprint
     * @param mail Email sender.
     * @param info Extra log information.
     * @return Identifies if it was sent sucessfully.
     */
    public boolean send(Blueprint blueprint, HtmlEmail mail, String info) {
        AuditorData auditorData = new AuditorData();

        String host = configurationService.getValue("EMAIL_HOST");
        int port = Integer.valueOf(configurationService.getValue("EMAIL_PORT"));
        String email = configurationService.getValue("EMAIL_ADDRESS");
        String password = configurationService.getValue("EMAIL_PASSWORD");
        boolean sent = true;
        String log = "";

        //Sents a e-mail. 
        try {
            mail.setHostName(host);
            mail.setSmtpPort(port);
            mail.setAuthenticator(new DefaultAuthenticator(email, password));
            mail.setSSLOnConnect(port != 25 && port != 80 && port != 3535);
            mail.addHeader("X-Priority", "1");
            mail.setFrom(email);
            mail.setSubject(blueprint.getSubject());
            mail.setHtmlMsg(this.getTemplateHTMLOf(blueprint.getPath(), blueprint.getTemplate(), blueprint.getVariables()));

            if (blueprint.getFile() != null) {
                mail.attach(blueprint.getFile());
            }

            mail.send();
        } catch (EmailException ex) {
            LOG.log(Level.ERROR, "Fail sending e-mail", ex);
            log = ex.getCause().getMessage();
            
            sent = false;
        }

        //Logs general information and errors in the e-mail. 
        if (info != null) {
            auditorData.addData("log", info);
        }

        auditorData.addData("to", mail.getBccAddresses().toString());
        auditorData.addData("javascript", blueprint.toString());

        if (!sent) {
            auditorData.addData("error", log);
        }

        auditorService.publish("SEND_EMAIL", auditorData.getData());
        return sent;
    }

    /**
     * Fill in a HTML Template.
     *
     * @param path Path
     * @param template Template
     * @param variables Variables
     * @return Filled HTML Template
     */
    private String getTemplateHTMLOf(String path, String template, HashMap<String, Object> variables) {
        //Define the template resolver. 
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix("classpath:" + path + (!path.endsWith("/") ? '/' : ""));
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);

        //Define the template engine. 
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);

        //Proccess the template. 
        return engine.process(template, new Context(Locale.getDefault(), variables));
    }

    /**
     * Verify if configuration of e-mail is ok.
     *
     * @return
     */
    public boolean isEmailOk() {
        String host = configurationService.getValue("EMAIL_HOST");
        String port = configurationService.getValue("EMAIL_PORT");
        String user = configurationService.getValue("EMAIL_ADDRESS");
        String password = configurationService.getValue("EMAIL_PASSWORD");

        return !(host.isEmpty()
                || port.isEmpty()
                || user.isEmpty()
                || password.isEmpty());
    }
}
