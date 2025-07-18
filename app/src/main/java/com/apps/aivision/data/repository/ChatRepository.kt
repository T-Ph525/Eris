package com.apps.aivision.data.repository

import android.util.Log
import com.apps.aivision.components.ApiKeyHelpers
import com.apps.aivision.data.model.AsticaVisionRequest
import com.apps.aivision.data.model.GPTRequestParam
import com.apps.aivision.data.model.ModerationRequest
import com.apps.aivision.data.model.VisionRequest
import com.apps.aivision.data.source.remote.AIVisionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject

interface ChatRepository {
    fun textCompletionsWithStream(scope: CoroutineScope, request: GPTRequestParam): Flow<String>
    fun textCompletionsWith(request: GPTRequestParam): Flow<String>
    fun textCompletionsWithVision(request: VisionRequest): Flow<String>
    fun textCompletionsWithVision(request: AsticaVisionRequest): Flow<String>

}


private const val MATCH_STRING = "\"text\":"
class ChatRepositoryImpl @Inject constructor(private val aiVisionService: AIVisionService, private val apiKeyHelpers: ApiKeyHelpers) :
    ChatRepository {
    override fun textCompletionsWithStream(
        scope: CoroutineScope,
        request: GPTRequestParam
    ): Flow<String> =
        callbackFlow {
            withContext(Dispatchers.IO) {
                runCatching {

                    val moderationResult = aiVisionService.inputModerations(ModerationRequest(request.messages.last().content),"Bearer ${apiKeyHelpers.getApiKey()}").execute()
                    if (moderationResult.isSuccessful && moderationResult.body() != null) {
                        val bodyResult = moderationResult.body()!!.results
                        if (bodyResult != null && bodyResult[0].flagged) {
                            trySend("Failure! Your request is flagged as inappropriate and Its against our privacy policy. Please try again with some different context.")
                            close()
                        } else {
                            val auth = "Bearer ${apiKeyHelpers.getApiKey()}"

                            val response = aiVisionService.textCompletionsWithStream(
                                request,auth).execute()
                            if (response.isSuccessful) {
                                val inputStream =
                                    response.body()?.byteStream()?.bufferedReader()
                                        ?: throw Exception()
                                try {
                                    var ts = System.currentTimeMillis()
                                    while (true) {
                                        val line = withContext(Dispatchers.IO) {
                                            inputStream.readLine()
                                        } ?: continue
                                        if (line == "data: [DONE]") {
                                            close()
                                        } else if (line.startsWith("data:")) {
                                            try {
                                                val value = parseResponse(
                                                    line
                                                )

                                                val currentTS = System.currentTimeMillis()
                                                if (value.isNotEmpty()) {
                                                    trySend(value)
                                                    if ((currentTS - ts) < 30) {
                                                        delay(30 - (currentTS - ts))
                                                    }
                                                    ts = currentTS
                                                }
                                            } catch (e: Exception) {

                                                e.printStackTrace()
                                            }
                                        }
                                        if (!scope.isActive) {
                                            break
                                        }
                                    }
                                } catch (e: IOException) {
                                    throw Exception(e)
                                } finally {
                                    withContext(Dispatchers.IO) {
                                        inputStream.close()
                                    }

                                    close()
                                }
                            } else {
                                if (!response.isSuccessful) {
                                    var jsonObject: JSONObject? = null
                                    try {
                                        jsonObject = JSONObject(response.errorBody()!!.string())
                                        println(jsonObject)
                                    } catch (e: JSONException) {
                                        e.printStackTrace()
                                    }
                                }
                                trySend("Failure!:Try again later.")
                                close()
                            }
                        }
                    } else {
                        trySend("Failure!:Try again later.")
                        close()
                    }

                }.onFailure {
                    it.printStackTrace()
                    trySend("Network Failure! Try again.")
                    close()
                }
            }

            close()
        }.flowOn(Dispatchers.IO)

    private fun parseResponse(jsonString: String): String {
        try {
            val jsonObject = JSONObject(jsonString.replace("data: ", ""))

            val choicesArray = jsonObject.optJSONArray("choices")
            if (choicesArray != null && choicesArray.length() > 0) {
                val choiceObject = choicesArray.optJSONObject(0)
                val deltaObject = choiceObject?.optJSONObject("delta")
                val contentElement = deltaObject?.optString("content")
                if (!contentElement.isNullOrEmpty()) {
                    return contentElement
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return ""
    }

    override fun textCompletionsWith(
        request: GPTRequestParam
    ): Flow<String> = flow {

        runCatching {
            aiVisionService.askAIAssistant(request,"Bearer ${apiKeyHelpers.getApiKey()}")
        }.onSuccess {
            Log.e("REsponse","$it")
            it.choices?.let { choice ->
                Log.e("Choice","$choice")
                choice[0].message.content.let { txt ->
                    emit(txt)
                }
            }

        }.onFailure { it.printStackTrace()
            emit("Failure! Try again later.")

        }


    }

    override fun textCompletionsWithVision(request: VisionRequest): Flow<String> = flow{
        runCatching {
            aiVisionService.askAIVision(request,"Bearer ${apiKeyHelpers.getApiKey()}")
        }.onSuccess {
            Log.e("REsponse","$it")
            it.choices?.let { choice ->
                Log.e("Choice","$choice")
                choice[0].message.content.let { txt ->
                    emit(txt)
                }
            }

        }.onFailure { it.printStackTrace()
            emit("Failure! Try again later.")
        }

    }

    override fun textCompletionsWithVision(request: AsticaVisionRequest): Flow<String> = flow {
        runCatching {
            Log.e("Request","${request.visionParams}")
            aiVisionService.askAsticaVisionAI(request)
        }.onSuccess {
            Log.e("REsponse","$it")
            it.let { result ->
                Log.e("Choice","$result")
                if (result.status.contentEquals("success"))
                {
                    if (!result.captionGPTS.isNullOrEmpty())
                    {
                        emit(result.captionGPTS)
                    }
                    else if (result.caption!=null)
                    {
                        emit(result.caption.text)
                    }else if (!result.tags.isNullOrEmpty())
                    {
                        var tags =""
                        result.tags.forEach {
                            tags += if (tags.isEmpty())
                                "#${it.name}"
                            else
                                ", #${it.name}"
                        }
                        emit(tags)
                    }else if (!result.objects.isNullOrEmpty())
                    {
                        var tags =""
                        result.objects.forEach {
                            tags += if (tags.isEmpty())
                                "${it.name}"
                            else
                                ", ${it.name}"
                        }
                        emit(tags)
                    }else if (result.asticaOCR!=null){
                        emit(result.asticaOCR.content)
                    }
                    else{
                        emit("Failure! can't analysed the image")
                    }
                }else{
                    emit("Failure! can't analysed the image.")
                }
            }

        }.onFailure { it.printStackTrace()
            emit("Failure! Try again later.")
        }

    }

}