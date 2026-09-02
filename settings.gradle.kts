rootProject.name = "DarkBot"

// Opt-in composite build: resolve eu.darkbot:unity-transport / eu.darkbot:unity-game from
// the sibling sources instead of mavenLocal, so protocol changes can be iterated without
// publishing. Activate with: gradlew compileJava -PunityComposite
// (or ORG_GRADLE_PROJECT_unityComposite=true). Without the property, the pinned
// versions from mavenLocal are used, keeping CI and releases reproducible.
if (providers.gradleProperty("unityComposite").isPresent) {
    includeBuild("../unity-transport") {
        dependencySubstitution {
            substitute(module("eu.darkbot:unity-transport")).using(project(":"))
        }
    }
    includeBuild("../unity-game") {
        dependencySubstitution {
            substitute(module("eu.darkbot:unity-game")).using(project(":"))
        }
    }
}
