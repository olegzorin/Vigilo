package dev.olegz.vf.api.resident;

import dev.olegz.vf.api.web.support.ActionContextFactory;
import dev.olegz.vf.api.web.support.ActionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static dev.olegz.vf.api.web.ApiHeaders.API_KEY;

@RestController
@RequestMapping(path = "/vf/residents", produces = "application/json")
public class ResidentController {
    private final ResidentAction action;
    private final ActionContextFactory contexts;
    public ResidentController(ResidentAction action, ActionContextFactory contexts) { this.action = action; this.contexts = contexts; }
    @GetMapping
    public ActionResponse list(@RequestHeader(API_KEY) String key) { return action.list(contexts.current()); }
    @GetMapping("{residentId}")
    public ActionResponse get(@RequestHeader(API_KEY) String key, @PathVariable int residentId) { return action.get(contexts.current(), residentId); }
    @PostMapping(consumes = "application/json")
    public ActionResponse create(@RequestHeader(API_KEY) String key, @Valid @RequestBody ResidentAction.CreateRequest request) { return action.create(contexts.current(), request); }
    @PutMapping(path = "{residentId}", consumes = "application/json")
    public ActionResponse update(@RequestHeader(API_KEY) String key, @PathVariable int residentId, @Valid @RequestBody ResidentAction.Request request) { return action.update(contexts.current(), residentId, request); }
}
