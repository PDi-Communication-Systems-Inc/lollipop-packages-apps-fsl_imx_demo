/*
/* Copyright 2012-2015 Freescale Semiconductor, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fsl.android.ota;

import android.os.SystemProperties;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import android.util.Log;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.Calendar;
import static java.util.concurrent.TimeUnit.*;
import android.content.ComponentName;

// TODO: get the configure from a configure file.
public class OTAServerConfig {
	
        final String default_serveraddr = "ota.pdiarm.com";
	final String default_protocol = "http";
	final int default_port = 80;
	URL updatePackageURL;
	URL buildpropURL;
        private final String default_build_prop_file = "build.prop";
        private final String default_ota_zip_file = ".ota.zip";
	private long delay;
	String product;
	final String TAG = "OTA_SC";
	final String configFileSys = "/system/etc/ota.conf";
        final String configFileData = "/data/system/ota.conf";
        public static final String protocol_tag = "protocol";
	final String server_ip_config = "server";
	final String port_config_str = "port";
        public static final String build_tag = "build";
        public static final String ota_tag = "ota";
	public static final String monthly_tag = "monthly";

	public OTAServerConfig (String productname) throws MalformedURLException {

                /* Try to load user configured ota.conf first, 
                   if that does not exist use the default system 
                   ota.conf */
                File configFile = new File(configFileData);
                if (!configFile.exists()) {
                   configFile = new File(configFileSys);
                }

		if (loadConfigureFromFile(configFile, productname) == false) {
                   Log.w(TAG, "Loading default configuration for product " + productname + ".");
		   defaultConfigure(productname);
		}
	}

	boolean loadConfigureFromFile (File configFile, String product) {
		try {
			BuildPropParser parser = new BuildPropParser(configFile, null);
                                                  
                        String protocol = parser.getProp(protocol_tag);                    
                        if (protocol == null) {
                           Log.w(TAG, "Using default protocol " + default_protocol + "\n");
                           protocol = default_protocol;
                        }

			String server = parser.getProp(server_ip_config);
                        if (server == null) {
			   Log.w(TAG, "Using default server " + default_serveraddr + "\n");
                           server = default_serveraddr;
                        }
			String port_str = parser.getProp(port_config_str);
                        int port;
                        if (port_str != null) {
                           port = new Long(port_str).intValue();
                        }
                        else {
			   Log.w(TAG, "Using default port " + default_port + "\n");
                           port = default_port;
                        }
                        String ota_filename = parser.getProp(ota_tag);
                        if (ota_filename == null) {
                           Log.w(TAG, "Using default OTA suffix " + default_ota_zip_file + "\n");
                           ota_filename = default_ota_zip_file;
                        }
                        String build_filename = parser.getProp(build_tag);
                        if (build_filename == null) {
                           Log.w(TAG, "Using default build config suffix " + 
                                 default_build_prop_file + "\n");
                           build_filename = default_build_prop_file;
                        }
			String build_monthly_check = parser.getProp(monthly_tag);

			String fileaddr = new String(product + "/" + product + ota_filename);
			String buildconfigAddr = new String(product + "/" + build_filename);

			updatePackageURL = new URL(default_protocol, server, port, fileaddr);
			buildpropURL = new URL(default_protocol, server, port, buildconfigAddr);

			Log.d(TAG, "Package is at URL: " + updatePackageURL);
			Log.d(TAG, "Build Property is at URL: " + buildpropURL);

                        // Prepare automate check if enabled
                        if (build_monthly_check != null) {
                           Calendar c = Calendar.getInstance();
                           long checkTime = Long.parseLong(build_monthly_check);
                           delay = checkTime - c.getTimeInMillis();
                           if (delay <= 0) {
                              // Determine next 30 day delay if original value expired
                              delay = 2592000000L;
                              parser.setProp(monthly_tag,Long.toString(delay));
                           }
                        }
                        else {
                            delay = Long.MIN_VALUE;
                        }

		} catch (Exception e) {
			Log.e(TAG, "wrong format/error of OTA configure file.");
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	void defaultConfigure(String productname) throws MalformedURLException
	{
		product = productname;
		String fileaddr = new String(product + "/" + product + default_ota_zip_file);
		String buildconfigAddr = new String(product + "/" + default_build_prop_file); 
		updatePackageURL = new URL(default_protocol, default_serveraddr, default_port, fileaddr );
		buildpropURL = new URL(default_protocol, default_serveraddr, default_port, buildconfigAddr);
		Log.d(TAG, "create a new server config: package url " + updatePackageURL.toString() + ":" + updatePackageURL.getPort());
		Log.d(TAG, "build.prop URL:" + buildpropURL.toString());
	}
	
	public URL getPackageURL () { return updatePackageURL; }
	public URL getBuildPropURL() { return buildpropURL; }
	
}
