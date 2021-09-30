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

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 *
 * @author Guilherme ALMEIDA
 * @author Valdiney V GOMES
 */
@Service
public class JwtService {

    private final String encryptKey;

    public JwtService(@Value("${hanger.encrypt.key}") String encryptKey) {
        this.encryptKey = encryptKey;
    }

    /**
     *
     * @param date
     * @param subject
     * @return
     */
    public String generateToken(String subject, Date date) {
        return Jwts.builder()
                .setIssuedAt(date)
                .setSubject(subject)
                .signWith(SignatureAlgorithm.HS256, encryptKey)
                .compact();
    }

    /**
     *
     * @param token
     * @return
     */
    public boolean validate(String token) {
        try {
            Jwts
                    .parser()
                    .setSigningKey(encryptKey)
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException
                | MalformedJwtException
                | SignatureException
                | UnsupportedJwtException
                | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     *
     * @param request
     * @return
     */
    public String extractToken(HttpServletRequest request) {
        String token = null;
        String authorization = request.getHeader("Authorization");

        if (authorization != null
                && !authorization.isEmpty()
                && authorization.startsWith("Bearer ")) {

            token = authorization.substring(7, authorization.length());
        }

        return token;
    }

    /**
     *
     * @param token
     * @return
     */
    public String getSubject(String token) {
        return Jwts
                .parser()
                .setSigningKey(encryptKey)
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    /**
     *
     * @param token
     * @return
     */
    public Date getCreatedAt(String token) {
        return Jwts
                .parser()
                .setSigningKey(encryptKey)
                .parseClaimsJws(token)
                .getBody()
                .getIssuedAt();
    }
}
