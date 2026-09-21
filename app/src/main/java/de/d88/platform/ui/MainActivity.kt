package de.d88.platform.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.d88.platform.R

/**
 * Hauptaktivität: Bottom-Navigation mit den fünf D88-Bereichen.
 * Security- und Wissensbereich sind vom Dashboard erreichbar.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        if (savedInstanceState == null) {
            show(DashboardFragment(), R.id.nav_dashboard)
        }
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> show(DashboardFragment(), item.itemId)
                R.id.nav_chat -> show(ChatFragment(), item.itemId)
                R.id.nav_devices -> show(DevicesFragment(), item.itemId)
                R.id.nav_approvals -> show(ApprovalsFragment(), item.itemId)
                R.id.nav_observatory -> show(ObservatoryFragment(), item.itemId)
            }
            true
        }
    }

    fun show(fragment: Fragment, menuId: Int? = null) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .commit()
        if (menuId != null) {
            findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId = menuId
        }
    }
}
