package patches.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.ui.*

/*
This patch script was generated by TeamCity on settings change in UI.
To apply the patch, change the buildType with id = 'Build'
accordingly, and delete the patch script.
*/
changeBuildType(RelativeId("Build")) {
    vcs {
        expectEntry(DslContext.settingsRoot.id!!)
        root(DslContext.settingsRoot.id!!, "+:backend => backend")
    }

    expectDisabledSettings("BUILD_EXT_1")
    updateDisabledSettings("BUILD_EXT_2")
}
