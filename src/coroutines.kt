import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.system.measureTimeMillis

/**
 * Created by wxk on 2020/4/24.
 */

fun testGlobalScope() = runBlocking {
    val job = GlobalScope.launch {
        println(1)
        delay(3000)
        println(2)
    }
    println(3)
    job.join()
}

fun testRunBlocking() = runBlocking {
    launch {
        println(1)
        delay(3000)
        println(2)
    }
    println(3)
}

fun testCoroutineScope() = runBlocking {
    launch {
        delay(200)
        println("task from runblocking")
    }

    coroutineScope {
        launch {
            delay(5000)
            println("task from coroutineScope launch")
        }
        delay(1000)
        println("task from coroutineScope")
    }
    print("coroutineScope is over")
}

suspend fun testSuspend() {
    delay(1000)
    print("test suspend")
}

fun cancelLaunch() = runBlocking {
    val job = launch {
        delay(10000)
        println("job")
    }
    job.cancel()
    job.join()
    job.cancelAndJoin()
    println("cancel")
}

fun cancelWhitCheckLaunch() = runBlocking {
    val startTime = System.currentTimeMillis()
    val job = launch(Dispatchers.Default) {
        var nextTime = startTime
        var i = 0
        while (isActive) {
//            yield() //也可以
            if (System.currentTimeMillis() >= nextTime) {
                println("job:sleep${i++}...")
                nextTime += 1000
            }
        }
    }
    delay(1300)
    job.cancelAndJoin()
    println("job cancel")
}

//处理取消协程抛出的异常CancellationException
fun tryCatchLaunch() = runBlocking {
    val job = launch {
        try {
            repeat(1000) {
                println("job im sleeping $it...")
                delay(500)
            }
        } finally {
            println("job im running finally")
        }
    }
    delay(1300)
    job.cancelAndJoin()
    println("job cancel")
}

//处理取消协程抛出的异常CancellationException运行不可取消代码块
fun tryCatchRunNoneCancelableLaunch() = runBlocking {
    val job = launch {
        try {
            repeat(1000) {
                println("job im sleeping $it...")
                delay(500)
            }
        } finally {
            withContext(NonCancellable) {
                println("job im running finally")
                delay(10000)
                println("job NonCancellable")
            }
        }
    }
    delay(1300)
    job.cancelAndJoin()
    println("job cancel")
}

//设置超时时间取消 必须trycatch
//withTimeoutOrNull不用，会返回null
fun withTimeOut() = runBlocking {
    try {
        withTimeout(1000) {
            repeat(1000) {
                println("im sleep $it..")
                delay(500)
            }
        }
    } catch (e: TimeoutCancellationException) {

    } finally {
        println("finally")
    }
}

//通道
fun channel() = runBlocking {
    val channel = Channel<Int>()
    launch {
        repeat(1000) {
            channel.send(it)
        }
    }
    repeat(5) {
        println(channel.receive())
    }
}

//通道关闭
fun channelClose() = runBlocking {
    val channel = Channel<Int>()
    launch {
        for (x in 1..10) channel.send(x)
        channel.close()
    }
    for (c in channel)
        println(c)
//    coroutineContext.cancelChildren()//取消所有子协程
}

//produce通道生产者 consumeEach消费者消费遍历
fun channelProduce() = runBlocking {
    fun CoroutineScope.produceSquares(): ReceiveChannel<Int> = produce {
        for (x in 1..10) send(x * x)
    }

    val squares = produceSquares()
    squares.consumeEach {
        println(it)
    }
}

//管道，无限发数据
fun channelProduceNums() = runBlocking {
    fun CoroutineScope.produceNumbers() = produce {
        var x = 1
        while (true) send(x++)
    }

    fun CoroutineScope.produceSquares(number: ReceiveChannel<Int>): ReceiveChannel<Int> = produce {
        for (x in number) send(x * x)
    }

    val number = produceNumbers()
    val squares = produceSquares(number)
    for (i in 1..10) println(squares.receive())
}

//计时通道
fun channelTicker() = runBlocking {
    val tickerChannel = ticker(delayMillis = 100, initialDelayMillis = 0)
    var nextElement = withTimeoutOrNull(1) {
        tickerChannel.receive()
    }
    println("Inital element is available immediately $nextElement")
    nextElement = withTimeoutOrNull(50) {
        tickerChannel.receive()
    }
    println("next element is not ready in 50ms $nextElement")
    nextElement = withTimeoutOrNull(60) {
        tickerChannel.receive()
    }
    println("next element is ready in 60ms $nextElement")
    println("模拟大量消费延迟")
    delay(150)
    nextElement = withTimeoutOrNull(1) {
        tickerChannel.receive()
    }
    println("Inital element is available immediately after lage consumer delay: $nextElement")
    nextElement = withTimeoutOrNull(60) {
        tickerChannel.receive()
    }
    println("next element is ready in 60ms after consumer pause in 150 ms $nextElement")
    tickerChannel.cancel()
}

//并发
fun bothDo() = runBlocking {
    suspend fun doSomethingUsefulOne(): Int {
        delay(1000)
        return 13
    }

    suspend fun doSomethingUsefulTwo(): Int {
        delay(3000)
        return 29
    }

    var time = measureTimeMillis {
        val one = async { doSomethingUsefulOne() }
        val two = async { doSomethingUsefulTwo() }
        println("add result：${one.await() + two.await()}")
    }
    println("time $time")
}

