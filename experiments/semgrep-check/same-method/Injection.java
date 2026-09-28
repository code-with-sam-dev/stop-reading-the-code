import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Injection {
    private final JdbcClient client;
    private final JdbcTemplate template;
    private final java.sql.Connection connection;

    Injection(JdbcClient client, JdbcTemplate template, java.sql.Connection connection) {
        this.client = client; this.template = template; this.connection = connection;
    }

    @GetMapping("/a")
    public List<Map<String, Object>> viaJdbcClient(@RequestParam String sort) {
        return client.sql("SELECT * FROM payments ORDER BY " + sort).query().listOfRows();
    }

    @GetMapping("/b")
    public List<Map<String, Object>> viaJdbcTemplate(@RequestParam String sort) {
        return template.queryForList("SELECT * FROM payments ORDER BY " + sort);
    }

    @GetMapping("/c")
    public void viaStatement(@RequestParam String sort) throws Exception {
        connection.createStatement().executeQuery("SELECT * FROM payments ORDER BY " + sort);
    }
}
