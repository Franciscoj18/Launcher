package com.calma.launcher.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/** Paquete de íconos compatible con Nova/ADW (por ejemplo Whicons). */
class IconPack(ctx: Context, val pkg: String) {
    private val res: Resources = ctx.packageManager.getResourcesForApplication(pkg)
    private val byComponent = HashMap<String, String>()
    private val byPackage = HashMap<String, String>()

    init { load() }

    @SuppressLint("DiscouragedApi")
    private fun load() {
        val id = res.getIdentifier("appfilter", "xml", pkg)
        val parser: XmlPullParser? = if (id != 0) {
            res.getXml(id)
        } else {
            try {
                val input = res.assets.open("appfilter.xml")
                Xml.newPullParser().apply { setInput(input, "utf-8") }
            } catch (e: Exception) { null }
        }
        parser ?: return
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component")
                val draw = parser.getAttributeValue(null, "drawable")
                if (comp != null && draw != null) {
                    val inner = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                    val p = inner.substringBefore('/')
                    var c = inner.substringAfter('/', "")
                    if (c.startsWith(".")) c = p + c
                    if (p.isNotEmpty() && c.isNotEmpty()) {
                        byComponent["$p/$c"] = draw
                        if (!byPackage.containsKey(p)) byPackage[p] = draw
                    }
                }
            }
            ev = parser.next()
        }
    }

    @SuppressLint("DiscouragedApi", "UseCompatLoadingForDrawables")
    fun drawableFor(cn: ComponentName): Drawable? {
        val name = byComponent["${cn.packageName}/${cn.className}"] ?: byPackage[cn.packageName] ?: return null
        val id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return try { res.getDrawable(id, null) } catch (e: Exception) { null }
    }

    companion object {
        private val ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
        )

        /** Paquetes de íconos instalados: (paquete, nombre). */
        fun installed(ctx: Context): List<Pair<String, String>> {
            val pm = ctx.packageManager
            val seen = LinkedHashMap<String, String>()
            for (a in ACTIONS) {
                val list = try {
                    pm.queryIntentActivities(Intent(a), PackageManager.GET_META_DATA)
                } catch (e: Exception) { emptyList() }
                for (ri in list) {
                    val p = ri.activityInfo.packageName
                    if (!seen.containsKey(p)) seen[p] = ri.loadLabel(pm).toString()
                }
            }
            return seen.map { it.key to it.value }.sortedBy { it.second.lowercase() }
        }
    }
}
