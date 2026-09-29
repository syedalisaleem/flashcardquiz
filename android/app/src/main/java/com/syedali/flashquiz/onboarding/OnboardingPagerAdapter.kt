package com.syedali.flashquiz.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.syedali.flashquiz.R

class OnboardingPagerAdapter(
    private val pages: List<Int>,
    private val onProfilePictureTap: (() -> Unit)? = null,
    private val onGetStarted: (() -> Unit)? = null
) : RecyclerView.Adapter<OnboardingPagerAdapter.PageViewHolder>() {

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        if (position == 3) {
            // Page 4 — wire up profile picture tap
            val cameraOverlay = holder.itemView.findViewById<View>(R.id.camera_overlay)
            cameraOverlay?.setOnClickListener {
                onProfilePictureTap?.invoke()
            }
            // Wire up the Get Started button
            val btnGetStarted = holder.itemView.findViewById<View>(R.id.btn_get_started)
            btnGetStarted?.setOnClickListener {
                onGetStarted?.invoke()
            }
        }
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemViewType(position: Int): Int = pages[position]
}
