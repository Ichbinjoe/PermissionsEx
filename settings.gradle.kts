/*
 * This file was generated by the Gradle 'init' task.
 *
 * The settings file is used to specify which projects to include in your build.
 *
 * Detailed information about configuring a multi-project build in Gradle can be found
 * in the user manual at https://docs.gradle.org/5.6.1/userguide/multi_project_builds.html
 */

rootProject.name = "PermissionsEx"

include("permissionsex-core",
    "permissionsex-sponge", "permissionsex-bukkit",
    "permissionsex-bungee", "permissionsex-velocity")

listOf("bungee-text", "proxy-common", "hikari-config").forEach {
    include("impl-blocks:$it")
    findProject(":impl-blocks:$it")?.name = "permissionsex-$it"
}
