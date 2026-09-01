package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.util.EntityIdProvider;
import com.tkzou.miniforum.util.IdProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * MySQL 行级用户仓库（生产适配，@Profile("prod")）
 * <p>
 * 对齐存储矩阵"用户=MySQL 主存储"：行级表 users，username 唯一索引（账号事实源）。
 */
@Repository
@Profile("prod")
public class MySqlUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(MySqlUserRepository.class);

    private final JdbcTemplate jdbcTemplate;
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();

    public MySqlUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        log.info("MySQL 用户仓库初始化（行级表 users）");
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users ("
                + "id BIGINT PRIMARY KEY,"
                + "username VARCHAR(50) NOT NULL,"
                + "email VARCHAR(100),"
                + "password VARCHAR(200) NOT NULL,"
                + "age INT,"
                + "nickname VARCHAR(50),"
                + "bio TEXT,"
                + "avatar VARCHAR(200),"
                + "UNIQUE KEY uk_username (username)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            user.setId(idProvider.next("User"));
        }
        jdbcTemplate.update("INSERT INTO users(id,username,email,password,age,nickname,bio,avatar) VALUES(?,?,?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE username=VALUES(username),email=VALUES(email),password=VALUES(password),"
                        + "age=VALUES(age),nickname=VALUES(nickname),bio=VALUES(bio),avatar=VALUES(avatar)",
                user.getId(), user.getUsername(), user.getEmail(), user.getPassword(), user.getAge(),
                user.getNickname(), user.getBio(), user.getAvatar());
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        return jdbcTemplate.query("SELECT id, username, email, password, age, nickname, bio, avatar FROM users WHERE id=?", this::mapUser, id).stream().findFirst();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jdbcTemplate.query("SELECT id, username, email, password, age, nickname, bio, avatar FROM users WHERE username=?", this::mapUser, username).stream().findFirst();
    }

    @Override
    public List<User> findAll() {
        return jdbcTemplate.query("SELECT id, username, email, password, age, nickname, bio, avatar FROM users ORDER BY id", this::mapUser);
    }

    @Override
    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM users WHERE id=?", id);
    }

    @Override
    public boolean existsById(Long id) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id=?", Integer.class, id);
        return n != null && n > 0;
    }

    @Override
    public List<User> exportAll() {
        return findAll();
    }

    @Override
    @Transactional
    public void importAll(List<User> users) {
        jdbcTemplate.update("DELETE FROM users");
        if (users == null) {
            return;
        }
        for (User u : users) {
            if (u != null && u.getId() != null) {
                save(u);
            }
        }
    }

    @Override
    public long count() {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        return n == null ? 0 : n;
    }

    private User mapUser(ResultSet rs, int rowNum) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setPassword(rs.getString("password"));
        int age = rs.getInt("age");
        u.setAge(rs.wasNull() ? null : age);
        u.setNickname(rs.getString("nickname"));
        u.setBio(rs.getString("bio"));
        u.setAvatar(rs.getString("avatar"));
        return u;
    }
}
