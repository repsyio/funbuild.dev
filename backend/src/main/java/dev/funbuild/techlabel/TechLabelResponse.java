package dev.funbuild.techlabel;

import java.util.UUID;

public record TechLabelResponse(UUID id, String name) {

  public static TechLabelResponse from(TechLabel label) {
    return new TechLabelResponse(label.getId(), label.getName());
  }
}
