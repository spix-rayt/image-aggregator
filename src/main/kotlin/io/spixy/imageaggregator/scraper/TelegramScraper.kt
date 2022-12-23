package io.spixy.imageaggregator.scraper

import io.spixy.imageaggregator.Config
import io.spixy.imageaggregator.RunnableRandomQueue
import io.spixy.imageaggregator.md5
import io.spixy.imageaggregator.paintGreen
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.IOError
import java.io.IOException
import java.lang.Runnable
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

class TelegramScraper(private val config: Config.Telegram) : Scraper() {
    var client: Client? = null

    private var haveAuthorization = false
    private var canQuit = false
    private val newLine: String = System.getProperty("line.separator")
    private var authorizationState: TdApi.AuthorizationState? = null
    private val chats = hashMapOf<Long, TdApi.Chat>()
    private val messages = hashMapOf<Long, TdApi.Message>()


    private val updateHandler = Client.ResultHandler { tdApiObj ->
        when (tdApiObj.constructor) {
            TdApi.UpdateAuthorizationState.CONSTRUCTOR -> {
                onAuthorizationStateUpdated((tdApiObj as TdApi.UpdateAuthorizationState).authorizationState)
            }

            TdApi.UpdateNewChat.CONSTRUCTOR -> {
                val u = tdApiObj as TdApi.UpdateNewChat
                chats[u.chat.id] = u.chat
            }

            TdApi.UpdateChatPosition.CONSTRUCTOR -> {
                val u = tdApiObj as TdApi.UpdateChatPosition
                val chat = chats[u.chatId]
                if (chat != null) {
                    chat.positions = chat.positions + u.position
                }
            }

            TdApi.UpdateNewMessage.CONSTRUCTOR -> {
                val u = tdApiObj as TdApi.UpdateNewMessage
                if(config.chatIds.contains(u.message.chatId)) {
                    messages[u.message.id] = u.message
                }
            }
        }
    }

    private val logMessageHandler = Client.LogMessageHandler { verbosityLevel: Int, message: String ->
        if (verbosityLevel == 0) {
            onFatalError(message)
        } else {
            log.info { message }
        }
    }

    private val authorizationRequestHandler = Client.ResultHandler { tdApiObj ->
        when (tdApiObj.constructor) {
            TdApi.Error.CONSTRUCTOR -> {
                System.err.println("Receive an error:$newLine$tdApiObj")
                onAuthorizationStateUpdated(null) // repeat last action
            }

            TdApi.Ok.CONSTRUCTOR -> {}
            else -> System.err.println("Receive wrong response from TDLib:$newLine$tdApiObj")
        }
    }

    suspend fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
        Client.setLogMessageHandler(0, logMessageHandler)

        Client.execute(TdApi.SetLogVerbosityLevel(0))
        if (Client.execute(TdApi.SetLogStream(TdApi.LogStreamFile("tdlib.log", (1 shl 27).toLong(), false))) is Error) {
            throw IOError(IOException("Write access to the current directory is required"))
        }

        client = Client.create(this, updateHandler, null, null)

        log.info { "TelegramScraper started".paintGreen() }

        while (!haveAuthorization) {
            delay(100L)
        }

        log.info { "Telegram have Authorization" }

        getChatList(TdApi.ChatListMain())
        log.info { "Main chat list ready" }

        getChatList(TdApi.ChatListArchive())
        log.info { "Archive chat list ready" }

        val loggedChats = mutableSetOf<Long>()

        delay(10000L)

