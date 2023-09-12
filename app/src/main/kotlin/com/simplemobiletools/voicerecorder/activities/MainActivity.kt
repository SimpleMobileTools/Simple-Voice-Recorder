package com.simplemobiletools.voicerecorder.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.voicerecorder.BuildConfig
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.adapters.ViewPagerAdapter
import com.simplemobiletools.voicerecorder.databinding.ActivityMainBinding
import com.simplemobiletools.voicerecorder.extensions.checkRecycleBinItems
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.helpers.STOP_AMPLITUDE_UPDATE
import com.simplemobiletools.voicerecorder.models.Events
import com.simplemobiletools.voicerecorder.services.RecorderService
import me.grantland.widget.AutofitHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : SimpleActivity() {

    private var bus: EventBus? = null
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()

        updateMaterialActivityViews(binding.mainCoordinator, binding.mainHolder, useTransparentNavigation = false, useTopSearchMenu = true)

        if (checkAppSideloading()) {
            return
        }

        if (savedInstanceState == null) {
            checkRecycleBinItems()
        }

        handlePermission(PERMISSION_RECORD_AUDIO) {
            if (it) {
                tryInitVoiceRecorder()
            } else {
                toast(com.simplemobiletools.commons.R.string.no_audio_permissions)
                finish()
            }
        }

        bus = EventBus.getDefault()
        bus!!.register(this)
        if (config.recordAfterLaunch && !RecorderService.isRunning) {
            Intent(this@MainActivity, RecorderService::class.java).apply {
                try {
                    startService(this)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateMenuColors()
        if (getPagerAdapter()?.showRecycleBin != config.useRecycleBin) {
            setupViewPager()
        }
        setupTabColors()
        getPagerAdapter()?.onResume()
    }

    override fun onPause() {
        super.onPause()
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
        getPagerAdapter()?.onDestroy()

        Intent(this@MainActivity, RecorderService::class.java).apply {
            action = STOP_AMPLITUDE_UPDATE
            try {
                startService(this)
            } catch (ignored: Exception) {
            }
        }
    }

    override fun onBackPressed() {
        if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
        } else if (isThirdPartyIntent()) {
            setResult(Activity.RESULT_CANCELED, null)
            super.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    private fun refreshMenuItems() {
        binding.mainMenu.getToolbar().menu.apply {
            findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(com.simplemobiletools.commons.R.bool.hide_google_relations)
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.getToolbar().inflateMenu(R.menu.menu)
        binding.mainMenu.toggleHideOnScroll(false)
        binding.mainMenu.setupMenu()

        binding.mainMenu.onSearchOpenListener = {
            if (binding.viewPager.currentItem == 0) {
                binding.viewPager.currentItem = 1
            }
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            getPagerAdapter()?.searchTextChanged(text)
        }

        binding.mainMenu.getToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateMenuColors() {
        updateStatusbarColor(getProperBackgroundColor())
        binding.mainMenu.updateColors()
    }

    private fun tryInitVoiceRecorder() {
        if (isRPlus()) {
            setupViewPager()
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE) {
                if (it) {
                    setupViewPager()
                } else {
                    finish()
                }
            }
        }
    }

    private fun setupViewPager() {
        binding.mainTabsHolder.removeAllTabs()
        var tabDrawables = arrayOf(com.simplemobiletools.commons.R.drawable.ic_microphone_vector, R.drawable.ic_headset_vector)
        var tabLabels = arrayOf(R.string.recorder, R.string.player)
        if (config.useRecycleBin) {
            tabDrawables += com.simplemobiletools.commons.R.drawable.ic_delete_vector
            tabLabels += com.simplemobiletools.commons.R.string.recycle_bin
        }

        tabDrawables.forEachIndexed { i, drawableId ->
            binding.mainTabsHolder.newTab().setCustomView(com.simplemobiletools.commons.R.layout.bottom_tablayout_item).apply {
                customView?.findViewById<ImageView>(com.simplemobiletools.commons.R.id.tab_item_icon)?.setImageDrawable(getDrawable(drawableId))
                customView?.findViewById<TextView>(com.simplemobiletools.commons.R.id.tab_item_label)?.setText(tabLabels[i])
                AutofitHelper.create(customView?.findViewById(com.simplemobiletools.commons.R.id.tab_item_label))
                binding.mainTabsHolder.addTab(this)
            }
        }

        binding.mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false)
                if (it.position == 1 || it.position == 2) {
                    binding.mainMenu.closeSearch()
                }
            },
            tabSelectedAction = {
                binding.viewPager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true)
            }
        )

        binding.viewPager.adapter = ViewPagerAdapter(this, config.useRecycleBin)
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.onPageChangeListener {
            binding.mainTabsHolder.getTabAt(it)?.select()
            (binding.viewPager.adapter as ViewPagerAdapter).finishActMode()
        }

        if (isThirdPartyIntent()) {
            binding.viewPager.currentItem = 0
        } else {
            binding.viewPager.currentItem = config.lastUsedViewPagerPage
            binding.mainTabsHolder.getTabAt(config.lastUsedViewPagerPage)?.select()
        }
    }

    private fun setupTabColors() {
        val activeView = binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.customView
        updateBottomTabItemColors(activeView, true)
        for (i in 0 until binding.mainTabsHolder.tabCount) {
            if (i != binding.viewPager.currentItem) {
                val inactiveView = binding.mainTabsHolder.getTabAt(i)?.customView
                updateBottomTabItemColors(inactiveView, false)
            }
        }

        binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.select()
        val bottomBarColor = getBottomNavigationBackgroundColor()
        binding.mainTabsHolder.setBackgroundColor(bottomBarColor)
        updateNavigationBarColor(bottomBarColor)
    }

    private fun getPagerAdapter() = (binding.viewPager.adapter as? ViewPagerAdapter)

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_AUDIO_RECORD_VIEW or LICENSE_ANDROID_LAME or LICENSE_AUTOFITTEXTVIEW

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(com.simplemobiletools.commons.R.string.faq_9_title_commons, com.simplemobiletools.commons.R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(com.simplemobiletools.commons.R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(com.simplemobiletools.commons.R.string.faq_2_title_commons, com.simplemobiletools.commons.R.string.faq_2_text_commons))
            faqItems.add(FAQItem(com.simplemobiletools.commons.R.string.faq_6_title_commons, com.simplemobiletools.commons.R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

    private fun isThirdPartyIntent() = intent?.action == MediaStore.Audio.Media.RECORD_SOUND_ACTION

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun recordingSaved(event: Events.RecordingSaved) {
        if (isThirdPartyIntent()) {
            Intent().apply {
                data = event.uri!!
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                setResult(Activity.RESULT_OK, this)
            }
            finish()
        }
    }
}
