# CodeFixerAgent
The following repository contains an autonomous coding agent. In the initial version of this agent. This agent receives trigger from a web hook in a github repository and it runs a build and if the build fails then it contacts the Langchain4J LLM for a fix. It then applies the fix to the code and creates a PR in the repository. In a subsequent version this agent will read a bug report, reproduce the error, fix the error and then apply that fix to a PR and raise it in github. 


