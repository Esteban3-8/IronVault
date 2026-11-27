package com.example.myapplication

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.widget.ScrollView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class ResponsiveLayoutTest {
    @Test fun actionsRemainReachableInCompactWindows() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val base = instrumentation.targetContext
            val config = Configuration(base.resources.configuration).apply { fontScale = 1.3f }
            val context = ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_MyApplication)
            val density = context.resources.displayMetrics.density
            for ((width, height) in listOf(320 to 480, 640 to 320, 800 to 1024)) {
                fun layout(resource: Int): View {
                    val root = LayoutInflater.from(context).inflate(resource, null)
                    root.findViewById<View>(R.id.groupDropdown)?.visibility = View.VISIBLE
                    root.measure(MeasureSpec.makeMeasureSpec((width * density).roundToInt(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec((height * density).roundToInt(), MeasureSpec.EXACTLY))
                    root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                    return root
                }
                val screenLayouts = R.layout::class.java.fields.filter { it.name.startsWith("activity_") }
                for (resource in screenLayouts) {
                    val root = layout(resource.getInt(null))
                    val back = root.findViewById<View>(R.id.btnBack) ?: continue
                    val minimum = (48 * density).roundToInt()
                    assertTrue(resource.name, back.width >= minimum && back.height >= minimum)
                }
                val tabLayouts = listOf(R.layout.activity_main, R.layout.activity_security,
                    R.layout.activity_password_generator, R.layout.activity_settings)
                val positions = tabLayouts.map { resource ->
                    val bar = layout(resource).findViewById<View>(R.id.bottomNavContainer)
                    listOf(bar.left, bar.top, bar.right, bar.bottom)
                }
                assertTrue("La barra debe coincidir en las cuatro pantallas", positions.distinct().size == 1)
                for (resource in listOf(R.layout.activity_nueva_categoria, R.layout.activity_editar_categoria)) {
                    val root = layout(resource)
                    val save = root.findViewById<View>(R.id.btnSave)
                    val card = root.findViewById<View>(R.id.cardGroup)
                    val scroll = card.parent.parent as ScrollView
                    assertTrue(scroll.height > 0 && scroll.bottom <= save.top && save.bottom <= root.height)
                }
                val verify = layout(R.layout.activity_verify_code)
                val last = verify.findViewById<View>(R.id.etDigit6)
                assertTrue(last.width > 0 && last.right <= (last.parent as View).width)
                val security = layout(R.layout.activity_security)
                val content = security.findViewById<View>(R.id.cardLock).parent.parent as ScrollView
                assertTrue(content.height > 0 && content.bottom <= security.findViewById<View>(R.id.bottomNavContainer).top)
            }
        }
    }
}
