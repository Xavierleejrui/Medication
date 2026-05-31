package com.example.medicationtracker.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.medicationtracker.R
import com.example.medicationtracker.data.local.MedicationDatabase
import com.example.medicationtracker.data.local.entities.Medication
import com.example.medicationtracker.data.repository.MedicationRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MedicationListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var repository: MedicationRepository
    private lateinit var adapter: MedicationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medication_list)

        recyclerView = findViewById(R.id.medicationRecyclerView)
        emptyText = findViewById(R.id.emptyText)

        val db = MedicationDatabase.getDatabase(this)
        repository = MedicationRepository(db.medicationDao())

        adapter = MedicationAdapter(
            onItemClick = { medication ->
                Toast.makeText(this, "${medication.name} selected", Toast.LENGTH_SHORT).show()
            },
            onItemLongClick = { medication ->
                // Show delete confirmation dialog
                AlertDialog.Builder(this)
                    .setTitle("Delete Medication")
                    .setMessage("Delete ${medication.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            repository.delete(medication)
                            Toast.makeText(
                                this@MedicationListActivity,
                                "${medication.name} deleted",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            repository.allMedications.collectLatest { medications ->
                adapter.submitList(medications)
                emptyText.visibility = if (medications.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (medications.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}

class MedicationAdapter(
    private val onItemClick: (Medication) -> Unit,
    private val onItemLongClick: (Medication) -> Unit
) : RecyclerView.Adapter<MedicationAdapter.ViewHolder>() {

    private var medications = listOf<Medication>()

    fun submitList(list: List<Medication>) {
        medications = list
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.medName)
        val dosageText: TextView = view.findViewById(R.id.medDosage)
        val scheduleText: TextView = view.findViewById(R.id.medSchedule)
        val hintText: TextView = view.findViewById(R.id.medHint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medication, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val med = medications[position]
        holder.nameText.text = med.name
        holder.dosageText.text = "Dosage: ${med.dosage}"
        holder.scheduleText.text = "Schedule: ${med.scheduleJson}"
        holder.hintText.text = "Long press to delete"
        holder.itemView.setOnClickListener { onItemClick(med) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(med)
            true
        }
    }

    override fun getItemCount() = medications.size
}