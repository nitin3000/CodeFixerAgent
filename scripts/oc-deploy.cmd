set OC_HOME=
set PATH=%PATH%;%OC_HOME%
  
oc set build-secret bc/ai-bug-fixer-agent your-github-secret --source

oc patch bc/ai-bug-fixer-agent -p "{\"spec\":{\"source\":{\"type\":\"Git\",\"git\":{\"uri\":\"https://github.com\",\"ref\":\"main\"}}}}"

# 1. Log into your OpenShift cluster namespace
oc project your-agent-namespace

# 2. Create a binary build config for your Dockerfile asset configuration
oc new-build --binary --name=ai-bug-fixer-agent

# 3. Fire the remote server build inside OpenShift
oc start-build ai-bug-fixer-agent --from-dir=. --follow

# 4. Generate the microservice application deployment mapping
oc new-app ai-bug-fixer-agent

# 5. Expose the port to the public web internet (Replaces your local ngrok requirement)
oc expose svc/ai-bug-fixer-agent
