import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildFeatures.FileContentReplacer
import jetbrains.buildServer.configs.kotlin.v2018_2.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.v2018_2.buildFeatures.replaceContent
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2018_2.failureConditions.BuildFailureOnMetric
import jetbrains.buildServer.configs.kotlin.v2018_2.failureConditions.failOnMetricChange
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2018_2.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2019.1"

project {

    vcsRoot(HttpsGitCodecommitUsWest2amazonawsComV1reposGuestbook)
    vcsRoot(HttpsGithubComYegnauTeamcityGuestbookDemo)
    vcsRoot(HttpsGithubComErnstHaagsmanGuestbookGitRefsHeadsMaster)

    subProject(Build)
    subProject(Deploy)
}

object HttpsGitCodecommitUsWest2amazonawsComV1reposGuestbook : GitVcsRoot({
    name = "https://git-codecommit.us-west-2.amazonaws.com/v1/repos/guestbook"
    url = "https://git-codecommit.us-west-2.amazonaws.com/v1/repos/guestbook"
    authMethod = password {
        userName = "Yegor.Naumov-at-913206223978"
        password = "credentialsJSON:2821e831-6550-4a2c-aa10-8ba2842a8a7e"
    }
})

object HttpsGithubComErnstHaagsmanGuestbookGitRefsHeadsMaster : GitVcsRoot({
    name = "Guestbook VCS"
    pollInterval = 10
    url = "https://github.com/dmitry-treskunov/teamcity-guestbook-demo"
    authMethod = password {
        userName = "dmitry-treskunov"
        password = "credentialsJSON:5e59e9c7-a537-4328-851a-65391b10c060"
    }
    param("teamcitySshKey", "id_rsa_teamcity_guestbook_demo_gh")
})

object HttpsGithubComYegnauTeamcityGuestbookDemo : GitVcsRoot({
    name = "https://github.com/yegnau/teamcity-guestbook-demo"
    url = "https://github.com/yegnau/teamcity-guestbook-demo"
})


object Build : Project({
    name = "Build"

    buildType(Build_ScanForVulnerabilities)
    buildType(Build_1)
    buildType(BuildFrontend)
    buildType(BuildBackendImage)
    buildType(BuildFrontendImage)
    buildTypesOrder = arrayListOf(Build_1, BuildBackendImage, BuildFrontend, BuildFrontendImage)
})

object Build_1 : BuildType({
    id("Build")
    name = "Build Backend"

    artifactRules = "backend/build/libs/*"

    vcs {
        root(DslContext.settingsRoot, "+:backend => backend")
    }

    steps {
        gradle {
            tasks = "build"
            workingDir = "backend"
            dockerImage = "openjdk:8-jdk"
        }
    }

    failureConditions {
        failOnMetricChange {
            enabled = false
            metric = BuildFailureOnMetric.MetricType.TEST_COUNT
            units = BuildFailureOnMetric.MetricUnit.DEFAULT_UNIT
            comparison = BuildFailureOnMetric.MetricComparison.LESS
            compareTo = build {
                buildRule = lastFinished()
            }
        }
    }
    
    disableSettings("BUILD_EXT_2")
})

object BuildBackendImage : BuildType({
    name = "Build Backend Image"

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        dockerCommand {
            name = "Build image"
            commandType = build {
                source = path {
                    path = "backend/Dockerfile"
                }
                contextDir = "backend"
                namesAndTags = "%dockerNamespace%/guestbook-backend:%build.number%"
                commandArgs = "--pull --build-arg JAR_FILE=build/*.jar"
            }
        }
        dockerCommand {
            name = "Push image"
            commandType = push {
                namesAndTags = "%dockerNamespace%/guestbook-backend:%build.number%"
            }
        }
    }

    features {
        dockerSupport {
            cleanupPushedImages = true
            loginToRegistry = on {
                dockerRegistryId = "PROJECT_EXT_4"
            }
        }
    }

    dependencies {
        dependency(Build_1) {
            snapshot {
                reuseBuilds = ReuseBuilds.ANY
            }

            artifacts {
                cleanDestination = true
                artifactRules = "*.jar => backend/build"
            }
        }
    }
})

object BuildFrontend : BuildType({
    name = "Build Frontend"

    artifactRules = "frontend/docker/dist/* => dist/"

    vcs {
        root(DslContext.settingsRoot, "+:frontend => frontend")
    }

    steps {
        script {
            workingDir = "frontend"
            scriptContent = """
                npm install
                npm run build
            """.trimIndent()
            dockerImage = "node"
        }
    }
})

object BuildFrontendImage : BuildType({
    name = "Build Frontend Image"

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        dockerCommand {
            name = "Build image"
            commandType = build {
                source = path {
                    path = "frontend/docker/Dockerfile"
                }
                contextDir = "frontend/docker"
                namesAndTags = "%dockerNamespace%/guestbook-frontend:%build.number%"
                commandArgs = "--pull"
            }
        }
        dockerCommand {
            name = "Push image"
            commandType = push {
                namesAndTags = "%dockerNamespace%/guestbook-frontend:%build.number%"
            }
        }
    }

    features {
        dockerSupport {
            cleanupPushedImages = true
            loginToRegistry = on {
                dockerRegistryId = "PROJECT_EXT_4"
            }
        }
    }

    dependencies {
        dependency(BuildFrontend) {
            snapshot {
                reuseBuilds = ReuseBuilds.ANY
            }

            artifacts {
                cleanDestination = true
                artifactRules = "dist/ => frontend/docker/dist"
            }
        }
    }
})

