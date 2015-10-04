package org.droidplanner.core.MAVLink.Mod;

import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Created by Great Esmidth on 2015/10/4.
 */
public class GetPhoneLocation {
    public void openGPSSettings(){
        LocationManager alm = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (alm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "GPS works well", Toast.LENGTH_SHORT).show();
            return;
        } else {
            Toast.makeText(this, "Please turn on GPS!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            startActivityForResult(intent, 0);
        }
    }
}