//并发惰性，start或者await触发执行
fun bothDoLazy() = runBlocking {
    suspend fun doSomethingUsefulOne(): Int {
        delay(1000)
        return 13
    }

    suspend fun doSomethingUsefulTwo(): Int {
        delay(3000)
        return 29
    }

    var time = measureTimeMillis {
        val one = async(start = CoroutineStart.LAZY) { doSomethingUsefulOne() }
        val two = async(start = CoroutineStart.LAZY) { doSomethingUsefulTwo() }
        //不start按照await顺序执行
        one.start()
        two.start()
        println("add result：${one.await() + two.await()}")
    }
    println("time $time")
}

/* async 风格函数，可在任意位置被调用，调用即为异步，kt文档不推荐使用。示例 start */
suspend fun doSomethingUsefulOne(): Int {
    delay(1000)
    return 13
}

suspend fun doSomethingUsefulTwo(): Int {
    delay(3000)
    return 29
}

fun somethingUsefulOneAsync() = GlobalScope.async {
    doSomethingUsefulOne()
}

fun somethingUsefulTwoAsync() = GlobalScope.async {
    doSomethingUsefulTwo()
}

//不推荐
fun useWhitOutCoroutines() {
    val timer = measureTimeMillis {
        val one = somethingUsefulOneAsync()
        val two = somethingUsefulTwoAsync()
        //异步已经开始执行，等待结果必须挂起或者阻塞
        runBlocking {
            println("结果: ${one.await() + two.await()}")
        }
    }
    print("time:${timer}")
}

//推荐,在上层使用时，捕获coroutineScope的异常会直接取消子协程
suspend fun useWhitCoroutineScop(): Int = coroutineScope {
    val one = async { doSomethingUsefulOne() }
    val two = async { doSomethingUsefulTwo() }
    one.await() + two.await()
}
/* async 风格函数，可在任意位置被调用，调用即为异步，kt文档不推荐使用。示例 end */

/* 协程调度器与线程。示例 start */
//调度不同线程
fun dispatcher() = runBlocking {
    launch {
        println("main runBlocking : im work in thread ${Thread.currentThread().name}")
    }
    launch(Dispatchers.Unconfined) {
        println("Unconfined runBlocking : im work in thread ${Thread.currentThread().name}")
    }
    launch(Dispatchers.Default) {
        println("Default runBlocking : im work in thread ${Thread.currentThread().name}")
    }
    launch(newSingleThreadContext("MyThread")) {
        println("MyThread runBlocking : im work in thread ${Thread.currentThread().name}")
    }
}

//日志调试
fun log(msg: String) = println("[${Thread.currentThread().name}] $msg")
fun logShow() = runBlocking {
    val a = async {
        log("a")
        6
    }
    val b = async {
        log("b")
        7
    }
    log("await ${a.await() + b.await()}")
}

//不同线程跳转
fun jumpThread() = runBlocking {
    newSingleThreadContext("ctx1").use { ctx1 ->
        newSingleThreadContext("ctx2").use { ctx2 ->
            runBlocking(ctx1) {
                log("started in ctx1")
                //转换上下文
                withContext(ctx2) {
                    log("working in ctx2")
                }
                log("back to ctx1")
            }
        }
    }
}

fun coroutinesWhitName() = runBlocking(CoroutineName("main")) {
    log("started maincoroutines")
    val v1 = async(CoroutineName("v1")) {
        delay(500)
        log("Computing v1")
        252
    }
    val v2 = async(CoroutineName("v2")) {
        delay(500)
        log("Computing v2")
        6
    }
    log("v1/v2 = ${v1.await() / v2.await()}")
}

//线程局部数据
val threadLocal = ThreadLocal<String?>()
fun localElement() = runBlocking {
    threadLocal.set("main")
    println("pre-main currentThread ${Thread.currentThread()} thread local value:${threadLocal.get()}")
    val job = launch(Dispatchers.Default + threadLocal.asContextElement("launch")) {
        println("launch start currentThread ${Thread.currentThread()} thread local value:${threadLocal.get()}")
        yield()
        println("after yield currentThread ${Thread.currentThread()} thread local value:${threadLocal.get()}")
    }
    job.join()
    println("post-main currentThread ${Thread.currentThread()} thread local value:${threadLocal.get()}")
}
/* 协程调度器与线程。示例 end */

/* 协程异步流。示例 start */
fun foo(): Flow<Int> = flow {//流构造器
    repeat(10) {
        delay(100)
        emit(it)
    }
//    另两种构造方式
//    (1..3).asFlow().collect {  }
//    flowOf(listOf<Int>(1,2,3,4)).collect {  }
}

//收集流
fun getFoo() = runBlocking {
    launch {
        repeat(10) {
            delay(300)
        }
    }
    foo().collect { value -> println(value) }
}

//超时取消流
fun timeOutFoo() = runBlocking {
    withTimeoutOrNull(600) {
        foo().collect {
            println(it)
        }
    }
    println("done")
}

suspend fun performRequest(request: Int): String {
    delay(1000)
    return "response $request"
}

//map操作符(进行操作后发给下游)
fun map() = runBlocking {
    (1..3).asFlow()
            .map { performRequest(it) }
            .collect { println(it) }
}

//发射任意值任意次
fun transform() = runBlocking {
    (1..3).asFlow()
            .transform {
                emit("Making Request $it")
                emit(performRequest(it))
            }
            .collect { println(it) }
}

//take 限制长度，接收个数
fun take() = runBlocking {
    (1..3).asFlow()
            .take(2)
            .collect{ println(it) }
}

//filter 过滤，根据条件
fun filter() = runBlocking {
    (1..10).asFlow()
            .filter { it%2 == 0 }
            .collect { println(it) }
}
/* 协程异步流。示例 end */

