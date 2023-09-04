import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.sshAgent
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

    buildType(ForecastChecker)
    buildType(ForecastInitializer)
    buildType(ForecastRunner)
}

object ForecastChecker : BuildType({
    name = "Forecast checker"

    artifactRules = """
        build-params.json
        input-data.json
        output-data.json
        report-forecast/results/report-forecast.bundle.html
    """.trimIndent()

    params {
        text("workflow.config.country", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        param("matlab.code.forecast", "copyfile('./workflow-forecast/artifact/config.json', pwd); copyfile('./workflow-forecast/analyst', pwd); startup; run_forecast;")
        param("matlab.code.report", "runner('../output-data.json', 'model', true);")
        text("workflow.dependencies.data-warehouse-client.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.iris-toolbox.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.model-infra.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.model.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.config.timestamp", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
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
            scriptContent = """matlab -nodisplay -nodesktop -nosplash -r "%matlab.code.forecast%"; exit ${'$'}?"""
        }
        script {
            name = "Report step: Generate report"
            workingDir = "report-forecast"
            scriptContent = """matlab -nodisplay -nodesktop -nosplash -r "%matlab.code.report%"; exit ${'$'}?"""
        }
    }

    triggers {
        vcs {
            triggerRules = "+:root=${DslContext.settingsRoot.id}:**"

            branchFilter = "+:forecast-*-ANALYST"
            perCheckinTriggering = true
            groupCheckinsByCommitter = true
            enableQueueOptimization = false
        }
    }

    requirements {
        equals("system.agent.name", "Agent 2-1")
    }
})

object ForecastInitializer : BuildType({
    name = "Forecast initializer"

    artifactRules = """
        build-params.json
        artifact => artifact.zip
    """.trimIndent()

    params {
        text("env.DATA_WAREHOUSE_CLIENT_REPO", "data-warehouse-client", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("env.MODEL_REPO", "%workflow.forecast.model%", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.model-cz.commit", "HEAD", label = "Model CZ commit", description = "Commit id of the Model CZ repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.forecast.snapshot-time", "", label = "Snapshot time", description = "Snapshot time of the series requested from the data warehouse formatted as YYYY-MM-DDThh:mm:ssZ. Current datetime is used if not specified.", display = ParameterDisplay.PROMPT,
              regex = """(^${'$'}|\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z)""", validationMessage = "Should be empty or datetime formatted as YYYY-MM-DDThh:mm:ssZ")
        text("env.CI_EMAIL", "noreply@ogresearch.com", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        select("workflow.forecast.model", "", label = "Model", description = "Model to be forecasted", display = ParameterDisplay.PROMPT,
                options = listOf("CZ" to "model-cz", "EA" to "model-ea", "US" to "model-us"))
        text("workflow.output.timestamp", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("env.IRIS_TOOLBOX_REPO", "iris-toolbox", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.output.rel-config-path", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("env.CI_AUTHOR", "Production workflow", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("env.TOOLSET_REPO", "toolset", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.model-ea.commit", "HEAD", label = "Model EA commit", description = "Commit id of the Model EA repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.model-us.commit", "HEAD", label = "Model US commit", description = "Commit id of the Model US repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.data-warehouse-client.commit", "HEAD", label = "Data Warehouse Client commit", description = "Commit id of the Data Warehouse Client repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("env.WORKFLOW_FORECAST_REPO", "workflow-forecast", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.output.forecast-branch-name", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.dependencies.iris-toolbox.commit", "HEAD", label = "IRIS toolbox commit", description = "Commit id of the IRIS toolbox repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.model-infra.commit", "HEAD", label = "Model infrastructure commit", description = "Commit id of the Model infrastructure repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.toolset.commit", "HEAD", label = "Toolset commit", description = "Commit id of the Toolset repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.workflow-forecast.commit", "HEAD", label = "Workflow forecast commit", description = "Commit id of the Workflow forecast repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
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
                
                data_warehouse_client=%workflow.dependencies.data-warehouse-client.commit%
                iris_toolbox=%workflow.dependencies.iris-toolbox.commit%
                workflow_forecast=%workflow.dependencies.workflow-forecast.commit%
                toolset=%workflow.dependencies.toolset.commit%
                model_infra=%workflow.dependencies.model-infra.commit%
                model_cz=%workflow.dependencies.model-cz.commit%
                model_ea=%workflow.dependencies.model-ea.commit%
                model_us=%workflow.dependencies.model-us.commit%
                
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
                cd ../model-cz
                if [ ${'$'}model_cz != "HEAD" ]; then git checkout ${'$'}model_cz; fi
                cd ../model-ea
                if [ ${'$'}model_ea != "HEAD" ]; then git checkout ${'$'}model_ea; fi
                cd ../model-us
                if [ ${'$'}model_us != "HEAD" ]; then git checkout ${'$'}model_us; fi
            """.trimIndent()
        }
        script {
            name = "Create timestamp and branch name"
            scriptContent = """
                #!/bin/bash
                
                if [ "%workflow.forecast.snapshot-time%" == "" ]; then
                  timestamp="${'$'}(date -uIseconds)"
                  timestamp="${'$'}{timestamp/+00:00/}Z"
                else
                  timestamp=%workflow.forecast.snapshot-time%
                fi
                
                forecast_branch_name="forecast-%workflow.forecast.model%-${'$'}timestamp"
                forecast_branch_name="${'$'}{forecast_branch_name//:/-}"
                
                echo "##teamcity[setParameter name='workflow.output.timestamp' value='${'$'}timestamp']"
                echo "##teamcity[setParameter name='workflow.output.forecast-branch-name' value='${'$'}forecast_branch_name']"
            """.trimIndent()
        }
        script {
            name = "Prepare config file"
            scriptContent = """
                #!/bin/bash
                
                DIR_PATH=${'$'}(pwd)
                REL_CONFIG_PATH=artifact/config.json
                CONFIG_PATH=${'$'}DIR_PATH/%env.WORKFLOW_FORECAST_REPO%/${'$'}REL_CONFIG_PATH
                echo '{' > ${'$'}CONFIG_PATH
                forecast_branch_name=%workflow.output.forecast-branch-name%
                timestamp=%workflow.output.timestamp%
                printf '    "forecast_branch_name": "%s",\n' ${'$'}forecast_branch_name >> ${'$'}CONFIG_PATH
                printf '    "timestamp": "%s",\n' ${'$'}timestamp >> ${'$'}CONFIG_PATH
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
    }

    requirements {
        equals("system.agent.name", "Agent 2-1")
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
        text("email.subject", "EIU PoC Forecast Report", label = "Email subject", description = "Email notification subject", allowEmpty = false)
        text("workflow.config.country", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        param("matlab.code.forecast", "copyfile('./workflow-forecast/artifact/config.json', pwd); copyfile('./workflow-forecast/analyst', pwd); startup; run_forecast;")
        text("workflow.dependencies.model.commit", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("workflow.config.timestamp", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("email.body", "Dear all, please find EIU PoC Forecast Report attached. Best regards, Ngoc Nam Nguyen", label = "Email message", description = "Text of the notification email", allowEmpty = false)
        param("matlab.code.report", "runner('../data-warehouse-client/post-output.json', 'model', true);")
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
            scriptContent = """matlab -nodisplay -nodesktop -nosplash -r "%matlab.code.forecast%"; exit ${'$'}?"""
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

    requirements {
        equals("system.agent.name", "Agent 2-1")
    }
})
