# Workflow executer for model forecasts

Repository contains model forecast workflow files, currently there are three steps withing this workflow:
- forecast step
- report step
- analyst step

## How does it work?

Each step requires config files and mapping files.

Input config files are used to request data from the DWH. Mapping files are used to connect requested data and the series in MATLAB for forecasting.

Report step is used to chart some basic graphs about a selected country. It requests their filtered data from the DWH and uses their mapping files to connect the protocol series and MATLAB series. 

Function create_input is used to assign the current snapshot time (in addition to geography) to define the current data in the DWH.

## Link to the TC project

Pre-model workflows can be found here: https://tc-eiu.ogresearch.com/project/ExampleWorkflows_ModelForecasts?mode=builds 
