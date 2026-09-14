package dev.funbuild.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.validator.constraints.URL;

public record ProjectRequest(
    @NotNull Long assignmentId,
    @NotBlank @Size(max = 200) String title,
    @NotBlank String description,
    @NotBlank @URL @Size(max = 500) String showcaseUrl,
    @URL @Size(max = 500) String gitRepoUrl,
    List<String> techLabels) {}
