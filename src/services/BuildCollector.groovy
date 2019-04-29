package services

import main.project.*
import main.util.TravisHelper
import main.util.GithubHelper
import main.util.ProcessRunner
import main.exception.TravisHelperException
import main.util.FileManager

class BuildCollector {

    private final FILE_NAME = '.travis.yml'
    private final PROJECT_PATH = 'localProject'
    private TravisHelper travisHelper;
    private GithubHelper githubHelper;

    BuildCollector(String accessKey) {
        this.githubHelper = new GithubHelper(accessKey)
        this.travisHelper = new TravisHelper(accessKey)
    }

    public void collectBuild(Project project, MergeCommit mergeCommit) {
        String branchName = mergeCommit.getSHA().take(5) + '_build_branch'
        
        checkoutCommitAndCreateBranch(branchName, mergeCommit.getSHA()).waitFor()

        File travisFile = new File("${PROJECT_PATH}/.travis.yml")
        if (travisFile.delete()) {
            travisFile << getnewTravisFile(mergeCommit.getSHA())
            commitChanges("'Trigger build #${mergeCommit.getSHA()}'").waitFor()
            pushBranch(branchName).waitFor()
        }
        
        goBackToMaster().waitFor()
    }

    private Process checkoutCommitAndCreateBranch(String branchName, String commitSha) {
        return ProcessRunner
            .runProcess(PROJECT_PATH, 'git', 'checkout', '-b', branchName, commitSha)
    }

    private String getTravisFileContent() {
        Process cat = ProcessRunner.runProcess(PROJECT_PATH, 'cat', FILE_NAME)
        if (cat.waitFor() == 0) {
            return cat.getInputStream().getText()
        }
        throw TravisHelperException(".travis.yml file does not exist in this commit")
    }

    private Process goBackToMaster() {
        return ProcessRunner.runProcess(PROJECT_PATH, 'git', 'checkout', 'master')
    }

    private Process pushBranch(String branchName) {
        return ProcessRunner.runProcess(PROJECT_PATH, 'git', 'push','-u', 'origin', branchName)
    }

    private Process commitChanges(String message) {
        return ProcessRunner
        .runProcess(PROJECT_PATH, "git", "commit", "-a", "-m", "${message}")
    }

    private getNewTravisFile(String commitSha) {
        return """
sudo: required
language: java

jdk:
    - openjdk7

script:
  - mvn package

before_deploy:
    - tar -zcvf result.tar.gz -C /home/travis/build/rafaelmotaalves/jsoup/target

deploy:
  provider: releases
  api_key:
    secure: $GITHUB_TOKENw
  file: result.tar.gz
  name: ${commitSha}
  file_glob: true
  overwrite: true
  skip_cleanup: true
  on:
    all_branches: true
            """
    }

}