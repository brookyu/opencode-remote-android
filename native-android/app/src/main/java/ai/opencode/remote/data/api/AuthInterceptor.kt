package ai.opencode.remote.data.api

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {
    @Volatile
    var username: String = ""

    @Volatile
    var password: String = ""

    fun setCredentials(user: String, pass: String) {
        username = user
        password = pass
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
            .header("Accept", "application/json")

        if (username.isNotEmpty()) {
            builder.header("Authorization", Credentials.basic(username, password))
        }

        if (request.method == "POST" || request.method == "PATCH") {
            val body = request.body
            if (body != null && request.header("Content-Type") == null) {
                builder.header("Content-Type", "application/json")
            }
        }

        return chain.proceed(builder.build())
    }
}
