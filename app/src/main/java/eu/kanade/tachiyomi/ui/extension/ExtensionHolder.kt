package eu.kanade.tachiyomi.ui.extension

import android.view.View
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.databinding.ExtensionCardItemBinding
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.base.holder.SlicedHolder
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.LocaleHelper
import io.github.mthli.slice.Slice

class ExtensionHolder(view: View, override val adapter: ExtensionAdapter) :
    BaseFlexibleViewHolder(view, adapter),
    SlicedHolder {
    private val binding = ExtensionCardItemBinding.bind(view)

    override val slice =
        Slice(binding.card).apply {
            setColor(adapter.cardBackground)
        }

    override val viewToSlice: View
        get() = binding.card

    init {
        binding.extButton.setOnClickListener {
            adapter.buttonClickListener.onButtonClick(bindingAdapterPosition)
        }
        binding.cancelButton.setOnClickListener {
            adapter.buttonClickListener.onCancelButtonClick(bindingAdapterPosition)
        }
        binding.webButton.setOnClickListener {
            adapter.buttonClickListener.onWebButtonClick(bindingAdapterPosition)
        }
    }

    fun bind(item: ExtensionItem) {
        val extension = item.extension
        setCardEdges(item)

        // Set source name
        binding.extTitle.text = extension.name
        binding.version.text = extension.versionName
        binding.lang.text =
            if (extension !is Extension.Untrusted) {
                LocaleHelper.getSourceDisplayName(extension.lang, itemView.context)
            } else {
                itemView.context.getString(R.string.ext_untrusted).uppercase()
            }

        GlideApp.with(itemView.context).clear(binding.image)
        if (extension is Extension.Available) {
            GlideApp.with(itemView.context)
                .load(extension.iconUrl)
                .into(binding.image)
        } else {
            extension.getApplicationIcon(itemView.context)?.let { binding.image.setImageDrawable(it) }
        }
        bindButtons(item)
    }

    @Suppress("ResourceType")
    fun bindButtons(item: ExtensionItem) {
        val extension = item.extension
        val installStep = item.installStep
        val isIdle = installStep == InstallStep.Idle || installStep == InstallStep.Error

        // Tentukan icon berdasarkan state dan tipe extension
        val iconRes = when (installStep) {
            InstallStep.Pending,
            InstallStep.Downloading,
            InstallStep.Installing,
            InstallStep.Installed -> R.drawable.ic_file_download_black_24dp
            InstallStep.Error -> R.drawable.ic_info_24dp
            InstallStep.Idle -> when (extension) {
                is Extension.Installed -> when {
                    extension.hasUpdate -> R.drawable.ic_download_update_24dp
                    extension.isObsolete || extension.isUnofficial || extension.isRedundant -> R.drawable.ic_warning_white_24dp
                    else -> R.drawable.ic_settings_24dp
                }
                is Extension.Available -> R.drawable.ic_file_download_black_24dp
                is Extension.Untrusted -> R.drawable.ic_error_grey
                else -> R.drawable.ic_file_download_black_24dp
            }
        }

        binding.extButton.setImageResource(iconRes)

        val hasUpdate = extension is Extension.Installed && extension.hasUpdate
        if (hasUpdate) {
            binding.extButton.setColorFilter(
                android.graphics.Color.WHITE,
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        } else {
            binding.extButton.clearColorFilter()
        }

        binding.cancelButton.isVisible = !isIdle
        binding.webButton.isVisible = isIdle && extension is Extension.Available
        binding.extButton.isEnabled = isIdle
        binding.extButton.isClickable = isIdle
    }


}
