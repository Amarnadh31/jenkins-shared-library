pipeline{
    agent{
        label 'AGENT-1'
    }
    options{
        disableConcurrentBuilds()

    }
    environment{
        appVersion = ''
        region = 'us-east-1'
        account_id = '767397679511'
        project = configMap.get("project")
        environment = 'dev'
        component = configMap.get("component")
    }
    stages{
        stage('version check') {
            steps{
                script{
                    def packageJson = readJSON file: 'backend/package.json'
                    appVersion = packageJson.version
                    echo "App version: ${appVersion}"

                }
            }
        }
        // stage('Code Analysis') {
        //     environment {
        //         scannerHome = tool 'sonar-6.0'
        //         SONAR_SCANNER_OPTS = '--add-opens java.base/java.lang=ALL-UNNAMED'
        //     }
        //     steps {
        //         script {
        //             withSonarQubeEnv('sonar-6.0') {
        //                 sh '''${scannerHome}/bin/sonar-scanner \
        //                     -Dsonar.projectKey=backend \
        //                     -Dsonar.projectName=backend \
        //                     -Dsonar.exclusions=helm/,Dockerfile,Jenkinsfile,schema,node_modules \
        //                     -Dsonar.sources=.
        //                 '''
        //             }
        //         }
        //     }
        // }
        stage('Deocker build'){
            steps{
                withAWS(region: 'us-east-1', credentials: 'aws-creds'){
                    sh """
                        cd backend
                        aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${account_id}.dkr.ecr.us-east-1.amazonaws.com
                        docker build -t ${account_id}.dkr.ecr.us-east-1.amazonaws.com/${project}/${environment}/${component}:${appVersion} .
                        docker push ${account_id}.dkr.ecr.us-east-1.amazonaws.com/${project}/${environment}/${component}:${appVersion}
                    """
                }
            }
        }
        stage('Deploy'){
            steps{
                withAWS(region: 'us-east-1', credentials: 'aws-creds'){
                    sh """
                        aws eks update-kubeconfig --region ${region} --name ${project}
                        cd backend/helm
                        sed -i 's/IMAGE_VERSION/${appVersion}/g' values.yaml
                        helm upgrade --install ${component} -n ${project} -f values.yaml .
                    """
                }
            }
        }
    }
    post{
        always{
            echo 'This section always run'
            deleteDir()
        }
        failure{
            echo " Build failed"
        }
        success{
            echo "Build run successfully"
        }
    }
}