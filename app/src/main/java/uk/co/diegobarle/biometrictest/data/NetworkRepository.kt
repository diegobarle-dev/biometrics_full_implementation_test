package uk.co.diegobarle.biometrictest.data

object NetworkRepository {

    //Token to use for the API calls
    var fakeToken: String? = null
        private set

    //Set used just for testing purposes. We keep a set of the tokens that we have already used to
    //simulate expired tokens on the server side.
    val expiredTokens = HashSet<String>()

    fun updateToken(newFakeToken: String) {
        if (fakeToken == newFakeToken) return
        fakeToken?.let { expiredTokens.add(it) }
        fakeToken = newFakeToken
    }
}