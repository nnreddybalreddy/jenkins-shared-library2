def call(Map configMap){
    pipeline {
        agent {
            label 'AGENT-1'
        }
        options{
            timeout(time: 30, unit: 'MINUTES')
            disableConcurrentBuilds()
            //retry(1)
        }
        parameters{
            booleanParam(name: 'deploy', defaultValue: false, description: 'Select to deploy or not')
        }
        environment {
            appVersion = '' // this will become global, we can use across pipeline
            region = 'us-east-1'
            account_id = '905418111046'
            project = configMap.get("project")
            environment = 'dev'
            component = configMap.get("component")
        }

        stages {
            stage('Read the version') {
                steps {
                    script{
                        def packageJson = readJSON file: 'package.json'
                        appVersion = packageJson.version
                        echo "App version: ${appVersion}"
                    }
                }
            }
            stage('Install Dependencies') {
                steps {
                    sh 'npm install'
                }
            }
            /* stage('SonarQube analysis') {
                environment {
                    SCANNER_HOME = tool 'sonar-6.0' //scanner config
                }
            } */

            stage('Docker build'){
                steps{

                    sh """
                        aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${account_id}.dkr.ecr.${region}.amazonaws.com
                        docker build -t ${account_id}.dkr.ecr.${region}.amazonaws.com/${project}-${component}:${appVersion} .
                        docker push ${account_id}.dkr.ecr.${region}.amazonaws.com/${project}-${component}:${appVersion}
                    """
                }
            }
        stage('Deploy'){
            steps{
                sh """
                    aws eks update-kubeconfig --region us-east-1 --name expense-dev
                    cd helm
                    sed -i 's/IMAGE_VERSION/${appVersion}/g' values.yaml
                    helm upgrade --install  ${component} -n expense . 
                    
                    
                """
            }
        }
        }

        post {
            always{
                echo "This sections runs always"
                deleteDir()
            }
            success{
                echo "This section run when pipeline success"
            }
            failure{
                echo "This section run when pipeline failure"
            }
        }
    }
}