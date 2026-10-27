package dev.adboptimizer.core.optimization

import dev.adboptimizer.core.model.ActionCatalog
import dev.adboptimizer.core.model.AppliesTo
import dev.adboptimizer.core.model.Category
import dev.adboptimizer.core.model.Impact
import dev.adboptimizer.core.model.Recipe
import dev.adboptimizer.core.model.RecipeStep
import dev.adboptimizer.core.model.RecipeStepType
import dev.adboptimizer.core.model.Risk
import dev.adboptimizer.core.model.VerifySpec
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogValidatorTest {
    @Test
    fun `accepts valid install system apk recipe metadata`() {
        val problems = CatalogValidator.validate(catalog(systemApkStep()), ActionCatalog(schemaVersion = 1, actions = emptyList()))

        assertTrue(problems.toString(), problems.isEmpty())
    }

    @Test
    fun `rejects install system apk without safe system path and metadata`() {
        val invalid = systemApkStep().copy(targetPath = "/data/local/tmp/launcher.apk", mode = "999")
        val problems = CatalogValidator.validate(catalog(invalid), ActionCatalog(schemaVersion = 1, actions = emptyList()))

        assertTrue(problems.any { it.contains("targetPath") })
        assertTrue(problems.any { it.contains("mode") })
    }

    private fun catalog(step: RecipeStep) =
        dev.adboptimizer.core.model.Catalog(
            schemaVersion = 1,
            catalogVersion = "test",
            recipes =
                listOf(
                    Recipe(
                        id = "wanboos-launcher",
                        category = Category.TV,
                        risk = Risk.ADVANCED,
                        requiresRoot = true,
                        impact = Impact.HIGH,
                        appliesTo = AppliesTo(),
                        titleKey = "title",
                        summaryKey = "summary",
                        steps = listOf(step),
                    ),
                ),
        )

    private fun systemApkStep() =
        RecipeStep(
            type = RecipeStepType.INSTALL_SYSTEM_APK,
            run = "installSystemApk {{apkPath}}",
            verify = VerifySpec("pm path com.tv.launcher.custom", contains = "package:"),
            undo = "restoreSystemApk",
            timeoutMs = 300_000,
            apkPath = "{{apkPath}}",
            targetPath = "/system/priv-app/WanboOSLauncher/WanboOSLauncher.apk",
            mode = "0644",
            owner = "0:0",
            selinuxContext = "u:object_r:system_file:s0",
            disablePackages = listOf("com.google.android.tvlauncher"),
        )
}
