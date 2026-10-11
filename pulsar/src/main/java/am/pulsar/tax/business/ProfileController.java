package am.pulsar.tax.business;

import am.pulsar.tax.identity.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final BusinessService businesses;

    public ProfileController(BusinessService businesses) {
        this.businesses = businesses;
    }

    @GetMapping
    Map<String, Object> get(@AuthenticationPrincipal AuthUser user) {
        return businesses.forUser(user.id()).toJson();
    }

    @PutMapping
    Map<String, Object> put(@AuthenticationPrincipal AuthUser user, @RequestBody Map<String, Object> body) {
        return businesses.update(user.id(), body).toJson();
    }
}
