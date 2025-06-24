/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.ui;

import static android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
import static java.util.concurrent.Executors.newCachedThreadPool;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import net.fabiszewski.ulogger.Logger;
import net.fabiszewski.ulogger.OpenLocalDocument;
import net.fabiszewski.ulogger.R;
import net.fabiszewski.ulogger.db.DbAccess;
import net.fabiszewski.ulogger.tasks.LoggerTask;
import net.fabiszewski.ulogger.utils.LocationFormatter;
import net.fabiszewski.ulogger.utils.PermissionHelper;

import java.util.concurrent.ExecutorService;

public class WaypointFragment extends Fragment implements LoggerTask.LoggerTaskCallback, PermissionHelper.PermissionRequester {

    private static final String TAG = WaypointFragment.class.getSimpleName();
    private static final String KEY_URI = "keyPhotoUri";
    private static final String KEY_LOCATION = "keyLocation";
    private static final String KEY_WAITING = "keyWaiting";
    private static final String PERMISSION_LOCATION = "permissionLocation";
    private static final String PERMISSION_WRITE = "permissionWrite";

    private TextView locationNotFoundTextView;
    private TextView locationTextView;
    private TextView locationDetailsTextView;
    private EditText commentEditText;
    private Button saveButton;
    private SwipeRefreshLayout swipe;
    private AlertDialog dialog;
    private LoggerTask loggerTask;
    private Location location = null;
    private boolean isWaitingForCamera = false;

    private final ExecutorService executor = newCachedThreadPool();

    final PermissionHelper permissionHelper;

    public WaypointFragment() {
        permissionHelper = new PermissionHelper(this, this);
    }

    static WaypointFragment newInstance() {
        return new WaypointFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Logger.DEBUG) { Log.d(TAG, "[onCreate]"); }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (Logger.DEBUG) { Log.d(TAG, "[onCreateView]"); }
        View layout = inflater.inflate(R.layout.fragment_waypoint, container, false);

        locationNotFoundTextView = layout.findViewById(R.id.waypointLocationNotFound);
        locationTextView = layout.findViewById(R.id.waypointLocation);
        locationDetailsTextView = layout.findViewById(R.id.waypointLocationDetails);
        commentEditText = layout.findViewById(R.id.waypointComment);
        saveButton = layout.findViewById(R.id.waypointButton);
        swipe = (SwipeRefreshLayout) layout;
        swipe.setOnRefreshListener(this::reloadTask);

