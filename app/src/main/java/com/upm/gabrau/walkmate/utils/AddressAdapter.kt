package com.upm.gabrau.walkmate.utils

import android.location.Address
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.upm.gabrau.walkmate.databinding.GatheredAddressesBinding

class AddressAdapter(private val addressList: List<Address>, private val listener: OnAddressClicked) :
    RecyclerView.Adapter<AddressAdapter.AddressViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddressViewHolder {
        val binding = GatheredAddressesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AddressViewHolder(binding)
    }

    override fun getItemCount() = addressList.size

    override fun onBindViewHolder(holder: AddressViewHolder, position: Int) {
        with(holder) {
            with(addressList[position]) {
                val text = "${this.locality}, ${this.countryName}"
                binding.address.text = text
                binding.address.setOnClickListener{
                    listener.onAddressClicked(this)
                }
            }
        }
    }

    inner class AddressViewHolder(val binding: GatheredAddressesBinding) :
        RecyclerView.ViewHolder(binding.root)

    interface OnAddressClicked{ fun onAddressClicked(address: Address) }
}