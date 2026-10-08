// Declarative pipeline. Assumes a JDK 17 tool named 'jdk17' and a Maven tool named 'maven3'
// are configured in Jenkins (Manage Jenkins > Tools).
pipeline {
    agent any

    tools {
        jdk 'jdk17'
        maven 'maven3'
    }

    parameters {
        choice(name: 'SUITE', choices: ['regression', 'smoke'], description: 'TestNG suite to run')
    }

    options {
        timeout(time: 20, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        // For a real sandbox, bind a Jenkins credential instead of the embedded simulator:
        // PAYMENTS_BASE_URL  = 'https://sandbox.example.test'
        // PAYMENTS_API_TOKEN = credentials('payments-sandbox-token')
        MAVEN_OPTS = '-Xmx1g'
    }

    stages {
        stage('Test') {
            steps {
                sh "mvn -B verify -Dsuite=${params.SUITE}"
            }
        }
    }

    post {
        always {
            junit testResults: 'target/surefire-reports/**/*.xml', allowEmptyResults: false
            archiveArtifacts artifacts: 'target/surefire-reports/**, target/reports/**, target/logs/**',
                             allowEmptyArchive: true
        }
    }
}
