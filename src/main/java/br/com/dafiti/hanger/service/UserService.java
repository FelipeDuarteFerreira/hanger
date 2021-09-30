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

import br.com.dafiti.hanger.repository.UserRepository;
import br.com.dafiti.hanger.model.Blueprint;
import br.com.dafiti.hanger.model.User;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 *
 * @author Guilherme ALMEIDA
 */
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final SessionRegistry sessionRegistry;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final MailService mailService;

    public UserService(
            UserRepository userRepository,
            SessionRegistry sessionRegistry,
            BCryptPasswordEncoder bCryptPasswordEncoder,
            MailService mailService) {

        this.userRepository = userRepository;
        this.sessionRegistry = sessionRegistry;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.mailService = mailService;
    }

    /**
     * Load a user by ID
     *
     * @param id User ID.
     * @return User.
     */
    public User load(long id) {
        return userRepository.findById(id).get();
    }

    /**
     * Load a user by username
     *
     * @param username Username.
     * @return User.
     * @throws UsernameNotFoundException
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);

        if (user == null) {
            throw new UsernameNotFoundException("Username not found");
        }

        return user;
    }

    /**
     * Find a user by username
     *
     * @param username Username.
     * @return User.
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Find a user by e-mail
     *
     * @param email User e-mail.
     * @return User.
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Save a user.
     *
     * @param user User.
     * @return Add or update a user.
     */
    public User save(User user) {
        if (user.getId() == null) {
            return add(user);
        } else {
            return update(user);
        }
    }

    /**
     * Create a user.
     *
     * @param user User object.
     * @return user User
     */
    public User add(User user) {
        String password;

        if (user.getPassword().isEmpty()) {
            password = RandomStringUtils.random(5, true, true);
        } else {
            password = user.getPassword();
        }

        user.setPassword(bCryptPasswordEncoder.encode(password));

        if (userRepository.save(user) != null) {
            if (mailService.isEmailOk()) {
                Blueprint blueprint = new Blueprint(user.getEmail(), "Welcome to Hanger", "userWelcome");
                blueprint.addVariable("name", user.getFirstName());
                blueprint.addVariable("username", user.getUsername());
                blueprint.addVariable("password", password);

                mailService.send(blueprint);
            }
        }

        return user;
    }

    /**
     * Update a user.
     *
     * @param user User object.
     * @return user User
     */
    public User update(User user) {
        User currentUser = this.load(user.getId());

        if (!user.getPassword().equals(currentUser.getPassword())) {
            currentUser.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
        }

        if (!user.getRoles().isEmpty()) {
            currentUser.setRoles(user.getRoles());
        }

        if (user.getFirstName() != null) {
            currentUser.setFirstName(user.getFirstName());
        }

        if (user.getLastName() != null) {
            currentUser.setLastName(user.getLastName());
        }

        if (!user.getPrivileges().isEmpty()) {
            currentUser.setPrivileges(user.getPrivileges());
        } else {
            currentUser.getPrivileges().clear();
        }

        currentUser.setEnabled(user.isEnabled());

        return userRepository.save(currentUser);
    }

    /**
     * Delete a user.
     *
     * @param id User ID.
     */
    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * List users.
     *
     * @return User list.
     */
    public Collection<User> list() {
        return list(false);
    }

    /**
     * List users.
     *
     * @param onlyEnabled Identify if shoul list only enabled users.
     * @return User list.
     */
    public Collection<User> list(boolean onlyEnabled) {
        if (onlyEnabled) {
            return userRepository.findByEnabledTrue();
        } else {
            return userRepository.findAll();
        }
    }

    /**
     * Generate a user password reset code.
     *
     * @param username Username
     * @return
     */
    public Boolean setResetCode(String username) {
        User user = findByUsername(username);
        boolean reseted = false;

        if (user != null) {
            user.setResetCode(RandomStringUtils.random(5, true, true));
            reseted = (save(user) != null);
        }

        return reseted;
    }

    /**
     * Reset the password of the user.
     *
     * @param userId id of the user.
     * @return Empty in case of fail, otherwise the reseted password
     */
    public String resetPassword(Long userId) {
        String rawPassword = RandomStringUtils.random(5, true, true);
        User user = new User();
        user.setId(userId);
        user.setPassword(rawPassword);

        user = this.save(user);

        if (user != null) {
            Blueprint blueprint = new Blueprint(user.getEmail(), "Hanger - Reset password", "resetPassword");
            blueprint.addVariable("name", user.getFirstName());
            blueprint.addVariable("username", user.getUsername());
            blueprint.addVariable("password", rawPassword);

            mailService.send(blueprint);
        }

        return rawPassword;
    }

    /**
     * Get logged in user.
     *
     * @return Logged in user.
     */
    public User getLoggedIn() {
        User user;
        Object principal = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        if (principal instanceof User) {
            user = (User) principal;
        } else {
            user = null;
        }

        return user;
    }

    /**
     * Get principal logged principal in list.
     *
     * @return Principal Logged in principal list.
     */
    public List<Object> listLoggedIn() {
        return sessionRegistry
                .getAllPrincipals()
                .stream()
                .filter(principal -> !sessionRegistry.getAllSessions(principal, false).isEmpty())
                .collect(Collectors.toList());
    }
}
