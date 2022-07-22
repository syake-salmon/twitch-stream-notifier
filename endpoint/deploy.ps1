Set-Location -Path ${PSScriptRoot}
mvn clean package
gcloud beta functions deploy endpoint `
  --entry-point=com.syakeapps.tsn.endpoint.application.Function `
  --trigger-http `
  --runtime=java11 `
  --memory=128MB `
  --min-instances=0 `
  --max-instances=1 `
  --env-vars-file=env.yml `
  --source=target/deployment `
  --allow-unauthenticated