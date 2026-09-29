package com.syedali.flashquiz.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.syedali.flashquiz.R

/**
 * Account creation/login dialog.
 * Following the guide: sell the outcome, not the form.
 * Show what they get (synced progress, cloud backup) not just "Sign Up".
 */
class AccountDialogFragment : DialogFragment() {

    interface AccountListener {
        fun onAccountCreated(name: String, email: String)
        fun onLoggedIn(name: String, email: String)
    }

    private var listener: AccountListener? = null
    private var isLoginMode = false

    fun setListener(listener: AccountListener) {
        this.listener = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.tv_auth_title)
        val subtitle = view.findViewById<TextView>(R.id.tv_auth_subtitle)
        val nameField = view.findViewById<EditText>(R.id.edit_name)
        val emailField = view.findViewById<EditText>(R.id.edit_email)
        val passwordField = view.findViewById<EditText>(R.id.edit_password)
        val btnSubmit = view.findViewById<Button>(R.id.btn_auth_submit)
        val btnToggle = view.findViewById<TextView>(R.id.btn_auth_toggle)

        updateMode(title, subtitle, nameField, btnSubmit, btnToggle)

        btnSubmit.setOnClickListener {
            val name = nameField.text.toString().trim()
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            btnSubmit.isEnabled = false
            btnSubmit.text = if (isLoginMode) "Signing in..." else "Creating account..."

            if (isLoginMode) {
                AccountManager.login(email, password) { result ->
                    activity?.runOnUiThread {
                        btnSubmit.isEnabled = true
                        btnSubmit.text = "Sign In"
                        result.onSuccess {
                            listener?.onLoggedIn(AccountManager.getDisplayName(), email)
                            dismiss()
                        }
                        result.onFailure { e ->
                            Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                AccountManager.createAccount(email, name, password) { result ->
                    activity?.runOnUiThread {
                        btnSubmit.isEnabled = true
                        btnSubmit.text = "Create Account"
                        result.onSuccess {
                            listener?.onAccountCreated(name, email)
                            dismiss()
                        }
                        result.onFailure { e ->
                            Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        btnToggle.setOnClickListener {
            isLoginMode = !isLoginMode
            updateMode(title, subtitle, nameField, btnSubmit, btnToggle)
        }
    }

    private fun updateMode(
        title: TextView, subtitle: TextView,
        nameField: EditText, btnSubmit: Button, btnToggle: TextView
    ) {
        if (isLoginMode) {
            title.text = "Welcome back"
            subtitle.text = "Sign in to sync your progress across devices"
            nameField.visibility = View.GONE
            btnSubmit.text = "Sign In"
            btnToggle.text = "Don't have an account? Sign up"
        } else {
            title.text = "Create Account"
            subtitle.text = "Save your progress, sync across devices, and track your streaks"
            nameField.visibility = View.VISIBLE
            btnSubmit.text = "Create Account"
            btnToggle.text = "Already have an account? Sign in"
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
