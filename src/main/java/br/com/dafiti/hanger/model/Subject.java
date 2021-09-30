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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.MapKeyColumn;
import javax.persistence.Transient;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.json.JSONObject;

/**
 *
 * @author Daniel D GOMES
 * @author Valdiney V GOMES
 */
@Entity
public class Subject extends Tracker<Subject> implements Serializable {

    private Long id;
    private String name;
    private String description;
    private boolean notified;
    private boolean mandatory;
    private Set<String> channel = new HashSet();
    private List<User> user = new ArrayList();
    private Map<String, String> swimlane = new TreeMap<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @NotEmpty
    @Column(unique = true)
    @Size(min = 2, max = 50)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name.toUpperCase();
    }

    @Column(columnDefinition = "text")
    public String getDescription() {
        if (description != null) {
            description = description.trim();
        }

        return description;
    }

    @Transient
    public String getHTMLDescription() {
        Parser parser = Parser.builder().build();
        Node document = parser.parse(this.getDescription());
        HtmlRenderer renderer = HtmlRenderer.builder().build();

        return renderer.render(document);
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "subject_channel", joinColumns = @JoinColumn(name = "id"))
    @Column(name = "channel")
    public Set<String> getChannel() {
        return channel;
    }

    public void setChannel(Set<String> channel) {
        this.channel = channel;
    }

    public void addChannel(String channel) {
        this.channel.add(channel);
    }

    @ManyToMany(cascade = {CascadeType.DETACH, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.PERSIST})
    @JoinTable(name = "subject_user",
            joinColumns = {
                @JoinColumn(name = "subject_id", referencedColumnName = "id")},
            inverseJoinColumns = {
                @JoinColumn(name = "user_id", referencedColumnName = "user_id")})
    public List<User> getUser() {
        return user;
    }

    public void setUser(List<User> user) {
        this.user = user;
    }

    public void addUser(User user) {
        this.user.add(user);
    }

    public void setSwimlane(Map<String, String> swimlane) {
        this.swimlane = swimlane;
    }

    @ElementCollection
    @MapKeyColumn(name = "swimlane")
    @Column(name = "criteria")
    @CollectionTable(name = "subject_swimlanes", joinColumns = @JoinColumn(name = "id"))
    public Map<String, String> getSwimlane() {
        return swimlane;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.id);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        final Subject other = (Subject) obj;

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

    @Override
    public String toString() {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("description", description);
        object.put("mandatory", mandatory);
        object.put("notified", notified);
        return object.toString(2);
    }
}
