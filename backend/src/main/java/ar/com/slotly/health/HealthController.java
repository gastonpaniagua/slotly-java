package ar.com.slotly.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private final JdbcTemplate jdbc;

    HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/health")
    ResponseEntity<HealthResponse> health() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(new HealthResponse("ok", "ok"));
        } catch (DataAccessException e) {
            log.warn("Error de conexión a la base de datos", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new HealthResponse("error", "caida"));
        }
    }
}