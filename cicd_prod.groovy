// REGISTRY 
REGISTRY_URL = "registry.datnxdevops.site"
REGISTRY_USER = "admin"
REGISTRY_PASSWORD = "Harbor12345"
REGISTRY_PROJECT = "fullstack"

// APP
appName="fullstack_Demo"
appUser="fullstack"
appType_Frontend = "frontend"
appType_Backend = "backend"
DOCKER_IMAGE_FRONTEND = "${REGISTRY_URL}/${REGISTRY_PROJECT}/${appType_Frontend}:production"
DOCKER_IMAGE_BACKEND = "${REGISTRY_URL}/${REGISTRY_PROJECT}/${appType_Backend}:production"
    
// PROJECT FOLDER
folderDeploy = "/jenkins-deploy/${appUser}"

// DEPLOY PROCESS
loginScript = "docker login -u ${REGISTRY_USER} -p ${REGISTRY_PASSWORD} ${REGISTRY_URL}"
permissionScript = "sudo chown -R ${appUser}. ${folderDeploy}"
stopScript = "docker compose down"

  //REMOVE OLD IMAGE
removeOldImageScript_Frontend = "docker image rm ${REGISTRY_URL}/${REGISTRY_PROJECT}/${appType_Frontend}:latest"
removeOldImageScript_Backend = "docker image rm ${REGISTRY_URL}/${REGISTRY_PROJECT}/${appType_Backend}:latest"

  //PULL IMAGE
pullImageScript_Frontend = "docker pull ${DOCKER_IMAGE_FRONTEND}"
pullImageScript_Backend = "docker pull ${DOCKER_IMAGE_BACKEND}"


runScript = "docker compose up -d"
 //DEPLOY
deployScript = """
                whoami &&
                sudo su ${appUser} -c '
                ${loginScript} &&
                whoami &&
                cd ${folderDeploy} &&
                ${permissionScript} &&
                ${stopScript} &&
                ${pullImageScript_Frontend} &&
                ${pullImageScript_Backend} &&
                ${runScript} '
             """

// CONTAINER
containerApp_Frontend = "datnxdevops.frontend"
containerApp_Backend = "datnxdevops.backend"
containerDB = "db"

// START
def startProcess(){
    stage('start'){
         sh(script: """ ${deployScript} """, label: "deploy on staging server")
          sleep 5
          sh(script: """ docker ps """, label: "check status")
    }
    echo("Fullstack Demo with server " + params.server + " started")
}

// STOP
def stopProcess(){
    stage('stop'){
        // Lấy danh sách tất cả các container đang chạy
        def runningContainers = sh(script: 'docker ps --format "{{.Names}}"', returnStdout: true).trim().split('\n')

        // Kiểm tra xem containerApp và containerDB có trong danh sách không
        def isContainerAppFrontendRunning = containerApp_Frontend in runningContainers
        def isContainerAppBackendRunning = containerApp_Backend in runningContainers
        def isContainerDBRunning = containerDB in runningContainers

        if (isContainerAppFrontendRunning || isContainerAppBackendRunning || isContainerDBRunning ) {
            // Nếu có ít nhất một trong 3 container đang chạy, thực hiện lệnh stop và remove
            sh(script: """ docker stop ${containerApp_Frontend} ${containerApp_Backend} ${containerDB} """, label: "stop containers")
            sh(script: """ docker rm ${containerApp_Frontend} ${containerApp_Backend} ${containerDB} """, label: "remove containers")
            echo("Containers stopped and removed successfully.")
        } else {
            // Nếu cả 3 container đều không chạy
            echo("There's no container to stop.")
        }
    }
     echo("Fullstack Demo with server " + params.server + " stopped")
}

// CHOICES
node(params.server){
    currentBuild.displayName = params.action
    if (params.action == "start") {
        startProcess()
    }
    if (params.action == "stop") {
        stopProcess()
    }
    if (params.action == "restart") {
        stopProcess()
        startProcess()
    }
}