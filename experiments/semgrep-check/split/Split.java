import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Split {
    private final JdbcClient client;

    Split(JdbcClient client) { this.client = client; }

    @GetMapping("/a")
    public List<Map<String, Object>> list(@RequestParam String sort) {
        return sorted(sort);
    }

    private List<Map<String, Object>> sorted(String sort) {
        return client.sql("SELECT * FROM payments ORDER BY " + sort).query().listOfRows();
    }
}
