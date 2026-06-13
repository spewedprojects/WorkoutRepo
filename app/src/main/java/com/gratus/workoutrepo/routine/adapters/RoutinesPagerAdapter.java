package com.gratus.workoutrepo.routine.adapters; //class moved

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.gratus.workoutrepo.R;
import com.gratus.workoutrepo.RoutinesActivity;
import com.gratus.workoutrepo.routine.model.Routine;
import com.gratus.workoutrepo.utils.ExpandableNoteHelper;

import java.util.List;

public class RoutinesPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private String activeRoutineId; // New field
    private String editingRoutineId = null; // Edit mode state
    private List<Routine> routines;
    private final RoutinesActivity.RoutineActionListener listener;
    private static final int TYPE_ROUTINE = 0;
    private static final int TYPE_ADD_NEW = 1;

    // Update Constructor
    public RoutinesPagerAdapter(List<Routine> routines, String activeRoutineId, RoutinesActivity.RoutineActionListener listener) {
        this.routines = routines;
        this.activeRoutineId = activeRoutineId;
        this.listener = listener;
    }

    public String getEditingRoutineId() {
        return editingRoutineId;
    }

    public void setEditingRoutineId(String id) {
        this.editingRoutineId = id;
    }

    public void exitEditMode() {
        if (editingRoutineId != null) {
            editingRoutineId = null;
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemViewType(int position) {
        return (position == routines.size()) ? TYPE_ADD_NEW : TYPE_ROUTINE;
    }

    @Override
    public int getItemCount() {
        return routines.size() + 1; // +1 for the "Add New" screen at the end
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Get screen width
        int screenWidth = parent.getContext().getResources().getDisplayMetrics().widthPixels;

        if (viewType == TYPE_ADD_NEW) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.add_routine_ui, parent, false);

            // LOGIC: Set "Add" card to 85% width to create the "Drawer/Peek" effect
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    (int)(screenWidth * 0.65),
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            v.setLayoutParams(lp);
            return new AddViewHolder(v);

        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.routines_view, parent, false);

            // LOGIC: Normal routines are Full Screen (Match Parent)
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    screenWidth, // Forces full width snapping
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            v.setLayoutParams(lp);
            return new RoutineViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_ADD_NEW) {
            ((AddViewHolder) holder).bind(listener);
        } else {
            // Pass active ID logic here
            Routine routine = routines.get(position);
            ((RoutineViewHolder) holder).bind(routine, activeRoutineId, listener, routine.id.equals(editingRoutineId), this);
        }
    }

    // --- ViewHolders ---

    static class AddViewHolder extends RecyclerView.ViewHolder {
        View addBlankBtn, importBtn;
        AddViewHolder(View v) {
            super(v);
            addBlankBtn = v.findViewById(R.id.addBlank_btn);
            importBtn = v.findViewById(R.id.import_btn);
        }
        void bind(RoutinesActivity.RoutineActionListener listener) {
            addBlankBtn.setOnClickListener(v -> listener.onAddBlank());
            importBtn.setOnClickListener(v -> listener.onImport());
        }
    }

    static class RoutineViewHolder extends RecyclerView.ViewHolder {
        TextView title, notesDetails;
        RecyclerView dayList;
        ImageButton btnDelete, btnExpand;
        MaterialButton btnApply, btnSave;
        View btnEdit;

        RoutineViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.routineId);
            notesDetails = v.findViewById(R.id.notesDetails);
            dayList = v.findViewById(R.id.rv_day_items);
            btnApply = v.findViewById(R.id.apply_routine_btn);
            btnSave = v.findViewById(R.id.save_routine_btn);
            btnDelete = v.findViewById(R.id.delete_routine_btn);
            btnExpand = v.findViewById(R.id.expand_notes_rout);
            btnEdit = v.findViewById(R.id.edit_routine_btn);

            // Setup internal RecyclerView for days (ReadOnlyDayAdapter not shown for brevity, but needed)
            dayList.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(v.getContext()));
        }

        // Update bind method
        void bind(Routine routine, String activeId, RoutinesActivity.RoutineActionListener listener, boolean isEditMode, RoutinesPagerAdapter adapter) {
            title.setText(routine.title);
            ExpandableNoteHelper.setupNoteState(notesDetails, btnExpand, routine.notes);

            btnExpand.setOnClickListener(v -> ExpandableNoteHelper.toggleNote(notesDetails, btnExpand));
            //notesDetails.setText(TextFormatUtils.formatNotesForDisplay(routine.notes));
            RoutineDayAdapter dayAdapter = new RoutineDayAdapter(routine, isEditMode, listener);
            dayList.setAdapter(dayAdapter);

            if (isEditMode) {
                btnDelete.setEnabled(false);
                btnDelete.setAlpha(0.3f);
                btnApply.setEnabled(false);
                btnApply.setAlpha(0.3f);
                btnSave.setText("CONFIRM");
                btnSave.setOnClickListener(v -> {
                    adapter.editingRoutineId = null;
                    listener.onConfirmEdit(routine);
                });
                if (btnEdit != null) {
                    btnEdit.setBackgroundResource(R.drawable.bg_edit_active_circle);
                }
            } else {
                btnDelete.setEnabled(true);
                btnDelete.setAlpha(1.0f);
                btnApply.setAlpha(1.0f);
                btnSave.setText("EXPORT");
                btnSave.setOnClickListener(v -> listener.onExport(routine));
                if (btnEdit != null) {
                    btnEdit.setBackgroundResource(android.R.color.transparent);
                }

                // LOGIC: Hide delete button if this is the active routine
                if (routine.id.equals(activeId)) {
                    btnDelete.setVisibility(View.INVISIBLE); // Or GONE
                    btnDelete.setOnClickListener(null);

                    // Optional: You can also disable the "APPLY" button since it's already applied
                    btnApply.setText("APPLIED");
                    btnApply.setEnabled(false);
                } else {
                    btnDelete.setVisibility(View.VISIBLE);
                    btnDelete.setOnClickListener(v -> listener.onDelete(routine));

                    btnApply.setText("APPLY");
                    btnApply.setEnabled(true);
                    btnApply.setOnClickListener(v -> listener.onApply(routine));
                }
            }

            if (btnEdit != null) {
                btnEdit.setOnClickListener(v -> {
                    if (isEditMode) {
                        adapter.editingRoutineId = null;
                    } else {
                        adapter.editingRoutineId = routine.id;
                    }
                    adapter.notifyDataSetChanged();
                });
            }

            title.setOnLongClickListener(v -> { listener.onEditRoutineMeta(routine, "title"); return true; });
            notesDetails.setOnLongClickListener(v -> { listener.onEditRoutineMeta(routine, "notes"); return true; });
        }
    }
}