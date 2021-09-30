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

import br.com.dafiti.hanger.model.Configuration;
import br.com.dafiti.hanger.model.ConfigurationGroup;
import br.com.dafiti.hanger.repository.ConfigurationRepository;
import br.com.dafiti.hanger.security.PasswordCryptor;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

/**
 *
 * @author Helio Leal
 */
@Service
public class ConfigurationService {

    private final ConfigurationRepository configurationRepository;
    private final ConfigurationGroupService configurationGroupService;
    private final PasswordCryptor passwordCryptor;

    @Autowired
    public ConfigurationService(
            ConfigurationRepository configurationRepository,
            ConfigurationGroupService configurationGroupService,
            PasswordCryptor passwordCryptor) {

        this.configurationRepository = configurationRepository;
        this.configurationGroupService = configurationGroupService;
        this.passwordCryptor = passwordCryptor;
    }

    public Iterable<Configuration> list() {
        return configurationRepository.findAll();
    }

    public Configuration load(Long id) {
        return configurationRepository.findById(id).get();
    }

    /**
     * Find a parameter value based on the parameter name.
     *
     * @param parameter name of the parameter.
     * @return Configuration object.
     */
    @Cacheable(value = "parameters", key = "#parameter")
    public Configuration findByParameter(String parameter) {
        Configuration configuration = configurationRepository.findByParameter(parameter);

        if (configuration != null) {
            if (configuration.getType().toUpperCase().equals("PASSWORD")) {
                configuration.setValue(passwordCryptor.decrypt(configuration.getValue()));
            }
        }

        return configuration;
    }

    /**
     * Value of a parameter.
     *
     * @param parameter name of the parameter.
     * @return String with the parameter value.
     */
    public String getValue(String parameter) {
        String value = "";
        Configuration configuration = this.findByParameter(parameter);

        if (configuration != null) {
            if (configuration.getValue() != null) {
                value = configuration.getValue();
            }
        }

        return value;
    }

    @Caching(evict = {
        @CacheEvict(value = "parameters", allEntries = true)})
    public void save(Configuration configuration) {
        save(configuration, false);
    }

    public void save(Configuration configuration, boolean add) {
        Configuration config = configurationRepository.findByParameter(configuration.getParameter());

        if (config == null || !add) {
            if (!add) {
                configuration.setId(config.getId());
            }

            if (configuration.getType().toUpperCase().equals("PASSWORD")) {
                configuration.setValue(passwordCryptor.encrypt(configuration.getValue()));
            }

            configurationRepository.save(configuration);
        }
    }

    /**
     * Insert default values into Configuration table.
     */
    public void createConfigurationIfNotExists() {
        //E-mail configuration.
        ConfigurationGroup emailGroup = new ConfigurationGroup("E-mail");
        emailGroup = this.configurationGroupService.save(emailGroup, true);
        this.save(new Configuration(
                "Host",
                "EMAIL_HOST",
                "",
                "text",
                emailGroup,
                10,
                255,
                "*"), true);

        this.save(new Configuration(
                "Port",
                "EMAIL_PORT",
                "",
                "number",
                emailGroup,
                0,
                9999,
                "*"), true);

        this.save(new Configuration(
                "Address",
                "EMAIL_ADDRESS",
                "hanger.manager@dafiti.com.br",
                "text",
                emailGroup,
                0,
                255,
                "[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,3}$"), true);

        this.save(new Configuration(
                "Password",
                "EMAIL_PASSWORD",
                "",
                "password",
                emailGroup,
                0,
                50,
                "*"), true);

        //Slack configuration.
        ConfigurationGroup slackGroup = new ConfigurationGroup("Slack");
        slackGroup = this.configurationGroupService.save(slackGroup, true);
        this.save(new Configuration(
                "Default channel",
                "SLACK_CHANNEL",
                "",
                "text",
                slackGroup,
                5,
                255,
                "*"), true);

        this.save(new Configuration(
                "Bot token",
                "SLACK_BOT_TOKEN",
                "",
                "password",
                slackGroup,
                5,
                255,
                "*"), true);

        this.save(new Configuration(
                "Incoming WebHooks URL",
                "SLACK_WEBHOOK_URL",
                "",
                "text",
                slackGroup,
                5,
                255,
                "*"), true);

        //Generic configuration.
        ConfigurationGroup others = new ConfigurationGroup("Others");
        others = this.configurationGroupService.save(others, true);
        this.save(new Configuration(
                "Log retention (in days)",
                "LOG_RETENTION_PERIOD",
                "90",
                "number",
                others,
                15,
                365,
                "*"), true);

        //Maximun number of tables to display on workbench.
        ConfigurationGroup workbench = new ConfigurationGroup("Workbench");
        workbench = this.configurationGroupService.save(workbench, true);
        this.save(new Configuration(
                "Schema and table searcheble",
                "WORKBENCH_MAX_ENTITY_NUMBER",
                "5000",
                "number",
                workbench,
                100,
                50000,
                "*"), true);

        //E-mail domain filter.
        this.save(new Configuration(
                "E-mail filter (RegExp)",
                "EMAIL_DOMAIN_FILTER",
                "",
                "text",
                workbench,
                0,
                255,
                "*"), true);

        //Limit of rows per query.
        this.save(new Configuration(
                "Max rows per query",
                "WORKBENCH_MAX_ROWS",
                "100",
                "number",
                workbench,
                100,
                1000000,
                "*"), true);
    }

    /**
     * Get max rows allowed in a query.
     *
     * @return int
     */
    @Cacheable(value = "maxRows")
    public int getMaxRows() {
        return Integer.valueOf(this
                .findByParameter("WORKBENCH_MAX_ROWS")
                .getValue());
    }

    public String getDataBaseTime() {
        return this.configurationRepository.now();
    }

    public String getServerTime() {
        return new LocalDateTime().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }
}
