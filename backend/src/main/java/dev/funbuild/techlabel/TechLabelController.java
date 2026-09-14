package dev.funbuild.techlabel;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tech-labels")
public class TechLabelController {

  private final TechLabelService service;

  public TechLabelController(TechLabelService service) {
    this.service = service;
  }

  /** Backs the tech-stack dropdown; ?q= filters as the member types. */
  @GetMapping
  public List<TechLabelResponse> search(@RequestParam(required = false) String q) {
    return service.search(q).stream().map(TechLabelResponse::from).toList();
  }
}
