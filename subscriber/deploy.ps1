Set-Location -Path ${PSScriptRoot}
mvn clean package
gcloud beta functions deploy subscriber `
  --entry-point=com.syakeapps.tsn.subscriber.application.Function `
  --trigger-topic=pubsub `
  --runtime=java11 `
  --memory=128MB `
  --min-instances=0 `
  --max-instances=1 `
  --env-vars-file=env.yml `
  --source=target/deployment