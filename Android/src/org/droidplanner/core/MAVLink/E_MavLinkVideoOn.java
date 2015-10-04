package org.droidplanner.core.MAVLink;
import org.droidplanner.core.model.Drone;

import com.MAVLink.common.msg_command_long;
import com.MAVLink.enums.MAV_CMD;
/**
 * Created by Great Esmidth on 2015/10/4.
 */
public class E_MavLinkVideoOn {
    public static void sendVideoOnMessage(Drone drone,boolean on)
    {
        msg_command_long msg = new msg_command_long();
        msg.target_component = drone.getCompid();

    }
}
