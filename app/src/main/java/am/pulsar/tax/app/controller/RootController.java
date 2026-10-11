package am.pulsar.tax.app.controller;import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Фронтенда здесь нет: корень отвечает, что это API. */
@RestController
public class RootController {

    @GetMapping("/")
    Map<String, String> root() {
        return Map.of("service", "pulsar-tax-api", "status", "ok");
    }
}