        while (!canQuit) {
            config.chatIds.forEach { chatId ->
                    val chat = chats[chatId]
                    if(chat != null) {
                        log.info { "Get chat history ${chat.title} (${chat.id})" }
                        getChatHistory(chatId, 0)
                        while(true) {
                            val filteredMessages = messages.values.filter { it.chatId == chatId }
                            val minMessageId = filteredMessages.minByOrNull { it.id }?.id ?: 0
                            if(filteredMessages.size < 100) {
                                if(getChatHistory(chatId, minMessageId) == 0) {
                                    break
                                }
                            } else {
                                break
                            }
                        }
                    }
                }

            chats
                .values
                .filter(this@TelegramScraper::inMainOrArchiveList)
                .filter(this@TelegramScraper::isBasicGroupOrSuperGroup)
                .filter { !loggedChats.contains(it.id) }
                .forEach { chat ->
                    loggedChats.add(chat.id)
                    log.info { "CHAT DISCOVERED: ${chat.id} ${chat.title}" }
                }

            config.chatIds.forEach { chatId ->
                val chat = chats[chatId]
                if(chat != null) {
                    log.info { "looking for new images in telegram chat ${chat.title} (${chat.id})" }
                    messages.values
                        .filter { it.chatId == chatId }
                        .mapNotNull { (it.content as? TdApi.MessagePhoto)?.photo?.sizes }
                        .mapNotNull { it.maxByOrNull { s -> s.width * s.height }?.photo }
                        .forEach { file ->
                            val digest = file.remote.uniqueId.md5()
                            if(isUnknownHash(digest)) {
                                RunnableRandomQueue.run {
                                    log.info { "download ${chat.title} (${chat.id}) ${file.remote.uniqueId}" }
                                    val tgFile = downloadFile(file.id)
                                    if(tgFile != null) {
                                        val localFile = File(tgFile.local.path)
                                        if(localFile.exists()) {
                                            val fileBytesHash = localFile.md5()
                                            val fileName = "${fileBytesHash}.${localFile.extension}"
                                            val moveTo = File("images/download/telegram/$chatId/$fileName")
                                            moveFile(localFile, moveTo, fileBytesHash, digest)
                                        }
                                        if(localFile.exists()) {
                                            localFile.delete()
                                        }
                                    }
                                }
                            }
                        }
                    delay(5.seconds)
                }
            }

            delay(5.minutes)
        }
    }

    private fun getChatList(loadChatType: TdApi.ChatList) {
        // send LoadChats request if there are some unknown chats and have not enough known chats
        client?.send(TdApi.LoadChats(loadChatType, 30)) { tdApiObj ->
            when (tdApiObj.constructor) {
                TdApi.Error.CONSTRUCTOR -> if ((tdApiObj as TdApi.Error).code != 404) {
                    System.err.println("Receive an error for LoadChats:$newLine$tdApiObj")
                }

                // chats had already been received through updates, let's retry request
                TdApi.Ok.CONSTRUCTOR -> getChatList(loadChatType)

                else -> System.err.println("Receive wrong response from TDLib:$newLine$tdApiObj")
            }
        }
    }

    private suspend fun getChatHistory(chatId: Long, fromMessageId: Long = 0): Int {
        return suspendCoroutine { continuation ->
            client?.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, 100, false)) { tdApiObject ->
                when(tdApiObject.constructor) {
                    TdApi.Messages.CONSTRUCTOR -> {
                        val m = tdApiObject as TdApi.Messages
                        m.messages.forEach {
                            messages[it.id] = it
                        }
                        continuation.resume(m.messages.size)
                    }
                }
            }
        }
    }

    private suspend fun downloadFile(id: Int): TdApi.File? {

        return suspendCoroutine { continuation ->
            client?.send(TdApi.DownloadFile(id, 1, 0, Long.MAX_VALUE, true)) { tdApiObj ->
                when(tdApiObj.constructor) {
                    TdApi.File.CONSTRUCTOR -> {
                        continuation.resume(tdApiObj as TdApi.File)
                    }
                    else -> continuation.resume(null)
                }
            }
        }
    }

    fun metrics(): List<String> {
        return config.chatIds.mapNotNull { chatId ->
            val chat = chats[chatId]
            if(chat != null) {
                val messagesCount = messages.values.count { it.chatId == chatId }
                "${chat.title} (${chat.id}) - $messagesCount messages"
            } else {
                null
            }
        }
    }

    private fun inMainOrArchiveList(chat: TdApi.Chat): Boolean {
        val constructors = listOf(TdApi.ChatListMain.CONSTRUCTOR, TdApi.ChatListArchive.CONSTRUCTOR)
        return chat.positions.any { position -> constructors.contains(position.list.constructor) }
    }

    private fun isBasicGroupOrSuperGroup(chat: TdApi.Chat): Boolean {
        return chat.type is TdApi.ChatTypeBasicGroup || chat.type is TdApi.ChatTypeSupergroup
    }

    private fun onFatalError(errorMessage: String) {
        class ThrowError constructor(
            private val errorMessage: String,
            private val errorThrowTime: AtomicLong
        ) : Runnable {
            override fun run() {
                if (isDatabaseBrokenError(errorMessage) || isDiskFullError(errorMessage) || isDiskError(errorMessage)) {
                    processExternalError()
                    return
                }
                errorThrowTime.set(System.currentTimeMillis())
                throw ClientError("TDLib fatal error: $errorMessage")
            }

            private fun processExternalError() {
                errorThrowTime.set(System.currentTimeMillis())
                throw ExternalClientError("Fatal error: $errorMessage")
            }

            inner class ClientError constructor(message: String) : java.lang.Error(message)
            inner class ExternalClientError(message: String?) : java.lang.Error(message)

            private fun isDatabaseBrokenError(message: String): Boolean {
                return message.contains("Wrong key or database is corrupted") ||
                        message.contains("SQL logic error or missing database") ||
                        message.contains("database disk image is malformed") ||
                        message.contains("file is encrypted or is not a database") ||
                        message.contains("unsupported file format") ||
                        message.contains("Database was corrupted and deleted during execution and can't be recreated")
            }

            private fun isDiskFullError(message: String): Boolean {
                return message.contains("PosixError : No space left on device") ||
                        message.contains("database or disk is full")
            }

            private fun isDiskError(message: String): Boolean {
                return message.contains("I/O error") || message.contains("Structure needs cleaning")
            }
        }

        val errorThrowTime = AtomicLong(Long.MAX_VALUE)
        Thread(ThrowError(errorMessage, errorThrowTime), "TDLib fatal error thread").start()

        // wait at least 10 seconds after the error is thrown
        while (errorThrowTime.get() >= System.currentTimeMillis() - 10000) {
            try {
                Thread.sleep(1000 /* milliseconds */)
            } catch (ignore: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun onAuthorizationStateUpdated(authorizationState: TdApi.AuthorizationState?) {
        if (authorizationState != null) {
            this.authorizationState = authorizationState
        }
        when (authorizationState?.constructor) {
            TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
                val request = TdApi.SetTdlibParameters()
                request.databaseDirectory = "tdlib"
                request.useMessageDatabase = true
                request.useSecretChats = true
                request.apiId = 94575
                request.apiHash = "a3406de8d171bb422bb6ddf3bbd800e2"
                request.systemLanguageCode = "en"
                request.deviceModel = "Desktop"
                request.applicationVersion = "1.0"
                request.enableStorageOptimizer = true
                client?.send(request, authorizationRequestHandler)
            }

            TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                print("Please enter phone number: ")
                val phoneNumber = config.phone
                client?.send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null), authorizationRequestHandler)
            }

            TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR -> {
                val link = (authorizationState as TdApi.AuthorizationStateWaitOtherDeviceConfirmation).link
                println("Please confirm this login link on another device: $link")
            }

            TdApi.AuthorizationStateWaitEmailAddress.CONSTRUCTOR -> {
                print("Please enter email address: ")
                val emailAddress = readln()
                client?.send(TdApi.SetAuthenticationEmailAddress(emailAddress), authorizationRequestHandler)
            }

            TdApi.AuthorizationStateWaitEmailCode.CONSTRUCTOR -> {
                print("Please enter email authentication code: ")
                val code = readln()
                client?.send(
                    TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(code)),
                    authorizationRequestHandler
                )
            }

            TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> {
                print("Please enter authentication code: ")
                val code = readln()
                client?.send(TdApi.CheckAuthenticationCode(code), authorizationRequestHandler)
            }

