plugins {
    id("quark.published")
}

description = "Annotation validation for Quark configurations: @NotNull, @Min, @Max, @Pattern and @Check"

dependencies {
    api(project(":quark-config"))
}
