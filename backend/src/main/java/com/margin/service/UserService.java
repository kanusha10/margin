package com.margin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private static final RowMapper<User> MAP = (rs, i) -> new User(rs.getLong("id"), rs.getString("email"), rs.getString("password_hash"));
    private final JdbcTemplate jdbc;

    public UserService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<User> findByEmail(String email) {
        return jdbc.query("SELECT * FROM users WHERE email=?", MAP, email).stream().findFirst();
    }

    public User create(String email, String hash) {
        Long id = jdbc.queryForObject("INSERT INTO users(email,password_hash) VALUES (?,?) RETURNING id", Long.class, email, hash);
        return new User(id, email, hash);
    }
}
