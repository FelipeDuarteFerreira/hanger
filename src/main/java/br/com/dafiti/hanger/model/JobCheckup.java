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
package br.com.dafiti.hanger.model;

import br.com.dafiti.hanger.option.Action;
import br.com.dafiti.hanger.option.Conditional;
import br.com.dafiti.hanger.option.Scope;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Transient;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 *
 * @author Valdiney V GOMES
 */
@Entity
public class JobCheckup implements Serializable {

    private Long id;
    private Job job;
    private String name;
    private String description;
    private Connection connection;
    private Scope scope;
    private Conditional conditional;
    private String query;
    private String threshold;
    private Action action;
    private List<Job> trigger = new ArrayList();
    private List<Command> command = new ArrayList();
    private List<JobCheckupLog> log = new ArrayList();
    private boolean enabled = true;
    private boolean prevalidation = false;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @ManyToOne
    @JoinColumn(name = "job_id", referencedColumnName = "id")
    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name.toUpperCase();
    }

    @Column(columnDefinition = "text")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description.toUpperCase();
    }

    @Transient
    public String getHTMLDescription() {
        Parser parser = Parser.builder().build();
        Node document = parser.parse(this.getDescription());
        HtmlRenderer renderer = HtmlRenderer.builder().build();

        return renderer.render(document);
    }

    @Column(columnDefinition = "text")
    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query.trim();
    }

    public String getThreshold() {
        return threshold;
    }

    public void setThreshold(String threshold) {
        this.threshold = threshold;
    }

    @Enumerated(EnumType.STRING)
    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    @Enumerated(EnumType.STRING)
    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    @Enumerated(EnumType.STRING)
    public Conditional getConditional() {
        return conditional;
    }

    public void setConditional(Conditional conditional) {
        this.conditional = conditional;
    }

    @OneToOne
    @JoinColumn(name = "connection_id", referencedColumnName = "id")
    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    @ManyToMany
    @JoinTable(name = "job_checkup_trigger",
            joinColumns = {
                @JoinColumn(name = "job_checkup_id", referencedColumnName = "id")},
            inverseJoinColumns = {
                @JoinColumn(name = "job_id", referencedColumnName = "id")})
    public List<Job> getTrigger() {
        return trigger;
    }

    public void setTrigger(List<Job> job) {
        this.trigger = job;
    }

    public void addTrigger(Job job) {
        this.trigger.add(job);
    }

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(name = "checkup_command",
            joinColumns = {
                @JoinColumn(name = "job_checkup_id", referencedColumnName = "id")},
            inverseJoinColumns = {
                @JoinColumn(name = "command_id", referencedColumnName = "id")})
    public List<Command> getCommand() {
        return command;
    }

    public void setCommand(List<Command> command) {
        this.command = command;
    }

    public void addCommand(Command command) {
        this.command.add(command);
    }

    @OneToMany(mappedBy = "checkup", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<JobCheckupLog> getLog() {
        return log;
    }

    public void setLog(List<JobCheckupLog> log) {
        this.log = log;
    }

    public void addLog(JobCheckupLog log) {
        this.log.add(log);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPrevalidation() {
        return prevalidation;
    }

    public void setPrevalidation(boolean prevalidation) {
        this.prevalidation = prevalidation;
    }

    @Transient
    public boolean getLastStatus() {
        boolean status = true;
        List<JobCheckupLog> log = this.getLog();

        if (!log.isEmpty()) {
            JobCheckupLog jobCheckupLog = log.get(0);

            if (jobCheckupLog != null) {
                status = jobCheckupLog.isSuccess();
            }
        }

        return status;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.id);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        final JobCheckup other = (JobCheckup) obj;

        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        return Objects.equals(this.id, other.id);
    }
}
