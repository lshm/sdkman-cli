package sdkman.specs

import sdkman.support.SdkmanEnvSpecification

class CandidatesFileBootstrapSpec extends SdkmanEnvSpecification {

    static final LEGACY_API = "http://localhost:8080"
    static final LEGACY_CANDIDATES_ENDPOINT = "$LEGACY_API/candidates"

    static final CURRENT_API = "http://localhost:8080"
    static final CURRENT_CANDIDATES_ENDPOINT = "$CURRENT_API/candidates"

    File candidatesFile

    def setup() {
        candidatesFile = new File("${sdkmanDotDirectory}/var", "candidates")
    }

    void "should store candidates if does not exist"() {
        given: 'a working sdkman installation without candidates'
        curlStub.primeWith(LEGACY_CANDIDATES_ENDPOINT, "echo groovy,scala")
        bash = sdkmanBashEnvBuilder
                .withAvailableCandidates([])
                .withLegacyService(LEGACY_API)
                .build()

        and:
        bash.start()

        when: 'bootstrap the system'
        bash.execute("source $bootstrapScript")

        then:
        candidatesFile.exists()
    }

    void "should not query server if fresh candidates file is found"() {
        given: 'a working sdkman installation with candidates file'
        bash = sdkmanBashEnvBuilder
                .withAvailableCandidates(['gradle', 'sbt'])
                .build()

        and:
        bash.start()

        when: 'bootstrap the system'
        bash.execute("source $bootstrapScript")

        then:
        candidatesFile.exists()
        candidatesFile.text.contains("gradle,sbt")
    }

    void "should query server for candidates and refresh if older than a day"() {
        given: 'a working sdkman installation with expired candidates'
        curlStub.primeWith(LEGACY_CANDIDATES_ENDPOINT, "echo groovy,scala")
        bash = sdkmanBashEnvBuilder
                .withLegacyService(LEGACY_API)
                .withAvailableCandidates(['groovy'])
                .build()
        def twoDaysAgo = System.currentTimeMillis() - (24 * 61 * 60 * 1000)
        candidatesFile.setLastModified(twoDaysAgo)

        and:
        bash.start()

        when: 'bootstrap the system'
        bash.execute("source $bootstrapScript")

        then:
        candidatesFile.exists()
        candidatesFile.text.contains('groovy,scala')
    }

    void "should always query server for candidates and refresh if subscribed to beta channel"() {
        given: 'a working sdkman installation with expired candidates'
        curlStub.primeWith(CURRENT_CANDIDATES_ENDPOINT, "echo groovy,java,scala")
        bash = sdkmanBashEnvBuilder
                .withLegacyService(LEGACY_API)
                .withCurrentService(CURRENT_API)
                .withConfiguration("sdkman_beta_channel", "true")
                .withAvailableCandidates(['groovy'])
                .build()

        and:
        bash.start()

        when: 'bootstrap the system'
        bash.execute("source $bootstrapScript")

        then:
        candidatesFile.exists()
        candidatesFile.text.contains('groovy,java,scala')
    }

    void "should ignore candidates if api is offline"() {
        given: 'a working sdkman installation with api down'
        def candidates = ['groovy', 'scala']
        curlStub.primeWith(LEGACY_CANDIDATES_ENDPOINT, "echo ''")
        bash = sdkmanBashEnvBuilder
                .withLegacyService(LEGACY_API)
                .withAvailableCandidates(candidates)
                .build()

        and:
        bash.start()

        when: 'bootstrap the system'
        bash.execute("source $bootstrapScript")

        then:
        candidatesFile.text.contains(candidates.join(','))
    }

    void "should not go offline if curl times out"() {
        given: 'a working sdkman installation with api down'
        curlStub.primeWith(LEGACY_CANDIDATES_ENDPOINT, "echo ''")
        bash = sdkmanBashEnvBuilder
                .withLegacyService(LEGACY_API)
                .build()

        and:
        bash.start()

        when: 'bootstrap the system'
        bash.execute("source $bootstrapScript")

        then:
        !bash.output.contains("SDKMAN can't reach the internet so going offline.")
    }

    void "should ignore candidates if api returns garbage"() {
        given: 'a working sdkman installation with garbled api'
        def candidates = ['groovy', 'scala']
        curlStub.primeWith(LEGACY_CANDIDATES_ENDPOINT, "echo '<html><title>sorry</title></html>'")
        bash = sdkmanBashEnvBuilder
                .withLegacyService(LEGACY_API)
                .withAvailableCandidates(candidates)
                .build()

        and:
        bash.start()

        when: 'bootstrap the system'
        bash.execute("source $bootstrapScript")

        then:
        candidatesFile.text.contains(candidates.join(','))
    }

}
