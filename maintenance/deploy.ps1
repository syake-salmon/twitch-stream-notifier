mvn clean package
gcloud beta functions deploy maintenance `
  --entry-point=com.syakeapps.tsn.maintenance.application.Function `
  --trigger-http `
  --runtime=java11 `
  --env-vars-file=env.yml `
  --source=target/deployment