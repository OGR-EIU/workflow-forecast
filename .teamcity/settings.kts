import jetbrains.buildServer.configs.kotlin.*
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

    buildType(ForecastRunner)
}

object ForecastRunner : BuildType({
    name = "Forecast runner"

    artifactRules = """
        build-params.json
        settings/forecast/forecast-output.json
        report/results/report-forecast.bundle.html
    """.trimIndent()

    params {
        text("email.subject", "EIU PoC Forecast Report", label = "Email subject", description = "Email notification subject", allowEmpty = false)
        select("workflow.forecast.request-id", "", label = "Request id", description = "Google Drive file id", display = ParameterDisplay.PROMPT,
                options = listOf("CZ scenario 1" to "1M4r9Xp1aQ_ryFdt9qIL_zPLJ6NfitsKJ", "US scenario 2" to "1Lmr-yiVvfWZQFLbj3k0uwMK7k0OIOgUS", "CZ scenario 2" to "1LvEP-31khLkjC59HM_e2tGHbRgqwxNjE", "EA scenario 1" to "1Ljf8u1ExyLVNmgTSpWnKxyB5Aqn5feRn", "EA scenario 2" to "1LkB7ulgEB6XLiqvTJrPeAQtoWYCLeBtx", "US scenario 1" to "1Lp6E1CIxPqSy6wZ4NZDaf2KmNnLeuhRp"))
        param("matlab.code.forecast", "runner(['../../model-' lower('%workflow.forecast.country%')], [lower('%workflow.forecast.country%') '-input-mapping.json'], '../../api-client/request-output.json', [lower('%workflow.forecast.country%') '-output-mapping.json'], 'forecast-output.json', true);")
        text("workflow.dependencies.iris.commit", "HEAD", label = "IRIS toolbox commit", description = "Commit id of the IRIS toolbox repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.settings.commit", "HEAD", label = "Workflow Settings commit", description = "Commit id of the Workflow Settings repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("email.body", "Dear all, please find EIU PoC Forecast Report attached. Best regards, Ngoc Nam Nguyen", label = "Email message", description = "Text of the notification email", allowEmpty = false)
        text("workflow.dependencies.model-template.commit", "HEAD", label = "Model template commit", description = "Commit id of the Model template repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.report.commit", "HEAD", label = "Report commit", description = "Commit id of the report repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        param("matlab.code.report", "runner('../api-client/post-output.json', ['../settings/report/' lower('%workflow.forecast.country%') '-input-mapping.json'], true);")
        text("workflow.adhoc.snapshot-time", "", label = "Snapshot time", description = "Snapshot time of the series requested from the data warehouse formatted as YYYY-MM-DDThh:mm:ss.SSSZ. Current datetime is used if not specified.", display = ParameterDisplay.PROMPT,
              regex = """(^${'$'}|\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3}Z)""", validationMessage = "Should be empty or datetime formatted as YYYY-MM-DDThh:mm:ss.SSSZ")
        text("workflow.dependencies.data-warehouse-client.commit", "HEAD", label = "Data Warehouse Client commit", description = "Commit id of the Data Warehouse Client repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("workflow.dependencies.model-infra.commit", "HEAD", label = "Model infrastructure commit", description = "Commit id of the Model infrastructure repo", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("email.recipients", "ngocnam.nguyen@ogresearch.com, jaromir.benes@ogresearch.com, sergey.plotnikov@ogresearch.com", label = "Email recipients", description = "List of notification email recipients", allowEmpty = false)
        select("workflow.forecast.country", "", label = "Country code", description = "Country to be forecasted", display = ParameterDisplay.PROMPT,
                options = listOf("EA"))
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
            """.trimIndent()
        }
        python {
            name = "Forecast step: Load settings"
            workingDir = "settings/forecast"
            command = file {
                filename = "create_input.py"
                scriptArguments = """--config-path input-cfg-template.json --output-file adjusted-input-cfg.json --params-json '{"snapshot_time":"%workflow.adhoc.snapshot-time%","geography":"%workflow.forecast.country%"}'"""
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
                scriptArguments = """--config-path input-cfg-template.json --output-file adjusted-input-cfg.json --params-json '{"snapshot_time":"%workflow.adhoc.snapshot-time%","geography":"%workflow.forecast.country%"}'"""
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
