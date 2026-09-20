plugins {
    id("quark.published")
}

description = "Quark configuration serializers for Bukkit: ItemStack, Location, Sound, Adventure Component and ConfigurationSerializable"

dependencies {
    api(project(":quark-config"))

    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
}
