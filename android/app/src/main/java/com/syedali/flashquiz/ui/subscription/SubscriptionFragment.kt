package com.syedali.flashquiz.ui.subscription

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.syedali.flashquiz.R
import com.syedali.flashquiz.billing.SubscriptionManager

class SubscriptionFragment : Fragment() {

    private lateinit var btnMonthly: LinearLayout
    private lateinit var btnYearly: LinearLayout
    private lateinit var btnStartTrial: Button
    private lateinit var btnRestore: Button
    private lateinit var tvStatus: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.screen_subscription, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnMonthly = view.findViewById(R.id.btn_monthly)
        btnYearly = view.findViewById(R.id.btn_yearly)
        btnStartTrial = view.findViewById(R.id.btn_start_trial)
        btnRestore = view.findViewById(R.id.btn_restore)
        tvStatus = view.findViewById(R.id.tv_status)

        // Check current subscription status
        SubscriptionManager.checkAdFree { isAdFree ->
            activity?.runOnUiThread {
                if (isAdFree) {
                    btnMonthly.alpha = 0.5f
                    btnYearly.alpha = 0.5f
                    btnStartTrial.alpha = 0.5f
                    btnMonthly.isEnabled = false
                    btnYearly.isEnabled = false
                    btnStartTrial.isEnabled = false
                    tvStatus.text = "Premium active - ads removed"
                    tvStatus.visibility = View.VISIBLE
                } else {
                    tvStatus.visibility = View.GONE
                }
            }
        }

        // Start free trial button defaults to yearly plan
        btnStartTrial.setOnClickListener {
            setPurchasing(true)
            SubscriptionManager.purchaseYearly(requireActivity()) { success, error ->
                activity?.runOnUiThread {
                    setPurchasing(false)
                    if (success) {
                        Toast.makeText(requireContext(), "Premium activated! Ads removed.", Toast.LENGTH_LONG).show()
                        parentFragmentManager.popBackStack()
                    } else if (error != "Cancelled") {
                        Toast.makeText(requireContext(), "Purchase failed: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        btnMonthly.setOnClickListener {
            setPurchasing(true)
            SubscriptionManager.purchaseMonthly(requireActivity()) { success, error ->
                activity?.runOnUiThread {
                    setPurchasing(false)
                    if (success) {
                        Toast.makeText(requireContext(), "Premium activated! Ads removed.", Toast.LENGTH_LONG).show()
                        parentFragmentManager.popBackStack()
                    } else if (error != "Cancelled") {
                        Toast.makeText(requireContext(), "Purchase failed: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        btnYearly.setOnClickListener {
            setPurchasing(true)
            SubscriptionManager.purchaseYearly(requireActivity()) { success, error ->
                activity?.runOnUiThread {
                    setPurchasing(false)
                    if (success) {
                        Toast.makeText(requireContext(), "Premium activated! Ads removed.", Toast.LENGTH_LONG).show()
                        parentFragmentManager.popBackStack()
                    } else if (error != "Cancelled") {
                        Toast.makeText(requireContext(), "Purchase failed: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        btnRestore.setOnClickListener {
            btnRestore.isEnabled = false
            btnRestore.text = "Restoring..."
            SubscriptionManager.restorePurchases { isAdFree ->
                activity?.runOnUiThread {
                    btnRestore.isEnabled = true
                    btnRestore.text = "Restore Purchase"
                    if (isAdFree) {
                        Toast.makeText(requireContext(), "Premium restored! Ads removed.", Toast.LENGTH_LONG).show()
                        parentFragmentManager.popBackStack()
                    } else {
                        Toast.makeText(requireContext(), "No active subscription found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setPurchasing(purchasing: Boolean) {
        btnMonthly.isEnabled = !purchasing
        btnYearly.isEnabled = !purchasing
        btnStartTrial.isEnabled = !purchasing
        btnMonthly.alpha = if (purchasing) 0.5f else 1f
        btnYearly.alpha = if (purchasing) 0.5f else 1f
        btnStartTrial.alpha = if (purchasing) 0.5f else 1f
    }
}