//            TdApi.AuthorizationStateWaitRegistration.CONSTRUCTOR -> {
//                print("Please enter your first name: ")
//                val firstName = readln()
//                print("Please enter your last name: ")
//                val lastName = readln()
//                client?.send(TdApi.RegisterUser(firstName, lastName), authorizationRequestHandler)
//            }

            TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                print("Please enter password: ")
                val password = readln()
                client?.send(TdApi.CheckAuthenticationPassword(password), authorizationRequestHandler)
            }

            TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                haveAuthorization = true
            }

            TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR -> {
                haveAuthorization = false
                print("Logging out")
            }

            TdApi.AuthorizationStateClosing.CONSTRUCTOR -> {
                haveAuthorization = false
                print("Closing")
            }

            TdApi.AuthorizationStateClosed.CONSTRUCTOR -> {
                print("Closed")
                canQuit = true
//                if (!needQuit) {
//                    client = Client.create(
//                        updateHandler,
//                        null,
//                        null
//                    ) // recreate client after previous has closed
//                } else {
//                    canQuit = true
//                }
            }

            else -> System.err.println("Unsupported authorization state:$newLine$authorizationState")
        }
    }

    fun close() {
        if(haveAuthorization) {
            haveAuthorization = false
            client?.send(TdApi.Close(), updateHandler)
        }
    }
}