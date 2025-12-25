package com.example.finddjiaccount

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.annotation.NonNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.uiAutomator
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.Part
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

const val SERIAL : String = "1581F7K3C251900CS94B"
const val EMAIL_PREFIX : String = "d67"
const val EMAIL_SUFFIX : String = "@djifly.pl"
const val PASS_PREFIX : String = "DJIfly67"
const val START_COUNT : Int = 1
const val CYCLE_COUNT : Int = 4
const val GEMINI_API_KEY = "---"
private var uiDevice: UiDevice? = null

var logFileName = "$EMAIL_PREFIX.log"

@RunWith(AndroidJUnit4::class)
class FindAccountTest {

    fun findSerialElement(email: String, passwd: String, items: List<UiObject2>) : Boolean {
        var found = false
        for (item in items) {
            item.click()
            SystemClock.sleep(100L)
            val serialTitle = getDevice().findObject(By.text("Серийный номер дрона"))
            val serialObject = findNextSiblingElement(serialTitle)
            if(serialObject != null && serialObject.text.length > 1) {
                logToFile("$email $passwd ${serialObject.text}")
            }
            if (serialObject != null && serialObject.text == SERIAL) {
                found = true
            }
        }
        return found
    }
    fun findMatrice(email: String, passwd: String) : Boolean {
        val panelObj = getDevice().findObject(By.clazz("androidx.recyclerview.widget.RecyclerView"))
        do {
            val items = getDevice().findObjects(By.text("Matrice 4 Series"))

            if(items.count() < 2) {
                break
            }

            if(findSerialElement(email, passwd, items)) {
                return true
            }

        } while(panelObj.scroll(Direction.DOWN, 1.1f))

        SystemClock.sleep(100L)

        return findSerialElement(email, passwd,getDevice().findObjects(By.text("Matrice 4 Series")))
    }

    companion object {
        @JvmStatic // Use @JvmStatic for Java interoperability if needed
        @NonNull
        fun getDevice(): UiDevice {
            if (uiDevice == null) {
                uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            }
            return uiDevice!!
        }
    }

    fun isCaptchaExists() : Boolean {
        return getDevice().findObject(By.res("com.dji.industry.pilot:id/sign_in_verify_code_btn")) != null
    }
    fun resolveCaptcha() : String? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = context.filesDir
        val img = getDevice().findObject(By.res("com.dji.industry.pilot:id/sign_in_verify_code_btn"))
        val rect = img.visibleBounds
        val result: Boolean = getDevice().takeScreenshot(File("${dir}/screenshot.png"))
        if (result) {
            val cropped = Bitmap.createBitmap(
                BitmapFactory.decodeFile(File("${dir}/screenshot.png").absolutePath),
                rect.left,
                rect.top,
                rect.width(),
                rect.height()
            )
            val file = File("$dir/screenshot.png")
            FileOutputStream(file).use { out ->
                cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val client = Client.builder()
                .apiKey(GEMINI_API_KEY)
                .build()

            val imageBytes = Files.readAllBytes(Paths.get("$dir/screenshot.png"))

            val content = Content.fromParts(
                Part.fromText("resolve captcha and respond only result"),
                Part.fromBytes(imageBytes, "image/png")
            )
            val response = client.models.generateContent(
                "gemini-2.5-flash",
                content,
                null
            )
            return response.text()
        } else {
            println("Screenshot Error !!!")
            return ""
        }
    }

    fun findNextSiblingElement(currentElement: UiObject2): UiObject2? {
        val parent = currentElement.parent

        // Check if parent is not null
        if (parent != null) {
            // Get all child elements of the parent
            val children = parent.children

            // Find the current index of the current element
            val currentIndex = children.indexOf(currentElement)

            // Check the next sibling
            if (currentIndex != -1 && currentIndex + 1 < children.size) {
                return children[currentIndex + 1] // Return the next sibling
            }
        }

        return null // Return null if no sibling found
    }

    fun findInput(id: String) : UiObject {
        return getDevice().findObject(UiSelector().resourceId(id))
    }

    fun emailInput() : UiObject {
        return findInput("com.dji.industry.pilot:id/sign_in_account")
    }

    fun passwordInput() : UiObject {
        return findInput("com.dji.industry.pilot:id/sign_in_password")
    }

    fun logOut() {
        findInput("com.dji.industry.pilot:id/home_logout_btn").click()
        getDevice().wait(
            Until.findObject(By.text("OK")),
            2000   // timeout ms
        )?.click()
    }

    fun logIn(username: String, password: String) : Boolean {
        emailInput().setText(username)
        passwordInput().setText(password)

        if(isCaptchaExists()) {
            findInput( "com.dji.industry.pilot:id/sign_in_verify_code_edit").setText(resolveCaptcha())
        }

        findInput("com.dji.industry.pilot:id/toolbar_log_in").click()
        getDevice().wait(
            Until.findObject(By.text("OK")),
            2000
        )?.click()

        SystemClock.sleep(100L)

        return !passwordInput().exists() && !emailInput().exists()
    }

    fun logoutLogin(username: String, password: String) : Boolean {
        val enterObj = getDevice().findObject(By.text("Вход"))
        if(enterObj != null) {
            enterObj.click()
        } else {
            logOut()
            getDevice().wait(
                Until.findObject(By.text("Вход")),
                2000
            )?.click()
        }
        SystemClock.sleep(200L)
        repeat(3) {
            if(logIn( username, password)) {
                return true
            }
        }
        SystemClock.sleep(200L)
        return false
    }
    fun goBack() {
        findInput( "com.dji.industry.pilot:id/navStartContainer").click()
    }

    fun logToFile(message : String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val logFile = File(context.getExternalFilesDir(null), logFileName)
        try {
            val writer = FileWriter(logFile, true) // Append mode
            writer.append("$message\n")
            writer.flush()
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun copyLogFileToMedia() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val logFile = File(context.getExternalFilesDir(null), logFileName)
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, logFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
        }

        val uri: Uri? = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                FileInputStream(logFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }
    @Test
    fun useAppContext() {
        uiAutomator {
            device.findObject(By.text("DJI Pilot 2")).click()
            SystemClock.sleep(200L)
            val listItems = onElements {
                isClickable
            }
            listItems[0].click()
            SystemClock.sleep(200L)
            for (i in START_COUNT..CYCLE_COUNT) {
                var number = i.toString()
                if(i < 10) {
                    number = "0$number"
                }
                val email = "$EMAIL_PREFIX$number$EMAIL_SUFFIX"
                val passwd = "$PASS_PREFIX$number"
                if(logoutLogin(email, passwd)) {
                    findInput("com.dji.industry.pilot:id/item_title").click()
                    if(findMatrice(email, passwd)) {
                        println(email)
                        println(passwd)
                        println(SERIAL)
                    } else {
                        goBack()
                    }
                }
            }
            copyLogFileToMedia()

        }
    }
}