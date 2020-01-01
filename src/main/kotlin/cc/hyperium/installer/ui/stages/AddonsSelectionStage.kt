/*
 * Copyright © 2020 by Sk1er LLC
 *
 * All rights reserved.
 *
 * Sk1er LLC
 * 444 S Fulton Ave
 * Mount Vernon, NY
 * sk1er.club
 */

package cc.hyperium.installer.ui.stages

import cc.hyperium.installer.backend.Installer
import cc.hyperium.installer.backend.config.JFXConfig
import cc.hyperium.installer.backend.util.Desktop
import cc.hyperium.installer.shared.entities.addon.Addon
import cc.hyperium.installer.shared.utils.VersionUtils
import cc.hyperium.installer.ui.ConfirmationDialog
import cc.hyperium.installer.ui.InstallerStyles
import cc.hyperium.installer.ui.InstallerView
import com.jfoenix.controls.JFXCheckBox
import javafx.beans.binding.BooleanBinding
import javafx.scene.control.Tooltip
import javafx.stage.FileChooser
import kfoenix.jfxbutton
import kfoenix.jfxcheckbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tornadofx.*
import java.io.File
import java.net.URI
import java.net.URL
import java.net.URLEncoder

class AddonsSelectionStage : View() {
    override val root = vbox {
        addClass(InstallerStyles.container)
        label("Addons") { addClass(InstallerStyles.title) }
        val desc = label("Please note that OptiFine requires it to be installed in the launcher.") {
            addClass(InstallerStyles.desc)
        }
        pane { addClass(InstallerStyles.spacer) }
        vbox {
            jfxcheckbox("OptiFine") {
                selectedProperty().bindBidirectional(JFXConfig.optifineProperty)
                tooltip = Tooltip("A Minecraft optimization mod.")
            }
            Installer.launch {
                val addons = VersionUtils.addonsManifest?.addons
                val checkboxes = mutableMapOf<Addon, JFXCheckBox>()
                runLater {
                    if (addons != null) {
                        addons.forEach {
                            jfxcheckbox(it.name) {
                                JFXConfig.addonsProperties[it.name] = selectedProperty()
                                tooltip = Tooltip("${it.description}\nAuthor(s): ${it.author}")
                                checkboxes[it] = this
                            }
                        }
                        checkboxes.forEach { (addon, checkbox) ->
                            checkbox.enableWhen { bindDependency(addon, checkboxes).and(addon.enabled) }
                        }
                    } else desc.text = "Failed to fetch addons manifest, please check your internet connection."
                }
            }
            maxWidth = 100.0
        }
        pane { addClass(InstallerStyles.spacer) }
        jfxbutton("NEXT") {
            addClass(InstallerStyles.longButton)
            action {
                next()
            }
        }
        jfxbutton("Back") {
            visibleWhen(JFXConfig.advancedProperty)
            addClass(InstallerStyles.backButton)
            action {
                find<InstallerView> { tabPane.selectionModel.selectPrevious() }
            }
        }
    }

    fun bindDependency(addon: Addon, checkboxes: Map<Addon, JFXCheckBox>) =
        addon.depends.mapNotNull { dep -> checkboxes.entries.find { it.key.name == dep } }
            .let {
                it.firstOrNull()
                    ?.let { f ->
                        it.fold(BooleanBinding.booleanExpression(f.value.selectedProperty())) { prop, checkbox ->
                            prop.and(checkbox.value.selectedProperty())
                        }
                    } ?: true.toProperty()
            }

    fun next() {
        if (JFXConfig.optifine) {
            val plat = Installer.getPlatform(JFXConfig)
            if (plat?.getOptiFineVersion() == null) {
                ConfirmationDialog(
                    "OptiFine not found",
                    "We could not find an OptiFine 1.8.9 installation. Would you like to download & install it?"
                ) {
                    Installer.launch {
                        val ver = withContext(Dispatchers.IO) {
                            URL("http://optifine.net/version/1.8.9/HD_U.txt")
                                .readText().lines().first()
                        }
                        val fileName = "OptiFine_1.8.9_HD_U_$ver.jar"
                        withContext(Dispatchers.IO) {
                            val url = "http://optifine.net/adloadx?f=${URLEncoder.encode(fileName, "UTF-8")}"
                            Installer.logger.info("Attempting to open $url")
                            if (!Desktop.browse(URI(url)))
                                Installer.logger.error("Failed to open $url. Please navigate to $url manually.")
                        }
                        val dialog = ConfirmationDialog(
                            "OptiFine installation",
                            "We've opened the download page for you, please download the jar. Once you have finished, press Ok to select the jar.",
                            true
                        ) {
                            if (plat?.getOptiFineVersion() == null) {
                                val files = chooseFile(
                                    "Choose OptiFine jar",
                                    arrayOf(FileChooser.ExtensionFilter("JAR Files (*.jar)", "*.jar"))
                                ) {
                                    initialDirectory = File(System.getProperty("user.home"), "Downloads")
                                    initialFileName = fileName
                                }
                                if (files.isNotEmpty()) {
                                    Installer.launch {
                                        run(files.first())
                                        if (plat?.getOptiFineVersion() != null)
                                            runLater { find<InstallerView> { tabPane.selectionModel.selectNext() } }
                                    }
                                }
                            } else find<InstallerView> { tabPane.selectionModel.selectNext() }
                        }
                        runLater { dialog.openModal() }
                    }
                }.openModal()
            } else find<InstallerView> { tabPane.selectionModel.selectNext() }
        } else
            find<InstallerView> { tabPane.selectionModel.selectNext() }
    }

    fun run(jar: File) {
        val win = System.getProperty("os.name").toLowerCase().contains("win")
        val java = if (win) "java.exe" else "java"
        val bin = File(System.getProperty("java.home"), "bin/$java").canonicalPath
        ProcessBuilder(bin, "-jar", jar.canonicalPath)
            .inheritIO()
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }
}