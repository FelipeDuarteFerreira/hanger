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
package br.com.dafiti.hanger.security;

import br.com.dafiti.hanger.model.Privilege;
import br.com.dafiti.hanger.model.User;
import br.com.dafiti.hanger.service.PrivilegeService;
import br.com.dafiti.hanger.service.UserService;
import java.io.Serializable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 *
 * @author Helio Leal
 */
@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    @Autowired
    private UserService userService;

    @Autowired
    private PrivilegeService privilegeService;

    @Override
    public boolean hasPermission(Authentication authentication,
            Object target,
            Object permission) {

        if (!authentication.getPrincipal().equals("anonymousUser")) {
            User user = this.userService.findByUsername(((User) authentication
                    .getPrincipal())
                    .getUsername());

            if (user != null) {
                Privilege privilege = privilegeService.findByName((String) target);

                if (privilege != null) {
                    if (user.getPrivileges()
                            .stream()
                            .anyMatch((p) -> (p.getName().toUpperCase()
                            .equals(privilege.getName().toUpperCase())))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth,
            Serializable id,
            String type,
            Object permission
    ) {
        throw new UnsupportedOperationException();
    }

}
