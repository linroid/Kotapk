package com.linroid.kotapk

import com.beust.klaxon.*
import com.github.kittinunf.fuel.httpDownload
import com.github.kittinunf.fuel.httpGet
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.util.zip.ZipFile


fun main(args: Array<String>) {
    val current = System.getProperty("user.dir")
    println(File(current).absolutePath)
    Kotapk("$current/build/apks", "0").execute()
}


fun File.makeSureDir(): File {
    if (!this.exists()) {
        println("create dir: $this")
        this.mkdirs()
    }
    return this
}

fun File.makeSureFile(): File {
    if (!this.exists()) {
        println("create file: $this")
        this.createNewFile()
    }
    return this
}

class Kotapk(path: String, private val categoryId: String) {
    private val downloadDir: File = File(path)

    init {
        downloadDir.makeSureDir()
        println("downloadDir=$downloadDir")
    }

    fun execute() {
        val apks = fetchAppList()
        val kotlinApks = mutableSetOf<JsonObject>()
        val totalCount = apks.size
        println("\ntotal apk: $totalCount")
        var id = 0
        val maxSize = 100 * 1024 * 1024 // 100M
        apks.forEach {
            println("[$id/$totalCount]" + it.string("appName"))

            if (it.long("fileSize")!! <= maxSize) {
                val apkFile = downloadApk(it)
                if (hasKotlin(apkFile)) {
                    println("=====================")
                    println("===== kotlin apk ====")
                    println("=====================")
                    kotlinApks.add(it)
                }
                println("\n")
            } else {
                println("ignore too large apk")
            }
            id += 1
        }
        printlnResult(kotlinApks, totalCount)
    }

    private fun printlnResult(kotApks: MutableSet<JsonObject>, totalCount: Int) {

        println("\n\nRESULT(${kotApks.size}/$totalCount)\n=================")
        kotApks.forEachIndexed { index, jsonObject ->
            val name = jsonObject.string("appName")
            println("- $name")
        }

    }

    private fun fetchAppList(): List<JsonObject> {
        val apks = mutableListOf<JsonObject>()
        var hasMore = true
        val pageSize = 20
        var page = 0
        val maxPage = 10
        while (hasMore && page < maxPage) {
            val url = "http://android.myapp.com/myapp/cate/appList.htm?orgame=1&categoryId=${categoryId}&pageSize=$pageSize&pageContext=${page * pageSize}"
            println("fetchUrl:$url")
            val (_, _, result) = url.httpGet().responseString()
            val json = result.get()
//            println("json:$json")
            val data = Parser().parse(StringBuilder(json)) as JsonObject
            val obj: JsonArray<JsonObject>? = data.array("obj")
            hasMore = data.int("count")!! > 0 && obj!=null
            if (hasMore) {
                apks.addAll(obj!!)
            }
            page += 1
        }
        return apks
    }

    private fun downloadApk(apk: JsonObject): File {
        val apkUrl = apk.string("apkUrl")!!
        val apkMd5 = apk.string("apkMd5")!!
        val apkPkgName = apk.string("pkgName")!!
        val apkFile = File(downloadDir, "${apkPkgName}.apk")
        val apkTmpFile = File(apkFile.absolutePath + ".tmp")
        if (apkFile.exists()) {
            val localMd5 = DigestUtils.md5Hex(apkFile.inputStream())
            if (apkMd5.toUpperCase() == localMd5.toUpperCase()) {
                println("apk has been downloaded, skip...")
                return apkFile
            }
            apkFile.delete()
            println("md5 not matched, redownload apk...")
        }
        apkTmpFile.makeSureFile().deleteOnExit()
        println("download apk to $apkFile")
        val (_, _, result) = apkUrl.httpDownload().destination { response, url ->
            return@destination apkTmpFile
        }.response()
        apkTmpFile.renameTo(apkFile)
        if (apkTmpFile.exists()) {
            apkTmpFile.delete()
        }
        result.component2()?.printStackTrace()
        return apkFile
    }

    private fun hasKotlin(apkFile: File): Boolean {
        val zipFile = ZipFile(apkFile)
        zipFile.entries().iterator().forEach {
            if (it.name.contains("kotlin")) {
                return@hasKotlin true
            }
        }
        zipFile.close()
        return false
    }

}