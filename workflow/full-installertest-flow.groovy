// Full installer test flow, in one file
// You must parameterize the build with: 

// @stringparameter dockerLabel (node label for docker nodes) - MUST be set, otherwise it'll try to run on master...

// **ARTIFACTS URLS - REQUIRED**
// Note: you can use an artifact archived in a Job build by an artifact:// URL
//  Ex: 'artifact://full/path/to/job/buildNr#artifact.ext'
// @stringparameter debfile URL to Debian package
// @stringparameter rpmfile URL to CentOS/RHEL RPM package
// @stringparameter susefile URL to SUSE RPM package

// Optional build parameters
// @stringparameter (optional) packagingTestBranch - branch in packaging repo to use for the workflow 
//      & the installer tests. (defaults to master)
// @stringparameter (optional) jenkinsPort - port number to use in testing jenkins (defaults 8080)
// @stringparameter (optional) artifactName - (jenkins artifactname, defaults to 'jenkins')

// Basic parameters
String packagingTestBranch = (binding.hasVariable('packagingTestBranch')) ? packagingTestBranch : 'oss-dockerized-tests'
String artifactName = (binding.hasVariable('artifactName')) ? artifactName : 'jenkins'
String jenkinsPort = (binding.hasVariable('jenkinsPort')) ? jenkinsPort : '8080'

// Set up
String scriptPath = 'packaging-docker/installtests'

// Core tests represent the basic supported linuxes, extended tests build out coverage further
def coreTests = []
def extendedTests = []
coreTests[0]=["sudo-ubuntu:14.04",  
              ["ubuntu-14.04",
               [
                       ["debian.sh", "installers/deb/*.deb"],
                       ["service-check.sh", "${artifactName} ${jenkinsPort}"]
               ]]]
coreTests[1]=["sudo-centos:6",  
              ["centos-6",
               [
                       ["centos.sh", "installers/rpm/*.rpm"],
                       ["service-check.sh", "${artifactName} ${jenkinsPort}"]
               ]]]
coreTests[2]=["sudo-opensuse:13.2",  
              ["opensuse-13.2",
               [
                       ["suse.sh", "installers/suse/*.rpm"],
                       ["service-check.sh", "${artifactName} ${jenkinsPort}"]
               ]]]
extendedTests[0]=["sudo-debian:wheezy",  
                  ["debian-wheezy",
                   [
                           ["debian.sh", "installers/deb/*.deb"],
                           ["service-check.sh", "${artifactName} ${jenkinsPort}"]
                   ]]]
extendedTests[1]=["sudo-centos:7",
                  ["centos-7",
                   [
                           ["centos.sh", "installers/rpm/*.rpm"],
                           ["service-check.sh", "${artifactName} ${jenkinsPort}"]
                   ]]]
extendedTests[2]=["sudo-ubuntu:15.10",
                  ["ubuntu-15.10",
                   [
                           ["debian.sh", "installers/deb/*.deb"],
                           ["service-check.sh", "${artifactName} ${jenkinsPort}"]
                   ]]]


node(dockerLabel) {
    stage "Load Lib"
    sh 'rm -rf workflowlib'
    dir ('workflowlib') {
        git branch: packagingTestBranch, url: 'https://github.com/jenkinsci/packaging.git'
        flow = load 'workflow/installertest.groovy'
    }
    

    stage 'Fetch Installer'
    flow.fetchInstallers(debfile, rpmfile, susefile)
    
    sh 'rm -rf packaging-docker'
    dir('packaging-docker') {
      git branch: packagingTestBranch, url: 'https://github.com/jenkinsci/packaging.git'
    }
    
    // Build the sudo dockerfiles
    stage 'Build sudo dockerfiles'
    withEnv(['HOME='+pwd()]) {
        sh 'packaging-docker/docker/build-sudo-images.sh'
    }
    
    stage 'Run Installation Tests'
    String[] stepNames = ['install', 'servicecheck']
    flow.execute_install_testset(scriptPath, coreTests, stepNames)
    flow.execute_install_testset(scriptPath, extendedTests, stepNames)
}