        saveButton.setOnClickListener(this::saveWaypoint);
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }
        return layout;
    }

    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    private void restoreState(@NonNull Bundle savedInstanceState) {
        if (Logger.DEBUG) { Log.d(TAG, "[restoreState]"); }
        if (savedInstanceState.containsKey(KEY_WAITING)) {
            isWaitingForCamera = true;
        }
        if (savedInstanceState.containsKey(KEY_LOCATION)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                location = savedInstanceState.getParcelable(KEY_LOCATION, Location.class);
            } else {
                location = savedInstanceState.getParcelable(KEY_LOCATION);
            }
            setLocationText();
            saveButton.setEnabled(true);
        }
    }

    private void reloadTask() {
        cancelLoggerTask();
        runLoggerTask();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (Logger.DEBUG) { Log.d(TAG, "[onSaveInstanceState]"); }
        if (location != null) {
            outState.putParcelable(KEY_LOCATION, location);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Logger.DEBUG) { Log.d(TAG, "[onResume]"); }
        if (!hasLocation()) {
            runLoggerTask();
        }
    }

    /**
     * Start logger task
     */
    private void runLoggerTask() {
        if (loggerTask == null || !loggerTask.isRunning()) {
            saveButton.setEnabled(false);
            location = null;
            clearLocationText();
            loggerTask = new LoggerTask(this);
            executor.execute(loggerTask);
            setRefreshing(true);
        }
    }

    /**
     * Stop logger task
     */
    private void cancelLoggerTask() {
        if (Logger.DEBUG) { Log.d(TAG, "[cancelLoggerTask]"); }
        if (loggerTask != null && loggerTask.isRunning()) {
            if (Logger.DEBUG) { Log.d(TAG, "[cancelLoggerTask effective]"); }
            loggerTask.cancel();
            loggerTask = null;
        }
    }

    private void setRefreshing(boolean refreshing) {
        swipe.setRefreshing(refreshing);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (Logger.DEBUG) { Log.d(TAG, "[onDetach]"); }
        cancelLoggerTask();
    }

    @Override
    public void onDestroy() {
        if (Logger.DEBUG) { Log.d(TAG, "[onDestroy]"); }
        if (isRemoving()) {
            if (Logger.DEBUG) { Log.d(TAG, "[onDestroy isRemoving]"); }
        }
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
        super.onDestroy();
    }

    /**
     * Display location details
     */
    private void setLocationText() {
        if (location != null) {
            LocationFormatter formatter = new LocationFormatter(location);
            locationNotFoundTextView.setVisibility(View.GONE);
            locationTextView.setText(String.format("%s\n—\n%s", formatter.getLongitudeDMS(), formatter.getLatitudeDMS()));
            locationTextView.setVisibility(View.VISIBLE);
            locationDetailsTextView.setText(formatter.getDetails(requireContext()));
        }
    }


    private void clearLocationText() {
        locationNotFoundTextView.setVisibility(View.GONE);
        locationTextView.setVisibility(View.VISIBLE);
        locationTextView.setText("");
        locationDetailsTextView.setText("");
    }

    /**
     * Save waypoint action
     * @param view View
     */
    private void saveWaypoint(@NonNull View view) {
        if (hasLocation()) {
            String comment = commentEditText.getText().toString();
            String uri = "NO";
            DbAccess.writeLocation(view.getContext(), location, comment, uri);
            if (Logger.DEBUG) { Log.d(TAG, "[saveWaypoint: " + location + ", " + comment + ", " + uri + "]"); }
        }
        finish();
    }

    /**
     * Go back to main fragment
     */
    private void finish() {
        requireActivity().getSupportFragmentManager().popBackStackImmediate();
    }

    private boolean hasLocation() {
        return location != null;
    }

    private boolean hasStoragePermission() {
        if (!permissionHelper.hasWriteExternalStoragePermission()) {
            showToast("You must accept permission for writing photo to external storage");
            permissionHelper.requestWriteExternalStoragePermission(PERMISSION_WRITE);
            return false;
        }
        return true;
    }

    /**
     * Display toast message
     * FIXME: duplicated method
     * @param text Message
     */
    private void showToast(CharSequence text) {
        Context context = getContext();
        if (context != null) {
            Toast toast = Toast.makeText(requireContext(), text, Toast.LENGTH_LONG);
            toast.show();
        }
    }

    /**
     * Update state on location received
     * @param location Current location
     */
    @Override
    public void onLoggerTaskCompleted(@NonNull Location location) {
        if (Logger.DEBUG) { Log.d(TAG, "[onLoggerTaskCompleted: " + location + "]"); }
        this.location = location;
        setLocationText();
        saveButton.setEnabled(true);
    }

    /**
     * Take actions on location request failure
     * @param reason Bit encoded failure reason
     */
    @Override
    public void onLoggerTaskFailure(int reason) {
        if (Logger.DEBUG) { Log.d(TAG, "[onLoggerTaskFailure: " + reason + "]"); }
        locationTextView.setVisibility(View.GONE);
        locationNotFoundTextView.setVisibility(View.VISIBLE);
        if ((reason & LoggerTask.E_PERMISSION) != 0) {
            showToast(getString(R.string.location_permission_denied));
            permissionHelper.requestFineLocationPermission(PERMISSION_LOCATION);
        }
        if ((reason & LoggerTask.E_DISABLED) != 0) {
            showToast(getString(R.string.location_disabled));
        }
    }

    @Override
    public void onPermissionGranted(@Nullable String requestCode) {
        if (PERMISSION_LOCATION.equals(requestCode)) {
            if (Logger.DEBUG) { Log.d(TAG, "[LocationPermission: granted]"); }
            runLoggerTask();
        } else if (PERMISSION_WRITE.equals(requestCode)) {
            if (Logger.DEBUG) { Log.d(TAG, "[WritePermission: granted]"); }
        }

    }

    @Override
    public void onPermissionDenied(@Nullable String requestCode) {
        if (PERMISSION_LOCATION.equals(requestCode)) {
            if (Logger.DEBUG) { Log.d(TAG, "[LocationPermission: refused]"); }
            finish();
        } else if (PERMISSION_WRITE.equals(requestCode)) {
            if (Logger.DEBUG) { Log.d(TAG, "[WritePermission: refused]"); }

        }
    }
}
