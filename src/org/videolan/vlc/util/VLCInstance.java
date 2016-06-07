/*****************************************************************************
 * VLCInstance.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.VLCCrashHandler;


public class VLCInstance {
    public final static String TAG = "VLC/Util/VLCInstance";
    private static LibVLC sLibVLC = null;

    public static LibVLC get() throws IllegalStateException {
    	if (sLibVLC == null) {
    		final Context context = VLCApplication.getAppContext();
    		return get(context);
    	}
    	return sLibVLC;
    }
    /** A set of utility functions for the VLC application */
    public synchronized static LibVLC get(Context context) throws IllegalStateException {
        if (sLibVLC == null) {
            Thread.setDefaultUncaughtExceptionHandler(new VLCCrashHandler());

            sLibVLC = new LibVLC();
            
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            VLCInstance.updateLibVlcSettings(pref);
            try {
                sLibVLC.init(context);
            } catch (LibVlcException e) {
                throw new IllegalStateException("LibVLC initialisation failed: XXXXXXXXXXXXXXXXXXXXXXXXXX");
            }
            LibVLC.setOnNativeCrashListener(new LibVLC.OnNativeCrashListener() {
                @Override
                public void onNativeCrash() {
                    //Intent i = new Intent(context, NativeCrashActivity.class);
                    //i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    //i.putExtra("PID", android.os.Process.myPid());
                    //context.startActivity(i);
                	Log.e(TAG, "NativeCrash");
                }
            });
        }
        return sLibVLC;
    }

    public static synchronized void restart(Context context) throws IllegalStateException {
        if (sLibVLC != null) {
            try {
                sLibVLC.destroy();
                sLibVLC.init(context);
            } catch (LibVlcException lve) {
                throw new IllegalStateException("LibVLC initialisation failed: OOOOOOOOOOOOOOOOOOOOOOOOOOOOOO");
            }
        }
    }


    public static synchronized void updateLibVlcSettings(SharedPreferences pref) {
        if (sLibVLC == null)
            return;

        sLibVLC.setSubtitlesEncoding(pref.getString("subtitle_text_encoding", ""));
        sLibVLC.setTimeStretching(pref.getBoolean("enable_time_stretching_audio", false));
        
        /*Speed up decoding but could lower video quality*/
        sLibVLC.setFrameSkip(pref.getBoolean("enable_frame_skip", true));
        
        /*force video chroma,
         * RV32 - default
         * RV16 - better performance but lower quality
         * YV12 - best performance
         * */
        sLibVLC.setChroma(pref.getString("chroma_format", "RV32"));
        sLibVLC.setVerboseMode(pref.getBoolean("enable_verbose_mode", true));

        if (pref.getBoolean("equalizer_enabled", false))
            sLibVLC.setEqualizer(Preferences.getFloatArray(pref, "equalizer_values"));

        int aout;
        try {
            aout = Integer.parseInt(pref.getString("aout", "1"));
        }
        catch (NumberFormatException nfe) {
            aout = 1;
        }
        int vout;
        try {
        	vout = Integer.parseInt(pref.getString("vout", "-1"));
        }
        catch (NumberFormatException nfe) {
        	vout = -1;
        }
        /*int deblocking;
        try {
            deblocking = Integer.parseInt(pref.getString("deblocking", "4"));
        }
        catch(NumberFormatException nfe) {
            deblocking = -1;
        }*/
        /*int hardwareAcceleration;
        try {
            hardwareAcceleration = Integer.parseInt(pref.getString("hardware_acceleration", "2"));
        }
        catch(NumberFormatException nfe) {
            hardwareAcceleration = -1;
        }*/
        int devHardwareDecoder;
        try {
            devHardwareDecoder = Integer.parseInt(pref.getString("dev_hardware_decoder", "-1"));
        }
        catch(NumberFormatException nfe) {
            devHardwareDecoder = -1;
        }
        /*int networkCaching = pref.getInt("network_caching_value", 60000);
        if(networkCaching > 60000)
            networkCaching = 60000;
        else if(networkCaching < 0)
            networkCaching = 0;*/
        
        sLibVLC.setAout(aout);
        sLibVLC.setVout(vout);
        
        /*Change the deblocking filter settings. Could improve video quality.
         * -1 : auto
         * 0 : Full deblocking (slowest)
         * 1 : Medium deblocking
         * 3 : Low deblocking
         * 4 : No deblocking (fastest)
         **/
        sLibVLC.setDeblocking(4);
        
        /*The amount of time to buffer network media (in ms). Does not work with hardware decoding.*/
        sLibVLC.setNetworkCaching(10);
        
        /* -1 : auto
         * 0 : Disabled: better stability.
         * 1 : Decoding: may improve performance.
         * 2 : Full: may improve performance further.*/
        sLibVLC.setHardwareAcceleration(-1);
        sLibVLC.setDevHardwareDecoder(devHardwareDecoder);
    }

    public static synchronized void setAudioHdmiEnabled(Context context, boolean enabled) {
        if (sLibVLC != null && sLibVLC.isHdmiAudioEnabled() != enabled) {
            sLibVLC.setHdmiAudioEnabled(enabled);
            restart(context);
        }
    }
}
