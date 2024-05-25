// Phần này config ở Active Choices Reactive Parameter: rollback_version
import jenkins.model.*
import hudson.FilePath

def backupPath
if (project == 'frontend') {
    backupPath = "/jenkins-deploy/fullstack/backups/frontend/"
} else if (project == 'backend') {
    backupPath = "/jenkins-deploy/fullstack/backups/backend/"
} else {
    return "Invalid project"
}

def node = Jenkins.getInstance().getNode(server)
def remoteDir = new FilePath(node.getChannel(), backupPath)

if (action == "rollback") {
   def files = remoteDir.list()
   def nameFile = files.collect { it.name }
   return nameFile
}



// Script cicd //

// REGISTRY
REGISTRY_URL = 'registry.datnxdevops.site'
REGISTRY_USER = 'admin'
REGISTRY_PASSWORD = 'Harbor12345'
REGISTRY_PROJECT = 'fullstack'

// APP
appName = 'fullstack_Demo'
appUser = 'fullstack'
appType_Frontend = 'frontend'
appType_Backend = 'backend'
DOCKER_IMAGE_FRONTEND = "${REGISTRY_URL}/${REGISTRY_PROJECT}/${appType_Frontend}"
DOCKER_IMAGE_BACKEND = "${REGISTRY_URL}/${REGISTRY_PROJECT}/${appType_Backend}"

// BUILD PROCESS_STAGING
loginScript = "docker login -u ${REGISTRY_USER} -p ${REGISTRY_PASSWORD} ${REGISTRY_URL}"
buildFrontEnd_Staging = "docker build -t ${DOCKER_IMAGE_FRONTEND}:staging --build-arg BUILD_ENV=staging ."
buildBackEnd_Staging = "docker build -t ${DOCKER_IMAGE_BACKEND}:staging ."
pushToRegistryFrontend_Staging = "docker push ${DOCKER_IMAGE_FRONTEND}:staging"
pushToRegistryBackend_Staging = "docker push ${DOCKER_IMAGE_BACKEND}:staging"
removeOldImage_Staging = "docker image rm -f ${DOCKER_IMAGE_FRONTEND}:staging ${DOCKER_IMAGE_BACKEND}:staging"

// PROJECT FOLDER
folderDeploy = "/jenkins-deploy/${appUser}/run" //folder chạy
folderBackup_FrontEnd = "/jenkins-deploy/${appUser}/backups/frontend" //folder backup fe để rollback
folderBackup_BackEnd = "/jenkins-deploy/${appUser}/backups/backend" //folder backup be để rollback
folderMain = "/jenkins-deploy/${appUser}" //folder origin

pathTo_Frontend = '/home/fullstack/source-code/frontend'
pathTo_Backend = '/home/fullstack/source-code/backend'

// DEPLOY PROCESS
permissionScript = "sudo chown -R ${appUser}. ${folderDeploy}"
stopScript = 'docker compose down'
pullImageScript_Frontend = "docker pull ${DOCKER_IMAGE_FRONTEND}:staging"
pullImageScript_Backend = "docker pull ${DOCKER_IMAGE_BACKEND}:staging"
runScript = 'docker compose up -d'
deployScript = """
                sudo su ${appUser} -c '
                ${loginScript} &&
                cd ${folderDeploy} &&
                ${permissionScript} &&
                ${stopScript} &&
                ${removeOldImage_Staging} &&
                ${pullImageScript_Frontend} &&
                ${pullImageScript_Backend} &&
                ${runScript} '
             """

deployAfterRollBackScript = """
                sudo su ${appUser} -c '
                ${loginScript} &&
                cd ${folderDeploy} &&
                ${permissionScript} &&
                ${stopScript} &&
                ${runScript} '
             """

// CONTAINER
containerApp_Frontend = 'datnxdevops.frontend'
containerApp_Backend = 'datnxdevops.backend'
containerDB = 'db'

// REPO LINK
gitLink_FrontEnd = 'http://gitlab.datnxdevops.tech/demo-fullstack-net6-vue3/fullstack-frontend.git' //link dự án
gitLink_BackEnd = 'http://gitlab.datnxdevops.tech/demo-fullstack-net6-vue3/fullstack-backend.git'

// START
def startProcess() {
    stage('start') {
        sh(script: """ ${deployScript} """, label: 'deploy on staging server')
        sleep 5
        sh(script: """ docker ps """, label: 'check status')
    }
    echo('Fullstack Demo with server ' + params.server + ' started')
}

// START AFTER ROLLBACK
def startAfterRollBackProcess() {
    stage('startAfterRollBack') {
        sh(script: """ ${deployAfterRollBackScript} """, label: 'deploy on staging server after rollback')
        sleep 5
        sh(script: """ docker ps """, label: 'check status')
    }
    echo("${appName} with server " + params.server + ' started')
}

// STOP
def stopProcess() {
    stage('stop') {
        // Lấy danh sách tất cả các container đang chạy
        def runningContainers = sh(script: 'docker ps --format "{{.Names}}"', returnStdout: true).trim().split('\n')

        // Kiểm tra xem containerApp và containerDB có trong danh sách không
        def isContainerAppFrontendRunning = containerApp_Frontend in runningContainers
        def isContainerAppBackendRunning = containerApp_Backend in runningContainers
        def isContainerDBRunning = containerDB in runningContainers

        if (isContainerAppFrontendRunning || isContainerAppBackendRunning || isContainerDBRunning) {
            // Nếu có ít nhất một trong 3 container đang chạy, thực hiện lệnh stop và remove
            sh(script: """ docker stop ${containerApp_Frontend} ${containerApp_Backend} ${containerDB} """, label: 'stop containers')
            sh(script: """ docker rm ${containerApp_Frontend} ${containerApp_Backend} ${containerDB} """, label: 'remove containers')
            echo('Containers stopped and removed successfully.')
        } else {
            // Nếu cả 3 container đều không chạy
            echo("There's no container to stop.")
        }
    }
    echo('Fullstack Demo with server ' + params.server + ' stopped')
}

