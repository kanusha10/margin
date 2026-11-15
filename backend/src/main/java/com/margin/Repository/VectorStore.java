package com.margin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class VectorStore {
    public record Chunk(String path, int start, int end, String content) {}
    public record Hit(String path, int start, int end, String content, double score) {}

    private final JdbcTemplate jdbc;

    public VectorStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(long repoId, List<Chunk> chunks, List<float[]> vectors) {
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            jdbc.update("INSERT INTO chunks(repo_id,path,start_line,end_line,content,embedding) VALUES (?,?,?,?,?,?::vector)",
                    repoId, c.path(), c.start(), c.end(), c.content(), literal(vectors.get(i)));
        }
    }

    public List<Hit> search(long repoId, float[] query, int k) {
        String v = literal(query);
        return jdbc.query("SELECT path,start_line,end_line,content,1-(embedding <=> ?::vector) AS score "
                        + "FROM chunks WHERE repo_id=? ORDER BY embedding <=> ?::vector LIMIT ?",
                (rs, i) -> new Hit(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getString(4), rs.getDouble(5)),
                v, repoId, v, k);
    }

    private static String literal(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
