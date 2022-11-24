package org.drinkless.tdlib

import kotlinx.coroutines.*
import mu.KotlinLogging
import org.scijava.nativelib.NativeLoader
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class Client private constructor(
    updateHandler: ResultHandler?,
    updateExceptionHandler: ExceptionHandler?,
    defaultExceptionHandler: ExceptionHandler?) {

    fun interface ResultHandler {
        fun onResult(tdApiObj: TdApi.Object)
    }

    fun interface ExceptionHandler {
        fun onException(e: Throwable)
    }

    fun interface LogMessageHandler {
        fun onLogMessage(verbosityLevel: Int, message: String)
    }

    fun send(query: TdApi.Function<*>, resultHandler: ResultHandler? = null) {
        val queryId = currentQueryId.incrementAndGet()
        if (resultHandler != null) {
            handlers[queryId] = Handler(resultHandler, null)
        }
        nativeClientSend(nativeClientId, queryId, query)
    }

    private class ResponseReceiver {
        var isRun = false
        fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
            while (true) {
                val n = withContext(Dispatchers.IO) { nativeClientReceive(clientIds, eventIds, events, 100000.0 /*seconds*/) }
                for (i in 0 until n) {
                    val event = events[i]
                    if(event != null) {
                        processResult(clientIds[i], eventIds[i], event)
                        events[i] = null
                    }
                }
            }
        }

        private fun processResult(clientId: Int, id: Long, tdApiObj: TdApi.Object) {
            var isClosed = false
            if (id == 0L && tdApiObj is TdApi.UpdateAuthorizationState) {
                val authorizationState = tdApiObj.authorizationState
                if (authorizationState is TdApi.AuthorizationStateClosed) {
                    isClosed = true
                }
            }
            val handler = if (id == 0L) updateHandlers[clientId] else handlers.remove(id)
            if (handler != null) {
                try {
                    handler.resultHandler.onResult(tdApiObj)
                } catch (cause: Throwable) {
                    var exceptionHandler = handler.exceptionHandler
                    if (exceptionHandler == null) {
                        exceptionHandler = defaultExceptionHandlers[clientId]
                    }
                    if (exceptionHandler != null) {
                        try {
                            exceptionHandler.onException(cause)
                        } catch (ignored: Throwable) {
                        }
                    }
                }
            }
            if (isClosed) {
                updateHandlers.remove(clientId)
                defaultExceptionHandlers.remove(clientId)
                clientCount.decrementAndGet()
            }
        }

        private val clientIds = IntArray(MAX_EVENTS)
        private val eventIds = LongArray(MAX_EVENTS)
        private val events =  arrayOfNulls<TdApi.Object>(MAX_EVENTS)

        companion object {
            private const val MAX_EVENTS = 1000
        }
    }

    private val nativeClientId: Int

    private class Handler(
        val resultHandler: ResultHandler,
        val exceptionHandler: ExceptionHandler?
    )

    init {
        clientCount.incrementAndGet()
        nativeClientId = createNativeClient()
        if (updateHandler != null) {
            updateHandlers[nativeClientId] = Handler(updateHandler, updateExceptionHandler)
        }
        if (defaultExceptionHandler != null) {
            defaultExceptionHandlers[nativeClientId] = defaultExceptionHandler
        }
        send(TdApi.GetOption("version"), null)
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        send(TdApi.Close(), null)
    }

    companion object {
        init {
            try {
                NativeLoader.loadLibrary("tdjni")
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

        fun execute(query: TdApi.Function<*>): TdApi.Object {
            return nativeClientExecute(query)
        }

        fun create(coroutineScope: CoroutineScope,
            updateHandler: ResultHandler?,
            updateExceptionHandler: ExceptionHandler?,
            defaultExceptionHandler: ExceptionHandler?): Client {

            val client = Client(updateHandler, updateExceptionHandler, defaultExceptionHandler)
            if (!responseReceiver.isRun) {
                responseReceiver.isRun = true
                responseReceiver.start(coroutineScope)
            }
            return client
        }

        fun setLogMessageHandler(maxVerbosityLevel: Int, logMessageHandler: LogMessageHandler) {
            nativeClientSetLogMessageHandler(maxVerbosityLevel, logMessageHandler)
        }

        private val defaultExceptionHandlers = ConcurrentHashMap<Int, ExceptionHandler>()
        private val updateHandlers = ConcurrentHashMap<Int, Handler>()
        private val handlers = ConcurrentHashMap<Long, Handler>()
        private val currentQueryId = AtomicLong()
        private val clientCount = AtomicLong()
        private val responseReceiver = ResponseReceiver()

        @JvmStatic
        private external fun createNativeClient(): Int

        @JvmStatic
        private external fun nativeClientSend(nativeClientId: Int, eventId: Long, function: TdApi.Function<*>)

        @JvmStatic
        private external fun nativeClientReceive(
            clientIds: IntArray,
            eventIds: LongArray,
            events: Array<TdApi.Object?>,
            timeout: Double
        ): Int

        @JvmStatic
        private external fun nativeClientExecute(function: TdApi.Function<*>): TdApi.Object

        @JvmStatic
        private external fun nativeClientSetLogMessageHandler(
            maxVerbosityLevel: Int,
            logMessageHandler: LogMessageHandler
        )
    }
}