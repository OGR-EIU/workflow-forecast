import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.sshAgent
import jetbrains.buildServer.configs.kotlin.buildSteps.python
import jetbrains.buildServer.configs.kotlin.buildSteps.script

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

    buildType(IntermediateForecastRunner)
    buildType(ForecastInitializer)
    buildType(ForecastRunner)
}

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
              regex = """(^${'$'}|\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3}Z)""", validationMessage = "Should be empty or datetime formatted as YYYY-MM-DDThh:mm:ssZ")
        text("env.CI_EMAIL", "noreply@ogresearch.com", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        select("workflow.forecast.model", "", label = "Model", description = "Model to be forecasted", display = ParameterDisplay.PROMPT,
                options = listOf("CZ" to "Model-CZ", "EA" to "Model-EA", "US" to "Model-US"))
        text("workflow.output.timestamp", "", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        text("env.IRIS_TOOLBOX_REPO", "iris-toolbox", display = ParameterDisplay.HIDDEN, allowEmpty = true)
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
            name = "Create main forecast branch on workflow-forecast repo and get its SHA"
            scriptContent = """
                #!/bin/bash
                
                cd workflow-forecast
                forecast_branch_name=%workflow.output.forecast-branch-name%
                git config user.name %env.CI_AUTHOR%
                git config user.email %env.CI_EMAIL%
                git switch -c "${'$'}forecast_branch_name"
                git push origin "${'$'}forecast_branch_name"
                sha="${'$'}(git rev-parse --short HEAD)"
                echo "##teamcity[setParameter name='workflow.output.sha' value='${'$'}sha']"
            """.trimIndent()
        }
        script {
            name = "Prepare artifact"
            scriptContent = """
                #!/bin/bash
                
                cp -r ./workflow-forecast/artifact ./
            """.trimIndent()
        }
        script {
            name = "Prepare config file"
            scriptContent = """
                #!/bin/bash
                
                CONFIG_FILE=./artifact/config.json
                echo '{' > ${'$'}CONFIG_FILE
                printf '    "forecast_branch_name": "%s",\n' %workflow.output.forecast-branch-name% >> ${'$'}CONFIG_FILE
                printf '    "timestamp": "%s",\n' %workflow.output.timestamp% >> ${'$'}CONFIG_FILE
                printf '    "dependencies": [\n' >> ${'$'}CONFIG_FILE
                cat ${'$'}CONFIG_FILE
                for d in \
                  %env.WORKFLOW_FORECAST_REPO% \
                  %env.MODEL_REPO% \
                  %env.MODEL_INFRA_REPO% \
                  %env.TOOLSET_REPO% \
                  %env.DATA_WAREHOUSE_CLIENT_REPO% \
                  %env.IRIS_TOOLBOX_REPO% \
                ; do
                  cd ${'$'}d
                  url=${'$'}(git remote get-url origin)
                  commit=${'$'}(git rev-parse --short HEAD)
                  cd ..
                  printf '        {"dir": "%s", "url": "%s", "commit": "%s"},\n' ${'$'}d ${'$'}url ${'$'}commit >> ${'$'}CONFIG_FILE
                done
                printf '    ],\n' >> ${'$'}CONFIG_FILE
                echo '    "end": ""' >> ${'$'}CONFIG_FILE
                echo '}' >> ${'$'}CONFIG_FILE
            """.trimIndent()
        }
        script {
            name = "Print config file"
            scriptContent = """
                #!/bin/bash
                
                echo ****************************************************************
                cat ./artifact/config.json
                echo ****************************************************************
            """.trimIndent()
        }
        script {
            name = "Create and commit the ANALYST forecast branch"
            scriptContent = """
                #!/bin/bash
                
                cd workflow-forecast
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
        settings/forecast/forecast-output.json
        report/results/report-forecast.bundle.html
    """.trimIndent()

    params {
        text("email.subject", "EIU PoC Forecast Report", label = "Email subject", description = "Email notification subject", allowEmpty = false)
        param("matlab.code.forecast", "runner('../../model-%workflow.forecast.country%', '%workflow.forecast.country%-input-mapping.json', '../../api-client/request-output.json', '../../toolset/tunes.csv', '%workflow.forecast.country%-output-mapping.json', 'forecast-output.json', true);")
        text("workflow.dependencies.model-cz.commit", "HEAD", label = "Model CZ commit", description = "Commit id of the Model CZ repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.iris.commit", "HEAD", label = "IRIS toolbox commit", description = "Commit id of the IRIS toolbox repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        select("workflow.forecast.scenario", "", label = "Scenario", description = "Which scenario to run", display = ParameterDisplay.PROMPT,
                options = listOf("None" to """""""", "EA scenario 2" to "1LkB7ulgEB6XLiqvTJrPeAQtoWYCLeBtx", "US scenario 1" to "1Lp6E1CIxPqSy6wZ4NZDaf2KmNnLeuhRp", "CZ scenario 1" to "1M4r9Xp1aQ_ryFdt9qIL_zPLJ6NfitsKJ", "US scenario 2" to "1Lmr-yiVvfWZQFLbj3k0uwMK7k0OIOgUS", "CZ scenario 2" to "1LvEP-31khLkjC59HM_e2tGHbRgqwxNjE", "EA scenario 1" to "1Ljf8u1ExyLVNmgTSpWnKxyB5Aqn5feRn"))
        text("workflow.dependencies.settings.commit", "HEAD", label = "Workflow Settings commit", description = "Commit id of the Workflow Settings repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("email.body", "Dear all, please find EIU PoC Forecast Report attached. Best regards, Ngoc Nam Nguyen", label = "Email message", description = "Text of the notification email", allowEmpty = false)
        text("workflow.dependencies.model-template.commit", "HEAD", label = "Model template commit", description = "Commit id of the Model template repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.report.commit", "HEAD", label = "Report commit", description = "Commit id of the report repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.model-ea.commit", "HEAD", label = "Model EA commit", description = "Commit id of the Model EA repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        param("matlab.code.report", "runner('../api-client/post-output.json', '../settings/report/%workflow.forecast.country%-input-mapping.json', true);")
        text("workflow.dependencies.model-us.commit", "HEAD", label = "Model US commit", description = "Commit id of the Model US repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.adhoc.snapshot-time", "", label = "Snapshot time", description = "Snapshot time of the series requested from the data warehouse formatted as YYYY-MM-DDThh:mm:ss.SSSZ. Current datetime is used if not specified.", display = ParameterDisplay.PROMPT,
              regex = """(^${'$'}|\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3}Z)""", validationMessage = "Should be empty or datetime formatted as YYYY-MM-DDThh:mm:ss.SSSZ")
        text("workflow.dependencies.data-warehouse-client.commit", "HEAD", label = "Data Warehouse Client commit", description = "Commit id of the Data Warehouse Client repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.model-infra.commit", "HEAD", label = "Model infrastructure commit", description = "Commit id of the Model infrastructure repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("email.recipients", "ngocnam.nguyen@ogresearch.com, jaromir.benes@ogresearch.com, sergey.plotnikov@ogresearch.com", label = "Email recipients", description = "List of notification email recipients", allowEmpty = false)
        select("workflow.forecast.country", "", label = "Country code", description = "Country to be forecasted", display = ParameterDisplay.PROMPT,
                options = listOf("CZ" to "cz", "EA" to "ea", "US" to "us"))
        text("workflow.dependencies.toolset.commit", "HEAD", label = "Toolset commit", description = "Commit id of the Toolset repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
    }

    vcs {
        root(AbsoluteId("ExampleWorkflows_ApiClient"), "+:. => api-client")
        root(AbsoluteId("ExampleWorkflows_Iris"), "+:. => iris")
        root(AbsoluteId("ExampleWorkflows_Toolset"), "+:. => toolset")
        root(AbsoluteId("ExampleWorkflows_ModelInfra"), "+:. => model-infra")
        root(DslContext.settingsRoot, "+:. => settings")
        root(AbsoluteId("ExampleWorkflows_ModelTemplate"), "+:. => model-template")
        root(AbsoluteId("ExampleWorkflows_ReportForecast"), "+:. => report")
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
                
                api_client=%workflow.dependencies.data-warehouse-client.commit%
                iris=%workflow.dependencies.iris.commit%
                settings=%workflow.dependencies.settings.commit%
                toolset=%workflow.dependencies.toolset.commit%
                model_infra=%workflow.dependencies.model-infra.commit%
                model_template=%workflow.dependencies.model-template.commit%
                model_cz=%workflow.dependencies.model-cz.commit%
                model_ea=%workflow.dependencies.model-ea.commit%
                model_us=%workflow.dependencies.model-us.commit%
                
                cd api-client
                if [ ${'$'}api_client != "HEAD" ]; then git checkout ${'$'}api_client; fi
                cd ../iris
                if [ ${'$'}iris != "HEAD" ]; then git checkout ${'$'}iris; fi
                cd ../settings
                if [ ${'$'}settings != "HEAD" ]; then git checkout ${'$'}settings; fi
                cd ../toolset
                if [ ${'$'}toolset != "HEAD" ]; then git checkout ${'$'}toolset; fi
                cd ../model-infra
                if [ ${'$'}model_infra != "HEAD" ]; then git checkout ${'$'}model_infra; fi
                cd ../model-template
                if [ ${'$'}model_template != "HEAD" ]; then git checkout ${'$'}model_template; fi
                cd ../model-cz
                if [ ${'$'}model_cz != "HEAD" ]; then git checkout ${'$'}model_cz; fi
                cd ../model-ea
                if [ ${'$'}model_ea != "HEAD" ]; then git checkout ${'$'}model_ea; fi
                cd ../model-us
                if [ ${'$'}model_us != "HEAD" ]; then git checkout ${'$'}model_us; fi
            """.trimIndent()
        }
        python {
            name = "Forecast step: Load settings"
            workingDir = "settings/forecast"
            command = file {
                filename = "create_input.py"
                scriptArguments = """--config-path %workflow.forecast.country%-cfg-template.json --output-file adjusted-input-cfg.json --params-json '{"snapshot_time":"%workflow.adhoc.snapshot-time%"}'"""
            }
        }
        python {
            name = "Forecast step: Request data from data warehouse"
            workingDir = "api-client"
            pythonVersion = customPython {
                executable = "/usr/bin/python3.11"
            }
            environment = venv {
            }
            command = file {
                filename = "retrieve_data.py"
                scriptArguments = "--settings ../settings/forecast/adjusted-input-cfg.json --save-to request-output.json --username %api.username% --password %api.password%"
            }
        }
        python {
            name = "Forecast step: Download tunes from Google Drive"
            workingDir = "toolset"
            environment = venv {
            }
            command = file {
                filename = "download_file_from_gdrive.py"
                scriptArguments = "--request-id %workflow.forecast.scenario% --output-path tunes.csv"
            }
        }
        script {
            name = "Forecast step: Run forecast"
            workingDir = "settings/forecast"
            scriptContent = """matlab -nodisplay -nodesktop -nosplash -r "%matlab.code.forecast%"; exit ${'$'}?"""
        }
        python {
            name = "Forecast step: Submit daily data to data warehouse"
            workingDir = "api-client"
            environment = venv {
                requirementsFile = ""
            }
            command = file {
                filename = "submit_data.py"
                scriptArguments = "--json-request ../settings/forecast/forecast-output.json --username %api.username% --password %api.password%"
            }
        }
        python {
            name = "Report step: Load settings"
            workingDir = "settings/report"
            command = file {
                filename = "create_input.py"
                scriptArguments = """--config-path %workflow.forecast.country%-cfg-template.json --output-file adjusted-input-cfg.json --params-json '{"snapshot_time":"%workflow.adhoc.snapshot-time%"}'"""
            }
        }
        python {
            name = "Report step: Request data from data warehouse"
            workingDir = "api-client"
            pythonVersion = customPython {
                executable = "/usr/bin/python3.11"
            }
            environment = venv {
            }
            command = file {
                filename = "retrieve_data.py"
                scriptArguments = "--settings ../settings/report/adjusted-input-cfg.json --save-to post-output.json --username %api.username% --password %api.password%"
            }
        }
        script {
            name = "Report step: Generate report"
            workingDir = "report"
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
                scriptArguments = "--subject '%email.subject%' --recipients '%email.recipients%' --body '%email.body%' --attachment './report/results/report-forecast.bundle.html'"
            }
        }
    }

    requirements {
        equals("system.agent.name", "Agent 2-1")
    }
})

object IntermediateForecastRunner : BuildType({
    name = "Intermediate forecast runner"

    artifactRules = """
        build-params.json
        settings/forecast/forecast-output.json
        report/results/report-forecast.bundle.html
    """.trimIndent()

    params {
        text("email.subject", "EIU PoC Forecast Report", label = "Email subject", description = "Email notification subject", allowEmpty = false)
        param("matlab.code.forecast", "runner('../../model-%workflow.forecast.country%', '%workflow.forecast.country%-input-mapping.json', '../../api-client/request-output.json', '../../toolset/tunes.csv', '%workflow.forecast.country%-output-mapping.json', 'forecast-output.json', true);")
        text("workflow.dependencies.model-cz.commit", "HEAD", label = "Model CZ commit", description = "Commit id of the Model CZ repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.iris.commit", "HEAD", label = "IRIS toolbox commit", description = "Commit id of the IRIS toolbox repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        select("workflow.forecast.scenario", "", label = "Scenario", description = "Which scenario to run", display = ParameterDisplay.PROMPT,
                options = listOf("None" to """""""", "EA scenario 2" to "1LkB7ulgEB6XLiqvTJrPeAQtoWYCLeBtx", "US scenario 1" to "1Lp6E1CIxPqSy6wZ4NZDaf2KmNnLeuhRp", "CZ scenario 1" to "1M4r9Xp1aQ_ryFdt9qIL_zPLJ6NfitsKJ", "US scenario 2" to "1Lmr-yiVvfWZQFLbj3k0uwMK7k0OIOgUS", "CZ scenario 2" to "1LvEP-31khLkjC59HM_e2tGHbRgqwxNjE", "EA scenario 1" to "1Ljf8u1ExyLVNmgTSpWnKxyB5Aqn5feRn"))
        text("workflow.dependencies.settings.commit", "HEAD", label = "Workflow Settings commit", description = "Commit id of the Workflow Settings repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("email.body", "Dear all, please find EIU PoC Forecast Report attached. Best regards, Ngoc Nam Nguyen", label = "Email message", description = "Text of the notification email", allowEmpty = false)
        text("workflow.dependencies.model-template.commit", "HEAD", label = "Model template commit", description = "Commit id of the Model template repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.report.commit", "HEAD", label = "Report commit", description = "Commit id of the report repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.model-ea.commit", "HEAD", label = "Model EA commit", description = "Commit id of the Model EA repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        param("matlab.code.report", "runner('../api-client/post-output.json', '../settings/report/%workflow.forecast.country%-input-mapping.json', true);")
        text("workflow.dependencies.model-us.commit", "HEAD", label = "Model US commit", description = "Commit id of the Model US repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.adhoc.snapshot-time", "", label = "Snapshot time", description = "Snapshot time of the series requested from the data warehouse formatted as YYYY-MM-DDThh:mm:ss.SSSZ. Current datetime is used if not specified.", display = ParameterDisplay.PROMPT,
              regex = """(^${'$'}|\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3}Z)""", validationMessage = "Should be empty or datetime formatted as YYYY-MM-DDThh:mm:ss.SSSZ")
        text("workflow.dependencies.data-warehouse-client.commit", "HEAD", label = "Data Warehouse Client commit", description = "Commit id of the Data Warehouse Client repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.model-infra.commit", "HEAD", label = "Model infrastructure commit", description = "Commit id of the Model infrastructure repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("email.recipients", "ngocnam.nguyen@ogresearch.com, jaromir.benes@ogresearch.com, sergey.plotnikov@ogresearch.com", label = "Email recipients", description = "List of notification email recipients", allowEmpty = false)
        select("workflow.forecast.country", "", label = "Country code", description = "Country to be forecasted", display = ParameterDisplay.PROMPT,
                options = listOf("CZ" to "cz", "EA" to "ea", "US" to "us"))
        text("workflow.dependencies.toolset.commit", "HEAD", label = "Toolset commit", description = "Commit id of the Toolset repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
    }

    vcs {
        root(AbsoluteId("ExampleWorkflows_ApiClient"), "+:. => api-client")
        root(AbsoluteId("ExampleWorkflows_Iris"), "+:. => iris")
        root(AbsoluteId("ExampleWorkflows_Toolset"), "+:. => toolset")
        root(AbsoluteId("ExampleWorkflows_ModelInfra"), "+:. => model-infra")
        root(DslContext.settingsRoot, "+:. => settings")
        root(AbsoluteId("ExampleWorkflows_ModelTemplate"), "+:. => model-template")
        root(AbsoluteId("ExampleWorkflows_ReportForecast"), "+:. => report")
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
                
                api_client=%workflow.dependencies.data-warehouse-client.commit%
                iris=%workflow.dependencies.iris.commit%
                settings=%workflow.dependencies.settings.commit%
                toolset=%workflow.dependencies.toolset.commit%
                model_infra=%workflow.dependencies.model-infra.commit%
                model_template=%workflow.dependencies.model-template.commit%
                model_cz=%workflow.dependencies.model-cz.commit%
                model_ea=%workflow.dependencies.model-ea.commit%
                model_us=%workflow.dependencies.model-us.commit%
                
                cd api-client
                if [ ${'$'}api_client != "HEAD" ]; then git checkout ${'$'}api_client; fi
                cd ../iris
                if [ ${'$'}iris != "HEAD" ]; then git checkout ${'$'}iris; fi
                cd ../settings
                if [ ${'$'}settings != "HEAD" ]; then git checkout ${'$'}settings; fi
                cd ../toolset
                if [ ${'$'}toolset != "HEAD" ]; then git checkout ${'$'}toolset; fi
                cd ../model-infra
                if [ ${'$'}model_infra != "HEAD" ]; then git checkout ${'$'}model_infra; fi
                cd ../model-template
                if [ ${'$'}model_template != "HEAD" ]; then git checkout ${'$'}model_template; fi
                cd ../model-cz
                if [ ${'$'}model_cz != "HEAD" ]; then git checkout ${'$'}model_cz; fi
                cd ../model-ea
                if [ ${'$'}model_ea != "HEAD" ]; then git checkout ${'$'}model_ea; fi
                cd ../model-us
                if [ ${'$'}model_us != "HEAD" ]; then git checkout ${'$'}model_us; fi
            """.trimIndent()
        }
        python {
            name = "Forecast step: Load settings"
            workingDir = "settings/forecast"
            command = file {
                filename = "create_input.py"
                scriptArguments = """--config-path %workflow.forecast.country%-cfg-template.json --output-file adjusted-input-cfg.json --params-json '{"snapshot_time":"%workflow.adhoc.snapshot-time%"}'"""
            }
        }
        python {
            name = "Forecast step: Request data from data warehouse"
            workingDir = "api-client"
            pythonVersion = customPython {
                executable = "/usr/bin/python3.11"
            }
            environment = venv {
            }
            command = file {
                filename = "retrieve_data.py"
                scriptArguments = "--settings ../settings/forecast/adjusted-input-cfg.json --save-to request-output.json --username %api.username% --password %api.password%"
            }
        }
        python {
            name = "Forecast step: Download tunes from Google Drive"
            workingDir = "toolset"
            environment = venv {
            }
            command = file {
                filename = "download_file_from_gdrive.py"
                scriptArguments = "--request-id %workflow.forecast.scenario% --output-path tunes.csv"
            }
        }
        script {
            name = "Forecast step: Run forecast"
            workingDir = "settings/forecast"
            scriptContent = """matlab -nodisplay -nodesktop -nosplash -r "%matlab.code.forecast%"; exit ${'$'}?"""
        }
        python {
            name = "Forecast step: Submit daily data to data warehouse"
            workingDir = "api-client"
            environment = venv {
                requirementsFile = ""
            }
            command = file {
                filename = "submit_data.py"
                scriptArguments = "--json-request ../settings/forecast/forecast-output.json --username %api.username% --password %api.password%"
            }
        }
        python {
            name = "Report step: Load settings"
            workingDir = "settings/report"
            command = file {
                filename = "create_input.py"
                scriptArguments = """--config-path %workflow.forecast.country%-cfg-template.json --output-file adjusted-input-cfg.json --params-json '{"snapshot_time":"%workflow.adhoc.snapshot-time%"}'"""
            }
        }
        python {
            name = "Report step: Request data from data warehouse"
            workingDir = "api-client"
            pythonVersion = customPython {
                executable = "/usr/bin/python3.11"
            }
            environment = venv {
            }
            command = file {
                filename = "retrieve_data.py"
                scriptArguments = "--settings ../settings/report/adjusted-input-cfg.json --save-to post-output.json --username %api.username% --password %api.password%"
            }
        }
        script {
            name = "Report step: Generate report"
            workingDir = "report"
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
                scriptArguments = "--subject '%email.subject%' --recipients '%email.recipients%' --body '%email.body%' --attachment './report/results/report-forecast.bundle.html'"
            }
        }
    }

    requirements {
        equals("system.agent.name", "Agent 2-1")
    }
})
