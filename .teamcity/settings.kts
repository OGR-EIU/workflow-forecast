import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.sshAgent
import jetbrains.buildServer.configs.kotlin.buildFeatures.swabra
import jetbrains.buildServer.configs.kotlin.buildSteps.python
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

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

version = "2023.05"

project {

    buildType(ForecastMerger)
    buildType(ForecastChecker)
    buildType(ForecastComparer)
    buildType(ForecastInitializer)
    buildType(ForecastRunner)
}

object ForecastChecker : BuildType({
    name = "Forecast checker"

    artifactRules = """
        build-params.json
        output-data.json
        report-forecast/results/report-forecast.bundle.html
    """.trimIndent()

    params {
        text("email.subject", "Intermediate EIU PoC Forecast Report", label = "Email subject", description = "Email notification subject", allowEmpty = false)
        text("workflow.config.country", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        param("matlab.code.forecast", "copyfile('./workflow-forecast/artifact/config.json', pwd); copyfile('./workflow-forecast/analyst', pwd); startup; run_forecast;")
        param("matlab.code.report", "runner('../output-data.json', '../workflow-forecast/report/%workflow.config.country%-input-mapping.json', true);")
        text("workflow.dependencies.data-warehouse-client.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.iris-toolbox.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.model-infra.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.model.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("email.recipients", "ngocnam.nguyen@ogresearch.com, jaromir.benes@ogresearch.com, sergey.plotnikov@ogresearch.com", label = "Email recipients", description = "List of notification email recipients", allowEmpty = false)
        text("workflow.config.timestamp", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("email.body", "Dear all, please find intermediate EIU PoC Forecast Report attached. Best regards, Ngoc Nam Nguyen", label = "Email message", description = "Text of the notification email", allowEmpty = false)
        text("workflow.dependencies.toolset.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
    }

    vcs {
        root(AbsoluteId("ExampleWorkflows_ApiClient"), "+:. => data-warehouse-client")
        root(AbsoluteId("ExampleWorkflows_Iris"), "+:. => iris-toolbox")
        root(AbsoluteId("ExampleWorkflows_Toolset"), "+:. => toolset")
        root(AbsoluteId("ExampleWorkflows_ModelInfra"), "+:. => model-infra")
        root(AbsoluteId("ExampleWorkflows_ReportForecast"), "+:. => report-forecast")
        root(AbsoluteId("ExampleWorkflows_ModelCz"), "+:. => model-cz")
        root(AbsoluteId("ExampleWorkflows_ModelEa"), "+:. => model-ea")
        root(AbsoluteId("ExampleWorkflows_ModelUs"), "+:. => model-us")
        root(DslContext.settingsRoot, "+:. => workflow-forecast")
    }

    steps {
        python {
            name = "Save build parameters"
            command = file {
                filename = "toolset/extract_build_params.py"
                scriptArguments = "--props-path %system.teamcity.configuration.properties.file% --params-path build-params.json"
            }
        }
        python {
            name = "Extract config file"
            workingDir = "workflow-forecast/artifact"
            command = script {
                content = """
                    import subprocess
                    import json
                    
                    # load config file
                    with open("config.json") as f:
                      configs = json.load(f)
                    
                    # get dependencies
                    for key, value in configs["dependencies"].items():
                      # get coutry code
                      if key != "model-infra" and "model" in key:
                        country = key.split("-")[1]
                        subprocess.run(f$TQ echo "##teamcity[setParameter \
                                       name='workflow.config.country' \
                                       value='{country}']" ${TQ}, shell=True)
                        subprocess.run(f$TQ echo "##teamcity[setParameter \
                                       name='workflow.dependencies.model.commit' \
                                       value='{value['commitish']}']" ${TQ}, shell=True)
                      if key == "end":
                        continue
                      else:
                        subprocess.run(f$TQ echo "##teamcity[setParameter \
                                       name='workflow.dependencies.{key}.commit' \
                                        value='{value['commitish']}']" ${TQ}, shell=True)
                    
                    # get timestamp
                    subprocess.run(f$TQ echo "##teamcity[setParameter \
                                   name='workflow.config.timestamp' \
                                   value='{configs['timestamp']}']" ${TQ}, shell=True)
                """.trimIndent()
            }
        }
        script {
            name = "Ensure repo revisions"
            scriptContent = """
                #!/bin/bash
                
                data_warehouse_client=%workflow.dependencies.data-warehouse-client.commit%
                iris_toolbox=%workflow.dependencies.iris-toolbox.commit%
                workflow_forecast="HEAD"
                toolset=%workflow.dependencies.toolset.commit%
                model_infra=%workflow.dependencies.model-infra.commit%
                model_%workflow.config.country%=%workflow.dependencies.model.commit%
                
                cd data-warehouse-client
                if [ ${'$'}data_warehouse_client != "HEAD" ]; then git checkout ${'$'}data_warehouse_client; fi
                cd ../iris-toolbox
                if [ ${'$'}iris_toolbox != "HEAD" ]; then git checkout ${'$'}iris_toolbox; fi
                cd ../workflow-forecast
                if [ ${'$'}workflow_forecast != "HEAD" ]; then git checkout ${'$'}workflow_forecast; fi
                cd ../toolset
                if [ ${'$'}toolset != "HEAD" ]; then git checkout ${'$'}toolset; fi
                cd ../model-infra
                if [ ${'$'}model_infra != "HEAD" ]; then git checkout ${'$'}model_infra; fi
                cd ../model-%workflow.config.country%
                if [ ${'$'}model_%workflow.config.country% != "HEAD" ]; then git checkout ${'$'}model_%workflow.config.country%; fi
                
                cd .. && cp -r model-%workflow.config.country% model
            """.trimIndent()
        }
        python {
            name = "Load settings"
            workingDir = "workflow-forecast/forecast"
            command = file {
                filename = "create_input.py"
                scriptArguments = """--config-path ../../model/requests/input-data-request.json --output-file adjusted-input-data-request.json --params-json '{"snapshot_time":"%workflow.config.timestamp%"}'"""
            }
        }
        python {
            name = "Request data from data warehouse"
            workingDir = "data-warehouse-client"
            pythonVersion = customPython {
                executable = "/usr/bin/python3.11"
            }
            environment = venv {
            }
            command = file {
                filename = "retrieve_data.py"
                scriptArguments = "--json-request ../workflow-forecast/forecast/adjusted-input-data-request.json --save-to ../input-data.json --username %api.username% --password %api.password%"
            }
        }
        script {
            name = "Run forecast"
            scriptContent = """matlab -nodisplay -nodesktop -nosplash -r "%matlab.code.forecast%"; exit ${'$'}?"""
        }
        python {
            name = "Check forecast"
            workingDir = "workflow-forecast"
            environment = venv {
            }
            command = file {
                filename = "forecast/check_forecast.py"
                scriptArguments = "--response-path ../output-data.json --mapping-path ../model/mappings/output-data-mapping.json"
            }
        }
        script {
            name = "Generate report"
            workingDir = "report-forecast"
            scriptContent = """matlab -nodisplay -nodesktop -nosplash -r "%matlab.code.report%"; exit ${'$'}?"""
        }
        python {
            name = "Send email"
            enabled = false
            pythonVersion = customPython {
                executable = "/usr/bin/python3.11"
            }
            command = file {
                filename = "toolset/send_mail.py"
                scriptArguments = "--subject '%email.subject%' --recipients '%email.recipients%' --body '%email.body%' --attachment './report-forecast/results/report-forecast.bundle.html'"
            }
        }
    }

    features {
        swabra {
            forceCleanCheckout = true
        }
    }

    requirements {
        equals("system.agent.name", "Agent 2-1")
    }
})

object ForecastComparer : BuildType({
    name = "Forecast comparer"

    artifactRules = """
        build-params.json
        report-compare/results/report-compare.bundle.html
    """.trimIndent()

    params {
        text("email.subject", "EIU PoC Compare Report", label = "Email subject", description = "Email notification subject", allowEmpty = false)
        select("workflow.config.country", "", label = "Country", description = "Country to report", display = ParameterDisplay.PROMPT,
                options = listOf("CZ" to "cz", "EA" to "ea", "US" to "us"))
        param("matlab.code.report", "runner('../data-warehouse-client/input-data-1.json', '../data-warehouse-client/input-data-2.json', '../workflow-forecast/report/%workflow.config.country%-input-mapping.json', true);")
        text("workflow.dependencies.data-warehouse-client.commit", "HEAD", label = "Data Warehouse Client commit", description = "Commit id of the Data Warehouse Client repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.config.timestamp-1", "", label = "Snapshot time of the 1st database", description = "Timestamp of the 1st database requested from the data warehouse formatted as YYYY-MM-DDThh:mm:ssZ. Current datetime is used if not specified.", display = ParameterDisplay.PROMPT,
              regex = """(^${'$'}|\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z)""", validationMessage = "Should be empty or datetime formatted as YYYY-MM-DDThh:mm:ssZ")
        text("workflow.dependencies.iris-toolbox.commit", "HEAD", label = "IRIS toolbox commit", description = "Commit id of the IRIS toolbox repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("email.recipients", "ngocnam.nguyen@ogresearch.com, jaromir.benes@ogresearch.com, sergey.plotnikov@ogresearch.com", label = "Email recipients", description = "List of notification email recipients", allowEmpty = false)
        text("workflow.config.timestamp-2", "", label = "Snapshot time of the 2nd database", description = "Timestamp of the 2nd database requested from the data warehouse formatted as YYYY-MM-DDThh:mm:ssZ. Current datetime is used if not specified.", display = ParameterDisplay.PROMPT,
              regex = """(^${'$'}|\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z)""", validationMessage = "Should be empty or datetime formatted as YYYY-MM-DDThh:mm:ssZ")
        text("email.body", "Dear all, please find EIU PoC Compare Report attached. Best regards, Ngoc Nam Nguyen", label = "Email message", description = "Text of the notification email", allowEmpty = false)
        text("workflow.dependencies.report-compare.commit", "HEAD", label = "Report compare commit", description = "Commit id of the report compare repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.toolset.commit", "HEAD", label = "Toolset commit", description = "Commit id of the Toolset repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.workflow-forecast.commit", "HEAD", label = "Workflow forecast commit", description = "Commit id of the Workflow forecast repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
    }

    vcs {
        root(AbsoluteId("ExampleWorkflows_ApiClient"), "+:. => data-warehouse-client")
        root(AbsoluteId("ExampleWorkflows_Iris"), "+:. => iris-toolbox")
        root(AbsoluteId("ExampleWorkflows_Toolset"), "+:. => toolset")
        root(DslContext.settingsRoot, "+:. => workflow-forecast")
        root(RelativeId("ReportCompare"), "+:. => report-compare")
    }

    steps {
        python {
            name = "Save build parameters"
            command = file {
                filename = "toolset/extract_build_params.py"
                scriptArguments = "--props-path %system.teamcity.configuration.properties.file% --params-path build-params.json"
            }
        }
        script {
            name = "Ensure repo revisions"
            scriptContent = """
                #!/bin/bash
                
                data_warehouse_client=%workflow.dependencies.data-warehouse-client.commit%
                iris_toolbox=%workflow.dependencies.iris-toolbox.commit%
                workflow_forecast=%workflow.dependencies.workflow-forecast.commit%
                toolset=%workflow.dependencies.toolset.commit%
                report_compare=%workflow.dependencies.report-compare.commit%
                
                cd data-warehouse-client
                if [ ${'$'}data_warehouse_client != "HEAD" ]; then git checkout ${'$'}data_warehouse_client; fi
                cd ../iris-toolbox
                if [ ${'$'}iris_toolbox != "HEAD" ]; then git checkout ${'$'}iris_toolbox; fi
                cd ../workflow-forecast
                if [ ${'$'}workflow_forecast != "HEAD" ]; then git checkout ${'$'}workflow_forecast; fi
                cd ../toolset
                if [ ${'$'}toolset != "HEAD" ]; then git checkout ${'$'}toolset; fi
                cd ../report-compare
                if [ ${'$'}report_compare != "HEAD" ]; then git checkout ${'$'}report_compare; fi
            """.trimIndent()
        }
        python {
            name = "Load settings for the 1st database"
            workingDir = "workflow-forecast/report"
            command = file {
                filename = "create_input.py"
                scriptArguments = """--config-path %workflow.config.country%-cfg-template.json --output-file adjusted-input-cfg.json --params-json '{"snapshot_time":"%workflow.config.timestamp-1%"}'"""
            }
        }
        python {
            name = "Request 1st database from data warehouse"
            workingDir = "data-warehouse-client"
            pythonVersion = customPython {
                executable = "/usr/bin/python3.11"
            }
            environment = venv {
            }
            command = file {
                filename = "retrieve_data.py"
                scriptArguments = "--settings ../workflow-forecast/report/adjusted-input-cfg.json --save-to input-data-1.json --username %api.username% --password %api.password%"
            }
        }
        python {
            name = "Load settings for the 2nd database"
            workingDir = "workflow-forecast/report"
            command = file {
                filename = "create_input.py"
                scriptArguments = """--config-path %workflow.config.country%-cfg-template.json --output-file adjusted-input-cfg.json --params-json '{"snapshot_time":"%workflow.config.timestamp-2%"}'"""
            }
        }
        python {
            name = "Request 2nd database from data warehouse"
            workingDir = "data-warehouse-client"
            pythonVersion = customPython {
                executable = "/usr/bin/python3.11"
            }
            environment = venv {
            }
            command = file {
                filename = "retrieve_data.py"
                scriptArguments = "--settings ../workflow-forecast/report/adjusted-input-cfg.json --save-to input-data-2.json --username %api.username% --password %api.password%"
            }
        }
        script {
            name = "Generate report"
            workingDir = "report-compare"
            scriptContent = """matlab -nodisplay -nodesktop -nosplash -r "%matlab.code.report%"; exit ${'$'}?"""
        }
        python {
            name = "Send email"
            enabled = false
            pythonVersion = customPython {
                executable = "/usr/bin/python3.11"
            }
            command = file {
                filename = "toolset/send_mail.py"
                scriptArguments = "--subject '%email.subject%' --recipients '%email.recipients%' --body '%email.body%' --attachment './report-compare/results/report-compare.bundle.html'"
            }
        }
    }

    features {
        swabra {
            forceCleanCheckout = true
        }
    }

    requirements {
        equals("system.agent.name", "Agent 2-1")
    }
})

object ForecastInitializer : BuildType({
    name = "Forecast initializer"

    artifactRules = """
        workflow-forecast/artifact => %workflow.output.forecast-branch-name%-ANALYST.zip
        build-params.json
    """.trimIndent()
    publishArtifacts = PublishMode.SUCCESSFUL

    params {
        select("workflow.aforecast.model", "", label = "Model", description = "Model to run", display = ParameterDisplay.PROMPT,
                options = listOf("CZ" to "model-cz", "EA" to "model-ea", "US" to "model-us"))
        text("env.DATA_WAREHOUSE_CLIENT_REPO", "data-warehouse-client", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.toolset.commitish", "HEAD", label = "Toolset version", description = "Commitish (SHA, tag, branch, ...) of the Toolset repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.iris-toolbox.commitish", "HEAD", label = "Iris Toolbox version", description = "Commitish (SHA, tag, branch, ...) of the IRIS toolbox repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("env.MODEL_REPO", "%workflow.aforecast.model%", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("env.CI_EMAIL", "noreply@ogresearch.com", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.output.timestamp", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("env.IRIS_TOOLBOX_REPO", "iris-toolbox", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.output.rel-config-path", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.aforecast.snapshot-time", "", label = "Snapshot time", description = "Snapshot time of the series requested from the data warehouse formatted as YYYY-MM-DDThh:mm:ssZ. Current datetime is used if not specified.", display = ParameterDisplay.PROMPT,
              regex = """(^${'$'}|\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z)""", validationMessage = "Should be empty or datetime formatted as YYYY-MM-DDThh:mm:ssZ")
        text("env.CI_AUTHOR", "Production workflow", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.data-warehouse-client.commitish", "HEAD", label = "Data Warehouse Client version", description = "Commitish (SHA, tag, branch, ...) of the Data Warehouse Client repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("env.TOOLSET_REPO", "toolset", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.workflow-forecast.commitish", "HEAD", label = "Workflow version", description = "Commitish (SHA, tag, branch, ...) of the Workflow forecast repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("env.WORKFLOW_FORECAST_REPO", "workflow-forecast", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.model-infra.commitish", "HEAD", label = "Model Infrastructure version", description = "Commitish (SHA, tag, branch, ...) of the Model infrastructure repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.output.forecast-branch-name", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.amodel.commitish", "HEAD", label = "Model version", description = "Commitish (SHA, tag, branch, ...) of the selected model repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("env.MODEL_INFRA_REPO", "model-infra", display = ParameterDisplay.HIDDEN, allowEmpty = true)
    }

    vcs {
        root(AbsoluteId("ExampleWorkflows_ApiClient"), "+:. => data-warehouse-client")
        root(AbsoluteId("ExampleWorkflows_Iris"), "+:. => iris-toolbox")
        root(AbsoluteId("ExampleWorkflows_Toolset"), "+:. => toolset")
        root(AbsoluteId("ExampleWorkflows_ModelInfra"), "+:. => model-infra")
        root(DslContext.settingsRoot, "+:. => workflow-forecast")
        root(AbsoluteId("ExampleWorkflows_ModelCz"), "+:. => model-cz")
        root(AbsoluteId("ExampleWorkflows_ModelEa"), "+:. => model-ea")
        root(AbsoluteId("ExampleWorkflows_ModelUs"), "+:. => model-us")
    }

    steps {
        python {
            name = "Save build parameters"
            command = file {
                filename = "toolset/extract_build_params.py"
                scriptArguments = "--props-path %system.teamcity.configuration.properties.file% --params-path build-params.json"
            }
        }
        script {
            name = "Ensure repo revisions"
            scriptContent = """
                #!/bin/bash
                
                data_warehouse_client_commitish=%workflow.dependencies.data-warehouse-client.commitish%%
                iris_toolbox_commitish=%workflow.dependencies.iris-toolbox.commitish%
                workflow_forecast_commitish=%workflow.dependencies.workflow-forecast.commitish%
                toolset_commitish=%workflow.dependencies.toolset.commitish%
                model_infra_commitish=%workflow.dependencies.model-infra.commitish%
                
                model=%env.MODEL_REPO%
                model_commitish=%workflow.dependencies.amodel.commitish%
                
                
                cd data-warehouse-client
                git checkout ${'$'}data_warehouse_client_commitish
                pwd
                git log -n 1
                cd ..
                
                cd iris-toolbox
                git checkout ${'$'}iris_toolbox_commitish
                pwd
                git log -n 1
                cd ..
                
                cd workflow-forecast
                git checkout ${'$'}workflow_forecast_commitish
                pwd
                git log -n 1
                cd ..
                
                cd toolset
                git checkout ${'$'}toolset_commitish
                pwd
                git log -n 1
                cd ..
                
                cd model-infra
                git checkout ${'$'}model_infra_commitish
                pwd
                git log -n 1
                cd ..
                
                cd ${'$'}model
                git checkout ${'$'}model_commitish
                cd ..
            """.trimIndent()
        }
        script {
            name = "Create timestamp and branch name"
            scriptContent = """
                #!/bin/bash
                
                if [ "%workflow.aforecast.snapshot-time%" == "" ]; then
                  timestamp="${'$'}(date -uIseconds)"
                  timestamp="${'$'}{timestamp/+00:00/}Z"
                else
                  timestamp=%workflow.aforecast.snapshot-time%
                fi
                
                forecast_branch_name="forecast-%workflow.aforecast.model%-${'$'}timestamp"
                forecast_branch_name="${'$'}{forecast_branch_name//:/-}"
                
                echo "##teamcity[setParameter name='workflow.output.timestamp' value='${'$'}timestamp']"
                echo "##teamcity[setParameter name='workflow.output.forecast-branch-name' value='${'$'}forecast_branch_name']"
            """.trimIndent()
        }
        script {
            name = "Create config.json"
            scriptContent = """
                #!/bin/bash
                
                DIR_PATH=${'$'}(pwd)
                REL_CONFIG_PATH=artifact/config.json
                CONFIG_PATH=${'$'}DIR_PATH/%env.WORKFLOW_FORECAST_REPO%/${'$'}REL_CONFIG_PATH
                echo '{' > ${'$'}CONFIG_PATH
                forecast_branch_name=%workflow.output.forecast-branch-name%
                timestamp=%workflow.output.timestamp%
                model=%workflow.aforecast.model%
                printf '    "forecast_branch_name": "%s",\n' ${'$'}forecast_branch_name >> ${'$'}CONFIG_PATH
                printf '    "timestamp": "%s",\n' ${'$'}timestamp >> ${'$'}CONFIG_PATH
                printf '    "model": "%s",\n' ${'$'}model >> ${'$'}CONFIG_PATH
                #
                # Local dependencies (only installed if local=True)
                printf '    "local-dependencies": {\n' >> ${'$'}CONFIG_PATH
                cd ${'$'}DIR_PATH/%env.WORKFLOW_FORECAST_REPO%
                directory="workflow-forecast"
                url=${'$'}(git remote get-url origin)
                branch=%workflow.output.forecast-branch-name%-ANALYST
                cd ${'$'}DIR_PATH
                printf '        "%s": {"url": "%s", "branch": "%s", "commitish": null},\n' ${'$'}directory ${'$'}url ${'$'}branch >> ${'$'}CONFIG_PATH
                printf '        "end": null\n' >> ${'$'}CONFIG_PATH
                printf '    },\n' >> ${'$'}CONFIG_PATH
                #
                # General dependencies
                printf '    "dependencies": {\n' >> ${'$'}CONFIG_PATH
                for directory in \
                    %env.MODEL_REPO% \
                    %env.MODEL_INFRA_REPO% \
                    %env.TOOLSET_REPO% \
                    %env.DATA_WAREHOUSE_CLIENT_REPO% \
                    %env.IRIS_TOOLBOX_REPO% \
                ; do
                    cd ${'$'}DIR_PATH/${'$'}directory
                    url=${'$'}(git remote get-url origin)
                    commitish=${'$'}(git rev-parse --short HEAD)
                    cd ${'$'}DIR_PATH
                    printf '        "%s": {"url": "%s", "branch": null, "commitish": "%s"},\n' ${'$'}directory ${'$'}url ${'$'}commitish >> ${'$'}CONFIG_PATH
                done
                printf '        "end": null\n' >> ${'$'}CONFIG_PATH
                printf '    },\n' >> ${'$'}CONFIG_PATH
                printf '    "end": null\n' >> ${'$'}CONFIG_PATH
                echo '}' >> ${'$'}CONFIG_PATH
                echo "##teamcity[setParameter name='workflow.output.rel-config-path' value='${'$'}REL_CONFIG_PATH']"
                
                echo ****************************************************************
                cat ${'$'}CONFIG_PATH
                echo ****************************************************************
            """.trimIndent()
        }
        script {
            name = "Create main forecast branch on workflow-forecast repo and get its SHA"
            scriptContent = """
                #!/bin/bash
                
                cd %env.WORKFLOW_FORECAST_REPO%
                forecast_branch_name=%workflow.output.forecast-branch-name%
                #
                git config user.name %env.CI_AUTHOR%
                git config user.email %env.CI_EMAIL%
                #
                git switch -c "${'$'}forecast_branch_name"
                git add %workflow.output.rel-config-path%
                git commit -m "Created config.json"
                git push origin "${'$'}forecast_branch_name"
                sha="${'$'}(git rev-parse --short HEAD)"
                echo "##teamcity[setParameter name='workflow.output.sha' value='${'$'}sha']"
            """.trimIndent()
        }
        script {
            name = "Create and commit the ANALYST forecast branch"
            scriptContent = """
                #!/bin/bash
                
                cd %env.WORKFLOW_FORECAST_REPO%
                forecast_branch_name=%workflow.output.forecast-branch-name%
                git switch -c "${'$'}forecast_branch_name-ANALYST"
                git push origin "${'$'}forecast_branch_name-ANALYST"
            """.trimIndent()
        }
    }

    features {
        sshAgent {
            teamcitySshKey = "TC-SSH"
        }
        swabra {
            forceCleanCheckout = true
        }
    }

    requirements {
        equals("system.agent.name", "Agent 2-1")
    }
})

object ForecastMerger : BuildType({
    name = "Forecast merger"

    artifactRules = "build-params.json"

    params {
        password("gh.token", "credentialsJSON:85b007fe-292c-4963-80ac-a225e833a9e7", label = "GitHub token", description = "GitHub token required to run GitHub CLI")
    }

    vcs {
        root(DslContext.settingsRoot, "+:. => workflow-forecast")
        root(AbsoluteId("ExampleWorkflows_Toolset"), "+:. => toolset")
    }

    steps {
        python {
            name = "Save build parameters"
            command = file {
                filename = "toolset/extract_build_params.py"
                scriptArguments = "--props-path %system.teamcity.configuration.properties.file% --params-path build-params.json"
            }
        }
        script {
            name = "Create merge request"
            workingDir = "workflow-forecast"
            scriptContent = """
                #!/bin/bash
                
                input_branch_name=${'$'}(git rev-parse --abbrev-ref HEAD)
                output_branch_name=${'$'}(echo ${'$'}input_branch_name | sed 's/-ANALYST//g')
                
                gh auth login --with-token <<< %gh.token%
                gh pr create \
                --title "Merging ${'$'}input_branch_name" \
                --body "Merging ${'$'}input_branch_name to ${'$'}output_branch_name" \
                --base ${'$'}output_branch_name \
                --head ${'$'}input_branch_name \
                --assignee nul0m \
                --assignee jaromir-benes
            """.trimIndent()
        }
    }

    triggers {
        vcs {
            triggerRules = "+:root=${DslContext.settingsRoot.id}:/analyst/**"

            branchFilter = "+:refs/heads/forecast-*Z-ANALYST"
        }
    }

    features {
        swabra {
            forceCleanCheckout = true
        }
    }

    dependencies {
        snapshot(ForecastChecker) {
            reuseBuilds = ReuseBuilds.NO
            onDependencyCancel = FailureAction.ADD_PROBLEM
        }
    }
})

object ForecastRunner : BuildType({
    name = "Forecast runner"

    artifactRules = """
        build-params.json
        output-data.json
        report-forecast/results/report-forecast.bundle.html
    """.trimIndent()

    params {
        text("email.subject", "Final EIU PoC Forecast Report", label = "Email subject", description = "Email notification subject", allowEmpty = false)
        text("workflow.config.country", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        param("matlab.code.forecast", "copyfile('./workflow-forecast/artifact/config.json', pwd); copyfile('./workflow-forecast/analyst', pwd); startup; run_forecast;")
        text("workflow.dependencies.model.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.config.timestamp", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("email.body", "Dear all, please find final EIU PoC Forecast Report attached. Best regards, Ngoc Nam Nguyen", label = "Email message", description = "Text of the notification email", allowEmpty = false)
        param("matlab.code.report", "runner('../data-warehouse-client/post-output.json', '../workflow-forecast/report/%workflow.config.country%-input-mapping.json', true);")
        text("workflow.dependencies.data-warehouse-client.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.iris-toolbox.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.model-infra.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("email.recipients", "ngocnam.nguyen@ogresearch.com, jaromir.benes@ogresearch.com, sergey.plotnikov@ogresearch.com", label = "Email recipients", description = "List of notification email recipients", allowEmpty = false)
        text("workflow.forecast.country", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.toolset.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
    }

    vcs {
        root(AbsoluteId("ExampleWorkflows_ApiClient"), "+:. => data-warehouse-client")
        root(AbsoluteId("ExampleWorkflows_Iris"), "+:. => iris-toolbox")
        root(AbsoluteId("ExampleWorkflows_Toolset"), "+:. => toolset")
        root(AbsoluteId("ExampleWorkflows_ModelInfra"), "+:. => model-infra")
        root(DslContext.settingsRoot, "+:. => workflow-forecast")
        root(AbsoluteId("ExampleWorkflows_ReportForecast"), "+:. => report-forecast")
        root(AbsoluteId("ExampleWorkflows_ModelCz"), "+:. => model-cz")
        root(AbsoluteId("ExampleWorkflows_ModelEa"), "+:. => model-ea")
        root(AbsoluteId("ExampleWorkflows_ModelUs"), "+:. => model-us")
    }

    steps {
        python {
            name = "Save build parameters"
            command = file {
                filename = "toolset/extract_build_params.py"
                scriptArguments = "--props-path %system.teamcity.configuration.properties.file% --params-path build-params.json"
            }
        }
        python {
            name = "Extract config file"
            workingDir = "workflow-forecast/artifact"
            command = script {
                content = """
                    import subprocess
                    import json
                    
                    # load config file
                    with open("config.json") as f:
                      configs = json.load(f)
                    
                    # get dependencies
                    for key, value in configs["dependencies"].items():
                      # get coutry code
                      if key != "model-infra" and "model" in key:
                        country = key.split("-")[1]
                        subprocess.run(f$TQ echo "##teamcity[setParameter \
                                       name='workflow.config.country' \
                                       value='{country}']" ${TQ}, shell=True)
                        subprocess.run(f$TQ echo "##teamcity[setParameter \
                                       name='workflow.dependencies.model.commit' \
                                       value='{value['commitish']}']" ${TQ}, shell=True)
                      if key == "end":
                        continue
                      else:
                        subprocess.run(f$TQ echo "##teamcity[setParameter \
                                       name='workflow.dependencies.{key}.commit' \
                                        value='{value['commitish']}']" ${TQ}, shell=True)
                    
                    # get timestamp
                    subprocess.run(f$TQ echo "##teamcity[setParameter \
                                   name='workflow.config.timestamp' \
                                   value='{configs['timestamp']}']" ${TQ}, shell=True)
                """.trimIndent()
            }
        }
        script {
            name = "Ensure repo revisions"
            scriptContent = """
                #!/bin/bash
                
                data_warehouse_client=%workflow.dependencies.data-warehouse-client.commit%
                iris_toolbox=%workflow.dependencies.iris-toolbox.commit%
                workflow_forecast="HEAD"
                toolset=%workflow.dependencies.toolset.commit%
                model_infra=%workflow.dependencies.model-infra.commit%
                model_%workflow.config.country%=%workflow.dependencies.model.commit%
                
                cd data-warehouse-client
                if [ ${'$'}data_warehouse_client != "HEAD" ]; then git checkout ${'$'}data_warehouse_client; fi
                cd ../iris-toolbox
                if [ ${'$'}iris_toolbox != "HEAD" ]; then git checkout ${'$'}iris_toolbox; fi
                cd ../workflow-forecast
                git branch
                git log -n 1
                if [ ${'$'}workflow_forecast != "HEAD" ]; then git checkout ${'$'}workflow_forecast; fi
                cd ../toolset
                if [ ${'$'}toolset != "HEAD" ]; then git checkout ${'$'}toolset; fi
                cd ../model-infra
                if [ ${'$'}model_infra != "HEAD" ]; then git checkout ${'$'}model_infra; fi
                cd ../model-%workflow.config.country%
                if [ ${'$'}model_%workflow.config.country% != "HEAD" ]; then git checkout ${'$'}model_%workflow.config.country%; fi
                
                cd .. && cp -r model-%workflow.config.country% model
            """.trimIndent()
        }
        python {
            name = "Forecast step: Load settings"
            workingDir = "workflow-forecast/forecast"
            command = file {
                filename = "create_input.py"
                scriptArguments = """--config-path ../../model/requests/input-data-request.json --output-file adjusted-input-data-request.json --params-json '{"snapshot_time":"%workflow.config.timestamp%"}'"""
            }
        }
        python {
            name = "Forecast step: Request data from data warehouse"
            workingDir = "data-warehouse-client"
            pythonVersion = customPython {
                executable = "/usr/bin/python3.11"
            }
            environment = venv {
            }
            command = file {
                filename = "retrieve_data.py"
                scriptArguments = "--json-request ../workflow-forecast/forecast/adjusted-input-data-request.json --save-to ../input-data.json --username %api.username% --password %api.password%"
            }
        }
        script {
            name = "Forecast step: Run forecast"
            scriptContent = """
                cp ./workflow-forecast/artifact/config.json ./
                cp ./workflow-forecast/analyst/run_forecast.m ./
                cp ./workflow-forecast/analyst/apply_new_judgment.m ./
                cp ./workflow-forecast/analyst/startup.m ./
                echo ==============================================================
                cat apply_new_judgment.m
                echo ==============================================================
                matlab -nodisplay -nodesktop -nosplash -batch run_forecast
            """.trimIndent()
        }
        python {
            name = "Forecast step: Check forecast"
            workingDir = "workflow-forecast"
            environment = venv {
            }
            command = file {
                filename = "forecast/check_forecast.py"
                scriptArguments = "--response-path ../output-data.json --mapping-path ../model/mappings/output-data-mapping.json"
            }
        }
        python {
            name = "Forecast step: Submit daily data to data warehouse"
            workingDir = "data-warehouse-client"
            environment = venv {
                requirementsFile = ""
            }
            command = file {
                filename = "submit_data.py"
                scriptArguments = "--json-request ../output-data.json --username %api.username% --password %api.password%"
            }
        }
        python {
            name = "Report step: Load settings"
            workingDir = "workflow-forecast/report"
            command = file {
                filename = "create_input.py"
                scriptArguments = """--config-path %workflow.config.country%-cfg-template.json --output-file adjusted-input-cfg.json --params-json '{"snapshot_time":"%workflow.config.timestamp%"}'"""
            }
        }
        python {
            name = "Report step: Request data from data warehouse"
            enabled = false
            workingDir = "data-warehouse-client"
            pythonVersion = customPython {
                executable = "/usr/bin/python3.11"
            }
            environment = venv {
            }
            command = file {
                filename = "retrieve_data.py"
                scriptArguments = "--settings ../workflow-forecast/report/adjusted-input-cfg.json --save-to post-output.json --username %api.username% --password %api.password%"
            }
        }
        script {
            name = "Report step: Generate report"
            enabled = false
            workingDir = "report-forecast"
            scriptContent = """matlab -nodisplay -nodesktop -nosplash -r "%matlab.code.report%"; exit ${'$'}?"""
        }
        python {
            name = "Report step: Send email"
            enabled = false
            pythonVersion = customPython {
                executable = "/usr/bin/python3.11"
            }
            command = file {
                filename = "toolset/send_mail.py"
                scriptArguments = "--subject '%email.subject%' --recipients '%email.recipients%' --body '%email.body%' --attachment './report-forecast/results/report-forecast.bundle.html'"
            }
        }
    }

    triggers {
        vcs {
            triggerRules = "+:root=${DslContext.settingsRoot.id}:/analyst/**"

            branchFilter = "+:refs/heads/forecast-*Z"
        }
    }

    features {
        swabra {
            forceCleanCheckout = true
        }
    }

    requirements {
        equals("system.agent.name", "Agent 2-1")
    }
})
