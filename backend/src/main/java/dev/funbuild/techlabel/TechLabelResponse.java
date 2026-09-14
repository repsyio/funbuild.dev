package dev.funbuild.techlabel;

public record TechLabelResponse(Long id, String name) {

  public static TechLabelResponse from(TechLabel label) {
    return new TechLabelResponse(label.getId(), label.getName());
  }
}