//BACKUP
def backupProcess() {
    if (params.project == 'frontend') {
      stage('backup') {
        // appName_time ngày_time giờ_mã hash cần back up.tar
        def timeStamp = new Date().format('ddMMyyyy_HHmm')
        sh(script: """ sudo su ${appUser} -c "cd ${folderMain}; docker save -o ${folderBackup_FrontEnd}/${appType_Frontend}_${timeStamp}.tar ${DOCKER_IMAGE_FRONTEND}:staging " """, label: 'backup old fe version')
      }
    }
     if (params.project == 'backend') {
      stage('backup') {
        // appName_time ngày_time giờ_mã hash cần back up.tar
        def timeStamp = new Date().format('ddMMyyyy_HHmm')
        sh(script: """ sudo su ${appUser} -c "cd ${folderMain}; docker save -o ${folderBackup_BackEnd}/${appType_Backend}_${timeStamp}.tar ${DOCKER_IMAGE_BACKEND}:staging " """, label: 'backup old be version')
     }
    }
   
}

 
//UPCODE
def upcodeProcess() {
    if (params.project == 'frontend') {
        stage('checkout staging branch') {
            // Xóa toàn bộ nội dung trong thư mục frontend
            sh(script: 'rm -rf /home/fullstack/source-code/frontend/*')

            // Clone mã nguồn vào thư mục làm việc của Jenkins
            checkout([$class: 'GitSCM', branches: [[name: params.hash ]],
                      userRemoteConfigs: [[credentialsId: 'jenkins-gitlab-user-account', url: gitLink_FrontEnd]]])
           
             // Thiết lập quyền truy cập cho các tệp bắt đầu bằng .env
            sh(script: 'chmod +x .env*')

            // Di chuyển mã nguồn vào thư mục frontend
            sh(script: """ mv * ${pathTo_Frontend} """)
        }
        stage('build for staging') {
            sh(script: """ ${loginScript} """, label: 'login to registry')
            sh(script: """ whoami """, label: 'check user')
            sh(script: """ cd ${pathTo_Frontend}; ls ; ${buildFrontEnd_Staging}  """, label: 'build frontend with Dockerfile')
            sh(script: """ ${pushToRegistryFrontend_Staging} """, label: 'push new fe image to registry')
            sh(script: """ ${removeOldImage_Staging} """, label: 'remove old images')
        }
    }
    if (params.project == 'backend') {
        stage('checkout staging branch') {
            // Xóa toàn bộ nội dung trong thư mục backend
            sh(script: 'rm -rf /home/fullstack/source-code/backend/*')

            // Clone mã nguồn vào thư mục làm việc của Jenkins
            checkout([$class: 'GitSCM', branches: [[name: params.hash ]],
                      userRemoteConfigs: [[credentialsId: 'jenkins-gitlab-user-account', url: gitLink_BackEnd]]])

            // Di chuyển mã nguồn vào thư mục backend
            sh(script: """ mv * ${pathTo_Backend}""")
        }
        stage('build for staging') {
            sh(script: """ ${loginScript} """, label: 'login to registry')
            sh(script: """ whoami """, label: 'check user')
            sh(script: """ cd ${pathTo_Backend}; ls ; ${buildBackEnd_Staging} """, label: 'build backend with Dockerfile')    
            sh(script: """ ${pushToRegistryBackend_Staging} """, label: 'push new be image to registry')
            sh(script: """ ${removeOldImage_Staging} """, label: 'remove old images')
        }
    }
}

// ROLLBACK
def rollbackProcess() {
      if (params.project == 'frontend') {
        stage('rollback') {
        sh(script: """ sudo su ${appUser} -c "cd ${folderDeploy}; docker image rm ${DOCKER_IMAGE_FRONTEND}:staging" """, label: 'delete the current image')
        sh(script: """ sudo su ${appUser} -c "cd ${folderBackup_FrontEnd}; docker load < ${params.rollback_version} " """, label: 'rollback process')
      }
    }
      if (params.project == 'backend') {
        stage('rollback') {
        sh(script: """ sudo su ${appUser} -c "cd ${folderDeploy}; docker image rm ${DOCKER_IMAGE_FRONTEND}:staging" """, label: 'delete the current image')
        sh(script: """ sudo su ${appUser} -c "cd ${folderBackup_BackEnd}; docker load < ${params.rollback_version} " """, label: 'rollback process')
      }
    }
}


// CHOICES
node(params.server) {
    currentBuild.displayName = params.action
    if (params.action == 'start') {
        startProcess()
    }
    if (params.action == 'stop') {
        stopProcess()
    }
    if (params.action == 'upcode') {
        // Clone source code - > build -> deploy
        currentBuild.description = 'server ' + params.server + ' with hash ' + params.hash
       backupProcess()
        stopProcess()
        upcodeProcess()
        startProcess()
    }
    if (params.action == 'rollback') {
        stopProcess()
        rollbackProcess()
        startAfterRollBackProcess()
    }
}