object Build_ScanForVulnerabilities : BuildType({
    name = "Scan for vulnerabilities"

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        step {
            type = "snykSecurity"
            param("severityThreshold", "high")
            param("file", "backend/build.gradle")
            param("version", "1.149.0")
            param("secure:apiToken", "credentialsJSON:b7a47992-5f9c-46a9-8968-9055eabf2a05")
        }
    }

    dependencies {
        snapshot(Build_1) {
            reuseBuilds = ReuseBuilds.ANY
        }
    }
})


object Deploy : Project({
    name = "Deploy"

    buildType(DeployGuestbook)
    buildType(DeployStaging)

    features {
        feature {
            id = "PROJECT_EXT_6"
            type = "project-graphs"
            param("series", """
                [
                  {
                    "type": "valueType",
                    "title": "Time Spent in Queue",
                    "sourceBuildTypeId": "Guestbook_DeployStaging",
                    "key": "TimeSpentInQueue"
                  }
                ]
            """.trimIndent())
            param("format", "duration")
            param("hideFilters", "")
            param("title", "Commit to Deployment delay")
            param("defaultFilters", "")
            param("seriesTitle", "Serie")
        }
    }
    buildTypesOrder = arrayListOf(DeployStaging, DeployGuestbook)
})

object DeployGuestbook : BuildType({
    name = "Deploy to Production"

    enablePersonalBuilds = false
    type = BuildTypeSettings.Type.DEPLOYMENT
    maxRunningBuilds = 1

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            workingDir = "deployment"
            scriptContent = """
                gcloud components install kubectl
                gcloud container clusters get-credentials guestbook-cluster --region=us-west2-a
                kubectl apply -f guestbook-deployment.yml
            """.trimIndent()
            dockerImage = "google/cloud-sdk:alpine"
        }
    }

    features {
        replaceContent {
            fileRules = "deployment/guestbook-deployment.yml"
            pattern = "us.gcr.io/teamcitytest-166414/guestbook-backend"
            regexMode = FileContentReplacer.RegexMode.FIXED_STRINGS
            replacement = "us.gcr.io/teamcitytest-166414/guestbook-backend:${BuildBackendImage.depParamRefs.buildNumber}"
        }
        replaceContent {
            fileRules = "deployment/guestbook-deployment.yml"
            pattern = "us.gcr.io/teamcitytest-166414/guestbook-frontend"
            regexMode = FileContentReplacer.RegexMode.FIXED_STRINGS
            replacement = "us.gcr.io/teamcitytest-166414/guestbook-frontend:${BuildFrontendImage.depParamRefs.buildNumber}"
        }
    }

    dependencies {
        snapshot(DeployStaging) {
            reuseBuilds = ReuseBuilds.ANY
        }
    }
})

object DeployStaging : BuildType({
    name = "Deploy to Staging"

    enablePersonalBuilds = false
    type = BuildTypeSettings.Type.DEPLOYMENT
    maxRunningBuilds = 1

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            workingDir = "deployment"
            scriptContent = """
                gcloud components install kubectl
                gcloud container clusters get-credentials guestbook-cluster --region=us-west2-a
                kubectl apply -f guestbook-staging.yml
            """.trimIndent()
            dockerImage = "google/cloud-sdk:alpine"
        }
    }

    triggers {
        vcs {
            triggerRules = "-:.teamcity/**"
            branchFilter = ""
        }
    }

    features {
        replaceContent {
            fileRules = "deployment/guestbook-staging.yml"
            pattern = "us.gcr.io/teamcitytest-166414/guestbook-backend"
            regexMode = FileContentReplacer.RegexMode.FIXED_STRINGS
            replacement = "us.gcr.io/teamcitytest-166414/guestbook-backend:${BuildBackendImage.depParamRefs.buildNumber}"
        }
        replaceContent {
            fileRules = "deployment/guestbook-staging.yml"
            pattern = "us.gcr.io/teamcitytest-166414/guestbook-frontend"
            regexMode = FileContentReplacer.RegexMode.FIXED_STRINGS
            replacement = "us.gcr.io/teamcitytest-166414/guestbook-frontend:${BuildFrontendImage.depParamRefs.buildNumber}"
        }
    }

    dependencies {
        snapshot(BuildBackendImage) {
            reuseBuilds = ReuseBuilds.ANY
            onDependencyFailure = FailureAction.IGNORE
        }
        snapshot(BuildFrontendImage) {
            reuseBuilds = ReuseBuilds.ANY
        }
        snapshot(Build_ScanForVulnerabilities) {
            reuseBuilds = ReuseBuilds.ANY
            onDependencyFailure = FailureAction.IGNORE
            onDependencyCancel = FailureAction.IGNORE
        }
    }
})
