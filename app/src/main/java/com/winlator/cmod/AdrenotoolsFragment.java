package com.winlator.cmod;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import com.winlator.cmod.R;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contents.AdrenotoolsManager;
import java.util.ArrayList;

public class AdrenotoolsFragment extends Fragment {
    
    private AdrenotoolsManager adrenotoolsManager;
    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private View btInstallDriver;
    private boolean isInstallingDriver;

    private void updateEmptyState() {
        if (tvEmpty == null || recyclerView.getAdapter() == null) return;
        tvEmpty.setVisibility(recyclerView.getAdapter().getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }
    
    @Override 
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.adrenotoolsManager = new AdrenotoolsManager(getActivity());
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup layout = (ViewGroup)inflater.inflate(R.layout.adrenotools_fragment, container, false);
        recyclerView = layout.findViewById(R.id.RecyclerView);
        tvEmpty = layout.findViewById(R.id.TVEmpty);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(new DriversAdapter(adrenotoolsManager.enumarateInstalledDrivers()));
        updateEmptyState();
        btInstallDriver = layout.findViewById(R.id.BTInstallDriver);
        View btPanVKRelease = layout.findViewById(R.id.BTPanVKRelease);
        btPanVKRelease.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/JimVulkan/mali-panvk/releases"));
            try {
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException exception) {
                Toast.makeText(getContext(), "No browser is available to open the PanVK release page.",
                        Toast.LENGTH_LONG).show();
            }
        });

        btInstallDriver.setOnClickListener((v) -> {
            ContentDialog.confirm(getContext(), getString(R.string.install_drivers_message) + " " + getString(R.string.install_drivers_warning), () -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                getActivity().startActivityFromFragment(this, intent, MainActivity.OPEN_FILE_REQUEST_CODE);               
            });
        });
        return layout;
    }
    
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ThemeManager.applyThemeToView(view, getContext());
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.adrenotools_gpu_drivers);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != MainActivity.OPEN_FILE_REQUEST_CODE || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null || isInstallingDriver) return;

        Uri uri = data.getData();
        isInstallingDriver = true;
        if (btInstallDriver != null) btInstallDriver.setEnabled(false);

        Thread installer = new Thread(() -> {
            String driver = adrenotoolsManager.installDriver(uri);
            Activity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                isInstallingDriver = false;
                if (!isAdded() || recyclerView == null || recyclerView.getAdapter() == null) return;
                if (btInstallDriver != null) btInstallDriver.setEnabled(true);

                if (!driver.isEmpty()) {
                    ((DriversAdapter) recyclerView.getAdapter()).addItem(driver);
                    Toast.makeText(getContext(), R.string.custom_driver_install_success,
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getContext(), R.string.custom_driver_install_failed,
                            Toast.LENGTH_LONG).show();
                }
            });
        }, "AdrenotoolsDriverInstaller");
        installer.start();
    }

    private class DriversAdapter extends RecyclerView.Adapter<DriversAdapter.ViewHolder> {
        private ArrayList<String> driversList;

        public class ViewHolder extends RecyclerView.ViewHolder {
            private TextView tvName;
            private TextView tvVersion;
            private ImageButton btMenu;

            public ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.TVName);
                tvVersion = v.findViewById(R.id.TVVersion);
                btMenu = v.findViewById(R.id.BTMenu);
            }
        }
        
        public DriversAdapter(ArrayList<String> driversList) {
            this.driversList = driversList;
        }
        
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.adrenotools_list_item, viewGroup, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder viewHolder, final int position) {
            String driver = driversList.get(position);
            String name = adrenotoolsManager.getDriverName(driver);
            viewHolder.tvName.setText(name);
            viewHolder.tvVersion.setText(adrenotoolsManager.getDriverVersion(driver));
            viewHolder.btMenu.setOnClickListener((v) -> {
                // Looked up at click time: the position captured at bind time is stale after an earlier removal.
                int current = viewHolder.getBindingAdapterPosition();
                if (current == RecyclerView.NO_POSITION) return;
                ContentDialog.confirm(getContext(), "Remove the driver \"" + name + "\"?", () -> removeAtIndex(current));
            });
        }

        public void addItem(String item) {
            driversList.add(item);
            notifyItemInserted(getItemCount() - 1);
            updateEmptyState();
        }

        public void removeAtIndex(int index) {
            if (index < 0 || index >= driversList.size()) return;
            String deletedDriver = driversList.remove(index);
            adrenotoolsManager.removeDriver(deletedDriver);
            notifyItemRemoved(index);
            notifyItemRangeChanged(index, getItemCount());
            updateEmptyState();
        }
        
        @Override
        public int getItemCount() {
            return driversList.size();
        }
    }
}
