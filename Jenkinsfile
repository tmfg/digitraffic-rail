def buildPipelineScript

node {

	String branch = getCIBranch()

	checkout changelog: false, poll: false, scm: [
		$class: 'GitSCM',
		branches: [
			[name: "refs/heads/${branch}"]
		],
		doGenerateSubmoduleConfigurations: false,
		extensions: [
			[$class: 'RelativeTargetDirectory', relativeTargetDir: 'liike_ci'],
			[$class: 'CleanBeforeCheckout'],
            [$class: 'DisableRemotePoll'],
            [$class: 'PathRestriction', excludedRegions: 'jenkins/.*\\.groovy', includedRegions: '']
		],
		submoduleCfg: [],
		userRemoteConfigs: [
			[credentialsId: '${credid}', url: '${git_url}']
		]
	]

    buildPipelineScript = load 'liike_ci/jenkins/build_pipeline_avoindata.groovy'
    echo "buildPipelineScript= ${buildPipelineScript}"
}

String getCIBranch() {
	try {
		String branch = LIIKE_CI_BRANCH.toString()
		echo "Checking out pipeline script from branch: ${branch}"
		return branch
	} catch(MissingPropertyException mpe) {
		echo "No LIIKE_CI_BRANCH defined. Using default: master"
		return "master"
	}
}

echo "Running build pipeline script..."
buildPipelineScript.runPipeline()
