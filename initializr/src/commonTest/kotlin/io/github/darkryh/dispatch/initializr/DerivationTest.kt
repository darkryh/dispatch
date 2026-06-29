package io.github.darkryh.dispatch.initializr

import io.github.darkryh.dispatch.initializr.model.ConfigField
import io.github.darkryh.dispatch.initializr.model.FormState
import io.github.darkryh.dispatch.initializr.model.ProjectConfigValidator
import io.github.darkryh.dispatch.initializr.model.deriveArtifactId
import io.github.darkryh.dispatch.initializr.model.deriveGroupId
import io.github.darkryh.dispatch.initializr.model.kebabCase
import io.github.darkryh.dispatch.initializr.model.toProjectConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DerivationTest {
    @Test
    fun defaultFormIsValidAndDerivesIdiomaticCoordinates() {
        val form = FormState.DEFAULT
        assertTrue(ProjectConfigValidator.isValid(form))
        val config = form.toProjectConfig()
        assertEquals("com.example", config.groupId)
        assertEquals("my-dispatch-app", config.artifactId)
        assertEquals("com/example/myapp", config.packagePath)
    }

    @Test
    fun groupIdStripsLastPackageSegment() {
        assertEquals("com.example", deriveGroupId("com.example.myapp"))
        assertEquals("dev.acme", deriveGroupId("dev.acme.todo"))
        // single-segment package falls back to the whole thing
        assertEquals("myapp", deriveGroupId("myapp"))
    }

    @Test
    fun artifactIdKebabCasesProjectNameWithFallbacks() {
        assertEquals("my-dispatch-app", deriveArtifactId("My Dispatch App", "com.x.app"))
        assertEquals("my-cool-tui", deriveArtifactId("MyCoolTUI", "io.foo.bar"))
        // blank name -> last package segment
        assertEquals("app", deriveArtifactId("", "com.example.app"))
        // leading digits/symbols stripped
        assertEquals("go", deriveArtifactId("123 Go!!!", "io.x.app"))
    }

    @Test
    fun kebabFoldsAccentsAndCamelCase() {
        assertEquals("creme-brulee", kebabCase("Crème Brûlée"))
        assertEquals("my-app", kebabCase("MyApp"))
        assertEquals("", kebabCase("!!!"))
    }

    @Test
    fun uppercasePackageIsRejected() {
        val form = FormState.DEFAULT.copy(packageName = "com.Example.App")
        val fields = ProjectConfigValidator.validate(form).map { it.field }
        assertTrue(ConfigField.PACKAGE_NAME in fields)
    }

    @Test
    fun derivedGroupAndArtifactErrorsAreHiddenUntilOverridden() {
        // A package whose derived artifact/group are fine, but a bad override should surface.
        val clean = FormState.DEFAULT
        assertTrue(ProjectConfigValidator.validate(clean).none { it.field == ConfigField.ARTIFACT_ID })

        val badOverride = clean.copy(artifactIdOverride = "Bad_Artifact")
        val fields = ProjectConfigValidator.validate(badOverride).map { it.field }
        assertTrue(ConfigField.ARTIFACT_ID in fields)
    }

    @Test
    fun overrideTakesPrecedenceOverDerivation() {
        val form = FormState.DEFAULT.copy(groupIdOverride = "org.custom", artifactIdOverride = "custom-cli")
        val config = form.toProjectConfig()
        assertEquals("org.custom", config.groupId)
        assertEquals("custom-cli", config.artifactId)
    }

    @Test
    fun blankProjectNameIsStillAFieldError() {
        val form = FormState.DEFAULT.copy(projectName = "   ")
        val fields = ProjectConfigValidator.validate(form).map { it.field }
        assertTrue(ConfigField.PROJECT_NAME in fields)
        // ...but derivation itself stays legal (artifact falls back to the package segment)
        assertFalse(form.toProjectConfig().artifactId.isEmpty())
    }
}
