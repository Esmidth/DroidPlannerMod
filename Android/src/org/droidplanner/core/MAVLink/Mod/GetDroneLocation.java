package org.droidplanner.core.MAVLink.Mod;

import org.droidplanner.core.helpers.coordinates.Coord2D;
import org.droidplanner.core.model.Drone;

/**
 * Created by Great Esmidth on 2015/10/4.
 */
public class GetDroneLocation {
    public Coord2D getDroneLocation(Drone drone)
    {
        return drone.getGps().getPosition();
    }
}
