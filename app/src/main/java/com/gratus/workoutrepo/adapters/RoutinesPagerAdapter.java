package com.gratus.workoutrepo.adapters; //class moved

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.google.android.material.button.MaterialButton;
import com.gratus.workoutrepo.R;
import com.gratus.workoutrepo.RoutinesActivity;
import com.gratus.workoutrepo.model.Routine;
import com.gratus.workoutrepo.utils.ExpandableNoteHelper;
import com.gratus.workoutrepo.utils.TextFormatUtils;

import java.util.List;

public class RoutinesPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private String activeRoutineId; // New field
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
            ((RoutineViewHolder) holder).bind(routines.get(position), activeRoutineId, listener);
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

        RoutineViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.routineId);
            notesDetails = v.findViewById(R.id.notesDetails);
            dayList = v.findViewById(R.id.rv_day_items);
            btnApply = v.findViewById(R.id.apply_routine_btn);
            btnSave = v.findViewById(R.id.save_routine_btn);
            btnDelete = v.findViewById(R.id.delete_routine_btn);
            btnExpand = v.findViewById(R.id.expand_notes_rout);

            // Setup internal RecyclerView for days (ReadOnlyDayAdapter not shown for brevity, but needed)
            dayList.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(v.getContext()));
        }

        // Update bind method
        void bind(Routine routine, String activeId, RoutinesActivity.RoutineActionListener listener) {
            title.setText(routine.title);
            ExpandableNoteHelper.setupNoteState(notesDetails, btnExpand, routine.notes);

            btnExpand.setOnClickListener(v -> ExpandableNoteHelper.toggleNote(notesDetails, btnExpand));
            //notesDetails.setText(TextFormatUtils.formatNotesForDisplay(routine.notes));
            RoutineDayAdapter dayAdapter = new RoutineDayAdapter(routine.days);
            dayList.setAdapter(dayAdapter);

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

            title.setOnLongClickListener(v -> { listener.onEditRoutineMeta(routine, "title"); return true; });
            notesDetails.setOnLongClickListener(v -> { listener.onEditRoutineMeta(routine, "notes"); return true; });
            btnSave.setOnClickListener(v -> listener.onExport(routine));
        }
    }
}