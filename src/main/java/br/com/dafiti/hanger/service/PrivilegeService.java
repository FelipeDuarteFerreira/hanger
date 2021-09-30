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
package br.com.dafiti.hanger.service;

import br.com.dafiti.hanger.model.Privilege;
import br.com.dafiti.hanger.repository.PrivilegeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author Helio Leal
 */
@Service
public class PrivilegeService {

    private final PrivilegeRepository privilegeRepository;

    @Autowired
    public PrivilegeService(PrivilegeRepository privilegeRepository) {
        this.privilegeRepository = privilegeRepository;
    }

    public Iterable<Privilege> list() {
        return privilegeRepository.findAll();
    }

    /**
     * Save a privilege
     *
     * @param privilege Privilege
     * @return Privilege
     */
    public Privilege save(Privilege privilege) {
        return privilegeRepository.save(privilege);
    }

    /**
     * Find a privilege by name
     *
     * @param name Privilege name
     * @return Privilege
     */
    public Privilege findByName(String name) {
        return privilegeRepository.findByName(name);
    }

    /**
     * Create a privilege if not exists one
     *
     * @param name Privilege name
     * @return Privilege
     */
    public Privilege createPrivilegeIfNotExists(String name) {
        Privilege privilege = this.findByName(name);

        if (privilege == null) {
            Privilege newPrivilege = new Privilege();
            newPrivilege.setName(name);
            privilege = this.save(newPrivilege);
        }

        return privilege;
    }
}
