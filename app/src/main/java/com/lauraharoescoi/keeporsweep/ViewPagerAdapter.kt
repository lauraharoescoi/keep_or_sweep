package com.lauraharoescoi.keeporsweep

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int {
        return 2 // Tenemos dos pestañas
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PhotosFragment()
            1 -> VideosFragment()
            else -> throw IllegalStateException("Posición inválida: $position")
        }
    }
}