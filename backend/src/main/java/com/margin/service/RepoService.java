package com.margin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class RepoService {
    private static final RowMapper<Repo> MAP = (rs, i) -> new Repo(rs.getLong("id"), rs.getString("name"),
            rs.getString("url"), rs.getString("status"), rs.getString("message"), rs.getInt("file_count"),
            rs.getInt("chunk_count"), rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;
    private final Path storage;

    public RepoService(JdbcTemplate jdbc, @Value("${app.storage-dir}") String dir) {
        this.jdbc = jdbc;
        this.storage = Path.of(dir).toAbsolutePath().normalize();
    }

    @EventListener(ApplicationReadyEvent.class)
    void recoverInterrupted() {
        jdbc.update("UPDATE repos SET status='FAILED', message='Indexing was interrupted by a restart. Remove it and add it again.' "
                + "WHERE status IN ('CLONING','INDEXING')");
    }

    public List<Repo> list(long owner) {
        return jdbc.query("SELECT * FROM repos WHERE owner_id=? ORDER BY created_at DESC", MAP, owner);
    }

    public Optional<Repo> find(long id, long owner) {
        return jdbc.query("SELECT * FROM repos WHERE id=? AND owner_id=?", MAP, id, owner).stream().findFirst();
    }

    public Optional<Repo> findByUrl(long owner, String url) {
        return jdbc.query("SELECT * FROM repos WHERE owner_id=? AND url=?", MAP, owner, url).stream().findFirst();
    }

    public int count(long owner) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM repos WHERE owner_id=?", Integer.class, owner);
        return n == null ? 0 : n;
    }

    public Repo create(long owner, String name, String url) {
        Long id = jdbc.queryForObject("INSERT INTO repos(owner_id,name,url,status,message) VALUES (?,?,?,'CLONING','Waiting to clone') RETURNING id",
                Long.class, owner, name, url);
        return find(id, owner).orElseThrow();
    }

    public void update(long id, String status, String message) {
        jdbc.update("UPDATE repos SET status=?, message=? WHERE id=?", status, message, id);
    }

    public void finish(long id, int files, int chunks) {
        jdbc.update("UPDATE repos SET status='READY', message='Ready', file_count=?, chunk_count=? WHERE id=?", files, chunks, id);
    }

    public void delete(long id, long owner) {
        if (jdbc.update("DELETE FROM repos WHERE id=? AND owner_id=?", id, owner) > 0) RepoFiles.purge(dir(id));
    }

    public Path dir(long id) { return storage.resolve(String.valueOf(id)); }
}
